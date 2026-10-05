package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/movimientos")
@RequiredArgsConstructor
public class HistorialClienteController {

    private final HistorialClienteService service;

    @GetMapping
    public HistorialClienteService.Pagina listar(
            @PathVariable("clienteId") UUID clienteId,
            @RequestParam("desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate desde,
            @RequestParam("hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate hasta,
            @RequestParam(name = "pagina", defaultValue = "0")
            int pagina,
            @RequestParam(name = "tamanio", defaultValue = "20")
            int tamanio
    ) {
        return service.listar(clienteId, desde, hasta, pagina, tamanio);
    }
}