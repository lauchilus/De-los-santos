package com.agua.backend.cierre;

import com.agua.backend.stock.EstadoStock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CerrarJornadaRequest(
        @NotNull UUID operacionId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 28, fraction = 2) BigDecimal efectivoContado,
        @Size(max = 500) String motivoDiferencia,
        @NotNull @Size(max = 1000) List<@NotNull @Valid Devolucion> devoluciones
) {
    public record Devolucion(
            @NotNull UUID productoId,
            @NotNull EstadoStock estado,
            @NotNull @Positive Long cantidad
    ) {}
}
