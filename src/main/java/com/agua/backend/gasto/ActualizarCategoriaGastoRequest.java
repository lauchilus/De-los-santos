package com.agua.backend.gasto;

import jakarta.validation.constraints.*;

public record ActualizarCategoriaGastoRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotNull Boolean activo,
        @NotNull @PositiveOrZero Long version
) {}
