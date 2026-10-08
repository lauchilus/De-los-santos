package com.agua.backend.jornada;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OpcionesAperturaController {
    private final OpcionesAperturaService service;
    @GetMapping("/api/v1/jornadas/apertura/opciones")
    public ResponseEntity<OpcionesAperturaService.Opciones> obtener(Authentication authentication){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.obtener(authentication.getName()));
    }
}
