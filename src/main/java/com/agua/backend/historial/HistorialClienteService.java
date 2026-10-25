package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class HistorialClienteService {

    private static final ZoneId ZONA =
            ZoneId.of("America/Argentina/Buenos_Aires");

    private final JdbcTemplate jdbc;

    private static final String MOVIMIENTOS = """
            WITH movimientos AS (
                SELECT
                    e.id AS operacion_id,
                    e.jornada_id,
                    e.cliente_id,
                    e.creado_en,
                    CASE
                        WHEN EXISTS (SELECT 1 FROM abonos_cliente a WHERE a.entrega_id=e.id)
                            THEN 'ENTREGA_CON_ABONO'
                        WHEN EXISTS (
                            SELECT 1
                            FROM detalles_entrega d
                            WHERE d.entrega_id = e.id
                              AND d.entregados > 0
                        ) THEN 'ENTREGA'
                        ELSE 'DEVOLUCION'
                    END AS tipo,
                    e.total AS importe,
                    e.saldo_cliente AS saldo_resultante
                FROM entregas e
                WHERE e.cliente_id = ?

                UNION ALL

                SELECT
                    c.id AS operacion_id,
                    c.jornada_id,
                    c.cliente_id,
                    c.creado_en,
                    'COBRO' AS tipo,
                    -c.total AS importe,
                    c.saldo_cliente AS saldo_resultante
                FROM cobros c
                WHERE c.cliente_id = ?
                UNION ALL
                SELECT i.cliente_id, NULL::uuid, i.cliente_id,
                       (imp.fecha_corte::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires'),
                       'SALDO_INICIAL', i.saldo_inicial, i.saldo_inicial
                FROM clientes_importados i JOIN importaciones_clientes imp ON imp.id=i.importacion_id
                WHERE i.cliente_id = ?
            )
            """;

    public record Movimiento(
            UUID operacionId,
            UUID jornadaId,
            OffsetDateTime creadoEn,
            String tipo,
            BigDecimal importe,
            BigDecimal saldoResultante
    ) {
    }

    public record Pagina(
            UUID clienteId,
            LocalDate desde,
            LocalDate hasta,
            BigDecimal saldoActual,
            List<Movimiento> contenido,
            int pagina,
            int tamanio,
            long totalElementos,
            long totalPaginas
    ) {
    }

    public Pagina listar(
            UUID clienteId,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamanio
    ) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El rango de fechas es inválido"
            );
        }

        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página debe ser >= 0 y el tamaño entre 1 y 100"
            );
        }

        // Necesitamos calcular el inicio del día siguiente.
        if (hasta.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha hasta está fuera del rango permitido"
            );
        }

        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM clientes WHERE id = ?)",
                Boolean.class,
                clienteId
        );

        if (!Boolean.TRUE.equals(existe)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cliente no encontrado"
            );
        }

        BigDecimal saldoActual = jdbc.queryForObject("""
                SELECT COALESCE(
                    (SELECT saldo
                     FROM cuentas_clientes
                     WHERE cliente_id = ?),
                    0
                )
                """, BigDecimal.class, clienteId);

        OffsetDateTime inicio =
                desde.atStartOfDay(ZONA).toOffsetDateTime();

        OffsetDateTime finExclusivo =
                hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();

        String filtro = """
                FROM movimientos
                WHERE creado_en >= ?
                  AND creado_en < ?
                """;

        Long cantidad = jdbc.queryForObject(
                MOVIMIENTOS + "SELECT COUNT(*) " + filtro,
                Long.class,
                clienteId,
                clienteId,
                clienteId,
                inicio,
                finExclusivo
        );

        long total = cantidad == null ? 0 : cantidad;

        List<Movimiento> contenido = jdbc.query(
                MOVIMIENTOS + """
                        SELECT operacion_id, jornada_id, creado_en,
                               tipo, importe, saldo_resultante
                        """ + filtro + """
                        ORDER BY creado_en DESC, operacion_id DESC, tipo DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new Movimiento(
                        rs.getObject("operacion_id", UUID.class),
                        rs.getObject("jornada_id", UUID.class),
                        rs.getObject("creado_en", OffsetDateTime.class),
                        rs.getString("tipo"),
                        rs.getBigDecimal("importe"),
                        rs.getBigDecimal("saldo_resultante")
                ),
                clienteId,
                clienteId,
                clienteId,
                inicio,
                finExclusivo,
                tamanio,
                (long) pagina * tamanio
        );

        return new Pagina(
                clienteId,
                desde,
                hasta,
                saldoActual,
                contenido,
                pagina,
                tamanio,
                total,
                total / tamanio + (total % tamanio == 0 ? 0 : 1)
        );
    }
}