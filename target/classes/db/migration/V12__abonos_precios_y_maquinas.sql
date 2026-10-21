CREATE TABLE planes_abono (
    id UUID PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL CHECK (btrim(nombre) <> ''),
    precio NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_plan_nombre ON planes_abono(lower(btrim(nombre)));
CREATE TABLE items_plan_abono (
    plan_id UUID NOT NULL REFERENCES planes_abono(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (plan_id, producto_id)
);
CREATE TABLE abonos_cliente (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    plan_id UUID NOT NULL REFERENCES planes_abono(id),
    periodo DATE NOT NULL CHECK (EXTRACT(DAY FROM periodo) = 1),
    entrega_id UUID NOT NULL UNIQUE REFERENCES entregas(id),
    plan_nombre VARCHAR(150) NOT NULL,
    plan_version BIGINT NOT NULL,
    importe NUMERIC(12,2) NOT NULL CHECK (importe >= 0),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cliente_id, plan_id, periodo)
);
CREATE TABLE cupos_abono (
    abono_id UUID NOT NULL REFERENCES abonos_cliente(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    incluidos INTEGER NOT NULL CHECK (incluidos > 0),
    consumidos INTEGER NOT NULL DEFAULT 0 CHECK (consumidos >= 0 AND consumidos <= incluidos),
    PRIMARY KEY (abono_id, producto_id)
);
CREATE TABLE consumos_abono (
    entrega_id UUID NOT NULL,
    producto_id UUID NOT NULL,
    abono_id UUID NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (entrega_id, producto_id, abono_id),
    FOREIGN KEY (entrega_id, producto_id) REFERENCES detalles_entrega(entrega_id, producto_id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (abono_id, producto_id) REFERENCES cupos_abono(abono_id, producto_id)
);
CREATE TABLE precios_cliente (
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    version BIGINT NOT NULL CHECK (version >= 0),
    precio NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    activo BOOLEAN NOT NULL,
    motivo VARCHAR(500) NOT NULL CHECK (btrim(motivo) <> ''),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cliente_id, producto_id, version)
);
ALTER TABLE detalles_entrega
    ADD COLUMN incluidos_abono INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN precio_general NUMERIC(12,2),
    ADD COLUMN precio_cliente NUMERIC(12,2),
    ADD COLUMN precio_cliente_version BIGINT,
    ADD COLUMN precio_excepcional NUMERIC(12,2),
    ADD COLUMN motivo_precio VARCHAR(500),
    ADD COLUMN origen_precio VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
UPDATE detalles_entrega SET precio_general = precio_unitario,
    origen_precio = CASE WHEN entregados = 0 THEN 'SIN_CARGO' ELSE 'GENERAL' END;
-- Nombre asignado por PostgreSQL al segundo CHECK de tabla de V8.
ALTER TABLE detalles_entrega DROP CONSTRAINT detalles_entrega_check1;
ALTER TABLE detalles_entrega ADD CONSTRAINT ck_detalle_precio_abono CHECK (
    incluidos_abono >= 0 AND incluidos_abono <= entregados AND
    ((entregados > 0 AND precio_version IS NOT NULL AND precio_version >= 0
        AND precio_unitario IS NOT NULL AND precio_unitario >= 0
        AND subtotal = (entregados - incluidos_abono)::NUMERIC * precio_unitario)
     OR (entregados = 0 AND precio_version IS NULL AND precio_unitario IS NULL AND subtotal = 0))
);
ALTER TABLE detalles_entrega ADD CONSTRAINT ck_precio_excepcional CHECK (
    (precio_excepcional IS NULL AND motivo_precio IS NULL)
    OR (precio_excepcional >= 0 AND motivo_precio IS NOT NULL AND btrim(motivo_precio) <> '')
);
CREATE TABLE maquinas_cliente (
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    cantidad BIGINT NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    PRIMARY KEY (cliente_id, producto_id)
);
CREATE TABLE movimientos_maquina (
    id UUID PRIMARY KEY,
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    entregadas INTEGER NOT NULL CHECK (entregadas >= 0),
    retiradas INTEGER NOT NULL CHECK (retiradas >= 0),
    saldo_cliente BIGINT NOT NULL CHECK (saldo_cliente >= 0),
    saldo_jornada BIGINT NOT NULL CHECK (saldo_jornada >= 0),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (entregadas > 0 OR retiradas > 0)
);
CREATE INDEX ix_maquinas_cliente ON movimientos_maquina(cliente_id, creado_en);
