CREATE TABLE cierres_jornada (
    id UUID PRIMARY KEY,
    jornada_id UUID NOT NULL UNIQUE REFERENCES jornadas(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    efectivo_inicial NUMERIC(30,2) NOT NULL CHECK (efectivo_inicial >= 0),
    ingresos_efectivo NUMERIC(30,2) NOT NULL CHECK (ingresos_efectivo >= 0),
    ingresos_transferencia NUMERIC(30,2) NOT NULL CHECK (ingresos_transferencia >= 0),
    ingresos_mercado_pago NUMERIC(30,2) NOT NULL CHECK (ingresos_mercado_pago >= 0),
    egresos_efectivo NUMERIC(30,2) NOT NULL CHECK (egresos_efectivo >= 0),
    egresos_transferencia NUMERIC(30,2) NOT NULL CHECK (egresos_transferencia >= 0),
    egresos_mercado_pago NUMERIC(30,2) NOT NULL CHECK (egresos_mercado_pago >= 0),
    efectivo_esperado NUMERIC(30,2) NOT NULL CHECK (efectivo_esperado >= 0),
    efectivo_contado NUMERIC(30,2) NOT NULL CHECK (efectivo_contado >= 0),
    diferencia NUMERIC(30,2) NOT NULL,
    motivo_diferencia VARCHAR(500),
    cerrado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (efectivo_esperado = efectivo_inicial + ingresos_efectivo - egresos_efectivo),
    CHECK (diferencia = efectivo_contado - efectivo_esperado),
    CHECK (diferencia = 0 OR (motivo_diferencia IS NOT NULL AND btrim(motivo_diferencia) <> ''))
);
CREATE INDEX ix_cierres_fecha ON cierres_jornada(cerrado_en);

CREATE TABLE devoluciones_cierre (
    cierre_id UUID NOT NULL REFERENCES cierres_jornada(id),
    producto_id UUID NOT NULL,
    estado VARCHAR(20) NOT NULL,
    cantidad BIGINT NOT NULL CHECK (cantidad > 0),
    saldo_fabrica BIGINT NOT NULL CHECK (saldo_fabrica >= 0),
    PRIMARY KEY (cierre_id, producto_id, estado),
    FOREIGN KEY (producto_id, estado) REFERENCES stock_fabrica(producto_id, estado)
);

ALTER TABLE movimientos_stock_fabrica DROP CONSTRAINT ck_movimiento_tipo;
ALTER TABLE movimientos_stock_fabrica ADD CONSTRAINT ck_movimiento_tipo
    CHECK (tipo IN ('AJUSTE', 'PRODUCCION', 'CARGA', 'DEVOLUCION_CIERRE'));
ALTER TABLE movimientos_stock_fabrica
    ADD COLUMN cierre_id UUID REFERENCES cierres_jornada(id);
CREATE INDEX ix_movimientos_cierre ON movimientos_stock_fabrica(cierre_id);
