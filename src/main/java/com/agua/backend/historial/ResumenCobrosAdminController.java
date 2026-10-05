package com.agua.backend.historial;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/cobros")
@RequiredArgsConstructor
public class ResumenCobrosAdminController {

    private final ResumenCobrosService service;

    @GetMapping("/resumen")
    public ResumenCobrosService.Resumen consultar(
            @RequestParam("desde")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate desde,
            @RequestParam("hasta")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate hasta,
            @RequestParam(name = "camionetaId", required = false)
            UUID camionetaId,
            @RequestParam(name = "repartidorId", required = false)
            UUID repartidorId
    ) {
        return service.consultar(
                desde,
                hasta,
                camionetaId,
                repartidorId
        );
    }
}