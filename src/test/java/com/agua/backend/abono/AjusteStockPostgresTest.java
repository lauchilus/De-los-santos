package com.agua.backend.abono;
import com.agua.backend.stock.*;
import com.agua.backend.producto.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AjusteStockPostgresTest extends AbonosPostgresTest {
 StockService stock;
 @BeforeEach void configurarStock(){
  var repo=mock(ProductoRepository.class);var p=mock(Producto.class);
  when(p.isActivo()).thenReturn(true);when(p.getTipo()).thenReturn(TipoProducto.BIDON);
  when(repo.findById(BIDON)).thenReturn(Optional.of(p));
  context.registerBean(ProductoRepository.class,()->repo);context.registerBean(StockService.class);stock=context.getBean(StockService.class);
 }
 @Test void reintentoNoDuplicaYRechazaCambios(){
  var id=UUID.randomUUID();var req=new AjustarStockRequest(id,BIDON,EstadoStock.VACIO,10,"Prueba");
  var primero=stock.ajustar(req,"admin");assertEquals(primero,stock.ajustar(req,"admin"));
  assertEquals(10L,jdbc.queryForObject("SELECT cantidad FROM stock_fabrica WHERE producto_id=? AND estado='VACIO'",Long.class,BIDON));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->stock.ajustar(new AjustarStockRequest(id,BIDON,EstadoStock.VACIO,11,"Prueba"),"admin")).getStatusCode().value());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->stock.ajustar(req,"otro")).getStatusCode().value());
 }
 @Test void negativoRollbackYCompatibilidadSinId(){
  assertThrows(ResponseStatusException.class,()->stock.ajustar(new AjustarStockRequest(UUID.randomUUID(),BIDON,EstadoStock.VACIO,-1,"Prueba"),"admin"));
  var r=stock.ajustar(new AjustarStockRequest(null,BIDON,EstadoStock.VACIO,2,"Anterior"),"admin");assertEquals(2,r.saldoResultante());
 }
}
