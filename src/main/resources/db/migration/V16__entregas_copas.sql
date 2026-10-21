-- Las copas se entregan desde DISPONIBLE y no generan envases retornables.
ALTER TABLE movimientos_stock_jornada
    DROP CONSTRAINT movimientos_stock_jornada_estado_check,
    DROP CONSTRAINT movimientos_stock_jornada_check,
    ADD CONSTRAINT movimientos_stock_jornada_estado_check
        CHECK (estado IN ('LLENO', 'VACIO', 'DANADO', 'DISPONIBLE')),
    ADD CONSTRAINT movimientos_stock_jornada_check
        CHECK ((estado IN ('LLENO', 'DISPONIBLE') AND cantidad < 0)
            OR (estado IN ('VACIO', 'DANADO') AND cantidad > 0));
