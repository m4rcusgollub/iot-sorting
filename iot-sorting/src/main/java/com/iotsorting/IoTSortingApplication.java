package com.iotsorting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IoT Sorting - Sistema de monitoramento e classificacao de objetos em uma esteira.
 *
 * <p>Aplicacao Spring Boot que recebe as deteccoes do ESP32-CAM via API REST, armazena
 * os resultados em um banco SQLite local e disponibiliza o dashboard em {@code http://localhost:8080}.</p>
 */
@SpringBootApplication
@EnableScheduling // mantem o status dos dispositivos atualizado (MonitorDispositivosService)
public class IoTSortingApplication {

    public static void main(String[] args) {
        SpringApplication.run(IoTSortingApplication.class, args);
    }

}
