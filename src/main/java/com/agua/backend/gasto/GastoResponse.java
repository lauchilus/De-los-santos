package com.agua.backend.gasto;

import com.agua.backend.cobro.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GastoResponse(
        UUID id, AmbitoGasto ambito, UUID jornadaId, UUID categoriaId, String categoriaNombre,
        LocalDate fecha, String descripcion, MedioPago medio, BigDecimal importe,
        String referencia, UUID usuarioId, OffsetDateTime creadoEn
) {}
