package com.iotsorting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Corpo de erro padronizado devolvido pela API.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-24T22:50:00",
 *   "status": 400,
 *   "message": "A confiança deve estar entre 0 e 100",
 *   "campos": { "confianca": "A confiança deve estar entre 0 e 100" }
 * }
 * </pre>
 *
 * <p>O campo {@code campos} so aparece quando existe erro de validacao em campos
 * especificos. Trace de excecao nunca e enviado ao cliente.</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResponse(

        LocalDateTime timestamp,
        int status,
        String message,
        Map<String, String> campos

) {

    public static ErroResponse de(int status, String mensagem) {
        return new ErroResponse(LocalDateTime.now(), status, mensagem, Map.of());
    }

    public static ErroResponse de(int status, String mensagem, Map<String, String> campos) {
        return new ErroResponse(LocalDateTime.now(), status, mensagem, campos);
    }

}
