package com.agua.backend.sincronizacion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jornadas/{jornadaId}/datos-offline")
public class DatosOfflineController {
    private final DatosOfflineService service;
    @GetMapping
    public ResponseEntity<DatosOfflineService.Datos> descargar(@PathVariable UUID jornadaId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.descargar(jornadaId, authentication.getName()));
    }
}
