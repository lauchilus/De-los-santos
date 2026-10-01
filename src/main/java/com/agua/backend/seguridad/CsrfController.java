package com.agua.backend.seguridad;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    @GetMapping("/api/v1/csrf")
    public CsrfResponse obtener(CsrfToken token) {
        return new CsrfResponse(
                token.getHeaderName(),
                token.getToken()
        );
    }

    public record CsrfResponse(String headerName, String token) {
    }
}