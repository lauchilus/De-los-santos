package com.agua.backend.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ActualizarPrecioRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal precioVenta,
        @NotNull @PositiveOrZero Long version
) {}
