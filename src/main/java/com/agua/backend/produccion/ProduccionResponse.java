package com.agua.backend.produccion;

import java.util.UUID;

public record ProduccionResponse(
        UUID id,
        UUID productoId,
        int cantidad,
        UUID usuarioId
) {
}