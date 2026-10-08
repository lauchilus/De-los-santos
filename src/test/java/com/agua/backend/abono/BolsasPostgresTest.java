package com.agua.backend.abono;
import com.agua.backend.producto.*;
import com.agua.backend.produccion.*;
import com.agua.backend.carga.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BolsasPostgresTest extends AbonosPostgresTest {
 static final UUID B12=UUID.fromString("00000000-0000-4000-8000-000000000001"), S12=UUID.fromString("00000000-0000-4000-8000-000000000008"), S20=UUID.fromString("00000000-0000-4000-8000-000000000006");
 ProduccionService produccion; CargaService cargas;
 @BeforeEach void servicios(){
  var repo=mock(ProductoRepository.class);
  for(var id:List.of(B12,BIDON)){var p=mock(Producto.class);when(p.isActivo()).thenReturn(true);when(p.getTipo()).thenReturn(TipoProducto.BIDON);when(p.getCapacidadLitros()).thenReturn(id.equals(B12)?12:20);when(repo.findById(id)).thenReturn(Optional.of(p));}
  context.registerBean(ProductoRepository.class,()->repo);context.registerBean(ProduccionService.class);context.registerBean(CargaService.class);produccion=context.getBean(ProduccionService.class);cargas=context.getBean(CargaService.class);
  jdbc.update("UPDATE stock_fabrica SET cantidad=10");
 }
 long saldo(UUID id,String estado){return jdbc.queryForObject("SELECT cantidad FROM stock_fabrica WHERE producto_id=? AND estado=?",Long.class,id,estado);}
 @Test void produccionSeparaBolsasYReintenta(){
  var req=new CrearProduccionRequest(UUID.randomUUID(),B12,2);produccion.producir(req,"admin");produccion.producir(req,"admin");assertEquals(8,saldo(S12,"DISPONIBLE"));assertEquals(10,saldo(S20,"DISPONIBLE"));
  produccion.producir(new CrearProduccionRequest(UUID.randomUUID(),BIDON,3),"admin");assertEquals(8,saldo(S12,"DISPONIBLE"));assertEquals(7,saldo(S20,"DISPONIBLE"));assertEquals(8,saldo(B12,"VACIO"));assertEquals(7,saldo(BIDON,"VACIO"));
 }
 @Test void noUsaOtraBolsaSiFaltaLaCorrecta(){
  jdbc.update("UPDATE stock_fabrica SET cantidad=0 WHERE producto_id=?",S12);
  assertThrows(ResponseStatusException.class,()->produccion.producir(new CrearProduccionRequest(UUID.randomUUID(),B12,1),"admin"));assertEquals(10,saldo(B12,"VACIO"));assertEquals(10,saldo(S20,"DISPONIBLE"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM producciones",Integer.class));
 }
 @Test void cargaMixtaConInsumoRevierteTodo(){
  var req=new CrearCargaRequest(UUID.randomUUID(),List.of(new CrearCargaRequest.Item(BIDON,1),new CrearCargaRequest.Item(S20,1)));
  assertEquals(400,assertThrows(ResponseStatusException.class,()->cargas.cargar(jornada,req,"admin")).getStatusCode().value());assertEquals(10,saldo(BIDON,"LLENO"));assertEquals(10,saldo(S20,"DISPONIBLE"));
 }
 @Test void migracionConservaStockAnteriorYCreaCero() throws Exception {
  jdbc.update("DELETE FROM stock_fabrica WHERE producto_id=?",S12);jdbc.update("DELETE FROM productos WHERE id=?",S12);jdbc.update("UPDATE productos SET codigo='BOLSA' WHERE id=?",S20);jdbc.update("UPDATE stock_fabrica SET cantidad=37 WHERE producto_id=?",S20);
  try(var conn=jdbc.getDataSource().getConnection()){ScriptUtils.executeSqlScript(conn,new ClassPathResource("db/migration/V14__bolsas_por_capacidad.sql"));}
  assertEquals(37,saldo(S20,"DISPONIBLE"));assertEquals(0,saldo(S12,"DISPONIBLE"));assertEquals("BOLSA_20",jdbc.queryForObject("SELECT codigo FROM productos WHERE id=?",String.class,S20));
 }
}
