package com.agua.backend.entrega;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EntregaResponse(
        UUID id, UUID jornadaId, UUID clienteId, UUID usuarioId,
        BigDecimal total, BigDecimal saldoCliente, OffsetDateTime creadoEn, List<Item> items
) {
    public record Item(UUID productoId, int entregados, int devueltosVacios, int devueltosDanados,
                       Long precioVersion, BigDecimal precioUnitario, BigDecimal subtotal,
                       long envasesResultantes) {}
}
