package com.iotsorting.dto;

/**
 * Resposta de {@code DELETE /api/objetos} (limpeza das deteccoes registradas).
 *
 * <pre>
 * { "removidos": 12 }
 * </pre>
 *
 * <p>Sao removidas apenas as deteccoes: os dispositivos cadastrados sao mantidos,
 * entao o ESP32 continua valido para registrar novos objetos.</p>
 */
public record LimpezaResponse(

        long removidos

) {
}
