package com.iotsorting.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo opcional recebido em {@code POST /api/dispositivos/{id}/heartbeat}.
 *
 * <pre>
 * {
 *   "ip": "192.168.1.25",
 *   "esteiraLigada": true
 * }
 * </pre>
 *
 * <p>O campo {@code ip} e opcional: quando nao informado o backend usa o IP da
 * requisicao. O campo {@code esteiraLigada} tambem e opcional e existe para o
 * firmware informar o estado real do rele da esteira.</p>
 */
public record HeartbeatRequest(

        @Size(max = 45, message = "O IP deve ter no máximo 45 caracteres")
        String ip,

        Boolean esteiraLigada

) {
}
