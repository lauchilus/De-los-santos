package com.agua.backend.produccion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CrearProduccionRequest(
        @NotNull
        UUID operacionId,

        @NotNull
        UUID productoId,

        @NotNull
        @Positive
        Integer cantidad
) {
}