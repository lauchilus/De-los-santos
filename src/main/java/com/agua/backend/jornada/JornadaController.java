package com.agua.backend.jornada;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jornadas")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaService service;

    @PostMapping
    public JornadaResponse abrir(
            @Valid @RequestBody AbrirJornadaRequest request,
            Authentication authentication
    ) {
        return service.abrir(request, authentication.getName());
    }

    @GetMapping("/{id}")
    public JornadaResponse obtener(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return service.obtener(id, authentication.getName());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConflicto(
            DataIntegrityViolationException exception
    ) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "No se pudo abrir la jornada. Verificá si la camioneta "
                        + "o el repartidor ya tienen una jornada abierta."
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problema);
    }
}