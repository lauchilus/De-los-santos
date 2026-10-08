package com.agua.backend.abono;
import com.agua.backend.cliente.GestionClienteService;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GestionClientesPostgresTest extends AbonosPostgresTest {
 GestionClienteService service;
 @BeforeEach void configurar(){context.registerBean(GestionClienteService.class);service=context.getBean(GestionClienteService.class);}
 GestionClienteService.Solicitud req(UUID op,String nombre,Long version){return new GestionClienteService.Solicitud(op,nombre,"Calle 456","112233","Observación",version);}
 @Test void altaSinJornadaPorRepartidorYReintento(){var r=req(UUID.randomUUID(),"Nuevo",null);var c=service.guardar(null,r,"otro");assertEquals(c,service.guardar(null,r,"otro"));assertEquals(r.operacionId(),c.id());assertEquals(1,service.listar("112233",0,"admin").contenido().size());assertEquals(1,service.listar("calle 456",0,"otro").contenido().size());assertThrows(ResponseStatusException.class,()->service.guardar(null,r,"admin"));}
 @Test void edicionVersionYRespuestaHistorica(){var r=req(UUID.randomUUID(),"Nuevo",null);var c=service.guardar(null,r,"admin");var edit=req(UUID.randomUUID(),"Editado",0L);var changed=service.guardar(c.id(),edit,"otro");assertEquals(1,changed.version());assertEquals(changed,service.guardar(c.id(),edit,"otro"));assertEquals(c,service.guardar(null,r,"admin"));assertEquals(409,assertThrows(ResponseStatusException.class,()->service.guardar(c.id(),req(UUID.randomUUID(),"Obsoleto",0L),"admin")).getStatusCode().value());assertEquals("Editado",service.listar("Editado",0,"otro").contenido().getFirst().nombre());}
 @Test void busquedaLiteralYPaginacion(){for(int i=0;i<22;i++)service.guardar(null,req(UUID.randomUUID(),"Grupo "+i,null),"admin");assertEquals(20,service.listar("Grupo",0,"otro").contenido().size());assertTrue(service.listar("Grupo",0,"otro").hayMas());assertEquals(2,service.listar("Grupo",1,"otro").contenido().size());assertTrue(service.listar("%",0,"otro").contenido().isEmpty());assertThrows(ResponseStatusException.class,()->service.listar("",-1,"otro"));}
}
