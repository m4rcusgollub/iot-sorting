package com.iotsorting.controller;

import com.iotsorting.dto.DispositivoRequest;
import com.iotsorting.dto.DispositivoResponse;
import com.iotsorting.dto.HeartbeatRequest;
import com.iotsorting.service.DispositivoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints dos dispositivos ESP32-CAM.
 */
@RestController
@RequestMapping("/api/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    /** {@code GET /api/dispositivos} - lista os dispositivos cadastrados. */
    @GetMapping
    public List<DispositivoResponse> listar() {
        return dispositivoService.listar();
    }

    /** {@code GET /api/dispositivos/{id}} - detalhe do dispositivo. */
    @GetMapping("/{id}")
    public DispositivoResponse buscarPorId(@PathVariable Long id) {
        return dispositivoService.buscarPorId(id);
    }

    /** {@code POST /api/dispositivos} - cadastra um novo ESP32-CAM (HTTP 201). */
    @PostMapping
    public ResponseEntity<DispositivoResponse> criar(@Valid @RequestBody DispositivoRequest requisicao) {
        DispositivoResponse resposta = dispositivoService.criar(requisicao);
        URI localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resposta.id())
                .toUri();
        return ResponseEntity.created(localizacao).body(resposta);
    }

    /**
     * {@code POST /api/dispositivos/{id}/heartbeat} - o ESP32-CAM informa que esta conectado.
     *
     * <p>Atualiza {@code ultimaConexao}, marca o dispositivo como ONLINE e registra o IP da
     * requisicao quando o corpo nao informa um IP. O corpo e opcional.</p>
     */
    @PostMapping("/{id}/heartbeat")
    public DispositivoResponse heartbeat(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) HeartbeatRequest requisicao,
                                         HttpServletRequest httpRequest) {
        String ip = (requisicao != null && requisicao.ip() != null && !requisicao.ip().isBlank())
                ? requisicao.ip()
                : obterIpDaRequisicao(httpRequest);
        Boolean esteiraLigada = (requisicao != null) ? requisicao.esteiraLigada() : null;

        return dispositivoService.registrarHeartbeat(id, ip, esteiraLigada);
    }

    /** IP de origem da requisicao, considerando proxy reverso quando presente. */
    private String obterIpDaRequisicao(HttpServletRequest httpRequest) {
        String encaminhado = httpRequest.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return httpRequest.getRemoteAddr();
    }

}
