package com.agua.backend.produccion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/producciones")
@RequiredArgsConstructor
public class ProduccionController {

    private final ProduccionService service;

    @PostMapping
    public ProduccionResponse producir(
            @Valid @RequestBody CrearProduccionRequest request,
            Authentication authentication
    ) {
        return service.producir(request, authentication.getName());
    }
}