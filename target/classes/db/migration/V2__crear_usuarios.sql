CREATE TABLE usuarios (
                          id UUID PRIMARY KEY,
                          username VARCHAR(50) NOT NULL,
                          nombre VARCHAR(150) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          rol VARCHAR(20) NOT NULL,
                          activo BOOLEAN NOT NULL DEFAULT TRUE,
                          creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT uk_usuarios_username UNIQUE (username),
                          CONSTRAINT ck_usuarios_username
                              CHECK (
                                  username = lower(btrim(username))
                                      AND username <> ''
                                  ),
                          CONSTRAINT ck_usuarios_nombre
                              CHECK (btrim(nombre) <> ''),
                          CONSTRAINT ck_usuarios_rol
                              CHECK (rol IN ('ADMIN', 'REPARTIDOR'))
);