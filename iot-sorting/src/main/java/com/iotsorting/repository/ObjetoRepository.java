package com.iotsorting.repository;

import com.iotsorting.entity.Objeto;
import com.iotsorting.enums.Cor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acesso as deteccoes de objetos.
 *
 * <p>Todas as consultas sao derivadas do nome do metodo (Spring Data JPA), sem SQL
 * escrito manualmente. O intervalo de datas e sempre tratado como
 * {@code dataHora >= inicio} e {@code dataHora < fim}.</p>
 */
public interface ObjetoRepository extends JpaRepository<Objeto, Long> {

    // ------------------------------------------------------------------
    // Consultas de listagem
    // ------------------------------------------------------------------

    /** Historico completo, do mais recente para o mais antigo. */
    List<Objeto> findAllByOrderByDataHoraDesc();

    /** Historico filtrado por cor. */
    List<Objeto> findByCorOrderByDataHoraDesc(Cor cor);

    /** Deteccoes mais recentes (a quantidade e definida pelo {@link Pageable}). */
    List<Objeto> findAllByOrderByDataHoraDesc(Pageable pageable);

    /** Deteccoes mais recentes de um dispositivo especifico. */
    List<Objeto> findAllByDispositivoIdOrderByDataHoraDesc(Long dispositivoId, Pageable pageable);

    /** Ultima deteccao registrada. */
    Optional<Objeto> findFirstByOrderByDataHoraDesc();

    /** Ultima deteccao de um dispositivo especifico. */
    Optional<Objeto> findFirstByDispositivoIdOrderByDataHoraDesc(Long dispositivoId);

    // ------------------------------------------------------------------
    // Contagens por periodo (utilizadas pela dashboard)
    // ------------------------------------------------------------------

    long countByDataHoraGreaterThanEqualAndDataHoraLessThan(LocalDateTime inicio, LocalDateTime fim);

    long countByCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(Cor cor, LocalDateTime inicio, LocalDateTime fim);

    long countByDispositivoIdAndDataHoraGreaterThanEqualAndDataHoraLessThan(Long dispositivoId,
                                                                            LocalDateTime inicio,
                                                                            LocalDateTime fim);

    long countByDispositivoIdAndCorAndDataHoraGreaterThanEqualAndDataHoraLessThan(Long dispositivoId,
                                                                                  Cor cor,
                                                                                  LocalDateTime inicio,
                                                                                  LocalDateTime fim);

}
