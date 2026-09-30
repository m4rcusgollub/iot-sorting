package com.iotsorting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Configuracao de CORS.
 *
 * <p>O dashboard e o simulador (mobile) sao servidos pela propria aplicacao Spring Boot,
 * portanto CORS nao e necessario e nenhuma origem e liberada por padrao. A configuracao so
 * e usada quando a pagina que consome a API roda em outra origem - por exemplo o simulador
 * aberto em {@code http://localhost:8080} (ou em um celular da rede local) consumindo o
 * backend publicado no Render.</p>
 *
 * <p>As origens sao informadas em {@code iot.sorting.cors.origens-permitidas} (separadas por
 * virgula) e aceitam curinga no host e na porta, o que cobre o IP dinamico do notebook
 * durante a demonstracao:</p>
 *
 * <pre>
 * iot.sorting.cors.origens-permitidas=http://localhost:8080,http://192.168.*.*:8080
 * </pre>
 *
 * <p>No Render a mesma propriedade pode ser definida pela variavel de ambiente
 * {@code IOT_SORTING_CORS_ORIGENS_PERMITIDAS}. Nenhuma outra origem e liberada.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> origensPermitidas;

    public CorsConfig(@Value("${iot.sorting.cors.origens-permitidas:}") String origensPermitidas) {
        this.origensPermitidas = Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isBlank())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origensPermitidas.isEmpty()) {
            // Mesma origem: nenhuma configuracao de CORS e necessaria.
            return;
        }

        registry.addMapping("/api/**")
                // Padroes sao aceitos no host/porta (ex.: http://192.168.*.*:8080) e tambem
                // origens exatas (ex.: https://iot-sorting.onrender.com).
                .allowedOriginPatterns(origensPermitidas.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

}
