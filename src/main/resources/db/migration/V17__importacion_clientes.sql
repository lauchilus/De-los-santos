CREATE TABLE importaciones_clientes (
 id UUID PRIMARY KEY,
 usuario_id UUID NOT NULL REFERENCES usuarios(id),
 fecha_corte DATE NOT NULL,
 archivo BYTEA NOT NULL,
 huella TEXT NOT NULL,
 creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 confirmado_en TIMESTAMPTZ,
 cantidad INTEGER NOT NULL CHECK(cantidad > 0)
);
CREATE TABLE clientes_importados (
 codigo VARCHAR(50) PRIMARY KEY,
 cliente_id UUID NOT NULL UNIQUE REFERENCES clientes(id),
 importacion_id UUID NOT NULL REFERENCES importaciones_clientes(id),
 saldo_inicial NUMERIC(30,2) NOT NULL,
 envases_12 INTEGER NOT NULL CHECK(envases_12 >= 0),
 envases_20 INTEGER NOT NULL CHECK(envases_20 >= 0),
 maquinas INTEGER NOT NULL CHECK(maquinas >= 0)
);
ALTER TABLE abonos_cliente ALTER COLUMN entrega_id DROP NOT NULL;
ALTER TABLE abonos_cliente ADD COLUMN importacion_id UUID REFERENCES importaciones_clientes(id);
ALTER TABLE abonos_cliente ADD CONSTRAINT ck_abono_origen CHECK (
 (entrega_id IS NOT NULL AND importacion_id IS NULL) OR
 (entrega_id IS NULL AND importacion_id IS NOT NULL)
);
