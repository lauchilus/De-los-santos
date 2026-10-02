package com.agua.backend.entrega;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EntregaController {
    private final EntregaService service;

    @PostMapping("/jornadas/{jornadaId}/entregas")
    public EntregaResponse registrar(@PathVariable UUID jornadaId,
                                      @Valid @RequestBody CrearEntregaRequest request,
                                      Authentication authentication) {
        return service.registrar(jornadaId, request, authentication.getName());
    }

    @GetMapping("/jornadas/{jornadaId}/entregas/{entregaId}")
    public EntregaResponse obtener(@PathVariable UUID jornadaId, @PathVariable UUID entregaId,
                                    Authentication authentication) {
        return service.obtener(jornadaId, entregaId, authentication.getName());
    }

    @GetMapping("/clientes/{clienteId}/situacion")
    public SituacionClienteResponse situacion(@PathVariable UUID clienteId, Authentication authentication) {
        return service.situacion(clienteId, authentication.getName());
    }
}
