package com.agua.backend.cliente;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/clientes/gestion") @RequiredArgsConstructor
public class GestionClienteController {
 private final GestionClienteService service;
 @GetMapping public GestionClienteService.Pagina listar(@RequestParam(defaultValue="") String busqueda,@RequestParam(defaultValue="0") int pagina,Authentication auth){return service.listar(busqueda,pagina,auth.getName());}
 @PostMapping public ClienteResponse crear(@Valid @RequestBody GestionClienteService.Solicitud r,Authentication auth){return service.guardar(null,r,auth.getName());}
 @PutMapping("/{id}") public ClienteResponse editar(@PathVariable UUID id,@Valid @RequestBody GestionClienteService.Solicitud r,Authentication auth){return service.guardar(id,r,auth.getName());}
}
