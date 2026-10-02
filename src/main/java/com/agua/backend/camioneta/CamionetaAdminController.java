package com.agua.backend.camioneta;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/camionetas")
@RequiredArgsConstructor
public class CamionetaAdminController {

    private final CamionetaService service;

    @PostMapping
    public ResponseEntity<CamionetaResponse> crear(
            @Valid @RequestBody CrearCamionetaRequest request
    ) {
        CamionetaResponse camioneta = service.crear(request);

        return ResponseEntity
                .created(URI.create("/api/v1/camionetas/" + camioneta.id()))
                .body(camioneta);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConflicto(
            DataIntegrityViolationException exception
    ) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Los datos entran en conflicto con una camioneta existente."
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problema);
    }
}