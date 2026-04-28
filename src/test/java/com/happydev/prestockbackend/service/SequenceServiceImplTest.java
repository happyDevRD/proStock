package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.NcfSequence;
import com.happydev.prestockbackend.repository.NcfSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SequenceServiceImplTest {

    @Mock
    private NcfSequenceRepository ncfSequenceRepository;

    @InjectMocks
    private SequenceServiceImpl sequenceService;

    private NcfSequence sequence;

    @BeforeEach
    void setUp() {
        sequence = new NcfSequence();
        sequence.setId(1L);
        sequence.setTipoComprobante("31");
        sequence.setPrefijo("E");
        sequence.setValorActual(0L);
        sequence.setValorFinal(9999999999L);
        sequence.setFechaVencimiento(LocalDate.now().plusMonths(6));
    }

    @Test
    void getNextSequence_ValidSequence_ReturnsFormattedNcfAndIncrementsCounter() {
        when(ncfSequenceRepository.findByTipoComprobanteForUpdate("31")).thenReturn(Optional.of(sequence));
        when(ncfSequenceRepository.save(any(NcfSequence.class))).thenReturn(sequence);

        String nextNcf = sequenceService.getNextSequence("31");

        assertEquals("E310000000001", nextNcf);
        assertEquals(1L, sequence.getValorActual());
        verify(ncfSequenceRepository).save(sequence);
    }

    @Test
    void getNextSequence_ExpiredSequence_ThrowsIllegalStateException() {
        sequence.setFechaVencimiento(LocalDate.now().minusDays(1));
        when(ncfSequenceRepository.findByTipoComprobanteForUpdate("31")).thenReturn(Optional.of(sequence));

        assertThrows(IllegalStateException.class, () -> sequenceService.getNextSequence("31"));
    }

    @Test
    void getNextSequence_ReachesFinalValue_ThrowsIllegalStateException() {
        sequence.setValorActual(sequence.getValorFinal());
        when(ncfSequenceRepository.findByTipoComprobanteForUpdate("31")).thenReturn(Optional.of(sequence));

        assertThrows(IllegalStateException.class, () -> sequenceService.getNextSequence("31"));
    }
}
