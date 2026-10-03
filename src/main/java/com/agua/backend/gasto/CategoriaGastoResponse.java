package com.agua.backend.gasto;

import java.util.UUID;

public record CategoriaGastoResponse(UUID id, String nombre, boolean activo, long version) {}
