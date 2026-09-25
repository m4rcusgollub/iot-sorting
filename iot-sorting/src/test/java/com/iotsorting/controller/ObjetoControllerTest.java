package com.iotsorting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotsorting.dto.ObjetoRequest;
import com.iotsorting.dto.ObjetoResponse;
import com.iotsorting.enums.Cor;
import com.iotsorting.exception.RecursoNaoEncontradoException;
import com.iotsorting.service.ObjetoService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da API de deteccoes (camada web + validacao + tratamento de erros).
 */
@WebMvcTest(ObjetoController.class)
class ObjetoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ObjetoService objetoService;

    @Test
    @DisplayName("POST /api/objetos deve retornar 201 com a deteccao criada")
    void deveRegistrarDeteccao() throws Exception {
        ObjetoResponse resposta = new ObjetoResponse(15L, 1L, "Esteira Principal", Cor.VERMELHO, 92,
                LocalDateTime.of(2026, 9, 24, 22, 51, 32));
        when(objetoService.registrar(any(ObjetoRequest.class))).thenReturn(resposta);

        mockMvc.perform(post("/api/objetos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ObjetoRequest(1L, Cor.VERMELHO, 92))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/objetos/15")))
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.dispositivoId").value(1))
                .andExpect(jsonPath("$.dispositivoNome").value("Esteira Principal"))
                .andExpect(jsonPath("$.cor").value("VERMELHO"))
                .andExpect(jsonPath("$.confianca").value(92))
                .andExpect(jsonPath("$.dataHora").value("2026-09-24T22:51:32"));
    }

    @Test
    @DisplayName("POST /api/objetos com confianca fora do intervalo deve retornar 400 em JSON")
    void deveRecusarConfiancaInvalida() throws Exception {
        mockMvc.perform(post("/api/objetos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispositivoId\":1,\"cor\":\"VERMELHO\",\"confianca\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A confiança deve estar entre 0 e 100"))
                .andExpect(jsonPath("$.campos.confianca").value("A confiança deve estar entre 0 e 100"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/objetos sem os campos obrigatorios deve retornar 400")
    void deveRecusarRequisicaoIncompleta() throws Exception {
        mockMvc.perform(post("/api/objetos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos.dispositivoId").value("O identificador do dispositivo é obrigatório"))
                .andExpect(jsonPath("$.campos.cor").value("A cor é obrigatória"))
                .andExpect(jsonPath("$.campos.confianca").value("A confiança é obrigatória"));
    }

    @Test
    @DisplayName("POST /api/objetos com cor inexistente deve retornar 400 com as cores aceitas")
    void deveRecusarCorInvalida() throws Exception {
        mockMvc.perform(post("/api/objetos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispositivoId\":1,\"cor\":\"ROXO\",\"confianca\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("VERMELHO")));
    }

    @Test
    @DisplayName("GET /api/objetos deve listar as deteccoes")
    void deveListarObjetos() throws Exception {
        when(objetoService.listar(null)).thenReturn(List.of(
                new ObjetoResponse(2L, 1L, "Esteira Principal", Cor.AZUL, 87, LocalDateTime.now()),
                new ObjetoResponse(1L, 1L, "Esteira Principal", Cor.VERDE, 95, LocalDateTime.now())));

        mockMvc.perform(get("/api/objetos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cor").value("AZUL"))
                .andExpect(jsonPath("$[1].cor").value("VERDE"));
    }

    @Test
    @DisplayName("GET /api/objetos?cor=VERMELHO deve filtrar por cor")
    void deveFiltrarPorCor() throws Exception {
        when(objetoService.listar(Cor.VERMELHO)).thenReturn(List.of(
                new ObjetoResponse(3L, 1L, "Esteira Principal", Cor.VERMELHO, 92, LocalDateTime.now())));

        mockMvc.perform(get("/api/objetos").param("cor", "VERMELHO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cor").value("VERMELHO"));
    }

    @Test
    @DisplayName("GET /api/objetos/recentes deve usar o limite informado")
    void deveListarRecentes() throws Exception {
        when(objetoService.listarRecentes(eq(1L), eq(3))).thenReturn(List.of(
                new ObjetoResponse(4L, 1L, "Esteira Principal", Cor.PRETO, 60, LocalDateTime.now())));

        mockMvc.perform(get("/api/objetos/recentes").param("limite", "3").param("dispositivoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cor").value("PRETO"));
    }

    @Test
    @DisplayName("GET /api/objetos/{id} inexistente deve retornar 404 em JSON")
    void deveRetornar404QuandoNaoEncontrado() throws Exception {
        when(objetoService.buscarPorId(999L))
                .thenThrow(RecursoNaoEncontradoException.de("Objeto", 999L));

        mockMvc.perform(get("/api/objetos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Objeto não encontrado: identificador 999"));
    }

}
