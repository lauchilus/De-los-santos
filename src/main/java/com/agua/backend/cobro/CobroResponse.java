package com.agua.backend.cobro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CobroResponse(
        UUID id, UUID jornadaId, UUID clienteId, UUID usuarioId,
        BigDecimal total, BigDecimal saldoCliente, OffsetDateTime creadoEn, List<Pago> pagos
) {
    public record Pago(MedioPago medio, BigDecimal importe, String referencia) {}
}
