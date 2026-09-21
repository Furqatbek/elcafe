package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import com.elcafe.modules.order.repository.DailyOrderSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyOrderSequenceServiceTest {

    @Mock private DailyOrderSequenceRepository dailyOrderSequenceRepository;
    @InjectMocks private DailyOrderSequenceService sequenceService;

    @Test
    @DisplayName("Generate order number — creates new sequence for new day")
    void generateOrderNumber_newDay_createsSequence() {
        when(dailyOrderSequenceRepository.findByDateWithLock(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        String orderNumber = sequenceService.generateNextOrderNumber();

        assertTrue(orderNumber.startsWith("ORD-"));
        assertTrue(orderNumber.endsWith("-0001"));
    }

    @Test
    @DisplayName("Generate order number — increments existing sequence")
    void generateOrderNumber_existingDay_increments() {
        DailyOrderSequence existing = DailyOrderSequence.builder()
                .date(LocalDate.now()).currentSequence(5).build();
        when(dailyOrderSequenceRepository.findByDateWithLock(any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        String orderNumber = sequenceService.generateNextOrderNumber();

        assertTrue(orderNumber.endsWith("-0006"));
    }

    @Test
    @DisplayName("Generate order number — format is ORD-YYYYMMDD-XXXX")
    void generateOrderNumber_correctFormat() {
        DailyOrderSequence existing = DailyOrderSequence.builder()
                .date(LocalDate.now()).currentSequence(0).build();
        when(dailyOrderSequenceRepository.findByDateWithLock(any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        String orderNumber = sequenceService.generateNextOrderNumber();

        String todayStr = LocalDate.now().toString().replace("-", "");
        assertEquals("ORD-" + todayStr + "-0001", orderNumber);
    }

    @Test
    @DisplayName("Cleanup — deletes sequences older than 7 days")
    void cleanup_deletesOldSequences() {
        sequenceService.cleanupOldSequences();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(dailyOrderSequenceRepository).deleteByDateBefore(captor.capture());
        assertEquals(LocalDate.now().minusDays(7), captor.getValue());
    }

    @Test
    @DisplayName("Generate order number — saves updated sequence")
    void generateOrderNumber_savesSequence() {
        DailyOrderSequence existing = DailyOrderSequence.builder()
                .date(LocalDate.now()).currentSequence(10).build();
        when(dailyOrderSequenceRepository.findByDateWithLock(any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        sequenceService.generateNextOrderNumber();

        ArgumentCaptor<DailyOrderSequence> captor = ArgumentCaptor.forClass(DailyOrderSequence.class);
        verify(dailyOrderSequenceRepository).save(captor.capture());
        assertEquals(11, captor.getValue().getCurrentSequence());
    }
}
