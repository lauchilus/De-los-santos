package com.agua.backend.cobro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CrearCobroRequest(
        @NotNull UUID operacionId,
        @NotNull UUID clienteId,
        @NotEmpty @Size(max = 3) List<@NotNull @Valid Pago> pagos
) {
    public record Pago(
            @NotNull MedioPago medio,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal importe,
            @Size(max = 100) String referencia
    ) {}
}
