package com.agua.backend.gasto;

import com.agua.backend.cobro.MedioPago;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

public record FiltroGastosRequest(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        UUID categoriaId,
        AmbitoGasto ambito,
        MedioPago medio,
        UUID jornadaId,
        UUID usuarioId
) {}
