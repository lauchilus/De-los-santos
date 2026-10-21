CREATE TABLE stock_fabrica (
                               producto_id UUID NOT NULL REFERENCES productos(id),
                               estado VARCHAR(20) NOT NULL,
                               cantidad BIGINT NOT NULL DEFAULT 0,
                               actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               PRIMARY KEY (producto_id, estado),

                               CONSTRAINT ck_stock_fabrica_estado
                                   CHECK (estado IN ('LLENO', 'VACIO', 'DANADO', 'DISPONIBLE')),

                               CONSTRAINT ck_stock_fabrica_cantidad
                                   CHECK (cantidad >= 0)
);

CREATE TABLE movimientos_stock_fabrica (
                                           id UUID PRIMARY KEY,
                                           producto_id UUID NOT NULL,
                                           estado VARCHAR(20) NOT NULL,
                                           tipo VARCHAR(30) NOT NULL,
                                           cantidad BIGINT NOT NULL,
                                           saldo_resultante BIGINT NOT NULL,
                                           motivo VARCHAR(500) NOT NULL,
                                           usuario_id UUID NOT NULL REFERENCES usuarios(id),
                                           creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           CONSTRAINT fk_movimiento_stock_fabrica
                                               FOREIGN KEY (producto_id, estado)
                                                   REFERENCES stock_fabrica(producto_id, estado),

                                           CONSTRAINT ck_movimiento_cantidad
                                               CHECK (cantidad <> 0),

                                           CONSTRAINT ck_movimiento_saldo
                                               CHECK (saldo_resultante >= 0),

                                           CONSTRAINT ck_movimiento_motivo
                                               CHECK (btrim(motivo) <> ''),

                                           CONSTRAINT ck_movimiento_tipo
                                               CHECK (tipo IN ('AJUSTE', 'PRODUCCION'))
);

CREATE INDEX ix_movimientos_fabrica_producto_fecha
    ON movimientos_stock_fabrica(producto_id, creado_en);

-- Cada bidón tiene tres estados.
INSERT INTO stock_fabrica (producto_id, estado)
SELECT p.id, e.estado
FROM productos p
         CROSS JOIN (
    VALUES ('LLENO'), ('VACIO'), ('DANADO')
) AS e(estado)
WHERE p.tipo = 'BIDON';

-- Los demás productos se cuentan como disponibles.
INSERT INTO stock_fabrica (producto_id, estado)
SELECT id, 'DISPONIBLE'
FROM productos
WHERE tipo <> 'BIDON';