package com.agua.backend.gasto;

import com.agua.backend.cobro.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResumenGastosResponse(LocalDate desde, LocalDate hasta, BigDecimal total, List<Fila> filas) {
    public record Fila(UUID categoriaId, String categoriaNombreActual, AmbitoGasto ambito,
                       MedioPago medio, long cantidadGastos, BigDecimal importe) {}
}
