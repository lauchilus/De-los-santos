package com.agua.backend.entrega;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CrearEntregaRequest(
        @NotNull UUID operacionId,
        @NotNull UUID clienteId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotNull UUID productoId,
            @NotNull @PositiveOrZero Integer entregados,
            @NotNull @PositiveOrZero Integer devueltosVacios,
            @NotNull @PositiveOrZero Integer devueltosDanados,
            @PositiveOrZero Long precioVersion
    ) {}
}
