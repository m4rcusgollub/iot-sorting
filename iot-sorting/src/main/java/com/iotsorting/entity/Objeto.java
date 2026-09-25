package com.iotsorting.entity;

import com.iotsorting.enums.Cor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Deteccao de um objeto na esteira.
 *
 * <p>Cada registro pertence a um {@link Dispositivo} e guarda apenas o resultado da
 * analise feita pelo ESP32-CAM (cor e confianca). A imagem nao e enviada nem
 * armazenada nesta versao.</p>
 */
@Entity
@Table(name = "objeto", indexes = @Index(name = "idx_objeto_data_hora", columnList = "data_hora"))
public class Objeto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    private Dispositivo dispositivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Cor cor;

    /** Confianca percentual informada pelo ESP32-CAM (0 a 100). */
    @Column(nullable = false)
    private Integer confianca;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dispositivo getDispositivo() {
        return dispositivo;
    }

    public void setDispositivo(Dispositivo dispositivo) {
        this.dispositivo = dispositivo;
    }

    public Cor getCor() {
        return cor;
    }

    public void setCor(Cor cor) {
        this.cor = cor;
    }

    public Integer getConfianca() {
        return confianca;
    }

    public void setConfianca(Integer confianca) {
        this.confianca = confianca;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

}
