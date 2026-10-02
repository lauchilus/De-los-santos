package com.agua.backend.producto;

import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrecioProductoService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;

    @Transactional
    public ProductoResponse actualizar(UUID id, ActualizarPrecioRequest request, String username) {
        var actor = usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
        if (actor.getRol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el administrador puede cambiar precios");
        }
        var filas = jdbc.query("""
                SELECT id, codigo, nombre, tipo, capacidad_litros, precio_venta, version, activo
                FROM productos WHERE id = ? FOR UPDATE
                """, (rs, n) -> new PrecioActual(new ProductoResponse(rs.getObject("id", UUID.class),
                rs.getString("codigo"), rs.getString("nombre"), TipoProducto.valueOf(rs.getString("tipo")),
                rs.getObject("capacidad_litros", Integer.class), rs.getBigDecimal("precio_venta"),
                rs.getLong("version")), rs.getBoolean("activo")), id);
        if (filas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        var actual = filas.getFirst();
        var producto = actual.producto();
        if (!actual.activo() || producto.tipo() != TipoProducto.BIDON) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este paso configura precios de bidones activos");
        }
        if (!producto.version().equals(request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El producto cambió: consultá su versión actual");
        }
        BigDecimal precio = request.precioVenta().setScale(2);
        if (producto.precioVenta() != null && producto.precioVenta().compareTo(precio) == 0) return producto;
        long version = producto.version() + 1;
        jdbc.update("UPDATE productos SET precio_venta = ?, version = ?, actualizado_en = CURRENT_TIMESTAMP WHERE id = ?",
                precio, version, id);
        jdbc.update("""
                INSERT INTO cambios_precio_producto (id, producto_id, precio_anterior, precio_nuevo, version_nueva, usuario_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, producto.precioVenta(), precio, version, actor.getId());
        return new ProductoResponse(id, producto.codigo(), producto.nombre(), producto.tipo(),
                producto.capacidadLitros(), precio, version);
    }

    private record PrecioActual(ProductoResponse producto, boolean activo) {}
}
