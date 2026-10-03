package com.agua.backend.gasto;

import com.agua.backend.cobro.MedioPago;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CrearGastoRequest(
        @NotNull UUID operacionId,
        @NotNull UUID categoriaId,
        @NotNull LocalDate fecha,
        @NotBlank @Size(max = 500) String descripcion,
        @NotNull MedioPago medio,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal importe,
        @Size(max = 100) String referencia
) {}
