package com.agua.backend.gasto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearCategoriaGastoRequest(@NotBlank @Size(max = 80) String nombre) {}
