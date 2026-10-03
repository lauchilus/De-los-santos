package com.agua.backend.gasto;

import java.util.List;

public record PaginaGastosResponse(List<GastoResponse> contenido, int pagina, int tamanio, boolean hayMas) {}
