package com.agua.backend.historial;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HistorialJornadaService {

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;

    private static final String FROM = """
            FROM jornadas j
            JOIN camionetas v ON v.id = j.camioneta_id
            JOIN usuarios u ON u.id = j.repartidor_id
            LEFT JOIN cierres_jornada c ON c.jornada_id = j.id
            """;

    public record Filtro(
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate desde,

            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate hasta,

            UUID camionetaId,
            UUID repartidorId,
            String estado,
            String fechaPor
    ) {}

    public record Fila(
            UUID jornadaId,
            LocalDate fecha,
            String estado,
            UUID camionetaId,
            String camionetaNombre,
            String patente,
            UUID repartidorId,
            String repartidorNombre,
            UUID cierreId,
            OffsetDateTime cerradaEn,
            BigDecimal efectivoEsperado,
            BigDecimal efectivoContado,
            BigDecimal diferencia
    ) {}

    public record Pagina(
            List<Fila> contenido,
            int pagina,
            int tamanio,
            long totalElementos,
            long totalPaginas
    ) {}

    public record TotalesCierres(
            BigDecimal ingresosEfectivo,
            BigDecimal ingresosTransferencia,
            BigDecimal ingresosMercadoPago,
            BigDecimal totalCobrado,
            BigDecimal egresosEfectivo,
            BigDecimal egresosTransferencia,
            BigDecimal egresosMercadoPago,
            BigDecimal totalGastado,
            BigDecimal efectivoEsperado,
            BigDecimal efectivoContado,
            BigDecimal diferenciaNeta,
            BigDecimal faltantes,
            BigDecimal sobrantes
    ) {}

    public record Resumen(
            LocalDate desde,
            LocalDate hasta,
            String fechaPor,
            long totalJornadas,
            long abiertas,
            long cerradas,
            long cierresRegistrados,
            long cierresConDiferencia,
            TotalesCierres totalesCierres
    ) {}

    public Pagina listar(
            Filtro filtro,
            int pagina,
            int tamanio,
            String username
    ) {
        exigirAdmin(username);

        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw error("Página no negativa y tamaño entre 1 y 100");
        }

        Consulta consulta = filtrar(filtro);

        long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + FROM + consulta.where(),
                Long.class,
                consulta.args().toArray()
        );

        var args = new ArrayList<>(consulta.args());
        args.add(tamanio);
        args.add((long) pagina * tamanio);

        List<Fila> filas = jdbc.query("""
                SELECT j.id AS jornada_id, j.fecha, j.estado,
                       j.camioneta_id, v.nombre AS camioneta_nombre,
                       v.patente, j.repartidor_id,
                       u.nombre AS repartidor_nombre,
                       c.id AS cierre_id, j.cerrada_en,
                       c.efectivo_esperado, c.efectivo_contado,
                       c.diferencia
                """ + FROM + consulta.where()
                        + " ORDER BY " + consulta.orden()
                        + " LIMIT ? OFFSET ?",
                (rs, fila) -> new Fila(
                        rs.getObject("jornada_id", UUID.class),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getString("estado"),
                        rs.getObject("camioneta_id", UUID.class),
                        rs.getString("camioneta_nombre"),
                        rs.getString("patente"),
                        rs.getObject("repartidor_id", UUID.class),
                        rs.getString("repartidor_nombre"),
                        rs.getObject("cierre_id", UUID.class),
                        rs.getObject("cerrada_en", OffsetDateTime.class),
                        rs.getBigDecimal("efectivo_esperado"),
                        rs.getBigDecimal("efectivo_contado"),
                        rs.getBigDecimal("diferencia")
                ),
                args.toArray()
        );

        long paginas = total / tamanio + (total % tamanio == 0 ? 0 : 1);
        return new Pagina(filas, pagina, tamanio, total, paginas);
    }

    public Resumen resumir(Filtro filtro, String username) {
        exigirAdmin(username);
        Consulta consulta = filtrar(filtro);

        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total_jornadas,
                       COALESCE(SUM(CASE WHEN j.estado = 'ABIERTA'
                           THEN 1 ELSE 0 END), 0) AS abiertas,
                       COALESCE(SUM(CASE WHEN j.estado = 'CERRADA'
                           THEN 1 ELSE 0 END), 0) AS cerradas,
                       COUNT(c.id) AS cierres,
                       COALESCE(SUM(CASE WHEN c.diferencia <> 0
                           THEN 1 ELSE 0 END), 0) AS con_diferencia,
                       COALESCE(SUM(c.ingresos_efectivo), 0) AS ingreso_efectivo,
                       COALESCE(SUM(c.ingresos_transferencia), 0) AS ingreso_transferencia,
                       COALESCE(SUM(c.ingresos_mercado_pago), 0) AS ingreso_mp,
                       COALESCE(SUM(c.egresos_efectivo), 0) AS egreso_efectivo,
                       COALESCE(SUM(c.egresos_transferencia), 0) AS egreso_transferencia,
                       COALESCE(SUM(c.egresos_mercado_pago), 0) AS egreso_mp,
                       COALESCE(SUM(c.efectivo_esperado), 0) AS esperado,
                       COALESCE(SUM(c.efectivo_contado), 0) AS contado,
                       COALESCE(SUM(c.diferencia), 0) AS diferencia,
                       COALESCE(SUM(CASE WHEN c.diferencia < 0
                           THEN -c.diferencia ELSE 0 END), 0) AS faltantes,
                       COALESCE(SUM(CASE WHEN c.diferencia > 0
                           THEN c.diferencia ELSE 0 END), 0) AS sobrantes
                """ + FROM + consulta.where(),
                (rs, fila) -> {
                    BigDecimal efectivo = rs.getBigDecimal("ingreso_efectivo");
                    BigDecimal transferencia = rs.getBigDecimal("ingreso_transferencia");
                    BigDecimal mp = rs.getBigDecimal("ingreso_mp");
                    BigDecimal egresoEfectivo = rs.getBigDecimal("egreso_efectivo");
                    BigDecimal egresoTransferencia = rs.getBigDecimal("egreso_transferencia");
                    BigDecimal egresoMp = rs.getBigDecimal("egreso_mp");

                    var totales = new TotalesCierres(
                            efectivo,
                            transferencia,
                            mp,
                            efectivo.add(transferencia).add(mp),
                            egresoEfectivo,
                            egresoTransferencia,
                            egresoMp,
                            egresoEfectivo.add(egresoTransferencia).add(egresoMp),
                            rs.getBigDecimal("esperado"),
                            rs.getBigDecimal("contado"),
                            rs.getBigDecimal("diferencia"),
                            rs.getBigDecimal("faltantes"),
                            rs.getBigDecimal("sobrantes")
                    );

                    return new Resumen(
                            filtro.desde(),
                            filtro.hasta(),
                            consulta.fechaPor(),
                            rs.getLong("total_jornadas"),
                            rs.getLong("abiertas"),
                            rs.getLong("cerradas"),
                            rs.getLong("cierres"),
                            rs.getLong("con_diferencia"),
                            totales
                    );
                },
                consulta.args().toArray()
        );
    }

    private Consulta filtrar(Filtro filtro) {
        if (filtro == null || filtro.desde() == null
                || filtro.hasta() == null
                || filtro.desde().isAfter(filtro.hasta())) {
            throw error("Indicá desde y hasta en un período válido");
        }

        String fechaPor = filtro.fechaPor() == null
                ? "JORNADA" : filtro.fechaPor();

        if (!fechaPor.equals("JORNADA") && !fechaPor.equals("CIERRE")) {
            throw error("fechaPor debe ser JORNADA o CIERRE");
        }

        if (filtro.estado() != null
                && !filtro.estado().equals("ABIERTA")
                && !filtro.estado().equals("CERRADA")) {
            throw error("estado debe ser ABIERTA o CERRADA");
        }

        // Ambas expresiones son constantes internas, no SQL recibido del cliente.
        String fecha = fechaPor.equals("JORNADA")
                ? "j.fecha"
                : "CAST(c.cerrado_en AT TIME ZONE 'America/Argentina/Buenos_Aires' AS DATE)";

        var where = new StringBuilder(
                " WHERE " + fecha + " >= ? AND " + fecha + " <= ?"
        );
        var args = new ArrayList<Object>();
        args.add(Date.valueOf(filtro.desde()));
        args.add(Date.valueOf(filtro.hasta()));

        if (filtro.camionetaId() != null) {
            where.append(" AND j.camioneta_id = ?");
            args.add(filtro.camionetaId());
        }

        if (filtro.repartidorId() != null) {
            where.append(" AND j.repartidor_id = ?");
            args.add(filtro.repartidorId());
        }

        if (filtro.estado() != null) {
            where.append(" AND j.estado = ?");
            args.add(filtro.estado());
        }

        String orden = fechaPor.equals("JORNADA")
                ? "j.fecha DESC, j.abierta_en DESC, j.id DESC"
                : "c.cerrado_en DESC, j.id DESC";

        return new Consulta(where.toString(), args, orden, fechaPor);
    }

    private void exigirAdmin(String username) {
        var usuario = usuarios.findByUsername(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no disponible"
                ));

        if (usuario.getRol() != Rol.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Se requiere administrador"
            );
        }
    }

    private ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private record Consulta(
            String where,
            List<Object> args,
            String orden,
            String fechaPor
    ) {}
}