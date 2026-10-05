package com.agua.backend.cierre;

import com.agua.backend.cobro.CajaJornadaResponse;
import com.agua.backend.jornada.JornadaResponse;
import com.agua.backend.stock.StockResponse;
import java.util.List;

public record PreviaCierreResponse(JornadaResponse jornada, CajaJornadaResponse caja, List<StockResponse> stockADevolver) {}
