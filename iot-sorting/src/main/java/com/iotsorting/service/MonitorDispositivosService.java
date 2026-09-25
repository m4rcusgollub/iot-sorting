package com.iotsorting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Verificacao periodica que mantem o status dos dispositivos atualizado.
 *
 * <p>Executa em intervalos (5 segundos por padrao) e marca como OFFLINE os dispositivos
 * que passaram do limite de tempo sem comunicacao. O intervalo e configurado em
 * {@code iot.sorting.monitor.intervalo-verificacao-ms}.</p>
 */
@Service
public class MonitorDispositivosService {

    private static final Logger log = LoggerFactory.getLogger(MonitorDispositivosService.class);

    private final DispositivoService dispositivoService;

    public MonitorDispositivosService(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    @Scheduled(fixedDelayString = "${iot.sorting.monitor.intervalo-verificacao-ms:5000}")
    public void verificarDispositivosInativos() {
        try {
            dispositivoService.marcarDispositivosOffline();
        } catch (RuntimeException excecao) {
            // Uma falha temporaria de banco nao deve interromper as proximas execucoes.
            log.warn("Não foi possível verificar os dispositivos inativos: {}", excecao.getMessage());
        }
    }

}
