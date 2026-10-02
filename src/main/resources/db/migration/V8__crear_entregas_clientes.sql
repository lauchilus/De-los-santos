CREATE TABLE entregas (
    id UUID PRIMARY KEY,
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    total NUMERIC(30,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    saldo_cliente NUMERIC(30,2) NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_entregas_cliente ON entregas(cliente_id, creado_en);
CREATE INDEX ix_entregas_jornada ON entregas(jornada_id, creado_en);

CREATE TABLE envases_cliente (
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    cantidad BIGINT NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cliente_id, producto_id)
);

CREATE TABLE cuentas_clientes (
    cliente_id UUID PRIMARY KEY REFERENCES clientes(id),
    -- Positivo: deuda; negativo: saldo a favor. Los cobros se incorporarán después.
    saldo NUMERIC(30,2) NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE detalles_entrega (
    entrega_id UUID NOT NULL REFERENCES entregas(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    entregados INTEGER NOT NULL CHECK (entregados >= 0),
    devueltos_vacios INTEGER NOT NULL CHECK (devueltos_vacios >= 0),
    devueltos_danados INTEGER NOT NULL CHECK (devueltos_danados >= 0),
    precio_version BIGINT,
    precio_unitario NUMERIC(12,2),
    subtotal NUMERIC(30,2) NOT NULL CHECK (subtotal >= 0),
    envases_resultantes BIGINT NOT NULL CHECK (envases_resultantes >= 0),
    PRIMARY KEY (entrega_id, producto_id),
    CHECK (entregados > 0 OR devueltos_vacios > 0 OR devueltos_danados > 0),
    CHECK (
        (entregados > 0 AND precio_version IS NOT NULL AND precio_version >= 0
            AND precio_unitario IS NOT NULL AND precio_unitario >= 0
            AND subtotal = entregados::NUMERIC * precio_unitario)
        OR (entregados = 0 AND precio_version IS NULL AND precio_unitario IS NULL AND subtotal = 0)
    )
);

CREATE TABLE movimientos_stock_jornada (
    id UUID PRIMARY KEY,
    entrega_id UUID NOT NULL REFERENCES entregas(id),
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('LLENO', 'VACIO', 'DANADO')),
    cantidad BIGINT NOT NULL CHECK (cantidad <> 0),
    saldo_resultante BIGINT NOT NULL CHECK (saldo_resultante >= 0),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entrega_id, producto_id, estado),
    FOREIGN KEY (jornada_id, producto_id, estado)
        REFERENCES stock_jornada(jornada_id, producto_id, estado),
    CHECK ((estado = 'LLENO' AND cantidad < 0) OR (estado <> 'LLENO' AND cantidad > 0))
);
CREATE INDEX ix_movimientos_jornada ON movimientos_stock_jornada(jornada_id, creado_en);

CREATE TABLE movimientos_cuenta_cliente (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    entrega_id UUID NOT NULL UNIQUE REFERENCES entregas(id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo = 'ENTREGA'),
    importe NUMERIC(30,2) NOT NULL CHECK (importe > 0),
    saldo_resultante NUMERIC(30,2) NOT NULL,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_movimientos_cuenta_cliente ON movimientos_cuenta_cliente(cliente_id, creado_en);

CREATE TABLE cambios_precio_producto (
    id UUID PRIMARY KEY,
    producto_id UUID NOT NULL REFERENCES productos(id),
    precio_anterior NUMERIC(12,2),
    precio_nuevo NUMERIC(12,2) NOT NULL CHECK (precio_nuevo >= 0),
    version_nueva BIGINT NOT NULL,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
