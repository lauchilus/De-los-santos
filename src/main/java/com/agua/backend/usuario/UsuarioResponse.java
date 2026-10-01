package com.agua.backend.usuario;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String username,
        String nombre,
        Rol rol,
        boolean activo,
        Instant creadoEn
) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getCreadoEn()
        );
    }
}