package com.agua.backend.cliente;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository repository;

    @Transactional
    public ClienteResponse crear(CrearClienteRequest request) {
        Cliente cliente = new Cliente(
                request.nombre(),
                request.direccion(),
                request.telefono(),
                request.observaciones()
        );

        return ClienteResponse.desde(repository.saveAndFlush(cliente));
    }

    public ClienteResponse obtener(UUID id) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"
                ));

        return ClienteResponse.desde(cliente);
    }

    public Page<ClienteResponse> listar(int pagina, int tamanio) {
        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página debe ser >= 0 y el tamaño entre 1 y 100"
            );
        }

        PageRequest paginacion = PageRequest.of(
                pagina,
                tamanio,
                Sort.by("nombre").ascending()
                        .and(Sort.by("id").ascending())
        );

        return repository.findAll(paginacion)
                .map(ClienteResponse::desde);
    }
}