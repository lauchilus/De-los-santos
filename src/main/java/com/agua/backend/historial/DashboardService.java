package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final ResumenVentasService ventasService;
    private final ResumenCobrosService cobrosService;
    private final CuentasClientesService cuentasService;
    private final EnvasesClientesService envasesService;

    public record Gastos(
            long cantidadGastos,
            BigDecimal gastosJornadas,
            BigDecimal gastosGenerales,
            BigDecimal egresosEfectivo,
            BigDecimal egresosTransferencia,
            BigDecimal egresosMercadoPago,
            BigDecimal totalGastado
    ) {
    }

    public record Periodo(
            LocalDate desde,
            LocalDate hasta,
            ResumenVentasService.Totales ventas,
            ResumenCobrosService.Totales cobros,
            Gastos gastos
    ) {
    }

    public record Actuales(
            CuentasClientesService.Totales cuentasClientes,
            EnvasesClientesService.Totales envasesClientes
    ) {
    }

    public record Resumen(
            Periodo periodo,
            Actuales actuales
    ) {
    }

    public Resumen consultar(LocalDate desde, LocalDate hasta) {
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

        var ventas = ventasService.consultar(
                desde, hasta, null, null, null
        );

        var cobros = cobrosService.consultar(
                desde, hasta, null, null
        );

        Gastos gastos = jdbc.queryForObject(
                """
                SELECT
                    COUNT(*) AS cantidad_gastos,
                    COALESCE(SUM(
                        CASE WHEN ambito = 'JORNADA'
                             THEN importe ELSE 0 END
                    ), 0) AS gastos_jornadas,
                    COALESCE(SUM(
                        CASE WHEN ambito = 'GENERAL'
                             THEN importe ELSE 0 END
                    ), 0) AS gastos_generales,
                    COALESCE(SUM(
                        CASE WHEN medio = 'EFECTIVO'
                             THEN importe ELSE 0 END
                    ), 0) AS efectivo,
                    COALESCE(SUM(
                        CASE WHEN medio = 'TRANSFERENCIA'
                             THEN importe ELSE 0 END
                    ), 0) AS transferencia,
                    COALESCE(SUM(
                        CASE WHEN medio = 'MERCADO_PAGO'
                             THEN importe ELSE 0 END
                    ), 0) AS mercado_pago,
                    COALESCE(SUM(importe), 0) AS total_gastado
                FROM gastos
                WHERE fecha >= ?
                  AND fecha <= ?
                """,
                (rs, rowNum) -> new Gastos(
                        rs.getLong("cantidad_gastos"),
                        rs.getBigDecimal("gastos_jornadas"),
                        rs.getBigDecimal("gastos_generales"),
                        rs.getBigDecimal("efectivo"),
                        rs.getBigDecimal("transferencia"),
                        rs.getBigDecimal("mercado_pago"),
                        rs.getBigDecimal("total_gastado")
                ),
                Date.valueOf(desde),
                Date.valueOf(hasta)
        );

        // Los totales abarcan todos los resultados,
        // aunque solicitemos una sola fila de detalle.
        var cuentas = cuentasService.listar(
                null, "TODOS", null, 0, 1
        );

        var envases = envasesService.listar(
                null, null, null, 0, 1
        );

        return new Resumen(
                new Periodo(
                        desde,
                        hasta,
                        ventas.totales(),
                        cobros.totales(),
                        gastos
                ),
                new Actuales(
                        cuentas.totales(),
                        envases.totales()
                )
        );
    }
}