package com.agua.backend.jornada;

import com.agua.backend.camioneta.CamionetaRepository;
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
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JornadaService {

    private static final BigDecimal FONDO_PREDETERMINADO =
            new BigDecimal("24000.00");

    private static final ZoneId ZONA =
            ZoneId.of("America/Argentina/Buenos_Aires");

    private final JdbcTemplate jdbc;
    private final UsuarioRepository usuarioRepository;
    private final CamionetaRepository camionetaRepository;

    @Transactional
    public JornadaResponse abrir(
            AbrirJornadaRequest request,
            String username
    ) {
        Usuario actor = obtenerActor(username);

        if (actor.getRol() != Rol.ADMIN
                && !actor.getId().equals(request.repartidorId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo podés abrir jornadas para vos mismo"
            );
        }

        BigDecimal efectivo = request.efectivoInicial() == null
                ? FONDO_PREDETERMINADO
                : request.efectivoInicial();

        var existentes = buscar(request.operacionId());

        if (!existentes.isEmpty()) {
            return validarReintento(
                    existentes.getFirst(), request, efectivo, actor.getId()
            );
        }

        var repartidor = usuarioRepository.findById(request.repartidorId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Repartidor no encontrado"
                ));

        if (!repartidor.isActivo()
                || repartidor.getRol() != Rol.REPARTIDOR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario asignado debe ser un repartidor activo"
            );
        }

        var camioneta = camionetaRepository.findById(request.camionetaId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Camioneta no encontrada"
                ));

        if (!camioneta.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La camioneta está inactiva"
            );
        }

        LocalDate fecha = LocalDate.now(ZONA);

        int insertados = jdbc.update("""
                INSERT INTO jornadas (
                    id,
                    fecha,
                    camioneta_id,
                    repartidor_id,
                    efectivo_inicial,
                    abierta_por
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                request.operacionId(),
                Date.valueOf(fecha),
                request.camionetaId(),
                request.repartidorId(),
                efectivo,
                actor.getId()
        );

        JornadaResponse jornada = buscar(request.operacionId()).getFirst();

        if (insertados == 0) {
            return validarReintento(
                    jornada, request, efectivo, actor.getId()
            );
        }

        return jornada;
    }

    public JornadaResponse obtener(UUID id, String username) {
        Usuario actor = obtenerActor(username);

        JornadaResponse jornada = buscar(id).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Jornada no encontrada"
                ));

        if (actor.getRol() != Rol.ADMIN
                && !actor.getId().equals(jornada.repartidorId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tenés acceso a esta jornada"
            );
        }

        return jornada;
    }

    private Usuario obtenerActor(String username) {
        return usuarioRepository.findByUsername(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario no disponible"
                ));
    }

    private List<JornadaResponse> buscar(UUID id) {
        return jdbc.query("""
                SELECT
                    id,
                    fecha,
                    camioneta_id,
                    repartidor_id,
                    efectivo_inicial,
                    estado,
                    abierta_por
                FROM jornadas
                WHERE id = ?
                """,
                (rs, fila) -> new JornadaResponse(
                        rs.getObject("id", UUID.class),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getObject("camioneta_id", UUID.class),
                        rs.getObject("repartidor_id", UUID.class),
                        rs.getBigDecimal("efectivo_inicial"),
                        rs.getString("estado"),
                        rs.getObject("abierta_por", UUID.class)
                ),
                id
        );
    }

    private JornadaResponse validarReintento(
            JornadaResponse existente,
            AbrirJornadaRequest request,
            BigDecimal efectivo,
            UUID actorId
    ) {
        if (!existente.camionetaId().equals(request.camionetaId())
                || !existente.repartidorId().equals(request.repartidorId())
                || existente.efectivoInicial().compareTo(efectivo) != 0
                || !existente.abiertaPor().equals(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El identificador de operación ya fue usado con otros datos"
            );
        }

        return existente;
    }
}