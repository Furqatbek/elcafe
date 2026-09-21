package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import com.elcafe.modules.order.repository.DailyOrderSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyOrderSequenceService {

    private final DailyOrderSequenceRepository dailyOrderSequenceRepository;

    @Transactional
    public String generateNextOrderNumber() {
        LocalDate today = LocalDate.now();

        // Get or create today's sequence with pessimistic locking to prevent race conditions
        DailyOrderSequence sequence = dailyOrderSequenceRepository.findByDateWithLock(today)
                .orElseGet(() -> {
                    log.info("Creating new daily order sequence for date: {}", today);
                    DailyOrderSequence newSequence = DailyOrderSequence.builder()
                            .date(today)
                            .currentSequence(0)
                            .build();
                    return dailyOrderSequenceRepository.save(newSequence);
                });

        // Increment the sequence
        int nextSequence = sequence.getCurrentSequence() + 1;
        sequence.setCurrentSequence(nextSequence);
        dailyOrderSequenceRepository.save(sequence);

        // Format: ORD-YYYYMMDD-XXXX (e.g., ORD-20251220-0001)
        String orderNumber = String.format("ORD-%s-%04d",
                today.toString().replace("-", ""),
                nextSequence);

        log.debug("Generated order number: {} for date: {}", orderNumber, today);
        return orderNumber;
    }

    @Transactional
    public void cleanupOldSequences() {
        // Keep last 7 days of sequences for reference
        LocalDate cutoffDate = LocalDate.now().minusDays(7);
        dailyOrderSequenceRepository.deleteByDateBefore(cutoffDate);
        log.info("Cleaned up order sequences before date: {}", cutoffDate);
    }
}
