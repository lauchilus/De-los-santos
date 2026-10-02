package com.agua.backend.jornada;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AbrirJornadaRequest(
        @NotNull
        UUID operacionId,

        @NotNull
        UUID camionetaId,

        @NotNull
        UUID repartidorId,

        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal efectivoInicial
) {
}