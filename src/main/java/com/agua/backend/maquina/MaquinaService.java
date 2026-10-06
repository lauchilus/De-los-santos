package com.agua.backend.maquina;

import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class MaquinaService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final UsuarioRepository usuarios;
    public record Request(@NotNull UUID operacionId,@NotNull UUID clienteId,@NotNull UUID productoId,
                          @NotNull @PositiveOrZero Integer entregadas,@NotNull @PositiveOrZero Integer retiradas) {}
    public record Movimiento(UUID id,UUID jornadaId,UUID clienteId,UUID productoId,UUID usuarioId,
                             int entregadas,int retiradas,long saldoCliente,long saldoJornada,OffsetDateTime creadoEn) {}
    public record Tenencia(UUID productoId,String codigo,String nombre,long cantidad) {}
    @Transactional
    public Movimiento registrar(UUID jornadaId,Request r,String username) {
        jornadas.obtener(jornadaId,username);
        var actor=usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Usuario no disponible"));
        if(r.entregadas()==0 && r.retiradas()==0) throw error(HttpStatus.BAD_REQUEST,"Indicá entrega o retiro");
        var anterior=buscar(r.operacionId());
        if(anterior!=null) return reintento(anterior,jornadaId,r,actor.getId());
        var estado=jdbc.queryForObject("SELECT estado FROM jornadas WHERE id=? FOR UPDATE",String.class,jornadaId);
        anterior=buscar(r.operacionId());
        if(anterior!=null) return reintento(anterior,jornadaId,r,actor.getId());
        if(!"ABIERTA".equals(estado)) throw error(HttpStatus.CONFLICT,"La jornada está cerrada");
        var clientes=jdbc.query("SELECT activo FROM clientes WHERE id=? FOR NO KEY UPDATE",(rs,n)->rs.getBoolean(1),r.clienteId());
        if(clientes.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Cliente no encontrado");
        if(r.entregadas()>0 && !clientes.getFirst()) throw error(HttpStatus.CONFLICT,"Cliente inactivo");
        var productos=jdbc.query("SELECT tipo FROM productos WHERE id=? FOR SHARE",(rs,n)->rs.getString(1),r.productoId());
        if(productos.isEmpty() || !"MAQUINA".equals(productos.getFirst())) throw error(HttpStatus.BAD_REQUEST,"Se requiere un producto MAQUINA");
        if(r.entregadas()>0 && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT activo FROM productos WHERE id=?",Boolean.class,r.productoId())))
            throw error(HttpStatus.CONFLICT,"Producto inactivo");
        // Reservar la operación antes de modificar stock. El UUID también protege entre jornadas.
        int insertados=jdbc.update("""
                INSERT INTO movimientos_maquina(id,jornada_id,cliente_id,producto_id,usuario_id,entregadas,retiradas,saldo_cliente,saldo_jornada)
                VALUES(?,?,?,?,?,?,?,0,0) ON CONFLICT(id) DO NOTHING
                """,r.operacionId(),jornadaId,r.clienteId(),r.productoId(),actor.getId(),r.entregadas(),r.retiradas());
        if(insertados==0) return reintento(buscar(r.operacionId()),jornadaId,r,actor.getId());
        if(r.entregadas()>0 && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM abonos_cliente WHERE cliente_id=?)",Boolean.class,r.clienteId())))
            throw error(HttpStatus.CONFLICT,"El cliente debe tener un abono registrado para recibir máquinas");
        jdbc.update("INSERT INTO maquinas_cliente(cliente_id,producto_id) VALUES(?,?) ON CONFLICT DO NOTHING",r.clienteId(),r.productoId());
        long variacion=(long)r.entregadas()-r.retiradas();
        var saldos=jdbc.query("""
                UPDATE maquinas_cliente SET cantidad=cantidad+?
                WHERE cliente_id=? AND producto_id=? AND cantidad>=? RETURNING cantidad
                """,(rs,n)->rs.getLong(1),variacion,r.clienteId(),r.productoId(),r.retiradas());
        if(saldos.isEmpty()) throw error(HttpStatus.CONFLICT,"El retiro supera las máquinas del cliente");
        jdbc.update("INSERT INTO stock_jornada(jornada_id,producto_id,estado,cantidad) VALUES(?,?,'DISPONIBLE',0) ON CONFLICT DO NOTHING",jornadaId,r.productoId());
        var stock=jdbc.query("""
                UPDATE stock_jornada SET cantidad=cantidad-?,actualizado_en=CURRENT_TIMESTAMP
                WHERE jornada_id=? AND producto_id=? AND estado='DISPONIBLE' AND cantidad>=? RETURNING cantidad
                """,(rs,n)->rs.getLong(1),variacion,jornadaId,r.productoId(),r.entregadas());
        if(stock.isEmpty()) throw error(HttpStatus.CONFLICT,"Stock de máquinas insuficiente en la jornada");
        jdbc.update("UPDATE movimientos_maquina SET saldo_cliente=?,saldo_jornada=? WHERE id=?",saldos.getFirst(),stock.getFirst(),r.operacionId());
        return buscar(r.operacionId());
    }
    public Movimiento obtener(UUID jornadaId,UUID id,String username) {
        jornadas.obtener(jornadaId,username);
        var r=buscar(id);
        if(r==null || !r.jornadaId().equals(jornadaId)) throw error(HttpStatus.NOT_FOUND,"Movimiento no encontrado");
        return r;
    }
    public List<Tenencia> cliente(UUID id) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clientes WHERE id=?)",Boolean.class,id)))
            throw error(HttpStatus.NOT_FOUND,"Cliente no encontrado");
        return jdbc.query("SELECT p.id,p.codigo,p.nombre,m.cantidad FROM maquinas_cliente m JOIN productos p ON p.id=m.producto_id WHERE cliente_id=? ORDER BY p.codigo",
                (rs,n)->new Tenencia(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4)),id);
    }
    private Movimiento buscar(UUID id) {
        var filas=jdbc.query("SELECT * FROM movimientos_maquina WHERE id=?",(rs,n)->new Movimiento(id,
                rs.getObject("jornada_id",UUID.class),rs.getObject("cliente_id",UUID.class),rs.getObject("producto_id",UUID.class),
                rs.getObject("usuario_id",UUID.class),rs.getInt("entregadas"),rs.getInt("retiradas"),rs.getLong("saldo_cliente"),
                rs.getLong("saldo_jornada"),rs.getObject("creado_en",OffsetDateTime.class)),id);
        return filas.isEmpty()?null:filas.getFirst();
    }
    private Movimiento reintento(Movimiento m,UUID jornada,Request r,UUID actor) {
        if(m==null || !m.jornadaId().equals(jornada) || !m.clienteId().equals(r.clienteId()) || !m.productoId().equals(r.productoId())
                || !m.usuarioId().equals(actor) || m.entregadas()!=r.entregadas() || m.retiradas()!=r.retiradas())
            throw error(HttpStatus.CONFLICT,"El identificador de operación ya fue usado con otros datos");
        return m;
    }
    private ResponseStatusException error(HttpStatus s,String m){return new ResponseStatusException(s,m);}
}
