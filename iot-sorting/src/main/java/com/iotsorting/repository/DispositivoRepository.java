package com.iotsorting.repository;

import com.iotsorting.entity.Dispositivo;
import com.iotsorting.enums.StatusDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Acesso aos dispositivos ESP32-CAM.
 */
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    /** Utilizado para evitar cadastro duplicado do mesmo hardware. */
    boolean existsByCodigo(String codigo);

    /** Dispositivos online que ficaram sem comunicacao antes do limite informado. */
    List<Dispositivo> findByStatusAndUltimaConexaoBefore(StatusDispositivo status, LocalDateTime limite);

    /** Lista ordenada pelo nome, usada na dashboard e no endpoint de listagem. */
    List<Dispositivo> findAllByOrderByNomeAsc();

    /** Dispositivos em um determinado status (ex.: todos os ONLINE). */
    List<Dispositivo> findByStatus(StatusDispositivo status);

}
