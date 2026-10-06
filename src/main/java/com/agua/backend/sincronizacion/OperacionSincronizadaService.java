package com.agua.backend.sincronizacion;

import com.agua.backend.cobro.CobroService;
import com.agua.backend.entrega.EntregaResponse;
import com.agua.backend.entrega.EntregaService;
import com.agua.backend.gasto.GastoService;
import com.agua.backend.maquina.MaquinaService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperacionSincronizadaService {
    private final AltaClienteSincronizadaService clientes;
    private final EntregaService entregas;
    private final CobroService cobros;
    private final GastoService gastos;
    private final MaquinaService maquinas;
    private final Validator validator;

    // El lote no es una transacción global. Cada resultado confirmado ya está persistido.
    // La comprobación del importe ocurre ANTES del commit de la entrega.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object ejecutar(UUID jornadaId, SincronizarRequest.Operacion op, String username) {
        validar(op);
        return switch (op.tipo()) {
            case ALTA_CLIENTE -> clientes.registrar(jornadaId, op.altaCliente(), username);
            case COBRO -> cobros.registrar(jornadaId, op.cobro(), username);
            case GASTO -> gastos.registrar(jornadaId, op.gasto(), username);
            case MAQUINA -> maquinas.registrar(jornadaId, op.maquina(), username);
            case ENTREGA -> {
                var respuesta = entregas.registrar(jornadaId, op.entrega(), username);
                verificarEntrega(respuesta, op.esperadoEntrega());
                yield respuesta;
            }
        };
    }

    private void validar(SincronizarRequest.Operacion op) {
        if (op == null) throw invalida("La operación no puede ser nula");
        var violaciones = validator.validate(op);
        if (!violaciones.isEmpty()) {
            String detalle = violaciones.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted().findFirst().orElse("Datos inválidos");
            throw invalida(detalle);
        }
        int presentes = (op.altaCliente() == null ? 0 : 1) + (op.entrega() == null ? 0 : 1)
                + (op.cobro() == null ? 0 : 1) + (op.gasto() == null ? 0 : 1) + (op.maquina() == null ? 0 : 1);
        if (presentes != 1 || op.operacionId() == null) throw invalida("Indicá un solo contenido, correspondiente al tipo de operación");
        if (op.tipo() == SincronizarRequest.Tipo.ENTREGA) {
            if (op.esperadoEntrega() == null) throw invalida("La entrega requiere esperadoEntrega para verificar cupo e importe");
            var ids = new HashSet<UUID>();
            for (var i : op.esperadoEntrega().items()) {
                if (!ids.add(i.productoId())) throw invalida("Producto repetido en esperadoEntrega");
            }
            if (op.esperadoEntrega().items().size() != op.entrega().items().size())
                throw invalida("esperadoEntrega debe contener los mismos productos de la entrega");
        } else if (op.esperadoEntrega() != null) throw invalida("esperadoEntrega solo corresponde a ENTREGA");
    }

    private void verificarEntrega(EntregaResponse real, SincronizarRequest.EntregaEsperada esperado) {
        if (!mismoImporte(real.total(), esperado.total())) throw conflicto();
        for (var item : real.items()) {
            var previo = esperado.items().stream().filter(i -> i.productoId().equals(item.productoId())).findFirst();
            if (previo.isEmpty() || previo.get().incluidosAbono() != item.incluidosAbono()
                    || !mismoImporte(previo.get().precioUnitario(), item.precioUnitario())) throw conflicto();
        }
    }
    private boolean mismoImporte(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    private ResponseStatusException invalida(String mensaje) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje); }
    private ResponseStatusException conflicto() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "El cupo o el importe difiere de lo registrado en el dispositivo. No se generaron cambios nuevos; revisá la operación antes de continuar");
    }
}
