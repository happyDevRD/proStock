package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.NcfSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NcfSequenceRepository extends JpaRepository<NcfSequence, Long> {

    Optional<NcfSequence> findByTipoComprobante(String tipoComprobante);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NcfSequence n where n.tipoComprobante = :tipoComprobante")
    Optional<NcfSequence> findByTipoComprobanteForUpdate(@Param("tipoComprobante") String tipoComprobante);
}
