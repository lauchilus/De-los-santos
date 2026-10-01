package com.agua.backend.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "Debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo"
        )
        String username,

        @NotBlank
        @Size(max = 150)
        String nombre,

        @NotBlank
        @Size(min = 12, max = 72)
        String password,

        @NotNull
        Rol rol
) {
    @Override
    public String toString() {
        return "CrearUsuarioRequest[datos omitidos]";
    }
}