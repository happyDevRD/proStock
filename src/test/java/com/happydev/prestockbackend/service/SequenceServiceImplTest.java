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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceServiceImplTest {

    @Mock
    private NcfSequenceRepository ncfSequenceRepository;

    @Mock
    private AuditService auditService;

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
        verify(auditService, never()).record(any(), any(), any(), any(), any());
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

    @Test
    void saveOrUpdate_NewSequence_RecordsAudit() {
        NcfSequence input = new NcfSequence();
        input.setTipoComprobante("32");
        input.setPrefijo("B");
        input.setValorActual(0L);
        input.setValorFinal(100L);
        input.setFechaVencimiento(LocalDate.now().plusYears(1));

        when(ncfSequenceRepository.findByTipoComprobante("32")).thenReturn(Optional.empty());
        when(ncfSequenceRepository.save(any(NcfSequence.class))).thenAnswer(invocation -> {
            NcfSequence s = invocation.getArgument(0);
            s.setId(42L);
            return s;
        });

        NcfSequence saved = sequenceService.saveOrUpdate(input);

        assertEquals(42L, saved.getId());
        verify(auditService).record(any(), eq("NCF_SEQUENCE_SAVED"), eq("NcfSequence"), eq(42L), any());
    }
}
