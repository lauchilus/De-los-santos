package com.agua.backend.historial;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ResumenAbonosService {
    private final JdbcTemplate jdbc;
    public record Resumen(LocalDate desde,LocalDate hasta,long cantidadAbonos,long cantidadClientes,BigDecimal importeAbonos) {}
    public Resumen consultar(LocalDate desde,LocalDate hasta) {
        if(desde==null || hasta==null || desde.isAfter(hasta) || hasta.equals(LocalDate.MAX))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rango de fechas inválido");
        var zona=ZoneId.of("America/Argentina/Buenos_Aires");
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS cantidad, COUNT(DISTINCT a.cliente_id) AS clientes,
                    COALESCE(SUM(a.importe),0) AS importe
                FROM abonos_cliente a JOIN entregas e ON e.id=a.entrega_id
                WHERE e.creado_en>=? AND e.creado_en<?
                """,(rs,n)->new Resumen(desde,hasta,rs.getLong("cantidad"),rs.getLong("clientes"),rs.getBigDecimal("importe")),
                desde.atStartOfDay(zona).toOffsetDateTime(),hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime());
    }
}
