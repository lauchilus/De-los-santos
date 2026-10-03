CREATE TABLE cobros (
    id UUID PRIMARY KEY,
    jornada_id UUID NOT NULL REFERENCES jornadas(id),
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    total NUMERIC(14,2) NOT NULL CHECK (total > 0),
    saldo_cliente NUMERIC(30,2) NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_cobros_jornada ON cobros(jornada_id, creado_en);
CREATE INDEX ix_cobros_cliente ON cobros(cliente_id, creado_en);

CREATE TABLE pagos_cobro (
    cobro_id UUID NOT NULL REFERENCES cobros(id),
    medio VARCHAR(20) NOT NULL CHECK (medio IN ('EFECTIVO', 'TRANSFERENCIA', 'MERCADO_PAGO')),
    importe NUMERIC(12,2) NOT NULL CHECK (importe > 0),
    referencia VARCHAR(100),
    PRIMARY KEY (cobro_id, medio),
    CHECK (referencia IS NULL OR btrim(referencia) <> '')
);

-- Las entregas siguen sumando deuda; los cobros la reducen y pueden dejar crédito.
ALTER TABLE movimientos_cuenta_cliente
    ALTER COLUMN entrega_id DROP NOT NULL;
ALTER TABLE movimientos_cuenta_cliente
    ADD COLUMN cobro_id UUID UNIQUE REFERENCES cobros(id);
ALTER TABLE movimientos_cuenta_cliente
    DROP CONSTRAINT movimientos_cuenta_cliente_tipo_check;
ALTER TABLE movimientos_cuenta_cliente
    DROP CONSTRAINT movimientos_cuenta_cliente_importe_check;
ALTER TABLE movimientos_cuenta_cliente
    ADD CONSTRAINT ck_movimiento_cuenta_origen CHECK (
        (tipo = 'ENTREGA' AND importe > 0 AND entrega_id IS NOT NULL AND cobro_id IS NULL)
        OR
        (tipo = 'COBRO' AND importe < 0 AND entrega_id IS NULL AND cobro_id IS NOT NULL)
    );
