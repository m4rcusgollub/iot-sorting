package com.iotsorting.repository;

import com.iotsorting.entity.Dispositivo;
import com.iotsorting.entity.Objeto;
import com.iotsorting.enums.Cor;
import com.iotsorting.enums.StatusDispositivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes das consultas derivadas utilizadas pela dashboard e pelo historico.
 *
 * <p>Executa com banco SQLite em arquivo (descartavel, dentro de target/),
 * sem depender de nenhum banco externo.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-iot-sorting-repo.db?date_class=TEXT",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ObjetoRepositoryTest {

    @Autowired
    private ObjetoRepository objetoRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    private Dispositivo dispositivo;

    private LocalDateTime hoje;

    @BeforeEach
    void preparar() {
        dispositivo = new Dispositivo();
        dispositivo.setNome("Esteira Principal");
        dispositivo.setCodigo("ESP32CAM-001");
        dispositivo.setStatus(StatusDispositivo.ONLINE);
        dispositivo.setEsteiraLigada(Boolean.TRUE);
        dispositivo = dispositivoRepository.save(dispositivo);

        hoje = LocalDate.now().atStartOfDay().plusHours(10);
    }

    @Test
    @DisplayName("deve contar as deteccoes do dia por cor e por dispositivo")
    void deveContarDeteccoesDoDia() {
        salvarObjeto(Cor.VERMELHO, 92, hoje);
        salvarObjeto(Cor.VERMELHO, 88, hoje.plusMinutes(5));
        salvarObjeto(Cor.AZUL, 75, hoje.plusMinutes(10));
        salvarObjeto(Cor.VERMELHO, 60, hoje.minusDays(1)); // ontem: nao deve entrar na contagem

        LocalDateTime inicio = LocalDate.now().atStartOfDay();
        LocalDateTime fim = inicio.plusDays(1);

        assertThat(objetoRepository.countByDataHoraGreaterThanEqualAndDataHoraLessThan(inicio, fim)).isEqualTo(3);
        assertThat(objetoRepository.countByCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(Cor.VERMELHO, inicio, fim))
                .isEqualTo(2);
        assertThat(objetoRepository.countByCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(Cor.VERDE, inicio, fim))
                .isZero();
        assertThat(objetoRepository.countByDispositivoIdAndDataHoraGreaterThanEqualAndDataHoraLessThan(
                dispositivo.getId(), inicio, fim)).isEqualTo(3);
        assertThat(objetoRepository.countByDispositivoIdAndCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(
                dispositivo.getId(), Cor.AZUL, inicio, fim)).isEqualTo(1);
    }

    @Test
    @DisplayName("deve listar as deteccoes mais recentes em ordem decrescente")
    void deveListarRecentes() {
        salvarObjeto(Cor.VERMELHO, 92, hoje);
        salvarObjeto(Cor.AZUL, 87, hoje.plusMinutes(1));
        salvarObjeto(Cor.VERDE, 95, hoje.plusMinutes(2));

        List<Objeto> recentes = objetoRepository.findAllByOrderByDataHoraDesc(PageRequest.of(0, 2));

        assertThat(recentes).hasSize(2);
        assertThat(recentes.get(0).getCor()).isEqualTo(Cor.VERDE);
        assertThat(recentes.get(1).getCor()).isEqualTo(Cor.AZUL);

        assertThat(objetoRepository.findFirstByOrderByDataHoraDesc())
                .get()
                .extracting(Objeto::getCor)
                .isEqualTo(Cor.VERDE);

        assertThat(objetoRepository.findAllByDispositivoIdOrderByDataHoraDesc(dispositivo.getId(), PageRequest.of(0, 10)))
                .hasSize(3);
        assertThat(objetoRepository.findByCorOrderByDataHoraDesc(Cor.AZUL)).hasSize(1);
    }

    private void salvarObjeto(Cor cor, int confianca, LocalDateTime dataHora) {
        Objeto objeto = new Objeto();
        objeto.setDispositivo(dispositivo);
        objeto.setCor(cor);
        objeto.setConfianca(confianca);
        objeto.setDataHora(dataHora);
        objetoRepository.save(objeto);
    }

}
