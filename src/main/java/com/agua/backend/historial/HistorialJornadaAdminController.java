package com.agua.backend.historial;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/jornadas")
@RequiredArgsConstructor
public class HistorialJornadaAdminController {

    private final HistorialJornadaService service;

    @GetMapping
    public HistorialJornadaService.Pagina listar(
            @Valid @ModelAttribute HistorialJornadaService.Filtro filtro,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio,
            Authentication authentication
    ) {
        return service.listar(
                filtro, pagina, tamanio, authentication.getName()
        );
    }

    @GetMapping("/resumen")
    public HistorialJornadaService.Resumen resumen(
            @Valid @ModelAttribute HistorialJornadaService.Filtro filtro,
            Authentication authentication
    ) {
        return service.resumir(filtro, authentication.getName());
    }
}