package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/clientes/cuentas")
@RequiredArgsConstructor
public class CuentasClientesAdminController {

    private final CuentasClientesService service;

    @GetMapping
    public CuentasClientesService.Pagina listar(
            @RequestParam(name = "busqueda", required = false)
            String busqueda,
            @RequestParam(name = "situacion", defaultValue = "TODOS")
            String situacion,
            @RequestParam(name = "activo", required = false)
            Boolean activo,
            @RequestParam(name = "pagina", defaultValue = "0")
            int pagina,
            @RequestParam(name = "tamanio", defaultValue = "20")
            int tamanio
    ) {
        return service.listar(
                busqueda,
                situacion,
                activo,
                pagina,
                tamanio
        );
    }
}