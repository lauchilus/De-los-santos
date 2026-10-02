CREATE TABLE cargas_jornada (
    id UUID PRIMARY KEY,
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    numero INTEGER NOT NULL CHECK (numero > 0),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('INICIAL', 'RECARGA')),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (jornada_id, numero),
    CHECK ((numero = 1 AND tipo = 'INICIAL') OR (numero > 1 AND tipo = 'RECARGA'))
);

CREATE TABLE stock_jornada (
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('LLENO', 'VACIO', 'DANADO', 'DISPONIBLE')),
    cantidad BIGINT NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (jornada_id, producto_id, estado)
);

CREATE TABLE detalles_carga_jornada (
    carga_id UUID NOT NULL REFERENCES cargas_jornada(id),
    producto_id UUID NOT NULL REFERENCES productos(id),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('LLENO', 'DISPONIBLE')),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    saldo_fabrica BIGINT NOT NULL CHECK (saldo_fabrica >= 0),
    saldo_jornada BIGINT NOT NULL CHECK (saldo_jornada >= 0),
    PRIMARY KEY (carga_id, producto_id)
);

ALTER TABLE movimientos_stock_fabrica
    DROP CONSTRAINT ck_movimiento_tipo;
ALTER TABLE movimientos_stock_fabrica
    ADD CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ('AJUSTE', 'PRODUCCION', 'CARGA'));
ALTER TABLE movimientos_stock_fabrica
    ADD COLUMN carga_id UUID REFERENCES cargas_jornada(id);
CREATE INDEX ix_movimientos_carga ON movimientos_stock_fabrica(carga_id);
