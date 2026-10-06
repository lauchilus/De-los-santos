package com.agua.backend.sincronizacion;

import com.agua.backend.jornada.JornadaResponse;
import com.agua.backend.jornada.JornadaService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Descarga completa. Las listas se relacionan por UUID; nunca contiene credenciales. */
@Service
@RequiredArgsConstructor
public class DatosOfflineService {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;

    public record Producto(UUID id, String codigo, String nombre, String tipo,
            Integer capacidadLitros, BigDecimal precioVenta, boolean activo, long version) {}
    public record Cliente(UUID id, String nombre, String direccion, String telefono,
            String observaciones, boolean activo, long version, BigDecimal saldo, boolean tieneAbonoRegistrado) {}
    public record Existencia(UUID productoId, String estado, long cantidad) {}
    public record Tenencia(UUID clienteId, UUID productoId, long cantidad) {}
    public record Precio(UUID clienteId, UUID productoId, BigDecimal precio, boolean activo, long version) {}
    public record Plan(UUID id, String nombre, BigDecimal precio, boolean activo, long version) {}
    public record ItemPlan(UUID planId, UUID productoId, int cantidad) {}
    public record Abono(UUID id, UUID clienteId, UUID planId, String planNombre, long planVersion,
            LocalDate periodo, BigDecimal importe) {}
    public record Cupo(UUID abonoId, UUID productoId, int incluidos, int consumidos, int disponibles) {}
    public record Categoria(UUID id, String nombre, boolean activo, long version) {}
    public record Datos(int versionFormato, Instant generadoEn, String zonaHoraria, LocalDate periodo,
            JornadaResponse jornada, List<Existencia> stockJornada, List<Producto> productos,
            List<Cliente> clientes, List<Tenencia> envasesClientes, List<Tenencia> maquinasClientes,
            List<Precio> preciosClientes, List<Plan> planesAbono, List<ItemPlan> itemsPlanesAbono,
            List<Abono> abonosClientes, List<Cupo> cuposAbono, List<Categoria> categoriasGasto) {}

    // Transaccion propia para garantizar el aislamiento incluso desde otro servicio.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public Datos descargar(UUID jornadaId, String username) {
        var jornada = jornadas.obtener(jornadaId, username); // Autorizar antes de leer datos compartidos.
        var ahora = Instant.now();
        var periodo = ahora.atZone(ZONA).toLocalDate().withDayOfMonth(1);
        var stock = jdbc.query("SELECT producto_id,estado,cantidad FROM stock_jornada WHERE jornada_id=? ORDER BY producto_id,estado",
                (r,n)->new Existencia(r.getObject(1,UUID.class),r.getString(2),r.getLong(3)),jornadaId);
        var productos = jdbc.query("SELECT id,codigo,nombre,tipo,capacidad_litros,precio_venta,activo,version FROM productos ORDER BY codigo",
                (r,n)->new Producto(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),
                        r.getObject(5,Integer.class),r.getBigDecimal(6),r.getBoolean(7),r.getLong(8)));
        var clientes = jdbc.query("""
                SELECT c.id,c.nombre,c.direccion,c.telefono,c.observaciones,c.activo,c.version,
                       COALESCE(cc.saldo,0),EXISTS(SELECT 1 FROM abonos_cliente a WHERE a.cliente_id=c.id)
                FROM clientes c LEFT JOIN cuentas_clientes cc ON cc.cliente_id=c.id ORDER BY c.id
                """,(r,n)->new Cliente(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),
                        r.getString(5),r.getBoolean(6),r.getLong(7),r.getBigDecimal(8),r.getBoolean(9)));
        var envases = jdbc.query("SELECT cliente_id,producto_id,cantidad FROM envases_cliente WHERE cantidad>0 ORDER BY cliente_id,producto_id",
                (r,n)->new Tenencia(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3)));
        var maquinas = jdbc.query("SELECT cliente_id,producto_id,cantidad FROM maquinas_cliente WHERE cantidad>0 ORDER BY cliente_id,producto_id",
                (r,n)->new Tenencia(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3)));
        // La ultima version inactiva tambien importa: no recuperar por error un precio anterior.
        var precios = jdbc.query("""
                SELECT DISTINCT ON(cliente_id,producto_id) cliente_id,producto_id,precio,activo,version
                FROM precios_cliente ORDER BY cliente_id,producto_id,version DESC
                """,(r,n)->new Precio(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getBigDecimal(3),r.getBoolean(4),r.getLong(5)));
        var planes = jdbc.query("SELECT id,nombre,precio,activo,version FROM planes_abono ORDER BY id",
                (r,n)->new Plan(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3),r.getBoolean(4),r.getLong(5)));
        var items = jdbc.query("SELECT plan_id,producto_id,cantidad FROM items_plan_abono ORDER BY plan_id,producto_id",
                (r,n)->new ItemPlan(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3)));
        var abonos = jdbc.query("""
                SELECT id,cliente_id,plan_id,plan_nombre,plan_version,periodo,importe
                FROM abonos_cliente WHERE periodo=? ORDER BY creado_en,id
                """,(r,n)->new Abono(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),
                        r.getString(4),r.getLong(5),r.getDate(6).toLocalDate(),r.getBigDecimal(7)),java.sql.Date.valueOf(periodo));
        var cupos = jdbc.query("""
                SELECT c.abono_id,c.producto_id,c.incluidos,c.consumidos,c.incluidos-c.consumidos
                FROM cupos_abono c JOIN abonos_cliente a ON a.id=c.abono_id
                WHERE a.periodo=? ORDER BY a.creado_en,a.id,c.producto_id
                """,(r,n)->new Cupo(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3),r.getInt(4),r.getInt(5)),java.sql.Date.valueOf(periodo));
        var categorias = jdbc.query("SELECT id,nombre,activo,version FROM categorias_gasto ORDER BY id",
                (r,n)->new Categoria(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getLong(4)));
        return new Datos(1,ahora,ZONA.getId(),periodo,jornada,stock,productos,clientes,envases,maquinas,
                precios,planes,items,abonos, cupos,categorias);
    }
}
