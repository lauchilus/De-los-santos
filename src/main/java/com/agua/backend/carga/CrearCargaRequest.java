package com.agua.backend.carga;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CrearCargaRequest(
        @NotNull UUID operacionId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items
) {
    public record Item(@NotNull UUID productoId, @NotNull @Positive Integer cantidad) {}
}
