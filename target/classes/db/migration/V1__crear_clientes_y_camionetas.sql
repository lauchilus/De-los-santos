CREATE TABLE clientes (
                          id UUID PRIMARY KEY,
                          nombre VARCHAR(150) NOT NULL,
                          direccion VARCHAR(250) NOT NULL,
                          telefono VARCHAR(50) NOT NULL,
                          observaciones TEXT,
                          activo BOOLEAN NOT NULL DEFAULT TRUE,
                          creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          version BIGINT NOT NULL DEFAULT 0,

                          CONSTRAINT ck_clientes_nombre
                              CHECK (btrim(nombre) <> ''),
                          CONSTRAINT ck_clientes_direccion
                              CHECK (btrim(direccion) <> ''),
                          CONSTRAINT ck_clientes_telefono
                              CHECK (btrim(telefono) <> '')
);

CREATE TABLE camionetas (
                            id UUID PRIMARY KEY,
                            nombre VARCHAR(100) NOT NULL,
                            patente VARCHAR(15) NOT NULL,
                            activo BOOLEAN NOT NULL DEFAULT TRUE,
                            creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            version BIGINT NOT NULL DEFAULT 0,

                            CONSTRAINT ck_camionetas_nombre
                                CHECK (btrim(nombre) <> ''),
                            CONSTRAINT ck_camionetas_patente
                                CHECK (btrim(patente) <> '')
);

CREATE UNIQUE INDEX uk_camionetas_patente
    ON camionetas (upper(replace(btrim(patente), ' ', '')));