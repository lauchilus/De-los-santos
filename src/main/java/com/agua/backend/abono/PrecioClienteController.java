package com.agua.backend.abono;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequiredArgsConstructor
public class PrecioClienteController {
    private final PrecioClienteService service;
    @GetMapping("/api/v1/clientes/{clienteId}/precios")
    public List<PrecioClienteService.Precio> listar(@PathVariable UUID clienteId){return service.listar(clienteId);}
    @PutMapping("/api/v1/admin/clientes/{clienteId}/precios/{productoId}")
    public PrecioClienteService.Precio guardar(@PathVariable UUID clienteId,@PathVariable UUID productoId,
            @Valid @RequestBody PrecioClienteService.Request r,Authentication a){return service.guardar(clienteId,productoId,r,a.getName());}
}
