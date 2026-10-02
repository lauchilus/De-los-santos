package com.agua.backend.stock;

import java.util.UUID;

public record MovimientoStockResponse(
        UUID id,
        UUID productoId,
        EstadoStock estado,
        long cantidad,
        long saldoResultante,
        String motivo,
        UUID usuarioId
) {
}