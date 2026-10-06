package com.agua.backend.sincronizacion;

import com.agua.backend.cliente.CrearClienteRequest;
import com.agua.backend.cobro.CrearCobroRequest;
import com.agua.backend.entrega.CrearEntregaRequest;
import com.agua.backend.gasto.CrearGastoRequest;
import com.agua.backend.maquina.MaquinaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Cada operación se valida al llegar su turno; un error no invalida las anteriores.
public record SincronizarRequest(@NotEmpty @Size(max = 50) List<Operacion> operaciones) {
    public enum Tipo { ALTA_CLIENTE, ENTREGA, COBRO, GASTO, MAQUINA }

    public record AltaCliente(@NotNull UUID operacionId, @NotNull @Valid CrearClienteRequest datos) {}

    public record ItemEsperado(
            @NotNull UUID productoId,
            @NotNull @PositiveOrZero Integer incluidosAbono,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal precioUnitario
    ) {}

    public record EntregaEsperada(
            @NotNull @DecimalMin("0.00") @Digits(integer = 28, fraction = 2) BigDecimal total,
            @NotNull @Size(max = 100) List<@NotNull @Valid ItemEsperado> items
    ) {}

    public record Operacion(
            @NotNull Tipo tipo,
            @Valid AltaCliente altaCliente,
            @Valid CrearEntregaRequest entrega,
            @Valid CrearCobroRequest cobro,
            @Valid CrearGastoRequest gasto,
            @Valid MaquinaService.Request maquina,
            @Valid EntregaEsperada esperadoEntrega
    ) {
        public UUID operacionId() {
            if (tipo == null) return null;
            return switch (tipo) {
                case ALTA_CLIENTE -> altaCliente == null ? null : altaCliente.operacionId();
                case ENTREGA -> entrega == null ? null : entrega.operacionId();
                case COBRO -> cobro == null ? null : cobro.operacionId();
                case GASTO -> gasto == null ? null : gasto.operacionId();
                case MAQUINA -> maquina == null ? null : maquina.operacionId();
            };
        }
    }
}
