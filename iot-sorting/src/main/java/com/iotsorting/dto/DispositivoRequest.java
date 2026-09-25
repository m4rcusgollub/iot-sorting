package com.iotsorting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo recebido em {@code POST /api/dispositivos}.
 *
 * <pre>
 * {
 *   "nome": "Esteira Principal",
 *   "codigo": "ESP32CAM-001",
 *   "ip": "192.168.1.25"
 * }
 * </pre>
 */
public record DispositivoRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "O código é obrigatório")
        @Size(max = 60, message = "O código deve ter no máximo 60 caracteres")
        String codigo,

        @Size(max = 45, message = "O IP deve ter no máximo 45 caracteres")
        String ip

) {
}
