package com.agua.backend.gasto;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoriaGastoService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;

    public List<CategoriaGastoResponse> listar(boolean incluirInactivas, String username) {
        Usuario actor = actor(username);
        if (incluirInactivas && actor.getRol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador puede consultar categorías inactivas");
        }
        return jdbc.query("SELECT id, nombre, activo, version FROM categorias_gasto WHERE activo OR ? ORDER BY lower(nombre), id",
                (rs, fila) -> new CategoriaGastoResponse(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getBoolean("activo"), rs.getLong("version")), incluirInactivas);
    }

    @Transactional
    public CategoriaGastoResponse crear(CrearCategoriaGastoRequest request, String username) {
        Usuario actor = admin(username);
        UUID id = UUID.randomUUID();
        String nombre = request.nombre().strip();
        jdbc.update("INSERT INTO categorias_gasto (id, nombre, creado_por, actualizado_por) VALUES (?, ?, ?, ?)",
                id, nombre, actor.getId(), actor.getId());
        return new CategoriaGastoResponse(id, nombre, true, 0);
    }

    @Transactional
    public CategoriaGastoResponse actualizar(UUID id, ActualizarCategoriaGastoRequest request, String username) {
        Usuario actor = admin(username);
        var filas = jdbc.query("SELECT id, nombre, activo, version FROM categorias_gasto WHERE id = ? FOR UPDATE",
                (rs, fila) -> new CategoriaGastoResponse(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getBoolean("activo"), rs.getLong("version")), id);
        if (filas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada");
        var actual = filas.getFirst();
        if (actual.version() != request.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría cambió: consultá su versión actual");
        }
        String nombre = request.nombre().strip();
        if (actual.nombre().equals(nombre) && actual.activo() == request.activo()) return actual;
        long version = actual.version() + 1;
        jdbc.update("""
                UPDATE categorias_gasto SET nombre = ?, activo = ?, version = ?, actualizado_por = ?,
                    actualizado_en = CURRENT_TIMESTAMP WHERE id = ?
                """, nombre, request.activo(), version, actor.getId(), id);
        return new CategoriaGastoResponse(id, nombre, request.activo(), version);
    }

    private Usuario actor(String username) {
        return usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
    }

    private Usuario admin(String username) {
        Usuario actor = actor(username);
        if (actor.getRol() != Rol.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere administrador");
        return actor;
    }
}
