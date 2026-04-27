package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.NcfSequence;

import java.util.Optional;

public interface SequenceService {

    Optional<NcfSequence> findByTipoComprobante(String tipoComprobante);

    NcfSequence saveOrUpdate(NcfSequence ncfSequence);

    String getNextSequence(String tipoComprobante);
}
