package com.agua.backend.abono;

import com.agua.backend.usuario.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AbonoService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;

    public record ItemPlan(@NotNull UUID productoId, @NotNull @Positive Integer cantidad) {}
    public record PlanRequest(@NotBlank @Size(max=150) String nombre,
            @NotNull @DecimalMin("0.00") @Digits(integer=10, fraction=2) BigDecimal precio,
            @NotNull Boolean activo, @PositiveOrZero Long version,
            @NotEmpty @Size(max=100) List<@NotNull @Valid ItemPlan> items) {}
    public record Plan(UUID id, String nombre, BigDecimal precio, boolean activo, long version, List<ItemPlan> items) {}
    public record Carga(@NotNull UUID planId, @NotNull @PositiveOrZero Long planVersion, @NotNull LocalDate periodo) {}
    public record Cupo(UUID productoId, int incluidos, int consumidos, int disponibles) {}
    public record Abono(UUID id, UUID planId, String planNombre, long planVersion, LocalDate periodo,
                        BigDecimal importe, UUID entregaId, boolean vigente, List<Cupo> cupos) {}

    public List<Plan> planes() {
        return jdbc.query("SELECT id FROM planes_abono ORDER BY nombre, id", (rs,n)->rs.getObject(1,UUID.class))
                .stream().map(this::plan).toList();
    }
    public Plan plan(UUID id) {
        var filas = jdbc.query("SELECT * FROM planes_abono WHERE id=?", (rs,n)-> new Plan(id,
                rs.getString("nombre"),rs.getBigDecimal("precio"),rs.getBoolean("activo"),rs.getLong("version"),List.of()),id);
        if(filas.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Plan no encontrado");
        var p=filas.getFirst();
        var items=jdbc.query("SELECT producto_id,cantidad FROM items_plan_abono WHERE plan_id=? ORDER BY producto_id",
                (rs,n)->new ItemPlan(rs.getObject(1,UUID.class),rs.getInt(2)),id);
        return new Plan(id,p.nombre(),p.precio(),p.activo(),p.version(),items);
    }
    @Transactional
    public Plan guardar(UUID id, PlanRequest r, String username) {
        var actor=admin(username);
        var ids=new HashSet<UUID>();
        if(r.nombre().isBlank()) throw error(HttpStatus.BAD_REQUEST,"Nombre requerido");
        for(var i:r.items()) if(!ids.add(i.productoId())) throw error(HttpStatus.BAD_REQUEST,"Producto repetido");
        var orden=r.items().stream().sorted(Comparator.comparing(i->i.productoId().toString())).toList();
        if(id!=null) {
            var versiones=jdbc.query("SELECT version FROM planes_abono WHERE id=? FOR UPDATE",(rs,n)->rs.getLong(1),id);
            if(versiones.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Plan no encontrado");
            if(!Objects.equals(r.version(),versiones.getFirst())) throw error(HttpStatus.CONFLICT,"El plan cambió; actualizá la consulta");
        } else if(r.version()!=null) throw error(HttpStatus.BAD_REQUEST,"El alta no lleva version");
        for(var i:orden) {
            var tipos=jdbc.query("SELECT tipo FROM productos WHERE id=? AND activo=true FOR SHARE",(rs,n)->rs.getString(1),i.productoId());
            if(tipos.isEmpty() || !"BIDON".equals(tipos.getFirst())) throw error(HttpStatus.BAD_REQUEST,"El plan admite bidones activos");
        }
        if(id==null) {
            id=UUID.randomUUID();
            jdbc.update("INSERT INTO planes_abono(id,nombre,precio,activo,usuario_id) VALUES(?,?,?,?,?)",
                    id,r.nombre().strip(),r.precio(),r.activo(),actor.getId());
        } else {
            jdbc.update("UPDATE planes_abono SET nombre=?,precio=?,activo=?,version=version+1,usuario_id=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=?",
                    r.nombre().strip(),r.precio(),r.activo(),actor.getId(),id);
            jdbc.update("DELETE FROM items_plan_abono WHERE plan_id=?",id);
        }
        for(var i:orden) jdbc.update("INSERT INTO items_plan_abono VALUES(?,?,?)",id,i.productoId(),i.cantidad());
        return plan(id);
    }

    // Llamado dentro de la transacción de entrega, con jornada y cliente bloqueados.
    @Transactional
    public BigDecimal cargar(UUID entregaId, UUID clienteId, Carga r, LocalDate hoy) {
        if(r==null) return BigDecimal.ZERO;
        if(!r.periodo().equals(hoy.withDayOfMonth(1))) throw error(HttpStatus.BAD_REQUEST,"Solo se carga el mes calendario actual; periodo debe ser su primer día");
        var bloqueado=jdbc.query("SELECT id FROM planes_abono WHERE id=? FOR SHARE",(rs,n)->rs.getObject(1,UUID.class),r.planId());
        if(bloqueado.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Plan no encontrado");
        var p=plan(r.planId());
        if(!p.activo()) throw error(HttpStatus.CONFLICT,"Plan inactivo");
        if(p.version()!=r.planVersion()) throw error(HttpStatus.CONFLICT,"El plan cambió; consultalo nuevamente");
        UUID id=UUID.randomUUID();
        int insertados=jdbc.update("""
                INSERT INTO abonos_cliente(id,cliente_id,plan_id,periodo,entrega_id,plan_nombre,plan_version,importe)
                VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(cliente_id,plan_id,periodo) DO NOTHING
                """,id,clienteId,p.id(),r.periodo(),entregaId,p.nombre(),p.version(),p.precio());
        if(insertados==0) throw error(HttpStatus.CONFLICT,"Este cliente ya tiene cargado ese plan en el mes");
        for(var i:p.items()) jdbc.update("INSERT INTO cupos_abono(abono_id,producto_id,incluidos) VALUES(?,?,?)",id,i.productoId(),i.cantidad());
        return p.precio();
    }

    @Transactional
    public int consumir(UUID entregaId, UUID clienteId, UUID productoId, int entregados, LocalDate hoy) {
        if(entregados==0) return 0;
        var cupos=jdbc.query("""
                SELECT a.id,c.incluidos-c.consumidos AS disponible FROM abonos_cliente a
                JOIN cupos_abono c ON c.abono_id=a.id
                WHERE a.cliente_id=? AND a.periodo=? AND c.producto_id=? AND c.consumidos<c.incluidos
                ORDER BY a.creado_en,a.id FOR UPDATE OF c
                """,(rs,n)->new Disponible(rs.getObject(1,UUID.class),rs.getInt(2)),clienteId,hoy.withDayOfMonth(1),productoId);
        int restantes=entregados;
        for(var c:cupos) {
            int cantidad=Math.min(restantes,c.cantidad());
            if(cantidad==0) break;
            jdbc.update("UPDATE cupos_abono SET consumidos=consumidos+? WHERE abono_id=? AND producto_id=?",cantidad,c.id(),productoId);
            jdbc.update("INSERT INTO consumos_abono VALUES(?,?,?,?)",entregaId,productoId,c.id(),cantidad);
            restantes-=cantidad;
        }
        return entregados-restantes;
    }
    private record Disponible(UUID id,int cantidad) {}

    public Abono deEntrega(UUID entregaId) {
        var ids=jdbc.query("SELECT id FROM abonos_cliente WHERE entrega_id=?",(rs,n)->rs.getObject(1,UUID.class),entregaId);
        return ids.isEmpty()?null:obtener(ids.getFirst());
    }
    public List<Abono> cliente(UUID clienteId, LocalDate periodo) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clientes WHERE id=?)",Boolean.class,clienteId)))
            throw error(HttpStatus.NOT_FOUND,"Cliente no encontrado");
        if(periodo.getDayOfMonth()!=1) throw error(HttpStatus.BAD_REQUEST,"Periodo debe ser el primer día del mes");
        return jdbc.query("SELECT id FROM abonos_cliente WHERE cliente_id=? AND periodo=? ORDER BY creado_en,id",
                (rs,n)->rs.getObject(1,UUID.class),clienteId,periodo).stream().map(this::obtener).toList();
    }
    private Abono obtener(UUID id) {
        var a=jdbc.queryForObject("SELECT * FROM abonos_cliente WHERE id=?",(rs,n)->new Abono(id,
                rs.getObject("plan_id",UUID.class),rs.getString("plan_nombre"),rs.getLong("plan_version"),
                rs.getObject("periodo",LocalDate.class),rs.getBigDecimal("importe"),rs.getObject("entrega_id",UUID.class),rs.getObject("periodo",LocalDate.class).equals(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).withDayOfMonth(1)),List.of()),id);
        var cupos=jdbc.query("SELECT producto_id,incluidos,consumidos FROM cupos_abono WHERE abono_id=? ORDER BY producto_id",
                (rs,n)->new Cupo(rs.getObject(1,UUID.class),rs.getInt(2),rs.getInt(3),a.vigente() ? rs.getInt(2)-rs.getInt(3) : 0),id);
        return new Abono(a.id(),a.planId(),a.planNombre(),a.planVersion(),a.periodo(),a.importe(),a.entregaId(),a.vigente(),cupos);
    }
    private Usuario admin(String username) {
        var u=usuarios.findByUsername(username).filter(Usuario::isActivo).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Usuario no disponible"));
        if(u.getRol()!=Rol.ADMIN) throw error(HttpStatus.FORBIDDEN,"Se requiere administrador");
        return u;
    }
    private ResponseStatusException error(HttpStatus s,String m){return new ResponseStatusException(s,m);}
}
