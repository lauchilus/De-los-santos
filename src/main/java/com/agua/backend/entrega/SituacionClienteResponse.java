package com.agua.backend.entrega;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SituacionClienteResponse(UUID clienteId, BigDecimal saldo, List<Envases> envases) {
    public record Envases(UUID productoId, String codigo, String nombre, long cantidad) {}
}
