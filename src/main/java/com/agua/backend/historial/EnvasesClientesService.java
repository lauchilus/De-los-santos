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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
)
public class EnvasesClientesService {

    private final JdbcTemplate jdbc;

    private static final String BASE = """
            FROM envases_cliente e
            JOIN clientes c ON c.id = e.cliente_id
            JOIN productos p ON p.id = e.producto_id
            WHERE e.cantidad > 0
            """;

    public record Fila(
            UUID clienteId,
            String clienteNombre,
            String telefono,
            boolean activo,
            UUID productoId,
            String codigo,
            String productoNombre,
            long cantidad
    ) {
    }

    public record TotalProducto(
            UUID productoId,
            String codigo,
            String nombre,
            long cantidadClientes,
            BigDecimal cantidadEnvases
    ) {
    }

    public record Totales(
            long cantidadClientes,
            BigDecimal cantidadEnvases,
            List<TotalProducto> porProducto
    ) {
    }

    public record Pagina(
            String busqueda,
            UUID productoId,
            Boolean activo,
            Totales totales,
            List<Fila> contenido,
            int pagina,
            int tamanio,
            long totalElementos,
            long totalPaginas
    ) {
    }

    private record Conteo(
            long filas,
            long clientes,
            BigDecimal envases
    ) {
    }

    public Pagina listar(
            String busqueda,
            UUID productoId,
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

        if (productoId != null) {
            Boolean existe = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM productos WHERE id = ?)",
                    Boolean.class,
                    productoId
            );

            if (!Boolean.TRUE.equals(existe)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                );
            }
        }

        StringBuilder filtro = new StringBuilder(BASE);
        List<Object> parametros = new ArrayList<>();

        if (!texto.isEmpty()) {
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

        if (productoId != null) {
            filtro.append(" AND e.producto_id = ? ");
            parametros.add(productoId);
        }

        if (activo != null) {
            filtro.append(" AND c.activo = ? ");
            parametros.add(activo);
        }

        Conteo conteo = jdbc.queryForObject(
                """
                SELECT
                    COUNT(*) AS filas,
                    COUNT(DISTINCT c.id) AS clientes,
                    COALESCE(SUM(e.cantidad), 0) AS envases
                """ + filtro,
                (rs, rowNum) -> new Conteo(
                        rs.getLong("filas"),
                        rs.getLong("clientes"),
                        rs.getBigDecimal("envases")
                ),
                parametros.toArray()
        );

        List<TotalProducto> porProducto = jdbc.query(
                """
                SELECT
                    p.id AS producto_id,
                    p.codigo,
                    p.nombre,
                    COUNT(DISTINCT c.id) AS clientes,
                    SUM(e.cantidad) AS envases
                """ + filtro + """
                GROUP BY p.id, p.codigo, p.nombre
                ORDER BY p.codigo ASC, p.id ASC
                """,
                (rs, rowNum) -> new TotalProducto(
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getLong("clientes"),
                        rs.getBigDecimal("envases")
                ),
                parametros.toArray()
        );

        List<Object> parametrosPagina = new ArrayList<>(parametros);
        parametrosPagina.add(tamanio);
        parametrosPagina.add((long) pagina * tamanio);

        List<Fila> contenido = jdbc.query(
                """
                SELECT
                    c.id AS cliente_id,
                    c.nombre AS cliente_nombre,
                    c.telefono,
                    c.activo,
                    p.id AS producto_id,
                    p.codigo,
                    p.nombre AS producto_nombre,
                    e.cantidad
                """ + filtro + """
                ORDER BY lower(c.nombre) ASC,
                         c.id ASC,
                         p.codigo ASC,
                         p.id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new Fila(
                        rs.getObject("cliente_id", UUID.class),
                        rs.getString("cliente_nombre"),
                        rs.getString("telefono"),
                        rs.getBoolean("activo"),
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("producto_nombre"),
                        rs.getLong("cantidad")
                ),
                parametrosPagina.toArray()
        );

        long total = conteo.filas();

        return new Pagina(
                texto,
                productoId,
                activo,
                new Totales(
                        conteo.clientes(),
                        conteo.envases(),
                        porProducto
                ),
                contenido,
                pagina,
                tamanio,
                total,
                total / tamanio + (total % tamanio == 0 ? 0 : 1)
        );
    }
}