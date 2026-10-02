package com.agua.backend.carga;

import com.agua.backend.stock.StockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jornadas/{jornadaId}")
@RequiredArgsConstructor
public class CargaController {
    private final CargaService service;

    @PostMapping("/cargas")
    public CargaResponse cargar(@PathVariable UUID jornadaId,
                                @Valid @RequestBody CrearCargaRequest request,
                                Authentication authentication) {
        return service.cargar(jornadaId, request, authentication.getName());
    }

    @GetMapping("/cargas/{cargaId}")
    public CargaResponse obtener(@PathVariable UUID jornadaId, @PathVariable UUID cargaId,
                                 Authentication authentication) {
        return service.obtener(jornadaId, cargaId, authentication.getName());
    }

    @GetMapping("/stock")
    public List<StockResponse> stock(@PathVariable UUID jornadaId, Authentication authentication) {
        return service.stock(jornadaId, authentication.getName());
    }
}
