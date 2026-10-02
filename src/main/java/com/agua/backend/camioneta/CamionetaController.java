package com.agua.backend.camioneta;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/camionetas")
@RequiredArgsConstructor
public class CamionetaController {

    private final CamionetaService service;

    @GetMapping
    public List<CamionetaResponse> listar() {
        return service.listarActivas();
    }

    @GetMapping("/{id}")
    public CamionetaResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }
}