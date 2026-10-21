package com.agua.backend.abono;

import com.agua.backend.jornada.ListadoJornadasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ListadoJornadasPostgresTest extends AbonosPostgresTest {
    ListadoJornadasService listado;
    @BeforeEach void configurarListado(){context.registerBean(ListadoJornadasService.class);listado=context.getBean(ListadoJornadasService.class);}
    @Test void adminVeTodasYRepartidorSoloPropias(){
        assertEquals(1,listado.listar("ABIERTA",0,20,"admin").contenido().size());
        assertTrue(listado.listar("TODAS",0,20,"otro").contenido().isEmpty());
        var rep=jdbc.queryForObject("SELECT id FROM usuarios WHERE username='otro'",UUID.class);
        jdbc.update("UPDATE jornadas SET repartidor_id=? WHERE id=?",rep,jornada);
        assertEquals(jornada,listado.listar("ABIERTA",0,20,"otro").contenido().getFirst().id());
        jdbc.update("UPDATE jornadas SET estado='CERRADA',cerrada_en=CURRENT_TIMESTAMP WHERE id=?",jornada);
        assertTrue(listado.listar("ABIERTA",0,20,"otro").contenido().isEmpty());
        assertEquals(1,listado.listar("CERRADA",0,20,"otro").contenido().size());
    }
    @Test void paginaSinRepetirYFiltrosInvalidos(){
        jdbc.update("UPDATE jornadas SET estado='CERRADA',cerrada_en=CURRENT_TIMESTAMP WHERE id=?",jornada);
        jdbc.update("INSERT INTO jornadas(id,fecha,camioneta_id,repartidor_id,efectivo_inicial,abierta_por) SELECT ?,fecha,camioneta_id,repartidor_id,0,abierta_por FROM jornadas WHERE id=?",UUID.randomUUID(),jornada);
        var primera=listado.listar("TODAS",0,1,"admin");var segunda=listado.listar("TODAS",1,1,"admin");
        assertTrue(primera.hayMas());assertFalse(segunda.hayMas());assertNotEquals(primera.contenido().getFirst().id(),segunda.contenido().getFirst().id());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->listado.listar("OTRO",0,20,"admin")).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->listado.listar("TODAS",-1,20,"admin")).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->listado.listar("TODAS",0,101,"admin")).getStatusCode().value());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->listado.listar("TODAS",0,20,"inexistente")).getStatusCode().value());
    }
}
