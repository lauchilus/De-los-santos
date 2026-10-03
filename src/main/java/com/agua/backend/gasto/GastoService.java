package com.agua.backend.gasto;

import com.agua.backend.cobro.CobroService;
import com.agua.backend.cobro.MedioPago;
import com.agua.backend.jornada.JornadaService;
import com.agua.backend.usuario.Rol;
import com.agua.backend.usuario.Usuario;
import com.agua.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GastoService {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final RowMapper<GastoResponse> MAPPER = (rs, fila) -> new GastoResponse(
            rs.getObject("id", UUID.class), AmbitoGasto.valueOf(rs.getString("ambito")),
            rs.getObject("jornada_id", UUID.class), rs.getObject("categoria_id", UUID.class),
            rs.getString("categoria_nombre"), rs.getDate("fecha").toLocalDate(), rs.getString("descripcion"),
            MedioPago.valueOf(rs.getString("medio")), rs.getBigDecimal("importe"), rs.getString("referencia"),
            rs.getObject("usuario_id", UUID.class), rs.getObject("creado_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;
    private final JornadaService jornadas;
    private final CobroService cobros;
    private final UsuarioRepository usuarios;

    @Transactional
    public GastoResponse registrar(UUID jornadaId, CrearGastoRequest request, String username) {
        Usuario actor = actor(username);
        LocalDate fechaJornada = null;
        if (jornadaId == null) exigirAdmin(actor);
        else fechaJornada = jornadas.obtener(jornadaId, username).fecha();
        validar(request);
        String descripcion = request.descripcion().strip();
        String referencia = request.referencia() == null ? null : request.referencia().strip();
        if (referencia != null && referencia.isEmpty()) referencia = null;
        var existente = buscar(request.operacionId());
        if (existente != null) return validarReintento(existente, jornadaId, request, actor.getId(), descripcion, referencia);

        if (jornadaId != null) {
            String estado = jdbc.queryForObject("SELECT estado FROM jornadas WHERE id = ? FOR UPDATE", String.class, jornadaId);
            existente = buscar(request.operacionId());
            if (existente != null) return validarReintento(existente, jornadaId, request, actor.getId(), descripcion, referencia);
            if (!"ABIERTA".equals(estado)) throw error(HttpStatus.CONFLICT, "La jornada está cerrada");
            if (request.fecha().isBefore(fechaJornada)) {
                throw error(HttpStatus.BAD_REQUEST, "La fecha del gasto no puede ser anterior a la jornada");
            }
        }
        if (request.fecha().isAfter(LocalDate.now(ZONA))) {
            throw error(HttpStatus.BAD_REQUEST, "No se puede registrar un gasto con fecha futura");
        }
        // Impide que la categoría sea desactivada o renombrada durante el registro.
        var categorias = jdbc.query("SELECT id, nombre, activo, version FROM categorias_gasto WHERE id = ? FOR SHARE",
                (rs, fila) -> new CategoriaGastoResponse(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getBoolean("activo"), rs.getLong("version")), request.categoriaId());
        if (categorias.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Categoría no encontrada");
        var categoria = categorias.getFirst();
        if (!categoria.activo()) throw error(HttpStatus.CONFLICT, "La categoría está inactiva");
        if (jornadaId != null && request.medio() == MedioPago.EFECTIVO
                && request.importe().compareTo(cobros.caja(jornadaId, username).efectivoEsperado()) > 0) {
            throw error(HttpStatus.CONFLICT, "El gasto supera el efectivo disponible de la jornada");
        }
        int insertados = jdbc.update("""
                INSERT INTO gastos (id, ambito, jornada_id, categoria_id, categoria_nombre, fecha,
                                    descripcion, medio, importe, referencia, usuario_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.operacionId(), jornadaId == null ? "GENERAL" : "JORNADA", jornadaId,
                request.categoriaId(), categoria.nombre(), Date.valueOf(request.fecha()), descripcion,
                request.medio().name(), request.importe(), referencia, actor.getId());
        var resultado = buscar(request.operacionId());
        if (insertados == 0) return validarReintento(resultado, jornadaId, request, actor.getId(), descripcion, referencia);
        return resultado;
    }

    public GastoResponse obtenerJornada(UUID jornadaId, UUID gastoId, String username) {
        jornadas.obtener(jornadaId, username);
        var gasto = buscar(gastoId);
        if (gasto == null || !Objects.equals(gasto.jornadaId(), jornadaId)) {
            throw error(HttpStatus.NOT_FOUND, "Gasto no encontrado en esta jornada");
        }
        return gasto;
    }

    public GastoResponse obtenerAdmin(UUID gastoId, String username) {
        exigirAdmin(actor(username));
        var gasto = buscar(gastoId);
        if (gasto == null) throw error(HttpStatus.NOT_FOUND, "Gasto no encontrado");
        return gasto;
    }

    public PaginaGastosResponse listarJornada(UUID jornadaId, int pagina, int tamanio, String username) {
        jornadas.obtener(jornadaId, username);
        return pagina(new Consulta(" WHERE g.jornada_id = ?", new ArrayList<>(List.of(jornadaId))), pagina, tamanio);
    }

    public PaginaGastosResponse listarAdmin(FiltroGastosRequest filtro, int pagina, int tamanio, String username) {
        exigirAdmin(actor(username));
        return pagina(filtrar(filtro), pagina, tamanio);
    }

    public ResumenGastosResponse resumen(FiltroGastosRequest filtro, String username) {
        exigirAdmin(actor(username));
        var consulta = filtrar(filtro);
        var filas = jdbc.query("""
                SELECT g.categoria_id, c.nombre, g.ambito, g.medio, COUNT(*) AS cantidad, SUM(g.importe) AS importe
                FROM gastos g JOIN categorias_gasto c ON c.id = g.categoria_id
                """ + consulta.where() + " GROUP BY g.categoria_id, c.nombre, g.ambito, g.medio ORDER BY c.nombre, g.ambito, g.medio",
                (rs, fila) -> new ResumenGastosResponse.Fila(rs.getObject("categoria_id", UUID.class), rs.getString("nombre"),
                        AmbitoGasto.valueOf(rs.getString("ambito")), MedioPago.valueOf(rs.getString("medio")),
                        rs.getLong("cantidad"), rs.getBigDecimal("importe")), consulta.args().toArray());
        BigDecimal total = filas.stream().map(ResumenGastosResponse.Fila::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenGastosResponse(filtro.desde(), filtro.hasta(), total, filas);
    }

    private PaginaGastosResponse pagina(Consulta consulta, int pagina, int tamanio) {
        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw error(HttpStatus.BAD_REQUEST, "Página no negativa y tamaño entre 1 y 100");
        }
        var args = new ArrayList<>(consulta.args());
        args.add(tamanio + 1);
        args.add((long) pagina * tamanio);
        var filas = jdbc.query("SELECT g.* FROM gastos g" + consulta.where()
                + " ORDER BY g.fecha DESC, g.creado_en DESC, g.id DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        boolean hayMas = filas.size() > tamanio;
        return new PaginaGastosResponse(List.copyOf(filas.subList(0, Math.min(tamanio, filas.size()))), pagina, tamanio, hayMas);
    }

    private Consulta filtrar(FiltroGastosRequest f) {
        if (f == null || f.desde() == null || f.hasta() == null || f.desde().isAfter(f.hasta())) {
            throw error(HttpStatus.BAD_REQUEST, "Indicá un período válido: desde y hasta inclusive");
        }
        StringBuilder where = new StringBuilder(" WHERE g.fecha >= ? AND g.fecha <= ?");
        var args = new ArrayList<Object>();
        args.add(Date.valueOf(f.desde()));
        args.add(Date.valueOf(f.hasta()));
        if (f.categoriaId() != null) { where.append(" AND g.categoria_id = ?"); args.add(f.categoriaId()); }
        if (f.ambito() != null) { where.append(" AND g.ambito = ?"); args.add(f.ambito().name()); }
        if (f.medio() != null) { where.append(" AND g.medio = ?"); args.add(f.medio().name()); }
        if (f.jornadaId() != null) { where.append(" AND g.jornada_id = ?"); args.add(f.jornadaId()); }
        if (f.usuarioId() != null) { where.append(" AND g.usuario_id = ?"); args.add(f.usuarioId()); }
        return new Consulta(where.toString(), args);
    }

    private GastoResponse buscar(UUID id) {
        var filas = jdbc.query("SELECT * FROM gastos WHERE id = ?", MAPPER, id);
        return filas.isEmpty() ? null : filas.getFirst();
    }

    private GastoResponse validarReintento(GastoResponse existente, UUID jornadaId, CrearGastoRequest r,
                                           UUID actorId, String descripcion, String referencia) {
        if (existente == null || !Objects.equals(existente.jornadaId(), jornadaId)
                || !existente.usuarioId().equals(actorId) || !existente.categoriaId().equals(r.categoriaId())
                || !existente.fecha().equals(r.fecha()) || !existente.descripcion().equals(descripcion)
                || existente.medio() != r.medio() || existente.importe().compareTo(r.importe()) != 0
                || !Objects.equals(existente.referencia(), referencia)) {
            throw error(HttpStatus.CONFLICT, "El identificador de operación ya fue usado con otros datos");
        }
        return existente;
    }

    private void validar(CrearGastoRequest r) {
        if (r == null || r.operacionId() == null || r.categoriaId() == null || r.fecha() == null
                || r.descripcion() == null || r.descripcion().isBlank() || r.descripcion().length() > 500
                || r.medio() == null || r.importe() == null || r.importe().signum() <= 0 || r.importe().scale() > 2
                || r.importe().compareTo(new BigDecimal("9999999999.99")) > 0
                || (r.referencia() != null && r.referencia().length() > 100)) {
            throw error(HttpStatus.BAD_REQUEST, "Completá categoría, fecha, descripción, medio e importe positivo con hasta 2 decimales");
        }
    }

    private Usuario actor(String username) {
        return usuarios.findByUsername(username).filter(Usuario::isActivo)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Usuario no disponible"));
    }

    private void exigirAdmin(Usuario actor) {
        if (actor.getRol() != Rol.ADMIN) throw error(HttpStatus.FORBIDDEN, "Se requiere administrador");
    }

    private ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }

    private record Consulta(String where, List<Object> args) {}
}
