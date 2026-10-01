package com.agua.backend.cliente;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService service;

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(
            @Valid @RequestBody CrearClienteRequest request
    ) {
        ClienteResponse cliente = service.crear(request);

        return ResponseEntity
                .created(URI.create("/api/v1/clientes/" + cliente.id()))
                .body(cliente);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    @GetMapping
    public PaginaClientes listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio
    ) {
        var resultado = service.listar(pagina, tamanio);

        return new PaginaClientes(
                resultado.getContent(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    public record PaginaClientes(
            List<ClienteResponse> contenido,
            int pagina,
            int tamanio,
            long totalElementos,
            int totalPaginas
    ) {
    }
}