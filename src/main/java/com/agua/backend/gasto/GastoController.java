package com.agua.backend.gasto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GastoController {
    private final GastoService service;

    @PostMapping("/jornadas/{jornadaId}/gastos")
    public GastoResponse registrarJornada(@PathVariable UUID jornadaId, @Valid @RequestBody CrearGastoRequest request,
                                           Authentication authentication) {
        return service.registrar(jornadaId, request, authentication.getName());
    }

    @PostMapping("/admin/gastos")
    public GastoResponse registrarGeneral(@Valid @RequestBody CrearGastoRequest request, Authentication authentication) {
        return service.registrar(null, request, authentication.getName());
    }

    @GetMapping("/jornadas/{jornadaId}/gastos/{gastoId}")
    public GastoResponse obtenerJornada(@PathVariable UUID jornadaId, @PathVariable UUID gastoId, Authentication authentication) {
        return service.obtenerJornada(jornadaId, gastoId, authentication.getName());
    }

    @GetMapping("/jornadas/{jornadaId}/gastos")
    public PaginaGastosResponse listarJornada(@PathVariable UUID jornadaId, @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanio, Authentication authentication) {
        return service.listarJornada(jornadaId, pagina, tamanio, authentication.getName());
    }

    @GetMapping("/admin/gastos/{gastoId}")
    public GastoResponse obtenerAdmin(@PathVariable UUID gastoId, Authentication authentication) {
        return service.obtenerAdmin(gastoId, authentication.getName());
    }

    @GetMapping("/admin/gastos")
    public PaginaGastosResponse listarAdmin(@Valid @ModelAttribute FiltroGastosRequest filtro,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamanio, Authentication authentication) {
        return service.listarAdmin(filtro, pagina, tamanio, authentication.getName());
    }

    @GetMapping("/admin/gastos/resumen")
    public ResumenGastosResponse resumen(@Valid @ModelAttribute FiltroGastosRequest filtro, Authentication authentication) {
        return service.resumen(filtro, authentication.getName());
    }
}
