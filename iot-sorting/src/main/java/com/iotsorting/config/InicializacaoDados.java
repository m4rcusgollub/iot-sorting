package com.iotsorting.config;

import com.iotsorting.entity.Dispositivo;
import com.iotsorting.enums.StatusDispositivo;
import com.iotsorting.repository.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o dispositivo de teste na primeira execucao da aplicacao.
 *
 * <p>Nao duplica registros: cadastra o ESP32CAM-001 apenas quando ele ainda nao existe.
 * Para desativar a inicializacao automatica defina
 * {@code iot.sorting.inicializacao.criar-dispositivo-padrao=false}. Esta classe pode ser
 * removida quando o projeto nao precisar mais do dispositivo de exemplo.</p>
 */
@Component
public class InicializacaoDados implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializacaoDados.class);

    private static final String CODIGO_DISPOSITIVO_PADRAO = "ESP32CAM-001";
    private static final String NOME_DISPOSITIVO_PADRAO = "Esteira Principal";
    private static final String IP_DISPOSITIVO_PADRAO = "192.168.1.100";

    private final DispositivoRepository dispositivoRepository;
    private final boolean criarDispositivoPadrao;

    public InicializacaoDados(DispositivoRepository dispositivoRepository,
                              @Value("${iot.sorting.inicializacao.criar-dispositivo-padrao:true}")
                              boolean criarDispositivoPadrao) {
        this.dispositivoRepository = dispositivoRepository;
        this.criarDispositivoPadrao = criarDispositivoPadrao;
    }

    @Override
    public void run(String... args) {
        if (!criarDispositivoPadrao) {
            log.info("Inicialização automática de dispositivo desativada.");
            return;
        }

        if (dispositivoRepository.existsByCodigo(CODIGO_DISPOSITIVO_PADRAO)) {
            log.info("Dispositivo padrão {} já existe. Nenhuma ação necessária.", CODIGO_DISPOSITIVO_PADRAO);
            return;
        }

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setNome(NOME_DISPOSITIVO_PADRAO);
        dispositivo.setCodigo(CODIGO_DISPOSITIVO_PADRAO);
        dispositivo.setIp(IP_DISPOSITIVO_PADRAO);
        dispositivo.setStatus(StatusDispositivo.OFFLINE);
        dispositivo.setEsteiraLigada(Boolean.FALSE);
        dispositivoRepository.save(dispositivo);

        log.info("Dispositivo padrão criado: {} ({})", NOME_DISPOSITIVO_PADRAO, CODIGO_DISPOSITIVO_PADRAO);
    }

}
