package com.agua.backend.stock;

import com.agua.backend.producto.ProductoRepository;
import com.agua.backend.producto.TipoProducto;
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
public class StockService {

    private final JdbcTemplate jdbc;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public List<StockResponse> listarFabrica() {
        return jdbc.query("""
                SELECT
                    p.id,
                    p.codigo,
                    p.nombre,
                    s.estado,
                    s.cantidad
                FROM stock_fabrica s
                JOIN productos p ON p.id = s.producto_id
                ORDER BY p.codigo, s.estado
                """,
                (rs, fila) -> new StockResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        EstadoStock.valueOf(rs.getString("estado")),
                        rs.getLong("cantidad")
                )
        );
    }

    @Transactional
    public MovimientoStockResponse ajustar(
            AjustarStockRequest request,
            String username
    ) {
        if (request.cantidad() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cantidad debe ser distinta de cero"
            );
        }

        var producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                ));

        if (!producto.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El producto está inactivo"
            );
        }

        boolean esBidon = producto.getTipo() == TipoProducto.BIDON;
        boolean esDisponible = request.estado() == EstadoStock.DISPONIBLE;

        if ((esBidon && esDisponible) || (!esBidon && !esDisponible)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estado no corresponde al tipo de producto"
            );
        }

        var usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado"
                ));

        long variacion = request.cantidad().longValue();

        List<Long> saldos = jdbc.query("""
                UPDATE stock_fabrica
                SET cantidad = cantidad + ?,
                    actualizado_en = CURRENT_TIMESTAMP
                WHERE producto_id = ?
                  AND estado = ?
                  AND cantidad + ? >= 0
                RETURNING cantidad
                """,
                (rs, fila) -> rs.getLong("cantidad"),
                variacion,
                request.productoId(),
                request.estado().name(),
                variacion
        );

        if (saldos.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock insuficiente o combinación de stock no inicializada"
            );
        }

        UUID movimientoId = UUID.randomUUID();
        long saldo = saldos.getFirst();
        String motivo = request.motivo().strip();

        jdbc.update("""
                INSERT INTO movimientos_stock_fabrica (
                    id,
                    producto_id,
                    estado,
                    tipo,
                    cantidad,
                    saldo_resultante,
                    motivo,
                    usuario_id
                )
                VALUES (?, ?, ?, 'AJUSTE', ?, ?, ?, ?)
                """,
                movimientoId,
                request.productoId(),
                request.estado().name(),
                variacion,
                saldo,
                motivo,
                usuario.getId()
        );

        return new MovimientoStockResponse(
                movimientoId,
                request.productoId(),
                request.estado(),
                variacion,
                saldo,
                motivo,
                usuario.getId()
        );
    }
}