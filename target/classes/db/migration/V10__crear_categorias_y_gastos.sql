CREATE TABLE categorias_gasto (
    id UUID PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL CHECK (btrim(nombre) <> ''),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    creado_por UUID NOT NULL REFERENCES usuarios(id),
    actualizado_por UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_categorias_gasto_nombre ON categorias_gasto(lower(btrim(nombre)));

CREATE TABLE gastos (
    id UUID PRIMARY KEY,
    ambito VARCHAR(10) NOT NULL CHECK (ambito IN ('JORNADA', 'GENERAL')),
    jornada_id UUID REFERENCES jornadas(id),
    categoria_id UUID NOT NULL REFERENCES categorias_gasto(id),
    -- Conserva el nombre usado en el momento del registro aunque se renombre la categoría.
    categoria_nombre VARCHAR(80) NOT NULL,
    fecha DATE NOT NULL,
    descripcion VARCHAR(500) NOT NULL CHECK (btrim(descripcion) <> ''),
    medio VARCHAR(20) NOT NULL CHECK (medio IN ('EFECTIVO', 'TRANSFERENCIA', 'MERCADO_PAGO')),
    importe NUMERIC(12,2) NOT NULL CHECK (importe > 0),
    referencia VARCHAR(100),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((ambito = 'JORNADA' AND jornada_id IS NOT NULL) OR (ambito = 'GENERAL' AND jornada_id IS NULL)),
    CHECK (referencia IS NULL OR btrim(referencia) <> '')
);
CREATE INDEX ix_gastos_jornada ON gastos(jornada_id, fecha);
CREATE INDEX ix_gastos_fecha_categoria ON gastos(fecha, categoria_id);
CREATE INDEX ix_gastos_categoria_fecha ON gastos(categoria_id, fecha);
