package com.agua.backend.entrega;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EntregaResponse(
        UUID id, UUID jornadaId, UUID clienteId, UUID usuarioId,
        BigDecimal total, BigDecimal saldoCliente, OffsetDateTime creadoEn, List<Item> items,
        CargoAbono abono
) {
    public record CargoAbono(UUID id,UUID planId,String planNombre,long planVersion,LocalDate periodo,BigDecimal importe) {}
    public record Item(UUID productoId, int entregados, int devueltosVacios, int devueltosDanados,
                       Long precioVersion, BigDecimal precioUnitario, BigDecimal subtotal,
                       long envasesResultantes, int incluidosAbono, int facturados,
                       BigDecimal precioGeneral, BigDecimal precioCliente, Long precioClienteVersion,
                       BigDecimal precioExcepcional, String motivoPrecio, String origenPrecio) {}
}
