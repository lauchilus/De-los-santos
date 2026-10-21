CREATE TABLE jornadas (
                          id UUID PRIMARY KEY,
                          fecha DATE NOT NULL,
                          camioneta_id UUID NOT NULL REFERENCES camionetas(id),
                          repartidor_id UUID NOT NULL REFERENCES usuarios(id),
                          efectivo_inicial NUMERIC(12, 2) NOT NULL,
                          estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
                          abierta_por UUID NOT NULL REFERENCES usuarios(id),
                          abierta_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          cerrada_en TIMESTAMPTZ,

                          CONSTRAINT ck_jornada_efectivo
                              CHECK (efectivo_inicial >= 0),

                          CONSTRAINT ck_jornada_estado
                              CHECK (estado IN ('ABIERTA', 'CERRADA')),

                          CONSTRAINT ck_jornada_cierre
                              CHECK (
                                  (estado = 'ABIERTA' AND cerrada_en IS NULL)
                                      OR
                                  (estado = 'CERRADA' AND cerrada_en IS NOT NULL)
                                  )
);

CREATE UNIQUE INDEX uk_jornada_camioneta_abierta
    ON jornadas(camioneta_id)
    WHERE estado = 'ABIERTA';

CREATE UNIQUE INDEX uk_jornada_repartidor_abierta
    ON jornadas(repartidor_id)
    WHERE estado = 'ABIERTA';

CREATE INDEX ix_jornadas_fecha
    ON jornadas(fecha);