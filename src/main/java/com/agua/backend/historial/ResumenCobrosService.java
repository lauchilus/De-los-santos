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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class ResumenCobrosService {

    private final JdbcTemplate jdbc;

    private static final ZoneId ZONA =
            ZoneId.of("America/Argentina/Buenos_Aires");

    private static final String BASE = """
            FROM cobros c
            JOIN pagos_cobro p ON p.cobro_id = c.id
            JOIN jornadas j ON j.id = c.jornada_id
            WHERE c.creado_en >= ?
              AND c.creado_en < ?
            """;

    public record Totales(
            long cantidadCobros,
            long cantidadClientes,
            BigDecimal ingresosEfectivo,
            BigDecimal ingresosTransferencia,
            BigDecimal ingresosMercadoPago,
            BigDecimal totalCobrado
    ) {
    }

    public record PorMedio(
            String medio,
            long cantidadCobros,
            BigDecimal importe
    ) {
    }

    public record Resumen(
            LocalDate desde,
            LocalDate hasta,
            UUID camionetaId,
            UUID repartidorId,
            Totales totales,
            List<PorMedio> porMedio
    ) {
    }

    public Resumen consultar(
            LocalDate desde,
            LocalDate hasta,
            UUID camionetaId,
            UUID repartidorId
    ) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El rango de fechas es inválido"
            );
        }

        if (hasta.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha hasta está fuera del rango permitido"
            );
        }

        List<Object> parametros = new ArrayList<>();
        parametros.add(desde.atStartOfDay(ZONA).toOffsetDateTime());
        parametros.add(
                hasta.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime()
        );

        StringBuilder filtro = new StringBuilder(BASE);

        if (camionetaId != null) {
            filtro.append(" AND j.camioneta_id = ? ");
            parametros.add(camionetaId);
        }

        if (repartidorId != null) {
            filtro.append(" AND j.repartidor_id = ? ");
            parametros.add(repartidorId);
        }

        Totales totales = jdbc.queryForObject(
                """
                SELECT
                    COUNT(DISTINCT c.id) AS cantidad_cobros,
                    COUNT(DISTINCT c.cliente_id) AS cantidad_clientes,
                    COALESCE(SUM(
                        CASE WHEN p.medio = 'EFECTIVO'
                             THEN p.importe ELSE 0 END
                    ), 0) AS efectivo,
                    COALESCE(SUM(
                        CASE WHEN p.medio = 'TRANSFERENCIA'
                             THEN p.importe ELSE 0 END
                    ), 0) AS transferencia,
                    COALESCE(SUM(
                        CASE WHEN p.medio = 'MERCADO_PAGO'
                             THEN p.importe ELSE 0 END
                    ), 0) AS mercado_pago,
                    COALESCE(SUM(p.importe), 0) AS total_cobrado
                """ + filtro,
                (rs, rowNum) -> new Totales(
                        rs.getLong("cantidad_cobros"),
                        rs.getLong("cantidad_clientes"),
                        rs.getBigDecimal("efectivo"),
                        rs.getBigDecimal("transferencia"),
                        rs.getBigDecimal("mercado_pago"),
                        rs.getBigDecimal("total_cobrado")
                ),
                parametros.toArray()
        );

        List<PorMedio> porMedio = jdbc.query(
                """
                SELECT
                    p.medio,
                    COUNT(DISTINCT c.id) AS cantidad_cobros,
                    SUM(p.importe) AS importe
                """ + filtro + """
                GROUP BY p.medio
                ORDER BY p.medio ASC
                """,
                (rs, rowNum) -> new PorMedio(
                        rs.getString("medio"),
                        rs.getLong("cantidad_cobros"),
                        rs.getBigDecimal("importe")
                ),
                parametros.toArray()
        );

        return new Resumen(
                desde,
                hasta,
                camionetaId,
                repartidorId,
                totales,
                porMedio
        );
    }
}