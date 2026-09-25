package com.iotsorting.service;

import com.iotsorting.dto.ObjetoRequest;
import com.iotsorting.dto.ObjetoResponse;
import com.iotsorting.entity.Dispositivo;
import com.iotsorting.entity.Objeto;
import com.iotsorting.enums.Cor;
import com.iotsorting.exception.DadosInvalidosException;
import com.iotsorting.exception.RecursoNaoEncontradoException;
import com.iotsorting.repository.ObjetoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Regras de negocio das deteccoes de objetos.
 *
 * <p>Fluxo de uma deteccao recebida do ESP32-CAM: localizar o dispositivo, validar os
 * dados, registrar a data/hora, persistir, atualizar a comunicacao do dispositivo e
 * devolver o DTO.</p>
 */
@Service
public class ObjetoService {

    private static final Logger log = LoggerFactory.getLogger(ObjetoService.class);

    /** Limite maximo aceito em {@code GET /api/objetos/recentes?limite=}. */
    public static final int LIMITE_MAXIMO_RECENTES = 100;

    /** Quantidade padrao de deteccoes recentes. */
    public static final int LIMITE_PADRAO_RECENTES = 10;

    private final ObjetoRepository objetoRepository;
    private final DispositivoService dispositivoService;

    public ObjetoService(ObjetoRepository objetoRepository, DispositivoService dispositivoService) {
        this.objetoRepository = objetoRepository;
        this.dispositivoService = dispositivoService;
    }

    /**
     * Registra uma deteccao enviada pelo ESP32-CAM.
     *
     * @return deteccao persistida, ja convertida para DTO
     */
    @Transactional
    public ObjetoResponse registrar(ObjetoRequest requisicao) {
        Dispositivo dispositivo = dispositivoService.buscarEntidade(requisicao.dispositivoId());
        validarDeteccao(requisicao);

        Objeto objeto = new Objeto();
        objeto.setDispositivo(dispositivo);
        objeto.setCor(requisicao.cor());
        objeto.setConfianca(requisicao.confianca());
        objeto.setDataHora(LocalDateTime.now());

        Objeto salvo = objetoRepository.save(objeto);

        // Comunicacao valida: atualiza ultimaConexao e marca o dispositivo como ONLINE.
        dispositivoService.registrarDeteccao(dispositivo);

        log.info("Detecção registrada: dispositivo={} cor={} confianca={}",
                dispositivo.getCodigo(), salvo.getCor(), salvo.getConfianca());

        return paraResposta(salvo);
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> listar(Cor cor) {
        List<Objeto> objetos = (cor == null)
                ? objetoRepository.findAllByOrderByDataHoraDesc()
                : objetoRepository.findByCorOrderByDataHoraDesc(cor);
        return objetos.stream().map(ObjetoService::paraResposta).toList();
    }

    @Transactional(readOnly = true)
    public ObjetoResponse buscarPorId(Long id) {
        return objetoRepository.findById(id)
                .map(ObjetoService::paraResposta)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Objeto", id));
    }

    /**
     * Deteccoes mais recentes, opcionalmente de um dispositivo especifico.
     *
     * @param dispositivoId filtro opcional por dispositivo
     * @param limite        quantidade desejada (1 a {@value #LIMITE_MAXIMO_RECENTES})
     */
    @Transactional(readOnly = true)
    public List<ObjetoResponse> listarRecentes(Long dispositivoId, int limite) {
        int quantidade = validarLimite(limite);
        PageRequest paginacao = PageRequest.of(0, quantidade);

        List<Objeto> objetos = (dispositivoId == null)
                ? objetoRepository.findAllByOrderByDataHoraDesc(paginacao)
                : objetoRepository.findAllByDispositivoIdOrderByDataHoraDesc(dispositivoId, paginacao);

        return objetos.stream().map(ObjetoService::paraResposta).toList();
    }

    private void validarDeteccao(ObjetoRequest requisicao) {
        if (requisicao.cor() == null) {
            throw new DadosInvalidosException("A cor é obrigatória");
        }
        if (requisicao.confianca() == null || requisicao.confianca() < 0 || requisicao.confianca() > 100) {
            throw new DadosInvalidosException("A confiança deve estar entre 0 e 100");
        }
    }

    private int validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO_RECENTES) {
            throw new DadosInvalidosException("O limite deve estar entre 1 e " + LIMITE_MAXIMO_RECENTES);
        }
        return limite;
    }

    /** Converte a entidade no DTO devolvido pela API. */
    public static ObjetoResponse paraResposta(Objeto objeto) {
        return new ObjetoResponse(
                objeto.getId(),
                objeto.getDispositivo().getId(),
                objeto.getDispositivo().getNome(),
                objeto.getCor(),
                objeto.getConfianca(),
                objeto.getDataHora()
        );
    }

}
