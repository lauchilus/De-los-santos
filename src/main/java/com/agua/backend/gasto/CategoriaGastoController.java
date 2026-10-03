package com.agua.backend.gasto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CategoriaGastoController {
    private final CategoriaGastoService service;

    @GetMapping("/categorias-gasto")
    public List<CategoriaGastoResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas,
                                               Authentication authentication) {
        return service.listar(incluirInactivas, authentication.getName());
    }

    @PostMapping("/admin/categorias-gasto")
    public CategoriaGastoResponse crear(@Valid @RequestBody CrearCategoriaGastoRequest request, Authentication authentication) {
        return service.crear(request, authentication.getName());
    }

    @PutMapping("/admin/categorias-gasto/{id}")
    public CategoriaGastoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarCategoriaGastoRequest request,
                                              Authentication authentication) {
        return service.actualizar(id, request, authentication.getName());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> duplicada(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre, incluso si está inactiva"));
    }
}
