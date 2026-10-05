package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/clientes/envases")
@RequiredArgsConstructor
public class EnvasesClientesAdminController {

    private final EnvasesClientesService service;

    @GetMapping
    public EnvasesClientesService.Pagina listar(
            @RequestParam(name = "busqueda", required = false)
            String busqueda,
            @RequestParam(name = "productoId", required = false)
            UUID productoId,
            @RequestParam(name = "activo", required = false)
            Boolean activo,
            @RequestParam(name = "pagina", defaultValue = "0")
            int pagina,
            @RequestParam(name = "tamanio", defaultValue = "20")
            int tamanio
    ) {
        return service.listar(
                busqueda,
                productoId,
                activo,
                pagina,
                tamanio
        );
    }
}