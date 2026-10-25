package com.agua.backend.importacion;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/importaciones/clientes")
public class ImportacionClientesController {
 private final ImportacionClientesService service;
 @PostMapping("/previa") public ImportacionClientesService.Vista previa(@RequestBody ImportacionClientesService.Solicitud r,Principal p){return service.preparar(r,p.getName());}
 @GetMapping("/{id}") public ImportacionClientesService.Vista obtener(@PathVariable UUID id,Principal p){return service.consultar(id,p.getName());}
 @PostMapping("/{id}/confirmar") public ImportacionClientesService.Vista confirmar(@PathVariable UUID id,Principal p){return service.confirmar(id,p.getName());}
}
