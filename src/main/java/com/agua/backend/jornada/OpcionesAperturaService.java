package com.agua.backend.jornada;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class OpcionesAperturaService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    public record Camioneta(UUID id,String nombre,String patente,boolean ocupada) {}
    public record Repartidor(UUID id,String nombre,boolean ocupado) {}
    public record Opciones(List<Camioneta> camionetas,List<Repartidor> repartidores,BigDecimal efectivoInicialSugerido) {}
    public Opciones obtener(String username) {
        var actor=usuarios.findByUsername(username).filter(u->u.isActivo())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuario no disponible"));
        if(actor.getRol()!=Rol.ADMIN && actor.getRol()!=Rol.REPARTIDOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sin acceso a jornadas");
        var camionetas=jdbc.query("""
                SELECT c.id,c.nombre,c.patente,EXISTS(SELECT 1 FROM jornadas j WHERE j.camioneta_id=c.id AND j.estado='ABIERTA')
                FROM camionetas c WHERE c.activo=true ORDER BY c.nombre,c.id
                """,(r,n)->new Camioneta(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4)));
        String sql="""
                SELECT u.id,u.nombre,EXISTS(SELECT 1 FROM jornadas j WHERE j.repartidor_id=u.id AND j.estado='ABIERTA')
                FROM usuarios u WHERE u.activo=true AND u.rol='REPARTIDOR'
                """;
        Object[] args=new Object[0];
        if(actor.getRol()!=Rol.ADMIN){sql+=" AND u.id=?";args=new Object[]{actor.getId()};}
        var repartidores=jdbc.query(sql+" ORDER BY u.nombre,u.id",(r,n)->new Repartidor(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3)),args);
        // Disponibilidad orientativa: JornadaService y las restricciones de BD validan al abrir.
        return new Opciones(camionetas,repartidores,new BigDecimal("24000.00"));
    }
}
