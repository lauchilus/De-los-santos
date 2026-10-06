-- La operación usa como identificador el UUID del cliente creado en el dispositivo.
-- Se conserva el alta original para reintentos, aunque el cliente se edite después.
CREATE TABLE altas_cliente_sincronizadas (
    cliente_id UUID PRIMARY KEY REFERENCES clientes(id),
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(250) NOT NULL,
    telefono VARCHAR(50) NOT NULL,
    observaciones TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_altas_sincronizadas_jornada ON altas_cliente_sincronizadas(jornada_id);
