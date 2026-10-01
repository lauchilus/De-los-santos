package com.agua.backend.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearClienteRequest(

        @NotBlank
        @Size(max = 150)
        String nombre,

        @NotBlank
        @Size(max = 250)
        String direccion,

        @NotBlank
        @Size(max = 50)
        String telefono,

        @Size(max = 2000)
        String observaciones
) {
}