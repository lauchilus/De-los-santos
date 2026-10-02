package com.agua.backend.stock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AjustarStockRequest(
        @NotNull
        UUID productoId,

        @NotNull
        EstadoStock estado,

        @NotNull
        Integer cantidad,

        @NotBlank
        @Size(max = 500)
        String motivo
) {
}