package com.iotsorting.dto;

import java.time.LocalDateTime;

/**
 * Resumo consumido pela dashboard em {@code GET /api/dashboard}.
 *
 * <pre>
 * {
 *   "objetosHoje": 147,
 *   "vermelhos": 52,
 *   "verdes": 38,
 *   "azuis": 41,
 *   "amarelos": 16,
 *   "brancos": 5,
 *   "pretos": 4,
 *   "indefinidos": 7,
 *   "dispositivoOnline": true,
 *   "esteiraLigada": true,
 *   "ultimaDeteccao": "2026-09-24T22:51:32",
 *   "totalDispositivos": 1,
 *   "dispositivo": { ... }
 * }
 * </pre>
 *
 * <p>Quando {@code dispositivoId} nao e informado na requisicao, os totais consideram
 * todos os dispositivos cadastrados e os indicadores de status refletem o conjunto
 * (basta um dispositivo online para {@code dispositivoOnline} ser {@code true}).</p>
 */
public record DashboardResponse(

        long objetosHoje,
        long vermelhos,
        long verdes,
        long azuis,
        long amarelos,
        long brancos,
        long pretos,
        long indefinidos,
        boolean dispositivoOnline,
        boolean esteiraLigada,
        LocalDateTime ultimaDeteccao,
        long totalDispositivos,
        DispositivoResponse dispositivo

) {
}
