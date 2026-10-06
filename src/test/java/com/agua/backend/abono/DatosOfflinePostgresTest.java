package com.agua.backend.abono;

import com.agua.backend.sincronizacion.DatosOfflineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DatosOfflinePostgresTest extends AbonosPostgresTest {
    DatosOfflineService offline;
    @BeforeEach void prepararOffline() {
        context.registerBean(DatosOfflineService.class);
        offline=context.getBean(DatosOfflineService.class);
    }
    @Test void descargaIncluyeSaldosCuposYUltimaVersionInactiva() {
        registrar(request(2,true));
        var precio=precios.guardar(cliente,BIDON,new PrecioClienteService.Request(new BigDecimal("4500"),true,null,"Prueba"),"admin");
        precios.guardar(cliente,BIDON,new PrecioClienteService.Request(new BigDecimal("4500"),false,precio.version(),"Fin"),"admin");
        var d=offline.descargar(jornada,"admin");
        assertEquals(1,d.versionFormato());assertEquals(mes,d.periodo());
        var c=d.clientes().stream().filter(x->x.id().equals(cliente)).findFirst().orElseThrow();
        dinero("18000",c.saldo());assertTrue(c.tieneAbonoRegistrado());
        assertEquals(2,d.envasesClientes().getFirst().cantidad());
        assertEquals(2,d.cuposAbono().getFirst().disponibles());
        assertEquals(1,d.preciosClientes().size());assertFalse(d.preciosClientes().getFirst().activo());
        assertEquals(precio.version()+1,d.preciosClientes().getFirst().version());
        assertEquals(18,d.stockJornada().stream().filter(x->x.productoId().equals(BIDON)).findFirst().orElseThrow().cantidad());
    }
    @Test void noDescargaJornadaAjena() {
        assertEquals(403,assertThrows(ResponseStatusException.class,()->offline.descargar(jornada,"otro")).getStatusCode().value());
    }
    @Test void incluyeClientesCompartidosInactivosYExcluyeCuposAnteriores() {
        registrar(request(1,true));
        jdbc.update("UPDATE abonos_cliente SET periodo=?",java.sql.Date.valueOf(mes.minusMonths(1)));
        UUID otro=UUID.randomUUID();
        jdbc.update("INSERT INTO clientes(id,nombre,direccion,telefono,activo) VALUES(?,'Sin jornada','Direccion','123',false)",otro);
        var d=offline.descargar(jornada,"admin");
        assertTrue(d.abonosClientes().isEmpty());assertTrue(d.cuposAbono().isEmpty());
        assertTrue(d.clientes().stream().filter(x->x.id().equals(cliente)).findFirst().orElseThrow().tieneAbonoRegistrado());
        var c=d.clientes().stream().filter(x->x.id().equals(otro)).findFirst().orElseThrow();
        assertFalse(c.activo());dinero("0",c.saldo());
    }
}
