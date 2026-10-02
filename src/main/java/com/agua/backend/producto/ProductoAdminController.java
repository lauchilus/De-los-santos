package com.agua.backend.producto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/productos")
@RequiredArgsConstructor
public class ProductoAdminController {
    private final PrecioProductoService service;

    @PutMapping("/{id}/precio")
    public ProductoResponse actualizar(@PathVariable UUID id,
                                       @Valid @RequestBody ActualizarPrecioRequest request,
                                       Authentication authentication) {
        return service.actualizar(id, request, authentication.getName());
    }
}
