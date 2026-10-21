-- Se conserva el ID y todo el historial de la bolsa existente.
UPDATE productos SET codigo = 'BOLSA_20', nombre = 'Bolsa para bidón de 20 litros',
    version = version + 1, actualizado_en = CURRENT_TIMESTAMP
WHERE codigo = 'BOLSA';

INSERT INTO productos (id, codigo, nombre, tipo)
VALUES ('00000000-0000-4000-8000-000000000008', 'BOLSA_12', 'Bolsa para bidón de 12 litros', 'INSUMO');

INSERT INTO stock_fabrica (producto_id, estado, cantidad)
VALUES ('00000000-0000-4000-8000-000000000008', 'DISPONIBLE', 0);
