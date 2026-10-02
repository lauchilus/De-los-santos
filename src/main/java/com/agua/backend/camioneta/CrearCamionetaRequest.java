package com.agua.backend.camioneta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearCamionetaRequest(

        @NotBlank
        @Size(max = 100)
        String nombre,

        @NotBlank
        @Size(max = 15)
        @Pattern(
                regexp = "^[A-Za-z0-9]+(?: [A-Za-z0-9]+)*$",
                message = "La patente debe contener letras y números, con espacios opcionales entre grupos"
        )
        String patente
) {
}