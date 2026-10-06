package com.agua.backend.historial;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/abonos")
public class ResumenAbonosAdminController {
    private final ResumenAbonosService service;
    @GetMapping("/resumen")
    public ResumenAbonosService.Resumen consultar(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta){return service.consultar(desde,hasta);}
}
