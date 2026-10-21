package com.agua.backend.abono;

import com.agua.backend.entrega.*;
import com.agua.backend.historial.ResumenVentasService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CopasPostgresTest extends AbonosPostgresTest {
    static final UUID COPA=UUID.fromString("00000000-0000-4000-8000-000000000003");
    void stockCopas() {
        jdbc.update("INSERT INTO stock_jornada(jornada_id,producto_id,estado,cantidad) VALUES(?,?,'DISPONIBLE',3)",jornada,COPA);
    }
    CrearEntregaRequest copa(int n, int vacios, int danados) {
        return new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(COPA,n,vacios,danados,0L)));
    }
    @Test void gratisNoGeneraEnvasesNiDeudaYReintentoNoDuplica() {
        stockCopas();var r=copa(2,0,0);var e=registrar(r);
        dinero("0",e.total());dinero("0",e.saldoCliente());
        assertEquals(0,e.items().getFirst().envasesResultantes());
        assertEquals(0,e.items().getFirst().incluidosAbono());
        assertEquals(e,registrar(r));
        assertEquals(1,scalar("SELECT cantidad FROM stock_jornada WHERE producto_id='"+COPA+"'"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM envases_cliente"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM movimientos_cuenta_cliente"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM movimientos_stock_jornada WHERE estado='DISPONIBLE' AND cantidad=-2"));
    }
    @Test void copasConPrecioYAbonoMixtoSeReportanSinConsumirCupo() {
        stockCopas();jdbc.update("UPDATE productos SET precio_venta=200 WHERE id=?",COPA);
        var r=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(
            new CrearEntregaRequest.Item(BIDON,5,0,0,0L),new CrearEntregaRequest.Item(COPA,2,0,0,0L)),
            new AbonoService.Carga(plan.id(),plan.version(),mes));
        var e=registrar(r);dinero("23400",e.total());
        assertEquals(4,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM envases_cliente WHERE producto_id='"+COPA+"'"));
        var ventas=context.getBean(ResumenVentasService.class).consultar(mes,mes.plusMonths(1).minusDays(1),COPA,null,null);
        dinero("400",ventas.totales().importeVendido());
    }
    @Test void precioExcepcionalCopa() {
        stockCopas();
        var r=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(COPA,2,0,0,0L,null,new BigDecimal("150"),"Promoción")));
        var e=registrar(r);dinero("300",e.total());assertEquals("EXCEPCIONAL",e.items().getFirst().origenPrecio());
    }
    @Test void rechazoDevolucionesYStockInsuficienteNoDejaCambios() {
        stockCopas();
        for(var r:List.of(copa(1,1,0),copa(1,0,1)))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->registrar(r)).getStatusCode().value());
        var mixed=new CrearEntregaRequest(UUID.randomUUID(),cliente,List.of(new CrearEntregaRequest.Item(BIDON,1,0,0,0L),new CrearEntregaRequest.Item(COPA,4,0,0,0L)));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->registrar(mixed)).getStatusCode().value());
        assertEquals(20,scalar("SELECT cantidad FROM stock_jornada WHERE producto_id='"+BIDON+"'"));
        assertEquals(3,scalar("SELECT cantidad FROM stock_jornada WHERE producto_id='"+COPA+"'"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM entregas"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM envases_cliente"));
    }
}
