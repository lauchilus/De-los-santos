package com.agua.backend.abono;

import com.agua.backend.jornada.OpcionesAperturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OpcionesAperturaPostgresTest extends AbonosPostgresTest {
    OpcionesAperturaService opciones;
    @BeforeEach void prepararOpciones(){context.registerBean(OpcionesAperturaService.class);opciones=context.getBean(OpcionesAperturaService.class);}
    @Test void administraDisponibilidadYLiberaAlCerrar(){
        var rep=jdbc.queryForObject("SELECT id FROM usuarios WHERE username='otro'",UUID.class);
        jdbc.update("UPDATE jornadas SET repartidor_id=? WHERE id=?",rep,jornada);
        var r=opciones.obtener("admin");assertEquals(1,r.camionetas().size());assertTrue(r.camionetas().getFirst().ocupada());assertTrue(r.repartidores().getFirst().ocupado());dinero("24000",r.efectivoInicialSugerido());
        jdbc.update("UPDATE jornadas SET estado='CERRADA',cerrada_en=CURRENT_TIMESTAMP WHERE id=?",jornada);
        r=opciones.obtener("admin");assertFalse(r.camionetas().getFirst().ocupada());assertFalse(r.repartidores().getFirst().ocupado());
    }
    @Test void repartidorSoloPuedeElegirseASiMismo(){
        var extra=UUID.randomUUID();jdbc.update("INSERT INTO usuarios(id,username,nombre,password_hash,rol) VALUES(?,'extra','Extra','test','REPARTIDOR')",extra);
        assertEquals(2,opciones.obtener("admin").repartidores().size());
        var r=opciones.obtener("otro");assertEquals(1,r.repartidores().size());assertEquals(jdbc.queryForObject("SELECT id FROM usuarios WHERE username='otro'",UUID.class),r.repartidores().getFirst().id());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->opciones.obtener("inexistente")).getStatusCode().value());
    }
    @Test void excluyeInactivos(){
        jdbc.update("UPDATE camionetas SET activo=false");jdbc.update("UPDATE usuarios SET activo=false WHERE username='otro'");
        var r=opciones.obtener("admin");assertTrue(r.camionetas().isEmpty());assertTrue(r.repartidores().isEmpty());
    }
}
