package com.agua.backend.abono;

import com.agua.backend.cliente.CrearClienteRequest;
import com.agua.backend.cobro.*;
import com.agua.backend.entrega.*;
import com.agua.backend.gasto.GastoService;
import com.agua.backend.sincronizacion.*;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

// Hereda las once comprobaciones del módulo anterior, con su esquema aislado por prueba.
class SincronizacionPostgresTest extends AbonosPostgresTest {
    SincronizacionService sync;
    @BeforeEach void configurarSync() {
        context.registerBean(Validator.class, LocalValidatorFactoryBean::new);
        context.registerBean(CobroService.class);
        context.registerBean(GastoService.class);
        context.registerBean(AltaClienteSincronizadaService.class);
        context.registerBean(OperacionSincronizadaService.class);
        context.registerBean(SincronizacionService.class);
        sync = context.getBean(SincronizacionService.class);
    }
    SincronizarRequest.Operacion alta(UUID id) {
        return new SincronizarRequest.Operacion(SincronizarRequest.Tipo.ALTA_CLIENTE,
                new SincronizarRequest.AltaCliente(id,new CrearClienteRequest("Offline","Calle 1","123456",null)),null,null,null,null,null);
    }
    SincronizarRequest.Operacion entrega(UUID id,UUID c,int cantidad,int incluidos,String total,String precio) {
        var r = new CrearEntregaRequest(id,c,List.of(new CrearEntregaRequest.Item(BIDON,cantidad,0,0,0L)));
        var esperado = new SincronizarRequest.EntregaEsperada(new BigDecimal(total),
                List.of(new SincronizarRequest.ItemEsperado(BIDON,incluidos,new BigDecimal(precio))));
        return new SincronizarRequest.Operacion(SincronizarRequest.Tipo.ENTREGA,null,r,null,null,null,esperado);
    }
    SincronizarRequest.Operacion cobro(UUID c,String importe) {
        var r = new CrearCobroRequest(UUID.randomUUID(),c,List.of(new CrearCobroRequest.Pago(MedioPago.EFECTIVO,new BigDecimal(importe),null)));
        return new SincronizarRequest.Operacion(SincronizarRequest.Tipo.COBRO,null,null,r,null,null,null);
    }
    SincronizacionService.Respuesta enviar(SincronizarRequest.Operacion... ops) {
        return sync.sincronizar(jornada,new SincronizarRequest(List.of(ops)),"admin");
    }
    @Test void altaEntregaCobroYReintentoDeLote() {
        UUID c=UUID.randomUUID();var a=alta(c);var e=entrega(UUID.randomUUID(),c,1,0,"5000","5000");var pago=cobro(c,"2000");
        var r=enviar(a,e,pago);
        assertTrue(r.completo());assertEquals(3,r.confirmadas());assertEquals(0,r.pendientes());
        assertEquals(r,enviar(a,e,pago));
        assertEquals(1,scalar("SELECT COUNT(*) FROM altas_cliente_sincronizadas"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM cobros"));assertEquals(1,scalar("SELECT COUNT(*) FROM entregas"));
        dinero("3000",jdbc.queryForObject("SELECT saldo FROM cuentas_clientes WHERE cliente_id=?",BigDecimal.class,c));
    }
    @Test void conflictoDeImporteRevierteEntregaYDetieneCobro() {
        UUID c=UUID.randomUUID();var r=enviar(alta(c),entrega(UUID.randomUUID(),c,1,0,"4000","4000"),cobro(c,"4000"));
        assertFalse(r.completo());assertEquals(1,r.confirmadas());assertEquals(2,r.pendientes());assertEquals(1,r.siguienteIndice());
        assertEquals("CONFLICTO",r.resultados().get(1).estado());
        assertEquals(1,scalar("SELECT COUNT(*) FROM altas_cliente_sincronizadas"));assertEquals(0,scalar("SELECT COUNT(*) FROM entregas"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM cobros"));assertEquals(20,scalar("SELECT cantidad FROM stock_jornada WHERE estado='LLENO'"));
    }
    @Test void conflictoDeCupoNoLoConsumeYPermiteReanudar() {
        registrar(request(0,true));UUID id=UUID.randomUUID();
        var r=enviar(entrega(id,cliente,1,0,"5000","5000"));
        assertEquals("CONFLICTO",r.resultados().getFirst().estado());assertEquals(0,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
        r=enviar(entrega(id,cliente,1,1,"0","0"));
        assertTrue(r.completo());assertEquals(1,scalar("SELECT SUM(consumidos) FROM cupos_abono"));
        dinero("18000",jdbc.queryForObject("SELECT saldo FROM cuentas_clientes WHERE cliente_id=?",BigDecimal.class,cliente));
    }
    @Test void datoInvalidoConservaPrefijoConfirmado() {
        UUID c=UUID.randomUUID();var r=enviar(alta(c),cobro(c,"-1"),cobro(c,"2000"));
        assertEquals(1,r.confirmadas());assertEquals(400,r.resultados().get(1).httpStatus());assertEquals(0,scalar("SELECT COUNT(*) FROM cobros"));
    }
    @Test void reintentoTrasCerrarYRechazoDeOperacionNueva() {
        var a=alta(UUID.randomUUID());var original=enviar(a);
        jdbc.update("UPDATE jornadas SET estado='CERRADA',cerrada_en=CURRENT_TIMESTAMP WHERE id=?",jornada);
        assertEquals(original,enviar(a));
        assertEquals(409,enviar(alta(UUID.randomUUID())).resultados().getFirst().httpStatus());
    }
    @Test void jornadaAjenaNoAceptaNiAltas() {
        assertEquals(403,assertThrows(ResponseStatusException.class,()->sync.sincronizar(jornada,
                new SincronizarRequest(List.of(alta(UUID.randomUUID()))),"otro")).getStatusCode().value());
        assertEquals(0,scalar("SELECT COUNT(*) FROM altas_cliente_sincronizadas"));
    }
    @Test void mismoUuidDeClienteConDatosDistintosEsConflicto() {
        UUID id=UUID.randomUUID();enviar(alta(id));
        var nuevo=new SincronizarRequest.Operacion(SincronizarRequest.Tipo.ALTA_CLIENTE,
                new SincronizarRequest.AltaCliente(id,new CrearClienteRequest("Otro","Calle 1","123456",null)),null,null,null,null,null);
        assertEquals(409,enviar(nuevo).resultados().getFirst().httpStatus());assertEquals(1,scalar("SELECT COUNT(*) FROM altas_cliente_sincronizadas"));
    }
}
