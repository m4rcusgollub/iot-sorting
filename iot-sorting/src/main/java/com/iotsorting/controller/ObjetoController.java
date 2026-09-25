package com.iotsorting.controller;

import com.iotsorting.dto.ObjetoRequest;
import com.iotsorting.dto.ObjetoResponse;
import com.iotsorting.enums.Cor;
import com.iotsorting.service.ObjetoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints das deteccoes de objetos.
 *
 * <p>O controller apenas recebe a requisicao, valida o DTO e delega para o
 * {@link ObjetoService}.</p>
 */
@RestController
@RequestMapping("/api/objetos")
public class ObjetoController {

    private final ObjetoService objetoService;

    public ObjetoController(ObjetoService objetoService) {
        this.objetoService = objetoService;
    }

    /** {@code POST /api/objetos} - registra a deteccao enviada pelo ESP32-CAM (HTTP 201). */
    @PostMapping
    public ResponseEntity<ObjetoResponse> registrar(@Valid @RequestBody ObjetoRequest requisicao) {
        ObjetoResponse resposta = objetoService.registrar(requisicao);
        URI localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(resposta.id())
                .toUri();
        return ResponseEntity.created(localizacao).body(resposta);
    }

    /** {@code GET /api/objetos} - historico completo, com filtro opcional por cor. */
    @GetMapping
    public List<ObjetoResponse> listar(@RequestParam(name = "cor", required = false) Cor cor) {
        return objetoService.listar(cor);
    }

    /** {@code GET /api/objetos/recentes?limite=10&dispositivoId=1} - ultimas deteccoes. */
    @GetMapping("/recentes")
    public List<ObjetoResponse> listarRecentes(
            @RequestParam(name = "limite", defaultValue = "" + ObjetoService.LIMITE_PADRAO_RECENTES) int limite,
            @RequestParam(name = "dispositivoId", required = false) Long dispositivoId) {
        return objetoService.listarRecentes(dispositivoId, limite);
    }

    /** {@code GET /api/objetos/{id}} - detalhe de uma deteccao. */
    @GetMapping("/{id}")
    public ObjetoResponse buscarPorId(@PathVariable Long id) {
        return objetoService.buscarPorId(id);
    }

}
