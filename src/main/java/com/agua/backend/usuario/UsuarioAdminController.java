package com.agua.backend.usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioService service;

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CrearUsuarioRequest request
    ) {
        UsuarioResponse usuario = service.crear(request);

        return ResponseEntity
                .created(URI.create("/api/v1/admin/usuarios/" + usuario.id()))
                .body(usuario);
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConflicto(
            DataIntegrityViolationException exception
    ) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Los datos entran en conflicto con un registro existente."
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }
}