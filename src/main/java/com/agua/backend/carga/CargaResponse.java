package com.agua.backend.carga;

import com.agua.backend.stock.EstadoStock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CargaResponse(
        UUID id,
        UUID jornadaId,
        int numero,
        String tipo,
        UUID usuarioId,
        OffsetDateTime creadoEn,
        List<Item> items
) {
    public record Item(
            UUID productoId,
            EstadoStock estado,
            int cantidad,
            long saldoFabrica,
            long saldoJornada
    ) {}
}
