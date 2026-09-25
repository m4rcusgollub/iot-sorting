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
 * <p>Na V1 o dashboard e servido pela propria aplicacao Spring Boot, portanto CORS nao
 * e necessario e nenhuma origem e liberada por padrao. Quando um frontend separado for
 * criado (React, Vue, etc.), basta informar as origens em
 * {@code iot.sorting.cors.origens-permitidas} (separadas por virgula), por exemplo:</p>
 *
 * <pre>
 * iot.sorting.cors.origens-permitidas=http://localhost:3000,http://192.168.1.50
 * </pre>
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
                .allowedOrigins(origensPermitidas.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

}
