package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.NcfSequence;
import com.happydev.prestockbackend.repository.NcfSequenceRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class SequenceServiceImpl implements SequenceService {

    private final NcfSequenceRepository ncfSequenceRepository;

    public SequenceServiceImpl(NcfSequenceRepository ncfSequenceRepository) {
        this.ncfSequenceRepository = ncfSequenceRepository;
    }

    @Override
    public Optional<NcfSequence> findByTipoComprobante(String tipoComprobante) {
        return ncfSequenceRepository.findByTipoComprobante(
                Objects.requireNonNull(tipoComprobante, "tipoComprobante es obligatorio")
        );
    }

    @Override
    public NcfSequence saveOrUpdate(NcfSequence ncfSequence) {
        Objects.requireNonNull(ncfSequence, "ncfSequence es obligatorio");
        validateSequence(ncfSequence);

        return ncfSequenceRepository.findByTipoComprobante(ncfSequence.getTipoComprobante())
                .map(existingSequence -> {
                    existingSequence.setPrefijo(ncfSequence.getPrefijo());
                    existingSequence.setValorActual(ncfSequence.getValorActual());
                    existingSequence.setValorFinal(ncfSequence.getValorFinal());
                    existingSequence.setFechaVencimiento(ncfSequence.getFechaVencimiento());
                    return ncfSequenceRepository.save(existingSequence);
                })
                .orElseGet(() -> ncfSequenceRepository.save(ncfSequence));
    }

    @Override
    public String getNextSequence(String tipoComprobante) {
        Objects.requireNonNull(tipoComprobante, "tipoComprobante es obligatorio");
        NcfSequence ncfSequence = ncfSequenceRepository.findByTipoComprobanteForUpdate(tipoComprobante)
                .orElseThrow(() -> new IllegalArgumentException("No existe secuencia configurada para tipo " + tipoComprobante));

        if (ncfSequence.getFechaVencimiento().isBefore(LocalDate.now())) {
            throw new IllegalStateException("La secuencia para tipo " + tipoComprobante + " esta vencida");
        }

        long nextValue = ncfSequence.getValorActual() + 1;
        if (nextValue > ncfSequence.getValorFinal()) {
            throw new IllegalStateException("La secuencia para tipo " + tipoComprobante + " alcanzo su valor final");
        }

        ncfSequence.setValorActual(nextValue);
        ncfSequenceRepository.save(ncfSequence);

        return ncfSequence.getPrefijo()
                + ncfSequence.getTipoComprobante()
                + String.format("%010d", nextValue);
    }

    private void validateSequence(NcfSequence ncfSequence) {
        if (ncfSequence.getValorActual() == null || ncfSequence.getValorFinal() == null) {
            throw new IllegalArgumentException("valorActual y valorFinal son obligatorios");
        }

        if (ncfSequence.getValorActual() < 0) {
            throw new IllegalArgumentException("valorActual no puede ser negativo");
        }

        if (ncfSequence.getValorFinal() <= 0) {
            throw new IllegalArgumentException("valorFinal debe ser mayor a cero");
        }

        if (ncfSequence.getValorActual() >= ncfSequence.getValorFinal()) {
            throw new IllegalArgumentException("valorActual debe ser menor que valorFinal");
        }
    }
}
