package com.iotsorting.entity;

import com.iotsorting.enums.StatusDispositivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Dispositivo ESP32-CAM instalado na esteira.
 *
 * <p>O campo {@code codigo} identifica fisicamente o hardware (ex.: {@code ESP32CAM-001})
 * e e unico no banco de dados.</p>
 */
@Entity
@Table(name = "dispositivo", uniqueConstraints = @UniqueConstraint(name = "uk_dispositivo_codigo", columnNames = "codigo"))
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(length = 45)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusDispositivo status = StatusDispositivo.OFFLINE;

    @Column
    private LocalDateTime ultimaConexao;

    /**
     * Estado do rele da esteira.
     *
     * <p>Na V1 este valor e atualizado pelo heartbeat (campo opcional {@code esteiraLigada})
     * e tambem recebe {@code true} quando o dispositivo envia uma deteccao, pois se houve
     * objeto na esteira o motor estava em operacao. A estrutura ja esta pronta para que o
     * firmware informe o estado real do rele no futuro.</p>
     */
    @Column
    private Boolean esteiraLigada = Boolean.FALSE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public StatusDispositivo getStatus() {
        return status;
    }

    public void setStatus(StatusDispositivo status) {
        this.status = status;
    }

    public LocalDateTime getUltimaConexao() {
        return ultimaConexao;
    }

    public void setUltimaConexao(LocalDateTime ultimaConexao) {
        this.ultimaConexao = ultimaConexao;
    }

    public Boolean getEsteiraLigada() {
        return esteiraLigada;
    }

    public void setEsteiraLigada(Boolean esteiraLigada) {
        this.esteiraLigada = esteiraLigada;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

}
