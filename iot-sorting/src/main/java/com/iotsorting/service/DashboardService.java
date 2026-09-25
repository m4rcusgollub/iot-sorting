package com.iotsorting.service;

import com.iotsorting.dto.DashboardResponse;
import com.iotsorting.entity.Dispositivo;
import com.iotsorting.entity.Objeto;
import com.iotsorting.enums.Cor;
import com.iotsorting.repository.ObjetoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Monta os dados exibidos na dashboard.
 *
 * <p>Quando {@code dispositivoId} e informado, todos os numeros se referem a esse
 * dispositivo. Sem filtro, os totais consideram todos os dispositivos cadastrados e os
 * indicadores de status representam o conjunto (basta um dispositivo online).</p>
 */
@Service
public class DashboardService {

    private final ObjetoRepository objetoRepository;
    private final DispositivoService dispositivoService;

    public DashboardService(ObjetoRepository objetoRepository, DispositivoService dispositivoService) {
        this.objetoRepository = objetoRepository;
        this.dispositivoService = dispositivoService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse obterResumo(Long dispositivoId) {
        Dispositivo dispositivo = (dispositivoId != null) ? dispositivoService.buscarEntidade(dispositivoId) : null;

        LocalDateTime inicioDoDia = LocalDate.now().atStartOfDay();
        LocalDateTime inicioDoProximoDia = inicioDoDia.plusDays(1);

        boolean dispositivoOnline;
        boolean esteiraLigada;
        if (dispositivo == null) {
            dispositivoOnline = dispositivoService.existeDispositivoOnline();
            esteiraLigada = dispositivoService.existeEsteiraLigada();
        } else {
            dispositivoOnline = dispositivoService.estaOnline(dispositivo);
            esteiraLigada = dispositivoOnline && dispositivoService.esteiraLigada(dispositivo);
        }

        return new DashboardResponse(
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, null),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.VERMELHO),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.VERDE),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.AZUL),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.AMARELO),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.BRANCO),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.PRETO),
                contar(inicioDoDia, inicioDoProximoDia, dispositivo, Cor.INDEFINIDO),
                dispositivoOnline,
                esteiraLigada,
                localizarUltimaDeteccao(dispositivo),
                dispositivoService.contarDispositivos(),
                dispositivo == null ? null : DispositivoService.paraResposta(dispositivo)
        );
    }

    /** Conta as deteccoes do periodo, para todos os dispositivos ou para um deles, por cor. */
    private long contar(LocalDateTime inicio, LocalDateTime fim, Dispositivo dispositivo, Cor cor) {
        if (dispositivo == null) {
            return (cor == null)
                    ? objetoRepository.countByDataHoraGreaterThanEqualAndDataHoraLessThan(inicio, fim)
                    : objetoRepository.countByCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(cor, inicio, fim);
        }
        return (cor == null)
                ? objetoRepository.countByDispositivoIdAndDataHoraGreaterThanEqualAndDataHoraLessThan(
                        dispositivo.getId(), inicio, fim)
                : objetoRepository.countByDispositivoIdAndCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(
                        dispositivo.getId(), cor, inicio, fim);
    }

    private LocalDateTime localizarUltimaDeteccao(Dispositivo dispositivo) {
        Optional<Objeto> ultima = (dispositivo == null)
                ? objetoRepository.findFirstByOrderByDataHoraDesc()
                : objetoRepository.findFirstByDispositivoIdOrderByDataHoraDesc(dispositivo.getId());
        return ultima.map(Objeto::getDataHora).orElse(null);
    }

}
