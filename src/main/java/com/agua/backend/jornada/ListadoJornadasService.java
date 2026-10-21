package com.agua.backend.jornada;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListadoJornadasService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    public record Fila(UUID id, LocalDate fecha, String estado, UUID camionetaId,
            String camionetaNombre, String patente, UUID repartidorId, String repartidorNombre) {}
    public record Pagina(List<Fila> contenido, int pagina, int tamanio, boolean hayMas) {}

    public Pagina listar(String estado, int pagina, int tamanio, String username) {
        var actor = usuarios.findByUsername(username).filter(u -> u.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        if (actor.getRol() != Rol.ADMIN && actor.getRol() != Rol.REPARTIDOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a jornadas");
        if (!List.of("ABIERTA", "CERRADA", "TODAS").contains(estado)
                || pagina < 0 || pagina > 100000 || tamanio < 1 || tamanio > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro o paginacion invalida");
        var args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("""
                SELECT j.id,j.fecha,j.estado,j.camioneta_id,c.nombre,c.patente,j.repartidor_id,u.nombre
                FROM jornadas j JOIN camionetas c ON c.id=j.camioneta_id
                JOIN usuarios u ON u.id=j.repartidor_id WHERE 1=1
                """);
        // El alcance se deriva de la identidad autenticada, nunca de un ID enviado por el cliente.
        if (actor.getRol() != Rol.ADMIN) { sql.append(" AND j.repartidor_id=?"); args.add(actor.getId()); }
        if (!estado.equals("TODAS")) { sql.append(" AND j.estado=?"); args.add(estado); }
        sql.append(" ORDER BY j.fecha DESC,j.abierta_en DESC,j.id DESC LIMIT ? OFFSET ?");
        args.add(tamanio + 1); args.add((long) pagina * tamanio);
        var filas = jdbc.query(sql.toString(), (r,n) -> new Fila(r.getObject(1,UUID.class),
                r.getDate(2).toLocalDate(),r.getString(3),r.getObject(4,UUID.class),r.getString(5),
                r.getString(6),r.getObject(7,UUID.class),r.getString(8)),args.toArray());
        boolean mas = filas.size() > tamanio;
        return new Pagina(List.copyOf(filas.subList(0,Math.min(tamanio,filas.size()))),pagina,tamanio,mas);
    }
}
