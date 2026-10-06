package com.agua.backend.entrega;

import com.agua.backend.abono.AbonoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CrearEntregaRequest(
        @NotNull UUID operacionId,
        @NotNull UUID clienteId,
        @NotNull @Size(max=100) List<@NotNull @Valid Item> items,
        @Valid AbonoService.Carga abono
) {
    public CrearEntregaRequest(UUID operacionId,UUID clienteId,List<Item> items){this(operacionId,clienteId,items,null);}
    public record Item(
            @NotNull UUID productoId,
            @NotNull @PositiveOrZero Integer entregados,
            @NotNull @PositiveOrZero Integer devueltosVacios,
            @NotNull @PositiveOrZero Integer devueltosDanados,
            @PositiveOrZero Long precioVersion,
            @PositiveOrZero Long precioClienteVersion,
            @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal precioExcepcional,
            @Size(max=500) String motivoPrecio
    ) {
        public Item(UUID productoId,Integer entregados,Integer devueltosVacios,Integer devueltosDanados,Long precioVersion){
            this(productoId,entregados,devueltosVacios,devueltosDanados,precioVersion,null,null,null);
        }
    }
}
