CREATE TABLE productos (
                           id UUID PRIMARY KEY,
                           codigo VARCHAR(40) NOT NULL,
                           nombre VARCHAR(150) NOT NULL,
                           tipo VARCHAR(20) NOT NULL,
                           capacidad_litros INTEGER,
                           precio_venta NUMERIC(12, 2),
                           activo BOOLEAN NOT NULL DEFAULT TRUE,
                           creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           version BIGINT NOT NULL DEFAULT 0,

                           CONSTRAINT uk_productos_codigo UNIQUE (codigo),

                           CONSTRAINT ck_productos_codigo
                               CHECK (codigo ~ '^[A-Z0-9_]+$'),

    CONSTRAINT ck_productos_nombre
        CHECK (btrim(nombre) <> ''),

    CONSTRAINT ck_productos_tipo
        CHECK (tipo IN ('BIDON', 'COPA', 'MAQUINA', 'INSUMO')),

    CONSTRAINT ck_productos_capacidad
        CHECK (
            (
                tipo = 'BIDON'
                AND capacidad_litros IS NOT NULL
                AND capacidad_litros > 0
            )
            OR
            (
                tipo <> 'BIDON'
                AND capacidad_litros IS NULL
            )
        ),

    CONSTRAINT ck_productos_precio
        CHECK (precio_venta IS NULL OR precio_venta >= 0)
);

INSERT INTO productos (
    id, codigo, nombre, tipo, capacidad_litros, precio_venta
)
VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        'BIDON_12',
        'Bidón de 12 litros',
        'BIDON',
        12,
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        'BIDON_20',
        'Bidón de 20 litros',
        'BIDON',
        20,
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000003',
        'COPA',
        'Copa para bidón',
        'COPA',
        NULL,
        0
    ),
    (
        '00000000-0000-4000-8000-000000000004',
        'MAQUINA',
        'Máquina dispensadora',
        'MAQUINA',
        NULL,
        0
    ),
    (
        '00000000-0000-4000-8000-000000000005',
        'TAPA',
        'Tapa para bidón',
        'INSUMO',
        NULL,
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000006',
        'BOLSA',
        'Bolsa para bidón',
        'INSUMO',
        NULL,
        NULL
    ),
    (
        '00000000-0000-4000-8000-000000000007',
        'FILTRO',
        'Filtro de fábrica',
        'INSUMO',
        NULL,
        NULL
    );