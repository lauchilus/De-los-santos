package com.agua.backend.produccion;

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
public class ProduccionService {

    private final JdbcTemplate jdbc;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public ProduccionResponse producir(
            CrearProduccionRequest request,
            String username
    ) {
        var usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado"
                ));

        // Un reintento confirmado devuelve la producción original.
        var existentes = buscarProduccion(request.operacionId());

        if (!existentes.isEmpty()) {
            return validarReintento(
                    existentes.getFirst(), request, usuario.getId()
            );
        }

        var producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                ));

        if (!producto.isActivo()
                || producto.getTipo() != TipoProducto.BIDON) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La producción requiere un bidón activo"
            );
        }

        Integer capacidad = producto.getCapacidadLitros();
        if (capacidad == null || (capacidad != 12 && capacidad != 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay bolsa configurada para esta capacidad");
        }
        String codigoBolsa = "BOLSA_" + capacidad;

        // La PK evita duplicar una operación, incluso con dos pedidos simultáneos.
        int insertados = jdbc.update("""
                INSERT INTO producciones (
                    id, producto_id, cantidad, usuario_id
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                request.operacionId(),
                request.productoId(),
                request.cantidad(),
                usuario.getId()
        );

        if (insertados == 0) {
            return validarReintento(
                    buscarProduccion(request.operacionId()).getFirst(),
                    request,
                    usuario.getId()
            );
        }

        /*
         * Bloqueamos las existencias involucradas hasta terminar.
         * El orden es siempre el mismo para las producciones concurrentes.
         */
        List<Existencia> existencias = jdbc.query("""
                SELECT
                    s.producto_id,
                    p.codigo,
                    p.tipo,
                    p.activo,
                    s.estado,
                    s.cantidad
                FROM stock_fabrica s
                JOIN productos p ON p.id = s.producto_id
                WHERE p.id = ?
                   OR p.codigo IN ('TAPA', ?)
                ORDER BY s.producto_id, s.estado
                FOR UPDATE OF s
                """,
                (rs, fila) -> new Existencia(
                        rs.getObject("producto_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("tipo"),
                        rs.getBoolean("activo"),
                        rs.getString("estado"),
                        rs.getLong("cantidad")
                ),
                request.productoId(), codigoBolsa
        );

        Existencia vacios = buscarBidon(
                existencias, request.productoId(), "VACIO"
        );
        Existencia llenos = buscarBidon(
                existencias, request.productoId(), "LLENO"
        );
        Existencia tapas = buscarInsumo(existencias, "TAPA");
        Existencia bolsas = buscarInsumo(existencias, codigoBolsa);

        long cantidad = request.cantidad().longValue();

        validarDisponible(vacios, cantidad);
        validarDisponible(tapas, cantidad);
        validarDisponible(bolsas, cantidad);

        mover(vacios, -cantidad, request.operacionId(), usuario.getId());
        mover(tapas, -cantidad, request.operacionId(), usuario.getId());
        mover(bolsas, -cantidad, request.operacionId(), usuario.getId());
        mover(llenos, cantidad, request.operacionId(), usuario.getId());

        return new ProduccionResponse(
                request.operacionId(),
                request.productoId(),
                request.cantidad(),
                usuario.getId()
        );
    }

    private List<ProduccionResponse> buscarProduccion(UUID id) {
        return jdbc.query("""
                SELECT id, producto_id, cantidad, usuario_id
                FROM producciones
                WHERE id = ?
                """,
                (rs, fila) -> new ProduccionResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("producto_id", UUID.class),
                        rs.getInt("cantidad"),
                        rs.getObject("usuario_id", UUID.class)
                ),
                id
        );
    }

    private ProduccionResponse validarReintento(
            ProduccionResponse existente,
            CrearProduccionRequest request,
            UUID usuarioId
    ) {
        if (!existente.productoId().equals(request.productoId())
                || existente.cantidad() != request.cantidad()
                || !existente.usuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El identificador de operación ya fue usado con otros datos"
            );
        }

        return existente;
    }

    private Existencia buscarBidon(
            List<Existencia> existencias,
            UUID productoId,
            String estado
    ) {
        return existencias.stream()
                .filter(e -> e.productoId().equals(productoId))
                .filter(e -> e.estado().equals(estado))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Falta inicializar el stock de bidones " + estado
                ));
    }

    private Existencia buscarInsumo(
            List<Existencia> existencias,
            String codigo
    ) {
        return existencias.stream()
                .filter(e -> e.codigo().equals(codigo))
                .filter(e -> e.estado().equals("DISPONIBLE"))
                .filter(e -> e.tipo().equals("INSUMO") && e.activo())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insumo no disponible: " + codigo
                ));
    }

    private void validarDisponible(Existencia existencia, long cantidad) {
        if (existencia.cantidad() < cantidad) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock insuficiente: "
                            + existencia.codigo()
                            + " / "
                            + existencia.estado()
            );
        }
    }

    private void mover(
            Existencia existencia,
            long variacion,
            UUID produccionId,
            UUID usuarioId
    ) {
        long saldo = Math.addExact(existencia.cantidad(), variacion);

        int actualizados = jdbc.update("""
                UPDATE stock_fabrica
                SET cantidad = ?,
                    actualizado_en = CURRENT_TIMESTAMP
                WHERE producto_id = ?
                  AND estado = ?
                """,
                saldo,
                existencia.productoId(),
                existencia.estado()
        );

        if (actualizados != 1) {
            throw new IllegalStateException(
                    "No se pudo actualizar una existencia de producción"
            );
        }

        jdbc.update("""
                INSERT INTO movimientos_stock_fabrica (
                    id,
                    producto_id,
                    estado,
                    tipo,
                    cantidad,
                    saldo_resultante,
                    motivo,
                    usuario_id,
                    produccion_id
                )
                VALUES (?, ?, ?, 'PRODUCCION', ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                existencia.productoId(),
                existencia.estado(),
                variacion,
                saldo,
                "Llenado de bidones",
                usuarioId,
                produccionId
        );
    }

    private record Existencia(
            UUID productoId,
            String codigo,
            String tipo,
            boolean activo,
            String estado,
            long cantidad
    ) {
    }
}