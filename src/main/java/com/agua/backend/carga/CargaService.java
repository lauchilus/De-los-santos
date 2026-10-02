package com.agua.backend.carga;

import com.agua.backend.jornada.JornadaService;
import com.agua.backend.stock.EstadoStock;
import com.agua.backend.stock.StockResponse;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CargaService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadaService;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public CargaResponse cargar(UUID jornadaId, CrearCargaRequest request, String username) {
        // Reutiliza la autorización: administrador o repartidor asignado a la jornada.
        jornadaService.obtener(jornadaId, username);
        Usuario actor = usuarioRepository.findByUsername(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        List<CrearCargaRequest.Item> items = ordenarYValidar(request);

        // Un reintento confirmado sigue siendo válido aunque la jornada se haya cerrado.
        CargaResponse existente = buscar(request.operacionId());
        if (existente != null) {
            return validarReintento(existente, jornadaId, actor.getId(), items);
        }

        // Serializa las cargas de una jornada y coordina con su futuro cierre.
        String estado = jdbc.queryForObject(
                "SELECT estado FROM jornadas WHERE id = ? FOR UPDATE", String.class, jornadaId);
        existente = buscar(request.operacionId());
        if (existente != null) {
            return validarReintento(existente, jornadaId, actor.getId(), items);
        }
        if (!"ABIERTA".equals(estado)) {
            throw error(HttpStatus.CONFLICT, "La jornada está cerrada");
        }

        Integer numero = jdbc.queryForObject(
                "SELECT COALESCE(MAX(numero), 0) + 1 FROM cargas_jornada WHERE jornada_id = ?",
                Integer.class, jornadaId);
        String tipo = numero == 1 ? "INICIAL" : "RECARGA";
        int insertados = jdbc.update("""
                INSERT INTO cargas_jornada (id, jornada_id, numero, tipo, usuario_id)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.operacionId(), jornadaId, numero, tipo, actor.getId());
        if (insertados == 0) {
            return validarReintento(buscar(request.operacionId()), jornadaId, actor.getId(), items);
        }

        // UUID en orden textual coincide con el orden de PostgreSQL usado en producción.
        // Toda la carga se revierte si una sola línea falla.
        for (CrearCargaRequest.Item item : items) {
            transferir(jornadaId, request.operacionId(), actor.getId(), item);
        }
        return buscar(request.operacionId());
    }

    public CargaResponse obtener(UUID jornadaId, UUID cargaId, String username) {
        jornadaService.obtener(jornadaId, username);
        CargaResponse carga = buscar(cargaId);
        if (carga == null || !carga.jornadaId().equals(jornadaId)) {
            throw error(HttpStatus.NOT_FOUND, "Carga no encontrada en esta jornada");
        }
        return carga;
    }

    public List<StockResponse> stock(UUID jornadaId, String username) {
        jornadaService.obtener(jornadaId, username);
        return jdbc.query("""
                SELECT p.id, p.codigo, p.nombre, s.estado, s.cantidad
                FROM stock_jornada s JOIN productos p ON p.id = s.producto_id
                WHERE s.jornada_id = ? ORDER BY p.codigo, s.estado
                """, (rs, fila) -> new StockResponse(
                rs.getObject("id", UUID.class), rs.getString("codigo"), rs.getString("nombre"),
                EstadoStock.valueOf(rs.getString("estado")), rs.getLong("cantidad")), jornadaId);
    }

    private List<CrearCargaRequest.Item> ordenarYValidar(CrearCargaRequest request) {
        if (request == null || request.operacionId() == null || request.items() == null
                || request.items().isEmpty() || request.items().size() > 100) {
            throw error(HttpStatus.BAD_REQUEST, "La carga requiere operación y entre 1 y 100 productos");
        }
        var productos = new HashSet<UUID>();
        for (var item : request.items()) {
            if (item == null || item.productoId() == null || item.cantidad() == null || item.cantidad() <= 0) {
                throw error(HttpStatus.BAD_REQUEST, "Cada producto requiere una cantidad positiva");
            }
            if (!productos.add(item.productoId())) {
                throw error(HttpStatus.BAD_REQUEST, "No repitas un producto dentro de la misma carga");
            }
        }
        return request.items().stream()
                .sorted(Comparator.comparing(item -> item.productoId().toString())).toList();
    }

    private void transferir(UUID jornadaId, UUID cargaId, UUID actorId, CrearCargaRequest.Item item) {
        var productos = jdbc.query("""
                SELECT codigo, tipo, activo FROM productos WHERE id = ?
                """, (rs, fila) -> new ProductoCarga(rs.getString("codigo"),
                rs.getString("tipo"), rs.getBoolean("activo")), item.productoId());
        if (productos.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "Producto no encontrado: " + item.productoId());
        }
        var producto = productos.getFirst();
        if (!producto.activo()) {
            throw error(HttpStatus.CONFLICT, "Producto inactivo: " + producto.codigo());
        }
        String estado = "BIDON".equals(producto.tipo()) ? "LLENO" : "DISPONIBLE";
        long cantidad = item.cantidad().longValue();
        var saldos = jdbc.query("""
                UPDATE stock_fabrica SET cantidad = cantidad - ?, actualizado_en = CURRENT_TIMESTAMP
                WHERE producto_id = ? AND estado = ? AND cantidad >= ? RETURNING cantidad
                """, (rs, fila) -> rs.getLong("cantidad"), cantidad, item.productoId(), estado, cantidad);
        if (saldos.isEmpty()) {
            throw error(HttpStatus.CONFLICT, "Stock insuficiente o no inicializado: " + producto.codigo());
        }
        long saldoFabrica = saldos.getFirst();
        long saldoJornada = jdbc.queryForObject("""
                INSERT INTO stock_jornada (jornada_id, producto_id, estado, cantidad)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (jornada_id, producto_id, estado) DO UPDATE
                SET cantidad = stock_jornada.cantidad + EXCLUDED.cantidad,
                    actualizado_en = CURRENT_TIMESTAMP
                RETURNING cantidad
                """, Long.class, jornadaId, item.productoId(), estado, cantidad);
        jdbc.update("""
                INSERT INTO detalles_carga_jornada
                    (carga_id, producto_id, estado, cantidad, saldo_fabrica, saldo_jornada)
                VALUES (?, ?, ?, ?, ?, ?)
                """, cargaId, item.productoId(), estado, cantidad, saldoFabrica, saldoJornada);
        jdbc.update("""
                INSERT INTO movimientos_stock_fabrica
                    (id, producto_id, estado, tipo, cantidad, saldo_resultante, motivo, usuario_id, carga_id)
                VALUES (?, ?, ?, 'CARGA', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), item.productoId(), estado, -cantidad, saldoFabrica,
                "Carga de jornada " + jornadaId, actorId, cargaId);
    }

    private CargaResponse buscar(UUID id) {
        var cabeceras = jdbc.query("""
                SELECT id, jornada_id, numero, tipo, usuario_id, creado_en FROM cargas_jornada WHERE id = ?
                """, (rs, fila) -> new CargaResponse(rs.getObject("id", UUID.class),
                rs.getObject("jornada_id", UUID.class), rs.getInt("numero"), rs.getString("tipo"),
                rs.getObject("usuario_id", UUID.class), rs.getObject("creado_en", OffsetDateTime.class),
                List.of()), id);
        if (cabeceras.isEmpty()) return null;
        var carga = cabeceras.getFirst();
        var items = jdbc.query("""
                SELECT producto_id, estado, cantidad, saldo_fabrica, saldo_jornada
                FROM detalles_carga_jornada WHERE carga_id = ? ORDER BY producto_id
                """, (rs, fila) -> new CargaResponse.Item(rs.getObject("producto_id", UUID.class),
                EstadoStock.valueOf(rs.getString("estado")), rs.getInt("cantidad"),
                rs.getLong("saldo_fabrica"), rs.getLong("saldo_jornada")), id);
        return new CargaResponse(carga.id(), carga.jornadaId(), carga.numero(), carga.tipo(),
                carga.usuarioId(), carga.creadoEn(), items);
    }

    private CargaResponse validarReintento(CargaResponse carga, UUID jornadaId, UUID actorId,
                                            List<CrearCargaRequest.Item> items) {
        if (carga == null || !carga.jornadaId().equals(jornadaId) || !carga.usuarioId().equals(actorId)
                || carga.items().size() != items.size()) {
            throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
        }
        for (int i = 0; i < items.size(); i++) {
            var anterior = carga.items().get(i);
            var nuevo = items.get(i);
            if (!anterior.productoId().equals(nuevo.productoId()) || anterior.cantidad() != nuevo.cantidad()) {
                throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
            }
        }
        return carga;
    }

    private ResponseStatusException error(HttpStatus status, String mensaje) {
        return new ResponseStatusException(status, mensaje);
    }

    private record ProductoCarga(String codigo, String tipo, boolean activo) {}
}
