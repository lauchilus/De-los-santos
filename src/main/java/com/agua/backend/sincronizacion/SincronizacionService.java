package com.agua.backend.sincronizacion;

import com.agua.backend.jornada.JornadaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SincronizacionService {
    private final JornadaService jornadas;
    private final OperacionSincronizadaService operaciones;

    public record Resultado(int indice, SincronizarRequest.Tipo tipo, UUID operacionId,
                            String estado, int httpStatus, String mensaje, Object respuesta) {}
    public record Respuesta(UUID jornadaId, boolean completo, int confirmadas, int pendientes,
                            int siguienteIndice, List<Resultado> resultados) {}

    // Intencionalmente sin @Transactional: conserva el prefijo confirmado ante un error posterior.
    public Respuesta sincronizar(UUID jornadaId, SincronizarRequest request, String username) {
        jornadas.obtener(jornadaId, username);
        if (request == null || request.operaciones() == null || request.operaciones().isEmpty()
                || request.operaciones().size() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote debe tener entre 1 y 50 operaciones");
        var resultados = new ArrayList<Resultado>();
        int confirmadas = 0;
        for (int i = 0; i < request.operaciones().size(); i++) {
            var op = request.operaciones().get(i);
            var tipo = op == null ? null : op.tipo();
            var id = op == null ? null : op.operacionId();
            try {
                Object respuesta = operaciones.ejecutar(jornadaId, op, username);
                resultados.add(new Resultado(i, tipo, id, "CONFIRMADA", 200, null, respuesta));
                confirmadas++;
            } catch (ResponseStatusException e) {
                int codigo = e.getStatusCode().value();
                resultados.add(new Resultado(i, tipo, id, codigo == 409 ? "CONFLICTO" : "RECHAZADA",
                        codigo, e.getReason(), null));
                break;
            } catch (DataIntegrityViolationException e) {
                resultados.add(new Resultado(i, tipo, id, "CONFLICTO", 409, "Los datos entran en conflicto con un registro existente", null));
                break;
            } catch (TransientDataAccessException e) {
                log.warn("Error transitorio al sincronizar jornada {}, operación {}", jornadaId, id);
                resultados.add(new Resultado(i, tipo, id, "REINTENTAR", 503, "Reintentá con los mismos identificadores y datos", null));
                break;
            } catch (RuntimeException e) {
                log.error("Error al sincronizar jornada {}, operación {}", jornadaId, id, e);
                resultados.add(new Resultado(i, tipo, id, "ERROR", 500,
                        "No se pudo confirmar la operación. Conservá sus datos e identificador para reintentar", null));
                break;
            }
        }
        int pendientes = request.operaciones().size() - confirmadas;
        return new Respuesta(jornadaId, pendientes == 0, confirmadas, pendientes, confirmadas, List.copyOf(resultados));
    }
}
