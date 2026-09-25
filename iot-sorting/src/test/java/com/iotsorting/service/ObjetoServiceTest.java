package com.iotsorting.service;

import com.iotsorting.dto.ObjetoRequest;
import com.iotsorting.dto.ObjetoResponse;
import com.iotsorting.entity.Dispositivo;
import com.iotsorting.entity.Objeto;
import com.iotsorting.enums.Cor;
import com.iotsorting.enums.StatusDispositivo;
import com.iotsorting.exception.DadosInvalidosException;
import com.iotsorting.exception.RecursoNaoEncontradoException;
import com.iotsorting.repository.ObjetoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes das regras de negocio do registro de deteccoes.
 */
@ExtendWith(MockitoExtension.class)
class ObjetoServiceTest {

    @Mock
    private ObjetoRepository objetoRepository;

    @Mock
    private DispositivoService dispositivoService;

    @InjectMocks
    private ObjetoService objetoService;

    private Dispositivo dispositivo;

    @BeforeEach
    void preparar() {
        dispositivo = new Dispositivo();
        dispositivo.setId(1L);
        dispositivo.setNome("Esteira Principal");
        dispositivo.setCodigo("ESP32CAM-001");
        dispositivo.setStatus(StatusDispositivo.ONLINE);
    }

    @Test
    @DisplayName("deve registrar a deteccao, salvar e atualizar a comunicacao do dispositivo")
    void deveRegistrarDeteccao() {
        when(dispositivoService.buscarEntidade(1L)).thenReturn(dispositivo);
        when(objetoRepository.save(any(Objeto.class))).thenAnswer(invocacao -> {
            Objeto objeto = invocacao.getArgument(0);
            objeto.setId(10L);
            return objeto;
        });

        ObjetoResponse resposta = objetoService.registrar(new ObjetoRequest(1L, Cor.VERMELHO, 92));

        ArgumentCaptor<Objeto> objetoCapturado = ArgumentCaptor.forClass(Objeto.class);
        verify(objetoRepository).save(objetoCapturado.capture());

        Objeto salvo = objetoCapturado.getValue();
        assertThat(salvo.getDispositivo()).isEqualTo(dispositivo);
        assertThat(salvo.getCor()).isEqualTo(Cor.VERMELHO);
        assertThat(salvo.getConfianca()).isEqualTo(92);
        assertThat(salvo.getDataHora()).isNotNull();

        assertThat(resposta.id()).isEqualTo(10L);
        assertThat(resposta.dispositivoId()).isEqualTo(1L);
        assertThat(resposta.dispositivoNome()).isEqualTo("Esteira Principal");
        assertThat(resposta.cor()).isEqualTo(Cor.VERMELHO);
        assertThat(resposta.confianca()).isEqualTo(92);
        assertThat(resposta.dataHora()).isNotNull();

        // A comunicacao do dispositivo precisa ser atualizada apos a deteccao.
        verify(dispositivoService).registrarDeteccao(dispositivo);
    }

    @Test
    @DisplayName("deve falhar quando o dispositivo nao existe")
    void deveFalharQuandoDispositivoNaoExiste() {
        when(dispositivoService.buscarEntidade(99L))
                .thenThrow(new RecursoNaoEncontradoException("Dispositivo não encontrado: identificador 99"));

        ObjetoRequest requisicao = new ObjetoRequest(99L, Cor.AZUL, 80);

        assertThatThrownBy(() -> objetoService.registrar(requisicao))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Dispositivo");

        verify(objetoRepository, never()).save(any(Objeto.class));
        verify(dispositivoService, never()).registrarDeteccao(any(Dispositivo.class));
    }

    @Test
    @DisplayName("deve falhar quando a confianca esta fora do intervalo de 0 a 100")
    void deveFalharQuandoConfiancaInvalida() {
        when(dispositivoService.buscarEntidade(1L)).thenReturn(dispositivo);

        assertThatThrownBy(() -> objetoService.registrar(new ObjetoRequest(1L, Cor.VERDE, 150)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("entre 0 e 100");

        assertThatThrownBy(() -> objetoService.registrar(new ObjetoRequest(1L, Cor.VERDE, null)))
                .isInstanceOf(DadosInvalidosException.class);

        verify(objetoRepository, never()).save(any(Objeto.class));
    }

    @Test
    @DisplayName("deve falhar quando a cor nao e informada")
    void deveFalharQuandoCorNaoInformada() {
        when(dispositivoService.buscarEntidade(1L)).thenReturn(dispositivo);

        assertThatThrownBy(() -> objetoService.registrar(new ObjetoRequest(1L, null, 50)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("cor");

        verify(objetoRepository, never()).save(any(Objeto.class));
    }

    @Test
    @DisplayName("deve validar o limite de deteccoes recentes")
    void deveValidarLimiteDeRecentes() {
        assertThatThrownBy(() -> objetoService.listarRecentes(null, 0))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("limite");

        assertThatThrownBy(() -> objetoService.listarRecentes(null, 500))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("deve montar o DTO com os dados do dispositivo")
    void deveConverterParaResposta() {
        Objeto objeto = new Objeto();
        objeto.setId(7L);
        objeto.setDispositivo(dispositivo);
        objeto.setCor(Cor.PRETO);
        objeto.setConfianca(73);
        objeto.setDataHora(LocalDateTime.of(2026, 9, 24, 22, 51, 32));

        ObjetoResponse resposta = ObjetoService.paraResposta(objeto);

        assertThat(resposta.id()).isEqualTo(7L);
        assertThat(resposta.dispositivoId()).isEqualTo(1L);
        assertThat(resposta.dispositivoNome()).isEqualTo("Esteira Principal");
        assertThat(resposta.cor()).isEqualTo(Cor.PRETO);
        assertThat(resposta.confianca()).isEqualTo(73);
        assertThat(resposta.dataHora()).isEqualTo(LocalDateTime.of(2026, 9, 24, 22, 51, 32));
    }

}
