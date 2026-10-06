package com.agua.backend.sincronizacion;

import com.agua.backend.cliente.ClienteResponse;
import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.UsuarioRepository;
import com.agua.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AltaClienteSincronizadaService {
    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final UsuarioRepository usuarios;

    @Transactional
    public ClienteResponse registrar(UUID jornadaId, SincronizarRequest.AltaCliente request, String username) {
        jornadas.obtener(jornadaId, username);
        var actor = usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        var anterior = buscar(request.operacionId());
        if (anterior != null) return reintento(anterior, jornadaId, actor.getId(), request);
        String estado = jdbc.queryForObject("SELECT estado FROM jornadas WHERE id=? FOR UPDATE", String.class, jornadaId);
        anterior = buscar(request.operacionId());
        if (anterior != null) return reintento(anterior, jornadaId, actor.getId(), request);
        if (!"ABIERTA".equals(estado)) throw new ResponseStatusException(HttpStatus.CONFLICT, "La jornada está cerrada");
        var r = request.datos();
        int insertados = jdbc.update("""
                INSERT INTO clientes(id,nombre,direccion,telefono,observaciones)
                VALUES(?,?,?,?,?) ON CONFLICT(id) DO NOTHING
                """, request.operacionId(), r.nombre().strip(), r.direccion().strip(), r.telefono().strip(), normalizar(r.observaciones()));
        if (insertados == 0) {
            anterior = buscar(request.operacionId());
            if (anterior != null) return reintento(anterior, jornadaId, actor.getId(), request);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El identificador ya pertenece a otro cliente");
        }
        jdbc.update("""
                INSERT INTO altas_cliente_sincronizadas(cliente_id,jornada_id,usuario_id,nombre,direccion,telefono,observaciones,creado_en)
                SELECT id,?,?,nombre,direccion,telefono,observaciones,creado_en FROM clientes WHERE id=?
                """, jornadaId, actor.getId(), request.operacionId());
        return buscar(request.operacionId()).cliente();
    }

    private record Alta(UUID jornadaId, UUID usuarioId, ClienteResponse cliente) {}
    private Alta buscar(UUID id) {
        var filas = jdbc.query("SELECT * FROM altas_cliente_sincronizadas WHERE cliente_id=?", (rs,n) -> {
            var fecha = rs.getObject("creado_en", OffsetDateTime.class).toInstant();
            return new Alta(rs.getObject("jornada_id", UUID.class), rs.getObject("usuario_id", UUID.class),
                    new ClienteResponse(id, rs.getString("nombre"), rs.getString("direccion"), rs.getString("telefono"),
                            rs.getString("observaciones"), true, fecha, fecha, 0L));
        }, id);
        return filas.isEmpty() ? null : filas.getFirst();
    }
    private ClienteResponse reintento(Alta anterior, UUID jornada, UUID actor, SincronizarRequest.AltaCliente nuevo) {
        var c = anterior.cliente();
        var r = nuevo.datos();
        if (!anterior.jornadaId().equals(jornada) || !anterior.usuarioId().equals(actor)
                || !c.nombre().equals(r.nombre().strip()) || !c.direccion().equals(r.direccion().strip())
                || !c.telefono().equals(r.telefono().strip()) || !Objects.equals(c.observaciones(), normalizar(r.observaciones()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El identificador de alta ya fue usado con otros datos");
        }
        return c;
    }
    private String normalizar(String texto) { return texto == null || texto.isBlank() ? null : texto.strip(); }
}
