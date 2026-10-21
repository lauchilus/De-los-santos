package com.agua.backend.jornada;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jornadas")
public class ListadoJornadasController {
    private final ListadoJornadasService service;
    @GetMapping
    public ResponseEntity<ListadoJornadasService.Pagina> listar(
            @RequestParam(defaultValue="ABIERTA") String estado,
            @RequestParam(defaultValue="0") int pagina,
            @RequestParam(defaultValue="20") int tamanio,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(estado,pagina,tamanio,authentication.getName()));
    }
}
