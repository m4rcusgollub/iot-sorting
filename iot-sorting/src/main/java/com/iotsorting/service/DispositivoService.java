package com.iotsorting.service;

import com.iotsorting.dto.DispositivoRequest;
import com.iotsorting.dto.DispositivoResponse;
import com.iotsorting.entity.Dispositivo;
import com.iotsorting.enums.StatusDispositivo;
import com.iotsorting.exception.ConflitoException;
import com.iotsorting.exception.RecursoNaoEncontradoException;
import com.iotsorting.repository.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Regras de negocio dos dispositivos ESP32-CAM.
 *
 * <p>O status nao depende do que o ESP32 envia: o backend considera o dispositivo
 * ONLINE enquanto houver comunicacao recente e OFFLINE quando
 * {@code ultimaConexao} ultrapassar o limite configurado em
 * {@code iot.sorting.dispositivo.tempo-limite-offline-segundos} (60 segundos por padrao).</p>
 */
@Service
public class DispositivoService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoService.class);

    private final DispositivoRepository dispositivoRepository;
    private final long tempoLimiteOfflineSegundos;

    public DispositivoService(DispositivoRepository dispositivoRepository,
                              @Value("${iot.sorting.dispositivo.tempo-limite-offline-segundos:60}")
                              long tempoLimiteOfflineSegundos) {
        this.dispositivoRepository = dispositivoRepository;
        this.tempoLimiteOfflineSegundos = tempoLimiteOfflineSegundos;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DispositivoResponse> listar() {
        return dispositivoRepository.findAllByOrderByNomeAsc()
                .stream()
                .map(DispositivoService::paraResposta)
                .toList();
    }

    @Transactional(readOnly = true)
    public DispositivoResponse buscarPorId(Long id) {
        return paraResposta(buscarEntidade(id));
    }

    /** Busca a entidade ou lanca {@link RecursoNaoEncontradoException}. */
    @Transactional(readOnly = true)
    public Dispositivo buscarEntidade(Long id) {
        if (id == null) {
            throw new RecursoNaoEncontradoException("O identificador do dispositivo é obrigatório");
        }
        return dispositivoRepository.findById(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Dispositivo", id));
    }

    @Transactional(readOnly = true)
    public long contarDispositivos() {
        return dispositivoRepository.count();
    }

    /** {@code true} quando existe pelo menos um dispositivo com comunicacao recente. */
    @Transactional(readOnly = true)
    public boolean existeDispositivoOnline() {
        return dispositivoRepository.findByStatus(StatusDispositivo.ONLINE)
                .stream()
                .anyMatch(this::estaOnline);
    }

    /** {@code true} quando existe pelo menos um dispositivo online com a esteira ligada. */
    @Transactional(readOnly = true)
    public boolean existeEsteiraLigada() {
        return dispositivoRepository.findByStatus(StatusDispositivo.ONLINE)
                .stream()
                .anyMatch(dispositivo -> estaOnline(dispositivo) && esteiraLigada(dispositivo));
    }

    /**
     * Calcula o status real do dispositivo a partir da ultima comunicacao,
     * sem depender de um campo enviado pelo ESP32.
     */
    public boolean estaOnline(Dispositivo dispositivo) {
        if (dispositivo == null || dispositivo.getUltimaConexao() == null) {
            return false;
        }
        LocalDateTime limite = LocalDateTime.now().minusSeconds(tempoLimiteOfflineSegundos);
        return dispositivo.getUltimaConexao().isAfter(limite);
    }

    /** Estado do rele da esteira informado pelo dispositivo (null = desligada). */
    public boolean esteiraLigada(Dispositivo dispositivo) {
        return dispositivo != null && Boolean.TRUE.equals(dispositivo.getEsteiraLigada());
    }

    // ------------------------------------------------------------------
    // Cadastro e comunicacao
    // ------------------------------------------------------------------

    @Transactional
    public DispositivoResponse criar(DispositivoRequest requisicao) {
        if (dispositivoRepository.existsByCodigo(requisicao.codigo())) {
            throw new ConflitoException("Já existe um dispositivo cadastrado com o código " + requisicao.codigo());
        }

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setNome(requisicao.nome().trim());
        dispositivo.setCodigo(requisicao.codigo().trim());
        dispositivo.setIp(requisicao.ip() == null || requisicao.ip().isBlank() ? null : requisicao.ip().trim());
        dispositivo.setStatus(StatusDispositivo.OFFLINE);
        dispositivo.setEsteiraLigada(Boolean.FALSE);

        Dispositivo salvo = dispositivoRepository.save(dispositivo);
        log.info("Dispositivo cadastrado: {} ({})", salvo.getNome(), salvo.getCodigo());
        return paraResposta(salvo);
    }

    /**
     * Registra a comunicacao gerada por uma deteccao: atualiza {@code ultimaConexao},
     * marca o dispositivo como ONLINE e assume a esteira ligada (houve objeto em movimento).
     */
    @Transactional
    public void registrarDeteccao(Dispositivo dispositivo) {
        atualizarEstadoConexao(dispositivo, null, Boolean.TRUE);
    }

    /**
     * Registra o heartbeat do ESP32-CAM.
     *
     * @param id             identificador do dispositivo
     * @param ipRequisicao   IP de origem da requisicao (usado quando o corpo nao informa o IP)
     * @param esteiraLigada  estado do rele informado pelo firmware (opcional)
     */
    @Transactional
    public DispositivoResponse registrarHeartbeat(Long id, String ipRequisicao, Boolean esteiraLigada) {
        Dispositivo dispositivo = buscarEntidade(id);
        Boolean estadoEsteira = esteiraLigada != null ? esteiraLigada : Boolean.TRUE;
        atualizarEstadoConexao(dispositivo, ipRequisicao, estadoEsteira);
        log.debug("Heartbeat recebido do dispositivo {} ({})", dispositivo.getCodigo(), ipRequisicao);
        return paraResposta(dispositivo);
    }

    /**
     * Marca como OFFLINE todos os dispositivos online sem comunicacao dentro do limite.
     *
     * @return quantidade de dispositivos atualizados
     */
    @Transactional
    public int marcarDispositivosOffline() {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(tempoLimiteOfflineSegundos);
        List<Dispositivo> inativos = dispositivoRepository
                .findByStatusAndUltimaConexaoBefore(StatusDispositivo.ONLINE, limite);

        if (inativos.isEmpty()) {
            return 0;
        }

        inativos.forEach(dispositivo -> {
            dispositivo.setStatus(StatusDispositivo.OFFLINE);
            // Sem comunicacao nao e possivel confirmar o estado do rele: a esteira e exibida como desligada.
            dispositivo.setEsteiraLigada(Boolean.FALSE);
        });
        dispositivoRepository.saveAll(inativos);

        log.info("{} dispositivo(s) marcados como OFFLINE por falta de comunicacao.", inativos.size());
        return inativos.size();
    }

    private void atualizarEstadoConexao(Dispositivo dispositivo, String ip, Boolean estadoEsteira) {
        dispositivo.setUltimaConexao(LocalDateTime.now());
        dispositivo.setStatus(StatusDispositivo.ONLINE);
        dispositivo.setEsteiraLigada(estadoEsteira);
        if (ip != null && !ip.isBlank()) {
            dispositivo.setIp(ip.trim());
        }
        dispositivoRepository.save(dispositivo);
    }

    /** Converte a entidade no DTO devolvido pela API. */
    public static DispositivoResponse paraResposta(Dispositivo dispositivo) {
        return new DispositivoResponse(
                dispositivo.getId(),
                dispositivo.getNome(),
                dispositivo.getCodigo(),
                dispositivo.getIp(),
                dispositivo.getStatus(),
                dispositivo.getUltimaConexao(),
                dispositivo.getCriadoEm()
        );
    }

}
