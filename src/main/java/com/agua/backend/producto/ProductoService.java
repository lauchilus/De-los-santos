package com.agua.backend.producto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository repository;

    public List<ProductoResponse> listarActivos() {
        return repository.findByActivoTrueOrderByCodigoAsc()
                .stream()
                .map(ProductoResponse::desde)
                .toList();
    }
}