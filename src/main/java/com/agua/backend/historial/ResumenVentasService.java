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
public class ResumenVentasService {

    private final JdbcTemplate jdbc;

    private static final ZoneId ZONA =
            ZoneId.of("America/Argentina/Buenos_Aires");

    private static final String BASE = """
            FROM entregas e
            JOIN detalles_entrega d ON d.entrega_id = e.id
            JOIN productos p ON p.id = d.producto_id
            JOIN jornadas j ON j.id = e.jornada_id
            WHERE e.creado_en >= ?
              AND e.creado_en < ?
            """;

    public record Totales(
            long operaciones,
            long clientes,
            BigDecimal unidadesEntregadas,
            BigDecimal devueltosVacios,
            BigDecimal devueltosDanados,
            BigDecimal importeVendido,
            BigDecimal unidadesIncluidasAbono,
            BigDecimal unidadesFacturadas
    ) {
    }

    public record PorProducto(
            UUID productoId,
            String codigo,
            String nombre,
            long operaciones,
            long clientes,
            BigDecimal unidadesEntregadas,
            BigDecimal devueltosVacios,
            BigDecimal devueltosDanados,
            BigDecimal importeVendido,
            BigDecimal unidadesIncluidasAbono,
            BigDecimal unidadesFacturadas
    ) {
    }

    public record Resumen(
            LocalDate desde,
            LocalDate hasta,
            UUID productoId,
            UUID camionetaId,
            UUID repartidorId,
            Totales totales,
            List<PorProducto> porProducto
    ) {
    }

    public Resumen consultar(
            LocalDate desde,
            LocalDate hasta,
            UUID productoId,
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

        if (productoId != null) {
            filtro.append(" AND d.producto_id = ? ");
            parametros.add(productoId);
        }

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
                    COUNT(DISTINCT e.id) AS operaciones,
                    COUNT(DISTINCT e.cliente_id) AS clientes,
                    COALESCE(SUM(d.entregados), 0) AS entregados,
                    COALESCE(SUM(d.devueltos_vacios), 0) AS vacios,
                    COALESCE(SUM(d.devueltos_danados), 0) AS danados,
                    COALESCE(SUM(d.subtotal), 0) AS vendido,
                    COALESCE(SUM(d.incluidos_abono), 0) AS incluidos,
                    COALESCE(SUM(d.entregados-d.incluidos_abono), 0) AS facturados
                """ + filtro,
                (rs, rowNum) -> new Totales(
                        rs.getLong("operaciones"),
                        rs.getLong("clientes"),
                        rs.getBigDecimal("entregados"),
                        rs.getBigDecimal("vacios"),
                        rs.getBigDecimal("danados"),
                        rs.getBigDecimal("vendido"),
                        rs.getBigDecimal("incluidos"),
                        rs.getBigDecimal("facturados")
                ),
                parametros.toArray()
        );

        List<PorProducto> porProducto = jdbc.query(
                """
                SELECT
                    p.id AS producto_id,
                    p.codigo,
                    p.nombre,
                    COUNT(DISTINCT e.id) AS operaciones,
                    COUNT(DISTINCT e.cliente_id) AS clientes,
                    COALESCE(SUM(d.entregados), 0) AS entregados,
                    COALESCE(SUM(d.devueltos_vacios), 0) AS vacios,
                    COALESCE(SUM(d.devueltos_danados), 0) AS danados,
                    COALESCE(SUM(d.subtotal), 0) AS vendido,
                    COALESCE(SUM(d.incluidos_abono), 0) AS incluidos,
                    COALESCE(SUM(d.entregados-d.incluidos_abono), 0) AS facturados
                """ + filtro + """
                GROUP BY p.id, p.codigo, p.nombre
                ORDER BY p.codigo ASC, p.id ASC
                """,
                (rs, rowNum) -> new PorProducto(
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getLong("operaciones"),
                        rs.getLong("clientes"),
                        rs.getBigDecimal("entregados"),
                        rs.getBigDecimal("vacios"),
                        rs.getBigDecimal("danados"),
                        rs.getBigDecimal("vendido"),
                        rs.getBigDecimal("incluidos"),
                        rs.getBigDecimal("facturados")
                ),
                parametros.toArray()
        );

        return new Resumen(
                desde,
                hasta,
                productoId,
                camionetaId,
                repartidorId,
                totales,
                porProducto
        );
    }
}