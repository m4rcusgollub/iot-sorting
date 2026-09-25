package com.iotsorting;

import com.iotsorting.config.InicializacaoDados;
import com.iotsorting.enums.Cor;
import com.iotsorting.repository.DispositivoRepository;
import com.iotsorting.repository.ObjetoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integracao: sobe a aplicacao com banco SQLite em arquivo (descartavel,
 * dentro de target/) e percorre o fluxo completo (cadastro de dispositivo,
 * deteccao e dashboard).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-iot-sorting-web.db?date_class=TEXT",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "iot.sorting.monitor.intervalo-verificacao-ms=600000"
})
@AutoConfigureMockMvc
class IoTSortingApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private ObjetoRepository objetoRepository;

    @Autowired
    private InicializacaoDados inicializacaoDados;

    @Test
    @DisplayName("a aplicacao inicia e cria o dispositivo padrao sem duplicar registros")
    void deveCriarDispositivoPadraoSemDuplicar() throws Exception {
        inicializacaoDados.run();

        assertThat(dispositivoRepository.existsByCodigo("ESP32CAM-001")).isTrue();
        assertThat(dispositivoRepository.findAllByOrderByNomeAsc()
                .stream()
                .filter(dispositivo -> "ESP32CAM-001".equals(dispositivo.getCodigo()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("deve registrar deteccao e refletir os dados na dashboard")
    void deveRegistrarDeteccaoERefletirNaDashboard() throws Exception {
        Long dispositivoId = dispositivoRepository.findAllByOrderByNomeAsc().get(0).getId();

        mockMvc.perform(post("/api/objetos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dispositivoId\":" + dispositivoId + ",\"cor\":\"VERMELHO\",\"confianca\":92}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cor").value(Cor.VERMELHO.toString()));

        assertThat(objetoRepository.count()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/dashboard").param("dispositivoId", String.valueOf(dispositivoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetosHoje").isNumber())
                .andExpect(jsonPath("$.vermelhos").isNumber())
                .andExpect(jsonPath("$.dispositivoOnline").value(true))
                .andExpect(jsonPath("$.esteiraLigada").value(true))
                .andExpect(jsonPath("$.dispositivo.codigo").value("ESP32CAM-001"))
                .andExpect(jsonPath("$.ultimaDeteccao").exists());

        mockMvc.perform(get("/api/objetos/recentes").param("limite", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dispositivoNome").value("Esteira Principal"));
    }

    @Test
    @DisplayName("deve responder as paginas do dashboard")
    void deveServirArquivosEstaticos() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/dashboard.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/vendor/chart.umd.min.js"))
                .andExpect(status().isOk());
    }

}
