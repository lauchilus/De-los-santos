package com.agua.backend.stock;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/stock")
@RequiredArgsConstructor
public class StockAdminController {

    private final StockService service;

    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStockResponse ajustar(
            @Valid @RequestBody AjustarStockRequest request,
            Authentication authentication
    ) {
        return service.ajustar(request, authentication.getName());
    }
}