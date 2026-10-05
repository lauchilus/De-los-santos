package com.agua.backend.cierre;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jornadas/{jornadaId}/cierre")
@RequiredArgsConstructor
public class CierreJornadaController {
    private final CierreJornadaService service;

    @GetMapping("/previa")
    public PreviaCierreResponse previa(@PathVariable UUID jornadaId, Authentication authentication) {
        return service.previa(jornadaId, authentication.getName());
    }

    @PostMapping
    public CierreJornadaResponse cerrar(@PathVariable UUID jornadaId,
                                        @Valid @RequestBody CerrarJornadaRequest request,
                                        Authentication authentication) {
        return service.cerrar(jornadaId, request, authentication.getName());
    }

    @GetMapping
    public CierreJornadaResponse obtener(@PathVariable UUID jornadaId, Authentication authentication) {
        return service.obtener(jornadaId, authentication.getName());
    }
}
