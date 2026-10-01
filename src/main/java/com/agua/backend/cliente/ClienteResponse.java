package com.agua.backend.cliente;

import java.time.Instant;
import java.util.UUID;

public record ClienteResponse(
        UUID id,
        String nombre,
        String direccion,
        String telefono,
        String observaciones,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn,
        Long version
) {

    public static ClienteResponse desde(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getDireccion(),
                cliente.getTelefono(),
                cliente.getObservaciones(),
                cliente.isActivo(),
                cliente.getCreadoEn(),
                cliente.getActualizadoEn(),
                cliente.getVersion()
        );
    }
}