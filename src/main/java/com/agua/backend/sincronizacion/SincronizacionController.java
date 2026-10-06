package com.agua.backend.sincronizacion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jornadas/{jornadaId}/sincronizacion")
public class SincronizacionController {
    private final SincronizacionService service;
    @PostMapping
    public SincronizacionService.Respuesta sincronizar(@PathVariable UUID jornadaId,
            @Valid @RequestBody SincronizarRequest request, Authentication authentication) {
        return service.sincronizar(jornadaId, request, authentication.getName());
    }
}
