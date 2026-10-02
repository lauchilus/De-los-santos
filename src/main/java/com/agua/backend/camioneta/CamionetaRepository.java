package com.agua.backend.camioneta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CamionetaRepository
        extends JpaRepository<Camioneta, UUID> {

    List<Camioneta> findByActivoTrueOrderByNombreAscIdAsc();

    boolean existsByPatente(String patente);
}