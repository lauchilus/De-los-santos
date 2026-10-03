package com.agua.backend.cobro;

import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CobroService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final UsuarioRepository usuarios;

    @Transactional
    public CobroResponse registrar(UUID jornadaId, CrearCobroRequest request, String username) {
        jornadas.obtener(jornadaId, username);
        Usuario actor = usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        var pagos = validarYOrdenar(request);
        var existente = buscar(request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, request.clienteId(), actor.getId(), pagos);

        // Orden de bloqueos compartido con entregas: primero jornada, después cliente.
        String estado = jdbc.queryForObject("SELECT estado FROM jornadas WHERE id = ? FOR UPDATE", String.class, jornadaId);
        existente = buscar(request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, request.clienteId(), actor.getId(), pagos);
        if (!"ABIERTA".equals(estado)) throw error(HttpStatus.CONFLICT, "La jornada está cerrada");
        var clientes = jdbc.query("SELECT activo FROM clientes WHERE id = ? FOR NO KEY UPDATE",
                (rs, fila) -> rs.getBoolean("activo"), request.clienteId());
        if (clientes.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        if (!clientes.getFirst()) throw error(HttpStatus.CONFLICT, "El cliente está inactivo");

        BigDecimal total = pagos.stream().map(CobroResponse.Pago::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        int insertados = jdbc.update("""
                INSERT INTO cobros (id, jornada_id, cliente_id, usuario_id, total)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.operacionId(), jornadaId, request.clienteId(), actor.getId(), total);
        if (insertados == 0) {
            return validarReintento(buscar(request.operacionId()), jornadaId, request.clienteId(), actor.getId(), pagos);
        }
        for (var pago : pagos) {
            jdbc.update("INSERT INTO pagos_cobro (cobro_id, medio, importe, referencia) VALUES (?, ?, ?, ?)",
                    request.operacionId(), pago.medio().name(), pago.importe(), pago.referencia());
        }

        // No limita el cobro a la deuda: un sobrepago o adelanto deja saldo negativo (a favor).
        BigDecimal saldo = jdbc.queryForObject("""
                INSERT INTO cuentas_clientes (cliente_id, saldo) VALUES (?, ?)
                ON CONFLICT (cliente_id) DO UPDATE
                SET saldo = cuentas_clientes.saldo + EXCLUDED.saldo, actualizado_en = CURRENT_TIMESTAMP
                RETURNING saldo
                """, BigDecimal.class, request.clienteId(), total.negate());
        jdbc.update("""
                INSERT INTO movimientos_cuenta_cliente
                    (id, cliente_id, cobro_id, tipo, importe, saldo_resultante, usuario_id)
                VALUES (?, ?, ?, 'COBRO', ?, ?, ?)
                """, UUID.randomUUID(), request.clienteId(), request.operacionId(), total.negate(), saldo, actor.getId());
        jdbc.update("UPDATE cobros SET saldo_cliente = ? WHERE id = ?", saldo, request.operacionId());
        return buscar(request.operacionId());
    }

    public CobroResponse obtener(UUID jornadaId, UUID cobroId, String username) {
        jornadas.obtener(jornadaId, username);
        var cobro = buscar(cobroId);
        if (cobro == null || !cobro.jornadaId().equals(jornadaId)) {
            throw error(HttpStatus.NOT_FOUND, "Cobro no encontrado en esta jornada");
        }
        return cobro;
    }

    public CajaJornadaResponse caja(UUID jornadaId, String username) {
        jornadas.obtener(jornadaId, username);
        // Agrega ingresos y egresos por separado para no multiplicar importes al unirlos.
        return jdbc.queryForObject("""
                WITH ingresos AS (
                    SELECT COALESCE(SUM(CASE WHEN p.medio = 'EFECTIVO' THEN p.importe ELSE 0 END), 0) AS efectivo,
                           COALESCE(SUM(CASE WHEN p.medio = 'TRANSFERENCIA' THEN p.importe ELSE 0 END), 0) AS transferencia,
                           COALESCE(SUM(CASE WHEN p.medio = 'MERCADO_PAGO' THEN p.importe ELSE 0 END), 0) AS mercado_pago
                    FROM cobros c JOIN pagos_cobro p ON p.cobro_id = c.id WHERE c.jornada_id = ?
                ), egresos AS (
                    SELECT COALESCE(SUM(CASE WHEN medio = 'EFECTIVO' THEN importe ELSE 0 END), 0) AS efectivo,
                           COALESCE(SUM(CASE WHEN medio = 'TRANSFERENCIA' THEN importe ELSE 0 END), 0) AS transferencia,
                           COALESCE(SUM(CASE WHEN medio = 'MERCADO_PAGO' THEN importe ELSE 0 END), 0) AS mercado_pago
                    FROM gastos WHERE jornada_id = ?
                )
                SELECT j.id, j.estado, j.efectivo_inicial,
                       i.efectivo, i.transferencia, i.mercado_pago,
                       e.efectivo AS egreso_efectivo, e.transferencia AS egreso_transferencia,
                       e.mercado_pago AS egreso_mercado_pago
                FROM jornadas j CROSS JOIN ingresos i CROSS JOIN egresos e WHERE j.id = ?
                """, (rs, fila) -> {
                    BigDecimal inicial = rs.getBigDecimal("efectivo_inicial");
                    BigDecimal efectivo = rs.getBigDecimal("efectivo");
                    BigDecimal transferencia = rs.getBigDecimal("transferencia");
                    BigDecimal mercadoPago = rs.getBigDecimal("mercado_pago");
                    BigDecimal egresoEfectivo = rs.getBigDecimal("egreso_efectivo");
                    BigDecimal egresoTransferencia = rs.getBigDecimal("egreso_transferencia");
                    BigDecimal egresoMercadoPago = rs.getBigDecimal("egreso_mercado_pago");
                    return new CajaJornadaResponse(rs.getObject("id", UUID.class), rs.getString("estado"),
                            inicial, efectivo, transferencia, mercadoPago,
                            efectivo.add(transferencia).add(mercadoPago),
                            egresoEfectivo, egresoTransferencia, egresoMercadoPago,
                            egresoEfectivo.add(egresoTransferencia).add(egresoMercadoPago),
                            inicial.add(efectivo).subtract(egresoEfectivo));
                }, jornadaId, jornadaId, jornadaId);
    }

    private List<CobroResponse.Pago> validarYOrdenar(CrearCobroRequest request) {
        if (request == null || request.operacionId() == null || request.clienteId() == null
                || request.pagos() == null || request.pagos().isEmpty() || request.pagos().size() > 3) {
            throw error(HttpStatus.BAD_REQUEST, "Se requiere operación, cliente y entre 1 y 3 medios de pago");
        }
        var medios = EnumSet.noneOf(MedioPago.class);
        var pagos = new ArrayList<CobroResponse.Pago>();
        for (var pago : request.pagos()) {
            if (pago == null || pago.medio() == null || pago.importe() == null || pago.importe().signum() <= 0
                    || pago.importe().scale() > 2 || pago.importe().compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw error(HttpStatus.BAD_REQUEST, "Cada pago debe tener un medio y un importe positivo con hasta 2 decimales");
            }
            if (!medios.add(pago.medio())) throw error(HttpStatus.BAD_REQUEST, "No repitas el mismo medio en un cobro");
            if (pago.referencia() != null && pago.referencia().length() > 100) {
                throw error(HttpStatus.BAD_REQUEST, "La referencia admite hasta 100 caracteres");
            }
            String referencia = pago.referencia() == null ? null : pago.referencia().strip();
            if (referencia != null && referencia.isEmpty()) referencia = null;
            pagos.add(new CobroResponse.Pago(pago.medio(), pago.importe().setScale(2), referencia));
        }
        pagos.sort(Comparator.comparing(pago -> pago.medio().name()));
        return List.copyOf(pagos);
    }

    private CobroResponse buscar(UUID id) {
        var filas = jdbc.query("""
                SELECT id, jornada_id, cliente_id, usuario_id, total, saldo_cliente, creado_en FROM cobros WHERE id = ?
                """, (rs, fila) -> new CobroResponse(rs.getObject("id", UUID.class), rs.getObject("jornada_id", UUID.class),
                rs.getObject("cliente_id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getBigDecimal("total"),
                rs.getBigDecimal("saldo_cliente"), rs.getObject("creado_en", OffsetDateTime.class), List.of()), id);
        if (filas.isEmpty()) return null;
        var cobro = filas.getFirst();
        var pagos = jdbc.query("SELECT medio, importe, referencia FROM pagos_cobro WHERE cobro_id = ? ORDER BY medio",
                (rs, fila) -> new CobroResponse.Pago(MedioPago.valueOf(rs.getString("medio")),
                        rs.getBigDecimal("importe"), rs.getString("referencia")), id);
        return new CobroResponse(cobro.id(), cobro.jornadaId(), cobro.clienteId(), cobro.usuarioId(),
                cobro.total(), cobro.saldoCliente(), cobro.creadoEn(), pagos);
    }

    private CobroResponse validarReintento(CobroResponse existente, UUID jornadaId, UUID clienteId, UUID usuarioId,
                                           List<CobroResponse.Pago> pagos) {
        if (existente == null || !existente.jornadaId().equals(jornadaId) || !existente.clienteId().equals(clienteId)
                || !existente.usuarioId().equals(usuarioId) || existente.pagos().size() != pagos.size()) {
            throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
        }
        for (int i = 0; i < pagos.size(); i++) {
            var anterior = existente.pagos().get(i);
            var nuevo = pagos.get(i);
            if (anterior.medio() != nuevo.medio() || anterior.importe().compareTo(nuevo.importe()) != 0
                    || !Objects.equals(anterior.referencia(), nuevo.referencia())) {
                throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
            }
        }
        return existente;
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
