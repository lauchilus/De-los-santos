package com.agua.backend.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {

    @GetMapping("/api/v1/auth/me")
    public UsuarioActual obtenerActual(Authentication authentication) {
        return new UsuarioActual(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
    }

    public record UsuarioActual(
            String username,
            List<String> roles
    ) {
    }
}