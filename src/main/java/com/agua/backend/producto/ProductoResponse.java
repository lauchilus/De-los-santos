package com.agua.backend.producto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductoResponse(
        UUID id,
        String codigo,
        String nombre,
        TipoProducto tipo,
        Integer capacidadLitros,
        BigDecimal precioVenta,
        Long version
) {
    public static ProductoResponse desde(Producto producto) {
        return new ProductoResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getNombre(),
                producto.getTipo(),
                producto.getCapacidadLitros(),
                producto.getPrecioVenta(),
                producto.getVersion()
        );
    }
}