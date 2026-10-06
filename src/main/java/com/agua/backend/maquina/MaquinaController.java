package com.agua.backend.maquina;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequiredArgsConstructor
public class MaquinaController {
    private final MaquinaService service;
    @PostMapping("/api/v1/jornadas/{jornadaId}/maquinas")
    public MaquinaService.Movimiento registrar(@PathVariable UUID jornadaId,@Valid @RequestBody MaquinaService.Request r,Authentication a){return service.registrar(jornadaId,r,a.getName());}
    @GetMapping("/api/v1/jornadas/{jornadaId}/maquinas/{id}")
    public MaquinaService.Movimiento obtener(@PathVariable UUID jornadaId,@PathVariable UUID id,Authentication a){return service.obtener(jornadaId,id,a.getName());}
    @GetMapping("/api/v1/clientes/{id}/maquinas")
    public List<MaquinaService.Tenencia> cliente(@PathVariable UUID id){return service.cliente(id);}
}
