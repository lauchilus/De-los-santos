package com.agua.backend.jornada;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record JornadaResponse(
        UUID id,
        LocalDate fecha,
        UUID camionetaId,
        UUID repartidorId,
        BigDecimal efectivoInicial,
        String estado,
        UUID abiertaPor
) {
}