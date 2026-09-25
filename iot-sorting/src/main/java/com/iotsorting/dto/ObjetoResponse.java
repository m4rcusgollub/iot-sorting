package com.iotsorting.dto;

import com.iotsorting.enums.Cor;

import java.time.LocalDateTime;

/**
 * Deteccao devolvida pela API.
 *
 * <pre>
 * {
 *   "id": 10,
 *   "dispositivoId": 1,
 *   "dispositivoNome": "Esteira Principal",
 *   "cor": "VERMELHO",
 *   "confianca": 92,
 *   "dataHora": "2026-09-24T22:51:32"
 * }
 * </pre>
 */
public record ObjetoResponse(

        Long id,
        Long dispositivoId,
        String dispositivoNome,
        Cor cor,
        Integer confianca,
        LocalDateTime dataHora

) {
}
