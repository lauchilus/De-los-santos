package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class CuentasClientesService {

    private final JdbcTemplate jdbc;

    private static final String BASE = """
            FROM clientes c
            LEFT JOIN cuentas_clientes cc ON cc.cliente_id = c.id
            WHERE 1 = 1
            """;

    public record Cuenta(
            UUID clienteId,
            String nombre,
            String telefono,
            boolean activo,
            BigDecimal saldo,
            String situacion
    ) {
    }

    public record Totales(
            long cantidadClientes,
            long deudores,
            long aFavor,
            long saldados,
            BigDecimal totalDeuda,
            BigDecimal totalAFavor,
            BigDecimal saldoNeto
    ) {
    }

    public record Pagina(
            String busqueda,
            String situacion,
            Boolean activo,
            Totales totales,
            List<Cuenta> contenido,
            int pagina,
            int tamanio,
            long totalElementos,
            long totalPaginas
    ) {
    }

    public Pagina listar(
            String busqueda,
            String situacion,
            Boolean activo,
            int pagina,
            int tamanio
    ) {
        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página debe ser >= 0 y el tamaño entre 1 y 100"
            );
        }

        String texto = busqueda == null ? "" : busqueda.strip();

        if (texto.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La búsqueda admite hasta 150 caracteres"
            );
        }

        String filtroSituacion = situacion == null
                ? "TODOS"
                : situacion.strip().toUpperCase(Locale.ROOT);

        String condicionSaldo = switch (filtroSituacion) {
            case "TODOS" -> "";
            case "DEUDORES" -> " AND COALESCE(cc.saldo, 0) > 0 ";
            case "A_FAVOR" -> " AND COALESCE(cc.saldo, 0) < 0 ";
            case "SALDADOS" -> " AND COALESCE(cc.saldo, 0) = 0 ";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Situación inválida: use TODOS, DEUDORES, A_FAVOR o SALDADOS"
            );
        };

        StringBuilder filtro = new StringBuilder(BASE);
        List<Object> parametros = new ArrayList<>();

        filtro.append(condicionSaldo);

        if (!texto.isEmpty()) {
            // Búsqueda literal: % y _ no se interpretan como comodines.
            filtro.append("""
                     AND (
                         strpos(lower(c.nombre), lower(CAST(? AS text))) > 0
                         OR strpos(
                             lower(COALESCE(c.telefono, '')),
                             lower(CAST(? AS text))
                         ) > 0
                     )
                    """);
            parametros.add(texto);
            parametros.add(texto);
        }

        if (activo != null) {
            filtro.append(" AND c.activo = ? ");
            parametros.add(activo);
        }

        Totales totales = jdbc.queryForObject(
                """
                SELECT
                    COUNT(*) AS cantidad_clientes,
                    COUNT(*) FILTER (
                        WHERE COALESCE(cc.saldo, 0) > 0
                    ) AS deudores,
                    COUNT(*) FILTER (
                        WHERE COALESCE(cc.saldo, 0) < 0
                    ) AS a_favor,
                    COUNT(*) FILTER (
                        WHERE COALESCE(cc.saldo, 0) = 0
                    ) AS saldados,
                    COALESCE(SUM(
                        CASE WHEN cc.saldo > 0 THEN cc.saldo ELSE 0 END
                    ), 0) AS total_deuda,
                    COALESCE(SUM(
                        CASE WHEN cc.saldo < 0 THEN -cc.saldo ELSE 0 END
                    ), 0) AS total_a_favor,
                    COALESCE(SUM(COALESCE(cc.saldo, 0)), 0) AS saldo_neto
                """ + filtro,
                (rs, rowNum) -> new Totales(
                        rs.getLong("cantidad_clientes"),
                        rs.getLong("deudores"),
                        rs.getLong("a_favor"),
                        rs.getLong("saldados"),
                        rs.getBigDecimal("total_deuda"),
                        rs.getBigDecimal("total_a_favor"),
                        rs.getBigDecimal("saldo_neto")
                ),
                parametros.toArray()
        );

        List<Object> parametrosPagina = new ArrayList<>(parametros);
        parametrosPagina.add(tamanio);
        parametrosPagina.add((long) pagina * tamanio);

        List<Cuenta> contenido = jdbc.query(
                """
                SELECT
                    c.id AS cliente_id,
                    c.nombre,
                    c.telefono,
                    c.activo,
                    COALESCE(cc.saldo, 0) AS saldo
                """ + filtro + """
                ORDER BY ABS(COALESCE(cc.saldo, 0)) DESC,
                         lower(c.nombre) ASC,
                         c.id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> {
                    BigDecimal saldo = rs.getBigDecimal("saldo");

                    String estado = saldo.signum() > 0
                            ? "DEUDOR"
                            : saldo.signum() < 0 ? "A_FAVOR" : "SALDADO";

                    return new Cuenta(
                            rs.getObject("cliente_id", UUID.class),
                            rs.getString("nombre"),
                            rs.getString("telefono"),
                            rs.getBoolean("activo"),
                            saldo,
                            estado
                    );
                },
                parametrosPagina.toArray()
        );

        long total = totales.cantidadClientes();

        return new Pagina(
                texto,
                filtroSituacion,
                activo,
                totales,
                contenido,
                pagina,
                tamanio,
                total,
                total / tamanio + (total % tamanio == 0 ? 0 : 1)
        );
    }
}