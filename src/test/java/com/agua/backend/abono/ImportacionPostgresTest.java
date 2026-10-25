package com.agua.backend.abono;
import com.agua.backend.importacion.*;
import com.agua.backend.historial.*;
import com.agua.backend.entrega.CrearEntregaRequest;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
class ImportacionPostgresTest extends AbonosPostgresTest {
 ImportacionClientesService service;
 @BeforeEach void importarConfig(){context.registerBean(ImportacionClientesService.class);service=context.getBean(ImportacionClientesService.class);}
 ImportacionClientesService.Solicitud solicitud(Map<String,String[][]> d)throws Exception{return new ImportacionClientesService.Solicitud(Base64.getEncoder().encodeToString(ExcelClientesTest.excel(d)),mes);}
 @Test void altaAtomicaReintentoCuposEHistorialSinVentas()throws Exception {
  var previa=service.preparar(solicitud(ExcelClientesTest.datos(mes.toString())),"admin");assertTrue(previa.errores().isEmpty(),previa.errores().toString());assertNotNull(previa.id());
  assertEquals(1,scalar("SELECT count(*) FROM clientes"));
  var r=service.confirmar(previa.id(),"admin");assertEquals("CONFIRMADA",r.estado());service.confirmar(previa.id(),"admin");
  assertEquals(2,scalar("SELECT count(*) FROM clientes"));assertEquals(0,scalar("SELECT count(*) FROM entregas"));assertEquals(0,scalar("SELECT count(*) FROM cobros"));
  UUID cid=jdbc.queryForObject("SELECT cliente_id FROM clientes_importados WHERE codigo='CLI-001'",UUID.class);
  dinero("15000",jdbc.queryForObject("SELECT saldo FROM cuentas_clientes WHERE cliente_id=?",BigDecimal.class,cid));
  assertEquals(2,abonos.cliente(cid,mes).getFirst().cupos().getFirst().disponibles());assertNull(abonos.cliente(cid,mes).getFirst().entregaId());
  var hist=context.getBean(HistorialClienteService.class).listar(cid,mes,mes.plusMonths(1).minusDays(1),0,20);assertEquals("SALDO_INICIAL",hist.contenido().getFirst().tipo());
  dinero("0",context.getBean(ResumenAbonosService.class).consultar(mes,mes.plusMonths(1).minusDays(1)).importeAbonos());
  assertFalse(service.preparar(solicitud(ExcelClientesTest.datos(mes.toString())),"admin").errores().isEmpty());
  var entrega=new CrearEntregaRequest(UUID.randomUUID(),cid,List.of(new CrearEntregaRequest.Item(BIDON,3,0,0,0L)),null);
  var resultado=entregas.registrar(jornada,entrega,"admin");dinero("5000",resultado.total());dinero("20000",resultado.saldoCliente());assertEquals(2,resultado.items().getFirst().incluidosAbono());
  assertEquals(resultado,entregas.registrar(jornada,entrega,"admin"));assertEquals(0,abonos.cliente(cid,mes).getFirst().cupos().getFirst().disponibles());
 }
 @Test void erroresNoGuardanYRepartidorBloqueado()throws Exception {
  var d=ExcelClientesTest.datos(mes.toString());d.get("saldos")[1][2]="100";d.get("abonos")[1][7]="8";
  var req=solicitud(d);assertEquals(403,assertThrows(ResponseStatusException.class,()->service.preparar(req,"otro")).getStatusCode().value());
  var v=service.preparar(req,"admin");assertNull(v.id());assertTrue(v.errores().size()>=2);assertEquals(0,scalar("SELECT count(*) FROM importaciones_clientes"));
 }
 @Test void planModificadoImpideConfirmar()throws Exception {
  var v=service.preparar(solicitud(ExcelClientesTest.datos(mes.toString())),"admin");jdbc.update("UPDATE planes_abono SET version=version+1 WHERE id=?",plan.id());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.confirmar(v.id(),"admin")).getStatusCode().value());assertEquals(0,scalar("SELECT count(*) FROM clientes_importados"));
 }
 @Test void fallaPosteriorRevierteTodosLosClientes()throws Exception {
  var v=service.preparar(solicitud(ExcelClientesTest.datos(mes.toString())),"admin");
  jdbc.execute("ALTER TABLE maquinas_cliente ADD CONSTRAINT prueba_fallo CHECK(cantidad=0)");
  assertThrows(Exception.class,()->service.confirmar(v.id(),"admin"));assertEquals(1,scalar("SELECT count(*) FROM clientes"));assertEquals(0,scalar("SELECT count(*) FROM clientes_importados"));assertEquals(0,scalar("SELECT count(*) FROM importaciones_clientes WHERE confirmado_en IS NOT NULL"));
 }
}
