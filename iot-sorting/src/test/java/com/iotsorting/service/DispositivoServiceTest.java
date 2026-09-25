package com.iotsorting.service;

import com.iotsorting.dto.DispositivoRequest;
import com.iotsorting.entity.Dispositivo;
import com.iotsorting.enums.StatusDispositivo;
import com.iotsorting.exception.ConflitoException;
import com.iotsorting.repository.DispositivoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes das regras de comunicacao e status dos dispositivos.
 */
@ExtendWith(MockitoExtension.class)
class DispositivoServiceTest {

    private static final long LIMITE_OFFLINE_SEGUNDOS = 60L;

    @Mock
    private DispositivoRepository dispositivoRepository;

    private DispositivoService dispositivoService;

    private Dispositivo dispositivo;

    @BeforeEach
    void preparar() {
        dispositivoService = new DispositivoService(dispositivoRepository, LIMITE_OFFLINE_SEGUNDOS);
        dispositivo = new Dispositivo();
        dispositivo.setId(1L);
        dispositivo.setNome("Esteira Principal");
        dispositivo.setCodigo("ESP32CAM-001");
        dispositivo.setStatus(StatusDispositivo.OFFLINE);
        dispositivo.setEsteiraLigada(Boolean.FALSE);
    }

    @Test
    @DisplayName("deteccao deve deixar o dispositivo ONLINE e registrar a ultima conexao")
    void deveAtualizarDispositivoAoReceberDeteccao() {
        dispositivoService.registrarDeteccao(dispositivo);

        assertThat(dispositivo.getStatus()).isEqualTo(StatusDispositivo.ONLINE);
        assertThat(dispositivo.getUltimaConexao()).isNotNull();
        assertThat(dispositivo.getUltimaConexao()).isAfter(LocalDateTime.now().minusSeconds(5));
        assertThat(dispositivo.getEsteiraLigada()).isTrue();

        verify(dispositivoRepository).save(dispositivo);
    }

    @Test
    @DisplayName("heartbeat deve registrar o IP informado e o estado do rele")
    void deveRegistrarHeartbeat() {
        when(dispositivoRepository.findById(1L)).thenReturn(Optional.of(dispositivo));

        dispositivoService.registrarHeartbeat(1L, "192.168.1.25", Boolean.FALSE);

        assertThat(dispositivo.getStatus()).isEqualTo(StatusDispositivo.ONLINE);
        assertThat(dispositivo.getUltimaConexao()).isNotNull();
        assertThat(dispositivo.getIp()).isEqualTo("192.168.1.25");
        assertThat(dispositivo.getEsteiraLigada()).isFalse();

        verify(dispositivoRepository).save(dispositivo);
    }

    @Test
    @DisplayName("heartbeat sem estado do rele deve considerar a esteira ligada")
    void deveAssumirEsteiraLigadaQuandoNaoInformada() {
        when(dispositivoRepository.findById(1L)).thenReturn(Optional.of(dispositivo));

        dispositivoService.registrarHeartbeat(1L, null, null);

        assertThat(dispositivo.getEsteiraLigada()).isTrue();
    }

    @Test
    @DisplayName("dispositivo sem comunicacao dentro de 60 segundos esta OFFLINE")
    void deveCalcularStatusPelaUltimaConexao() {
        dispositivo.setUltimaConexao(LocalDateTime.now().minusSeconds(10));
        assertThat(dispositivoService.estaOnline(dispositivo)).isTrue();

        dispositivo.setUltimaConexao(LocalDateTime.now().minusSeconds(61));
        assertThat(dispositivoService.estaOnline(dispositivo)).isFalse();

        dispositivo.setUltimaConexao(null);
        assertThat(dispositivoService.estaOnline(dispositivo)).isFalse();

        assertThat(dispositivoService.estaOnline(null)).isFalse();
    }

    @Test
    @DisplayName("deve marcar como OFFLINE os dispositivos inativos")
    void deveMarcarDispositivosOffline() {
        Dispositivo inativo = new Dispositivo();
        inativo.setId(2L);
        inativo.setCodigo("ESP32CAM-002");
        inativo.setStatus(StatusDispositivo.ONLINE);
        inativo.setEsteiraLigada(Boolean.TRUE);
        inativo.setUltimaConexao(LocalDateTime.now().minusSeconds(120));

        when(dispositivoRepository.findByStatusAndUltimaConexaoBefore(any(), any()))
                .thenReturn(List.of(inativo));

        int atualizados = dispositivoService.marcarDispositivosOffline();

        assertThat(atualizados).isEqualTo(1);
        assertThat(inativo.getStatus()).isEqualTo(StatusDispositivo.OFFLINE);
        assertThat(inativo.getEsteiraLigada()).isFalse();
        verify(dispositivoRepository).saveAll(List.of(inativo));
    }

    @Test
    @DisplayName("nao deve cadastrar dois dispositivos com o mesmo codigo")
    void deveImpedirCodigoDuplicado() {
        when(dispositivoRepository.existsByCodigo("ESP32CAM-001")).thenReturn(true);

        DispositivoRequest requisicao = new DispositivoRequest("Esteira 2", "ESP32CAM-001", "192.168.1.30");

        assertThatThrownBy(() -> dispositivoService.criar(requisicao))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ESP32CAM-001");
    }

    @Test
    @DisplayName("deve cadastrar o dispositivo como OFFLINE")
    void deveCadastrarDispositivo() {
        when(dispositivoRepository.existsByCodigo("ESP32CAM-002")).thenReturn(false);
        when(dispositivoRepository.save(any(Dispositivo.class))).thenAnswer(invocacao -> {
            Dispositivo salvo = invocacao.getArgument(0);
            salvo.setId(2L);
            return salvo;
        });

        var resposta = dispositivoService.criar(new DispositivoRequest("Esteira 2", "ESP32CAM-002", "192.168.1.30"));

        assertThat(resposta.id()).isEqualTo(2L);
        assertThat(resposta.codigo()).isEqualTo("ESP32CAM-002");
        assertThat(resposta.ip()).isEqualTo("192.168.1.30");
        assertThat(resposta.status()).isEqualTo(StatusDispositivo.OFFLINE);
    }

}
