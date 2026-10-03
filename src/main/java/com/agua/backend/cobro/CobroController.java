package com.agua.backend.cobro;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jornadas/{jornadaId}")
@RequiredArgsConstructor
public class CobroController {
    private final CobroService service;

    @PostMapping("/cobros")
    public CobroResponse registrar(@PathVariable UUID jornadaId,
                                   @Valid @RequestBody CrearCobroRequest request,
                                   Authentication authentication) {
        return service.registrar(jornadaId, request, authentication.getName());
    }

    @GetMapping("/cobros/{cobroId}")
    public CobroResponse obtener(@PathVariable UUID jornadaId, @PathVariable UUID cobroId,
                                 Authentication authentication) {
        return service.obtener(jornadaId, cobroId, authentication.getName());
    }

    @GetMapping("/caja")
    public CajaJornadaResponse caja(@PathVariable UUID jornadaId, Authentication authentication) {
        return service.caja(jornadaId, authentication.getName());
    }
}
