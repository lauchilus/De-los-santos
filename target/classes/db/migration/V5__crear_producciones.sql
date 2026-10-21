CREATE TABLE producciones (
                              id UUID PRIMARY KEY,
                              producto_id UUID NOT NULL REFERENCES productos(id),
                              cantidad INTEGER NOT NULL CHECK (cantidad > 0),
                              usuario_id UUID NOT NULL REFERENCES usuarios(id),
                              creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE movimientos_stock_fabrica
    ADD COLUMN produccion_id UUID REFERENCES producciones(id);

CREATE INDEX ix_movimientos_produccion
    ON movimientos_stock_fabrica(produccion_id);