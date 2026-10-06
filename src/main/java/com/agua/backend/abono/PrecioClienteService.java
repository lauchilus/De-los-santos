package com.agua.backend.abono;

import com.agua.backend.usuario.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PrecioClienteService {
    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    public record Request(@NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal precio,
            @NotNull Boolean activo, @PositiveOrZero Long version, @NotBlank @Size(max=500) String motivo) {}
    public record Precio(UUID productoId,BigDecimal precio,boolean activo,long version,String motivo) {}
    public List<Precio> listar(UUID clienteId) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clientes WHERE id=?)",Boolean.class,clienteId)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado");
        return jdbc.query("""
                SELECT DISTINCT ON(producto_id) producto_id,precio,activo,version,motivo
                FROM precios_cliente WHERE cliente_id=? ORDER BY producto_id,version DESC
                """,(rs,n)->new Precio(rs.getObject(1,UUID.class),rs.getBigDecimal(2),rs.getBoolean(3),rs.getLong(4),rs.getString(5)),clienteId);
    }
    public Precio actual(UUID clienteId,UUID productoId) {
        var filas=jdbc.query("SELECT * FROM precios_cliente WHERE cliente_id=? AND producto_id=? ORDER BY version DESC LIMIT 1",
                (rs,n)->new Precio(productoId,rs.getBigDecimal("precio"),rs.getBoolean("activo"),rs.getLong("version"),rs.getString("motivo")),clienteId,productoId);
        return filas.isEmpty()?null:filas.getFirst();
    }
    @Transactional
    public Precio guardar(UUID clienteId,UUID productoId,Request r,String username) {
        var actor=usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuario no disponible"));
        if(actor.getRol()!=Rol.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Se requiere administrador");
        var clientes=jdbc.query("SELECT id FROM clientes WHERE id=? FOR NO KEY UPDATE",(rs,n)->rs.getObject(1,UUID.class),clienteId);
        if(clientes.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado");
        var tipos=jdbc.query("SELECT tipo FROM productos WHERE id=? FOR SHARE",(rs,n)->rs.getString(1),productoId);
        if(tipos.isEmpty() || !"BIDON".equals(tipos.getFirst())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere un producto BIDON");
        var anterior=actual(clienteId,productoId);
        if(!Objects.equals(r.version(),anterior==null?null:anterior.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"El precio del cliente cambió; actualizá la consulta");
        long version=anterior==null?0:anterior.version()+1;
        jdbc.update("INSERT INTO precios_cliente(cliente_id,producto_id,version,precio,activo,motivo,usuario_id) VALUES(?,?,?,?,?,?,?)",
                clienteId,productoId,version,r.precio(),r.activo(),r.motivo().strip(),actor.getId());
        return actual(clienteId,productoId);
    }
}
