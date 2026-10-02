package com.agua.backend.camioneta;

import java.util.UUID;

public record CamionetaResponse(
        UUID id,
        String nombre,
        String patente,
        boolean activo,
        Long version
) {
    public static CamionetaResponse desde(Camioneta camioneta) {
        return new CamionetaResponse(
                camioneta.getId(),
                camioneta.getNombre(),
                camioneta.getPatente(),
                camioneta.isActivo(),
                camioneta.getVersion()
        );
    }
}