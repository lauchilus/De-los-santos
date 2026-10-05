package com.agua.backend.cierre;

import com.agua.backend.cobro.CajaJornadaResponse;
import com.agua.backend.cobro.CobroService;
import com.agua.backend.jornada.JornadaService;
import com.agua.backend.stock.EstadoStock;
import com.agua.backend.stock.StockResponse;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CierreJornadaService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final CobroService cobros;
    private final UsuarioRepository usuarios;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreviaCierreResponse previa(UUID jornadaId, String username) {
        var jornada = jornadas.obtener(jornadaId, username);
        var caja = cobros.caja(jornadaId, username);
        var stock = jdbc.query("""
                SELECT s.producto_id, p.codigo, p.nombre, s.estado, s.cantidad
                FROM stock_jornada s JOIN productos p ON p.id = s.producto_id
                WHERE s.jornada_id = ? AND s.cantidad > 0 ORDER BY s.producto_id, s.estado
                """, (rs, fila) -> new StockResponse(rs.getObject("producto_id", UUID.class),
                rs.getString("codigo"), rs.getString("nombre"), EstadoStock.valueOf(rs.getString("estado")),
                rs.getLong("cantidad")), jornadaId);
        return new PreviaCierreResponse(jornada, caja, stock);
    }

    @Transactional
    public CierreJornadaResponse cerrar(UUID jornadaId, CerrarJornadaRequest request, String username) {
        jornadas.obtener(jornadaId, username);
        var actor = usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        var devoluciones = validarYOrdenar(request);
        String motivo = request.motivoDiferencia() == null ? null : request.motivoDiferencia().strip();
        if (motivo != null && motivo.isEmpty()) motivo = null;
        var existente = buscar("id", request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, actor.getId(), request, motivo, devoluciones);

        // Todos los módulos mutables de la jornada toman este mismo bloqueo.
        // Una operación que llega después del cierre espera y luego ve CERRADA.
        String estado = jdbc.queryForObject("SELECT estado FROM jornadas WHERE id = ? FOR UPDATE", String.class, jornadaId);
        existente = buscar("id", request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, actor.getId(), request, motivo, devoluciones);
        if (!"ABIERTA".equals(estado)) throw error(HttpStatus.CONFLICT, "La jornada ya está cerrada");

        var stock = jdbc.query("""
                SELECT producto_id, estado, cantidad FROM stock_jornada
                WHERE jornada_id = ? AND cantidad > 0 ORDER BY producto_id, estado FOR UPDATE
                """, (rs, fila) -> new CerrarJornadaRequest.Devolucion(rs.getObject("producto_id", UUID.class),
                EstadoStock.valueOf(rs.getString("estado")), rs.getLong("cantidad")), jornadaId);
        if (!stock.equals(devoluciones)) {
            throw error(HttpStatus.CONFLICT, "Las cantidades devueltas no coinciden con todo el stock de la jornada. Revisá la previa del cierre");
        }
        var caja = cobros.caja(jornadaId, username);
        BigDecimal diferencia = request.efectivoContado().subtract(caja.efectivoEsperado());
        if (diferencia.signum() != 0 && motivo == null) {
            throw error(HttpStatus.CONFLICT, "Indicá el motivo de la diferencia de efectivo");
        }
        int insertados = jdbc.update("""
                INSERT INTO cierres_jornada
                    (id, jornada_id, usuario_id, efectivo_inicial, ingresos_efectivo, ingresos_transferencia,
                     ingresos_mercado_pago, egresos_efectivo, egresos_transferencia, egresos_mercado_pago,
                     efectivo_esperado, efectivo_contado, diferencia, motivo_diferencia)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.operacionId(), jornadaId, actor.getId(), caja.efectivoInicial(), caja.ingresosEfectivo(),
                caja.ingresosTransferencia(), caja.ingresosMercadoPago(), caja.egresosEfectivo(), caja.egresosTransferencia(),
                caja.egresosMercadoPago(), caja.efectivoEsperado(), request.efectivoContado(), diferencia, motivo);
        if (insertados == 0) {
            return validarReintento(buscar("id", request.operacionId()), jornadaId, actor.getId(), request, motivo, devoluciones);
        }
        // Orden compatible con cargas y producción para serializar el stock de fábrica.
        for (var item : devoluciones) devolver(request.operacionId(), jornadaId, actor.getId(), item);
        int cerradas = jdbc.update("""
                UPDATE jornadas SET estado = 'CERRADA',
                    cerrada_en = (SELECT cerrado_en FROM cierres_jornada WHERE id = ?)
                WHERE id = ? AND estado = 'ABIERTA'
                """, request.operacionId(), jornadaId);
        if (cerradas != 1) throw new IllegalStateException("No se pudo completar el cierre de la jornada");
        return buscar("id", request.operacionId());
    }

    public CierreJornadaResponse obtener(UUID jornadaId, String username) {
        jornadas.obtener(jornadaId, username);
        var cierre = buscar("jornada_id", jornadaId);
        if (cierre == null) throw error(HttpStatus.NOT_FOUND, "La jornada todavía no tiene cierre registrado");
        return cierre;
    }

    private void devolver(UUID cierreId, UUID jornadaId, UUID actorId, CerrarJornadaRequest.Devolucion item) {
        var saldos = jdbc.query("""
                UPDATE stock_fabrica SET cantidad = cantidad + ?, actualizado_en = CURRENT_TIMESTAMP
                WHERE producto_id = ? AND estado = ? RETURNING cantidad
                """, (rs, fila) -> rs.getLong("cantidad"), item.cantidad(), item.productoId(), item.estado().name());
        if (saldos.isEmpty()) throw error(HttpStatus.CONFLICT, "Falta inicializar una existencia en fábrica: " + item.productoId());
        long saldoFabrica = saldos.getFirst();
        int actualizados = jdbc.update("""
                UPDATE stock_jornada SET cantidad = 0, actualizado_en = CURRENT_TIMESTAMP
                WHERE jornada_id = ? AND producto_id = ? AND estado = ? AND cantidad = ?
                """, jornadaId, item.productoId(), item.estado().name(), item.cantidad());
        if (actualizados != 1) throw error(HttpStatus.CONFLICT, "El stock de jornada cambió durante el cierre");
        jdbc.update("""
                INSERT INTO devoluciones_cierre (cierre_id, producto_id, estado, cantidad, saldo_fabrica)
                VALUES (?, ?, ?, ?, ?)
                """, cierreId, item.productoId(), item.estado().name(), item.cantidad(), saldoFabrica);
        jdbc.update("""
                INSERT INTO movimientos_stock_fabrica
                    (id, producto_id, estado, tipo, cantidad, saldo_resultante, motivo, usuario_id, cierre_id)
                VALUES (?, ?, ?, 'DEVOLUCION_CIERRE', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), item.productoId(), item.estado().name(), item.cantidad(), saldoFabrica,
                "Devolución por cierre de jornada " + jornadaId, actorId, cierreId);
    }

    private List<CerrarJornadaRequest.Devolucion> validarYOrdenar(CerrarJornadaRequest r) {
        if (r == null || r.operacionId() == null || r.efectivoContado() == null || r.efectivoContado().signum() < 0
                || r.efectivoContado().scale() > 2 || r.efectivoContado().precision() - r.efectivoContado().scale() > 28
                || r.devoluciones() == null || r.devoluciones().size() > 1000
                || (r.motivoDiferencia() != null && r.motivoDiferencia().length() > 500)) {
            throw error(HttpStatus.BAD_REQUEST, "Indicá operación, efectivo contado no negativo y lista de devoluciones válida");
        }
        var claves = new HashSet<String>();
        for (var item : r.devoluciones()) {
            if (item == null || item.productoId() == null || item.estado() == null || item.cantidad() == null || item.cantidad() <= 0) {
                throw error(HttpStatus.BAD_REQUEST, "Cada devolución requiere producto, estado y cantidad positiva");
            }
            if (!claves.add(item.productoId() + "/" + item.estado().name())) {
                throw error(HttpStatus.BAD_REQUEST, "No repitas el mismo producto y estado");
            }
        }
        return r.devoluciones().stream().sorted(Comparator.comparing((CerrarJornadaRequest.Devolucion d) -> d.productoId().toString())
                .thenComparing(d -> d.estado().name())).toList();
    }

    private CierreJornadaResponse validarReintento(CierreJornadaResponse c, UUID jornadaId, UUID actorId,
                                                   CerrarJornadaRequest r, String motivo,
                                                   List<CerrarJornadaRequest.Devolucion> devoluciones) {
        if (c == null || !c.jornadaId().equals(jornadaId) || !c.usuarioId().equals(actorId)
                || c.efectivoContado().compareTo(r.efectivoContado()) != 0 || !Objects.equals(c.motivoDiferencia(), motivo)
                || c.devoluciones().size() != devoluciones.size()) {
            throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
        }
        for (int i = 0; i < devoluciones.size(); i++) {
            var anterior = c.devoluciones().get(i);
            var nuevo = devoluciones.get(i);
            if (!anterior.productoId().equals(nuevo.productoId()) || anterior.estado() != nuevo.estado()
                    || anterior.cantidad() != nuevo.cantidad()) {
                throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
            }
        }
        return c;
    }

    private CierreJornadaResponse buscar(String columna, UUID valor) {
        // La columna solo se elige internamente; nunca proviene de una petición.
        if (!columna.equals("id") && !columna.equals("jornada_id")) throw new IllegalArgumentException("Columna inválida");
        var cabeceras = jdbc.query("SELECT * FROM cierres_jornada WHERE " + columna + " = ?",
                (rs, fila) -> new CierreJornadaResponse(rs.getObject("id", UUID.class), rs.getObject("jornada_id", UUID.class),
                        rs.getObject("usuario_id", UUID.class), rs.getObject("cerrado_en", OffsetDateTime.class), cajaGuardada(rs),
                        rs.getBigDecimal("efectivo_contado"), rs.getBigDecimal("diferencia"), rs.getString("motivo_diferencia"), List.of()), valor);
        if (cabeceras.isEmpty()) return null;
        var c = cabeceras.getFirst();
        var devoluciones = jdbc.query("""
                SELECT producto_id, estado, cantidad, saldo_fabrica FROM devoluciones_cierre
                WHERE cierre_id = ? ORDER BY producto_id, estado
                """, (rs, fila) -> new CierreJornadaResponse.Devolucion(rs.getObject("producto_id", UUID.class),
                EstadoStock.valueOf(rs.getString("estado")), rs.getLong("cantidad"), rs.getLong("saldo_fabrica")), c.id());
        return new CierreJornadaResponse(c.id(), c.jornadaId(), c.usuarioId(), c.cerradoEn(), c.caja(),
                c.efectivoContado(), c.diferencia(), c.motivoDiferencia(), devoluciones);
    }

    private CajaJornadaResponse cajaGuardada(ResultSet rs) throws SQLException {
        BigDecimal efectivo = rs.getBigDecimal("ingresos_efectivo");
        BigDecimal transferencia = rs.getBigDecimal("ingresos_transferencia");
        BigDecimal mercadoPago = rs.getBigDecimal("ingresos_mercado_pago");
        BigDecimal egresoEfectivo = rs.getBigDecimal("egresos_efectivo");
        BigDecimal egresoTransferencia = rs.getBigDecimal("egresos_transferencia");
        BigDecimal egresoMercadoPago = rs.getBigDecimal("egresos_mercado_pago");
        return new CajaJornadaResponse(rs.getObject("jornada_id", UUID.class), "CERRADA", rs.getBigDecimal("efectivo_inicial"),
                efectivo, transferencia, mercadoPago, efectivo.add(transferencia).add(mercadoPago),
                egresoEfectivo, egresoTransferencia, egresoMercadoPago, egresoEfectivo.add(egresoTransferencia).add(egresoMercadoPago),
                rs.getBigDecimal("efectivo_esperado"));
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
