package com.agua.backend.stock;

import java.util.UUID;

public record StockResponse(
        UUID productoId,
        String codigo,
        String nombre,
        EstadoStock estado,
        long cantidad
) {
}