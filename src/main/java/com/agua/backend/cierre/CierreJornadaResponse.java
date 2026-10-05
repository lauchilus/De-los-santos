package com.agua.backend.cierre;

import com.agua.backend.cobro.CajaJornadaResponse;
import com.agua.backend.stock.EstadoStock;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CierreJornadaResponse(
        UUID id, UUID jornadaId, UUID usuarioId, OffsetDateTime cerradoEn,
        CajaJornadaResponse caja, BigDecimal efectivoContado, BigDecimal diferencia,
        String motivoDiferencia, List<Devolucion> devoluciones
) {
    public record Devolucion(UUID productoId, EstadoStock estado, long cantidad, long saldoFabrica) {}
}
