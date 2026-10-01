package com.agua.backend.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        String username = request.username().toLowerCase(Locale.ROOT);

        if (repository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El nombre de usuario ya existe"
            );
        }

        // BCrypt admite como máximo 72 bytes, no necesariamente 72 caracteres.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña supera los 72 bytes UTF-8"
            );
        }

        Usuario usuario = new Usuario(
                username,
                request.nombre(),
                passwordEncoder.encode(request.password()),
                request.rol()
        );

        return UsuarioResponse.desde(repository.saveAndFlush(usuario));
    }

    public UsuarioResponse obtener(UUID id) {
        return repository.findById(id)
                .map(UsuarioResponse::desde)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario no encontrado"
                ));
    }
}