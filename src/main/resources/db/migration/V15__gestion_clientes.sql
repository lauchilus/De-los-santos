CREATE TABLE operaciones_gestion_cliente (
 id UUID PRIMARY KEY,
 cliente_id UUID NOT NULL REFERENCES clientes(id),
 usuario_id UUID NOT NULL REFERENCES usuarios(id),
 solicitud TEXT NOT NULL,
 respuesta JSONB NOT NULL,
 creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
