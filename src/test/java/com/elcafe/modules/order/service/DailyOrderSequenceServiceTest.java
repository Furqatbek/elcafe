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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyOrderSequenceServiceTest {

    @Mock private DailyOrderSequenceRepository dailyOrderSequenceRepository;
    @InjectMocks private DailyOrderSequenceService sequenceService;

    @Test
    @DisplayName("Generate order number — creates new sequence for new day")
    void generateOrderNumber_newDay_createsSequence() {
        when(dailyOrderSequenceRepository.incrementSequence(any(LocalDate.class))).thenReturn(0);
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        String orderNumber = sequenceService.generateNextOrderNumber();

        assertTrue(orderNumber.startsWith("ORD-"));
        assertTrue(orderNumber.endsWith("-0001"));
    }

    @Test
    @DisplayName("Generate order number — increments existing sequence")
    void generateOrderNumber_existingDay_increments() {
        when(dailyOrderSequenceRepository.incrementSequence(any(LocalDate.class))).thenReturn(1);
        when(dailyOrderSequenceRepository.findSequenceByDate(any(LocalDate.class)))
                .thenReturn(Optional.of(6));

        String orderNumber = sequenceService.generateNextOrderNumber();

        assertTrue(orderNumber.endsWith("-0006"));
    }

    @Test
    @DisplayName("Generate order number — format is ORD-YYYYMMDD-XXXX")
    void generateOrderNumber_correctFormat() {
        when(dailyOrderSequenceRepository.incrementSequence(any(LocalDate.class))).thenReturn(1);
        when(dailyOrderSequenceRepository.findSequenceByDate(any(LocalDate.class)))
                .thenReturn(Optional.of(1));

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
    @DisplayName("Generate order number — bumps an existing day without writing the row back")
    void generateOrderNumber_doesNotRewriteTheRow() {
        when(dailyOrderSequenceRepository.incrementSequence(any(LocalDate.class))).thenReturn(1);
        when(dailyOrderSequenceRepository.findSequenceByDate(any(LocalDate.class)))
                .thenReturn(Optional.of(11));

        sequenceService.generateNextOrderNumber();

        // The counter moves by statement, never by saving the entity. Saving it would carry the date
        // column along, and a date written back from a value just read is how the numbering drifted
        // off its day and started handing out numbers that already existed.
        verify(dailyOrderSequenceRepository).incrementSequence(LocalDate.now());
        verify(dailyOrderSequenceRepository, never()).save(any(DailyOrderSequence.class));
    }

    @Test
    @DisplayName("Generate order number — the day's first order starts the counter at one")
    void generateOrderNumber_firstOfDayStartsAtOne() {
        when(dailyOrderSequenceRepository.incrementSequence(any(LocalDate.class))).thenReturn(0);
        when(dailyOrderSequenceRepository.save(any(DailyOrderSequence.class)))
                .thenAnswer(i -> i.getArgument(0));

        sequenceService.generateNextOrderNumber();

        ArgumentCaptor<DailyOrderSequence> captor = ArgumentCaptor.forClass(DailyOrderSequence.class);
        verify(dailyOrderSequenceRepository).save(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getDate());
        // Created at 1, not 0-then-incremented: the row and the number it hands out are one write.
        assertEquals(1, captor.getValue().getCurrentSequence());
        verify(dailyOrderSequenceRepository, never()).findSequenceByDate(any(LocalDate.class));
    }
}
