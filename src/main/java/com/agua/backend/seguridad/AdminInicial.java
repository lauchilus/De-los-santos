package com.agua.backend.seguridad;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AdminInicial implements ApplicationRunner {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsByRol(Rol.ADMIN)) {
            return;
        }

        String clave = environment.getProperty("APP_ADMIN_PASSWORD");

        if (clave == null
                || clave.isBlank()
                || clave.length() < 12
                || clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Para crear el administrador inicial, configurá "
                            + "APP_ADMIN_PASSWORD con al menos 12 caracteres "
                            + "y como máximo 72 bytes UTF-8."
            );
        }

        if (repository.findByUsername("admin").isPresent()) {
            throw new IllegalStateException(
                    "El usuario admin ya existe sin rol ADMIN. Revisá la base."
            );
        }

        repository.saveAndFlush(new Usuario(
                "admin",
                "Administrador",
                passwordEncoder.encode(clave),
                Rol.ADMIN
        ));
    }
}