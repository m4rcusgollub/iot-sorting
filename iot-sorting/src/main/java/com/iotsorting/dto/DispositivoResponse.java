package com.iotsorting.dto;

import com.iotsorting.enums.StatusDispositivo;

import java.time.LocalDateTime;

/**
 * Dispositivo devolvido pela API.
 *
 * <pre>
 * {
 *   "id": 1,
 *   "nome": "Esteira Principal",
 *   "codigo": "ESP32CAM-001",
 *   "ip": "192.168.1.100",
 *   "status": "ONLINE",
 *   "ultimaConexao": "2026-09-24T22:51:32",
 *   "criadoEm": "2026-09-24T20:00:00"
 * }
 * </pre>
 */
public record DispositivoResponse(

        Long id,
        String nome,
        String codigo,
        String ip,
        StatusDispositivo status,
        LocalDateTime ultimaConexao,
        LocalDateTime criadoEm

) {
}
