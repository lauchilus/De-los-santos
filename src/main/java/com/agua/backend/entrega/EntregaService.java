package com.agua.backend.entrega;

import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntregaService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final UsuarioRepository usuarios;

    @Transactional
    public EntregaResponse registrar(UUID jornadaId, CrearEntregaRequest request, String username) {
        jornadas.obtener(jornadaId, username);
        Usuario actor = actor(username);
        var items = validarYOrdenar(request);
        var existente = buscar(request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, request.clienteId(), actor.getId(), items);

        // Mismo bloqueo que cargas y futuro cierre: una operación por jornada a la vez.
        String estado = jdbc.queryForObject("SELECT estado FROM jornadas WHERE id = ? FOR UPDATE", String.class, jornadaId);
        existente = buscar(request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, request.clienteId(), actor.getId(), items);
        if (!"ABIERTA".equals(estado)) throw error(HttpStatus.CONFLICT, "La jornada está cerrada");

        // Serializa tenencia y cuenta incluso si dos jornadas atienden al mismo cliente.
        var clientes = jdbc.query("SELECT activo FROM clientes WHERE id = ? FOR NO KEY UPDATE",
                (rs, fila) -> rs.getBoolean("activo"), request.clienteId());
        if (clientes.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        if (!clientes.getFirst()) throw error(HttpStatus.CONFLICT, "El cliente está inactivo");
        int insertados = jdbc.update("""
                INSERT INTO entregas (id, jornada_id, cliente_id, usuario_id)
                VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.operacionId(), jornadaId, request.clienteId(), actor.getId());
        if (insertados == 0) {
            return validarReintento(buscar(request.operacionId()), jornadaId, request.clienteId(), actor.getId(), items);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (var item : items) {
            total = total.add(procesarItem(request.operacionId(), jornadaId, request.clienteId(), item));
        }
        BigDecimal saldo = jdbc.queryForObject("""
                INSERT INTO cuentas_clientes (cliente_id, saldo) VALUES (?, ?)
                ON CONFLICT (cliente_id) DO UPDATE
                SET saldo = cuentas_clientes.saldo + EXCLUDED.saldo, actualizado_en = CURRENT_TIMESTAMP
                RETURNING saldo
                """, BigDecimal.class, request.clienteId(), total);
        if (total.signum() > 0) {
            jdbc.update("""
                    INSERT INTO movimientos_cuenta_cliente
                        (id, cliente_id, entrega_id, tipo, importe, saldo_resultante, usuario_id)
                    VALUES (?, ?, ?, 'ENTREGA', ?, ?, ?)
                    """, UUID.randomUUID(), request.clienteId(), request.operacionId(), total, saldo, actor.getId());
        }
        jdbc.update("UPDATE entregas SET total = ?, saldo_cliente = ? WHERE id = ?", total, saldo, request.operacionId());
        return buscar(request.operacionId());
    }

    public EntregaResponse obtener(UUID jornadaId, UUID entregaId, String username) {
        jornadas.obtener(jornadaId, username);
        var entrega = buscar(entregaId);
        if (entrega == null || !entrega.jornadaId().equals(jornadaId)) {
            throw error(HttpStatus.NOT_FOUND, "Entrega no encontrada en esta jornada");
        }
        return entrega;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SituacionClienteResponse situacion(UUID clienteId, String username) {
        actor(username);
        var saldos = jdbc.query("""
                SELECT COALESCE(c.saldo, 0) AS saldo FROM clientes p
                LEFT JOIN cuentas_clientes c ON c.cliente_id = p.id WHERE p.id = ?
                """, (rs, fila) -> rs.getBigDecimal("saldo"), clienteId);
        if (saldos.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        var envases = jdbc.query("""
                SELECT p.id, p.codigo, p.nombre, e.cantidad FROM envases_cliente e
                JOIN productos p ON p.id = e.producto_id WHERE e.cliente_id = ? ORDER BY p.codigo
                """, (rs, fila) -> new SituacionClienteResponse.Envases(rs.getObject("id", UUID.class),
                rs.getString("codigo"), rs.getString("nombre"), rs.getLong("cantidad")), clienteId);
        return new SituacionClienteResponse(clienteId, saldos.getFirst(), envases);
    }

    private BigDecimal procesarItem(UUID entregaId, UUID jornadaId, UUID clienteId, CrearEntregaRequest.Item item) {
        // Conserva el precio/version usados; un cambio posterior no altera entregas ya confirmadas.
        var productos = jdbc.query("""
                SELECT codigo, tipo, activo, precio_venta, version FROM productos WHERE id = ? FOR SHARE
                """, (rs, fila) -> new ProductoEntrega(rs.getString("codigo"), rs.getString("tipo"),
                rs.getBoolean("activo"), rs.getBigDecimal("precio_venta"), rs.getLong("version")), item.productoId());
        if (productos.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Producto no encontrado: " + item.productoId());
        var producto = productos.getFirst();
        if (!"BIDON".equals(producto.tipo())) throw error(HttpStatus.BAD_REQUEST, "Esta operación admite solo bidones");
        if (!producto.activo()) throw error(HttpStatus.CONFLICT, "Producto inactivo: " + producto.codigo());

        BigDecimal precio = null;
        BigDecimal subtotal = BigDecimal.ZERO;
        if (item.entregados() > 0) {
            if (producto.precio() == null) throw error(HttpStatus.CONFLICT, "Precio sin configurar: " + producto.codigo());
            if (!Objects.equals(item.precioVersion(), producto.version())) {
                throw error(HttpStatus.CONFLICT, "El precio cambió: consultá el producto antes de confirmar la entrega");
            }
            precio = producto.precio();
            subtotal = precio.multiply(BigDecimal.valueOf(item.entregados()));
        }

        jdbc.update("INSERT INTO envases_cliente (cliente_id, producto_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                clienteId, item.productoId());
        long variacion = item.entregados().longValue() - item.devueltosVacios().longValue() - item.devueltosDanados().longValue();
        var envases = jdbc.query("""
                UPDATE envases_cliente SET cantidad = cantidad + ?, actualizado_en = CURRENT_TIMESTAMP
                WHERE cliente_id = ? AND producto_id = ? AND cantidad + ? >= 0 RETURNING cantidad
                """, (rs, fila) -> rs.getLong("cantidad"), variacion, clienteId, item.productoId(), variacion);
        if (envases.isEmpty()) {
            throw error(HttpStatus.CONFLICT, "La devolución supera los envases registrados del cliente: " + producto.codigo());
        }

        moverStock(entregaId, jornadaId, item.productoId(), "LLENO", -item.entregados().longValue());
        moverStock(entregaId, jornadaId, item.productoId(), "VACIO", item.devueltosVacios().longValue());
        moverStock(entregaId, jornadaId, item.productoId(), "DANADO", item.devueltosDanados().longValue());
        jdbc.update("""
                INSERT INTO detalles_entrega
                    (entrega_id, producto_id, entregados, devueltos_vacios, devueltos_danados,
                     precio_version, precio_unitario, subtotal, envases_resultantes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entregaId, item.productoId(), item.entregados(), item.devueltosVacios(), item.devueltosDanados(),
                item.precioVersion(), precio, subtotal, envases.getFirst());
        return subtotal;
    }

    private void moverStock(UUID entregaId, UUID jornadaId, UUID productoId, String estado, long cantidad) {
        if (cantidad == 0) return;
        long saldo;
        if (cantidad < 0) {
            var saldos = jdbc.query("""
                    UPDATE stock_jornada SET cantidad = cantidad + ?, actualizado_en = CURRENT_TIMESTAMP
                    WHERE jornada_id = ? AND producto_id = ? AND estado = ? AND cantidad >= ? RETURNING cantidad
                    """, (rs, fila) -> rs.getLong("cantidad"), cantidad, jornadaId, productoId, estado, -cantidad);
            if (saldos.isEmpty()) throw error(HttpStatus.CONFLICT, "Stock de jornada insuficiente: " + productoId);
            saldo = saldos.getFirst();
        } else {
            saldo = jdbc.queryForObject("""
                    INSERT INTO stock_jornada (jornada_id, producto_id, estado, cantidad) VALUES (?, ?, ?, ?)
                    ON CONFLICT (jornada_id, producto_id, estado) DO UPDATE
                    SET cantidad = stock_jornada.cantidad + EXCLUDED.cantidad, actualizado_en = CURRENT_TIMESTAMP
                    RETURNING cantidad
                    """, Long.class, jornadaId, productoId, estado, cantidad);
        }
        jdbc.update("""
                INSERT INTO movimientos_stock_jornada
                    (id, entrega_id, jornada_id, producto_id, estado, cantidad, saldo_resultante)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), entregaId, jornadaId, productoId, estado, cantidad, saldo);
    }

    private List<CrearEntregaRequest.Item> validarYOrdenar(CrearEntregaRequest request) {
        if (request == null || request.operacionId() == null || request.clienteId() == null
                || request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw error(HttpStatus.BAD_REQUEST, "Se requiere operación, cliente y entre 1 y 100 productos");
        }
        var ids = new HashSet<UUID>();
        for (var item : request.items()) {
            if (item == null || item.productoId() == null || item.entregados() == null || item.devueltosVacios() == null
                    || item.devueltosDanados() == null || item.entregados() < 0 || item.devueltosVacios() < 0
                    || item.devueltosDanados() < 0) throw error(HttpStatus.BAD_REQUEST, "Cantidades inválidas");
            if (item.entregados() == 0 && item.devueltosVacios() == 0 && item.devueltosDanados() == 0) {
                throw error(HttpStatus.BAD_REQUEST, "Cada producto debe tener entrega o devolución");
            }
            if (item.entregados() > 0 && (item.precioVersion() == null || item.precioVersion() < 0)) {
                throw error(HttpStatus.BAD_REQUEST, "Indicá la versión del precio para los bidones entregados");
            }
            if (item.entregados() == 0 && item.precioVersion() != null) {
                throw error(HttpStatus.BAD_REQUEST, "Una devolución sin entrega no requiere precioVersion");
            }
            if (!ids.add(item.productoId())) throw error(HttpStatus.BAD_REQUEST, "Producto repetido");
        }
        return request.items().stream().sorted(Comparator.comparing(item -> item.productoId().toString())).toList();
    }

    private EntregaResponse buscar(UUID id) {
        var filas = jdbc.query("""
                SELECT id, jornada_id, cliente_id, usuario_id, total, saldo_cliente, creado_en FROM entregas WHERE id = ?
                """, (rs, fila) -> new EntregaResponse(rs.getObject("id", UUID.class), rs.getObject("jornada_id", UUID.class),
                rs.getObject("cliente_id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getBigDecimal("total"),
                rs.getBigDecimal("saldo_cliente"), rs.getObject("creado_en", OffsetDateTime.class), List.of()), id);
        if (filas.isEmpty()) return null;
        var e = filas.getFirst();
        var items = jdbc.query("""
                SELECT producto_id, entregados, devueltos_vacios, devueltos_danados, precio_version,
                       precio_unitario, subtotal, envases_resultantes
                FROM detalles_entrega WHERE entrega_id = ? ORDER BY producto_id
                """, (rs, fila) -> new EntregaResponse.Item(rs.getObject("producto_id", UUID.class), rs.getInt("entregados"),
                rs.getInt("devueltos_vacios"), rs.getInt("devueltos_danados"), rs.getObject("precio_version", Long.class),
                rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("subtotal"), rs.getLong("envases_resultantes")), id);
        return new EntregaResponse(e.id(), e.jornadaId(), e.clienteId(), e.usuarioId(), e.total(), e.saldoCliente(), e.creadoEn(), items);
    }

    private EntregaResponse validarReintento(EntregaResponse e, UUID jornadaId, UUID clienteId, UUID usuarioId,
                                             List<CrearEntregaRequest.Item> items) {
        if (e == null || !e.jornadaId().equals(jornadaId) || !e.clienteId().equals(clienteId)
                || !e.usuarioId().equals(usuarioId) || e.items().size() != items.size()) {
            throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
        }
        for (int i = 0; i < items.size(); i++) {
            var anterior = e.items().get(i);
            var nuevo = items.get(i);
            if (!anterior.productoId().equals(nuevo.productoId()) || anterior.entregados() != nuevo.entregados()
                    || anterior.devueltosVacios() != nuevo.devueltosVacios() || anterior.devueltosDanados() != nuevo.devueltosDanados()
                    || !Objects.equals(anterior.precioVersion(), nuevo.precioVersion())) {
                throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
            }
        }
        return e;
    }

    private Usuario actor(String username) {
        return usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private record ProductoEntrega(String codigo, String tipo, boolean activo, BigDecimal precio, Long version) {}
}
