package com.agua.backend.abono;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class AbonoController {
    private final AbonoService service;
    @GetMapping("/api/v1/planes-abono")
    public List<AbonoService.Plan> planes(){return service.planes().stream().filter(AbonoService.Plan::activo).toList();}
    @GetMapping("/api/v1/admin/planes-abono")
    public List<AbonoService.Plan> todos(){return service.planes();}
    @PostMapping("/api/v1/admin/planes-abono")
    @ResponseStatus(HttpStatus.CREATED)
    public AbonoService.Plan crear(@Valid @RequestBody AbonoService.PlanRequest r,Authentication a){return service.guardar(null,r,a.getName());}
    @PutMapping("/api/v1/admin/planes-abono/{id}")
    public AbonoService.Plan editar(@PathVariable UUID id,@Valid @RequestBody AbonoService.PlanRequest r,Authentication a){return service.guardar(id,r,a.getName());}
    @GetMapping("/api/v1/clientes/{id}/abonos")
    public List<AbonoService.Abono> cliente(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate periodo){return service.cliente(id,periodo);}
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail conflicto(){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"El nombre del plan ya existe o los datos entran en conflicto");}
}
