package com.agua.backend.cobro;

import java.math.BigDecimal;
import java.util.UUID;

public record CajaJornadaResponse(
        UUID jornadaId,
        String estado,
        BigDecimal efectivoInicial,
        BigDecimal ingresosEfectivo,
        BigDecimal ingresosTransferencia,
        BigDecimal ingresosMercadoPago,
        BigDecimal totalCobrado,
        BigDecimal efectivoEsperado
) {}
