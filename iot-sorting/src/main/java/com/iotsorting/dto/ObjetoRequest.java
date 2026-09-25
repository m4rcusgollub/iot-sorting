package com.iotsorting.dto;

import com.iotsorting.enums.Cor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo recebido do ESP32-CAM em {@code POST /api/objetos}.
 *
 * <pre>
 * {
 *   "dispositivoId": 1,
 *   "cor": "VERMELHO",
 *   "confianca": 92
 * }
 * </pre>
 */
public record ObjetoRequest(

        @NotNull(message = "O identificador do dispositivo é obrigatório")
        Long dispositivoId,

        @NotNull(message = "A cor é obrigatória")
        Cor cor,

        @NotNull(message = "A confiança é obrigatória")
        @Min(value = 0, message = "A confiança deve estar entre 0 e 100")
        @Max(value = 100, message = "A confiança deve estar entre 0 e 100")
        Integer confianca

) {
}
