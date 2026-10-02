package com.agua.backend.camioneta;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CamionetaService {

    private final CamionetaRepository repository;

    @Transactional
    public CamionetaResponse crear(CrearCamionetaRequest request) {
        String patente = Camioneta.normalizarPatente(request.patente());

        if (repository.existsByPatente(patente)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una camioneta con esa patente"
            );
        }

        Camioneta camioneta = new Camioneta(
                request.nombre(),
                patente
        );

        return CamionetaResponse.desde(
                repository.saveAndFlush(camioneta)
        );
    }

    public List<CamionetaResponse> listarActivas() {
        return repository.findByActivoTrueOrderByNombreAscIdAsc()
                .stream()
                .map(CamionetaResponse::desde)
                .toList();
    }

    public CamionetaResponse obtener(UUID id) {
        return repository.findById(id)
                .map(CamionetaResponse::desde)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Camioneta no encontrada"
                ));
    }
}