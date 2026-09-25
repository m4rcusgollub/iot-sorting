package com.iotsorting.controller;

import com.iotsorting.dto.DashboardResponse;
import com.iotsorting.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint consumido pela dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * {@code GET /api/dashboard} - resumo do dia (totais, distribuicao de cores e status).
     *
     * @param dispositivoId filtro opcional; sem ele os totais consideram todos os dispositivos
     */
    @GetMapping
    public DashboardResponse obterResumo(@RequestParam(name = "dispositivoId", required = false) Long dispositivoId) {
        return dashboardService.obterResumo(dispositivoId);
    }

}
