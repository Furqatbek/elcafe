package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import com.elcafe.modules.order.repository.DailyOrderSequenceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order numbers must stay unique, and they stop being unique in a way nothing else catches.
 *
 * <p>The bug these pin down: {@code daily_order_sequences.date} was rewritten every time a number was
 * taken, from a value that had just been read back. Under a JVM whose default zone had been changed
 * after the connection pool captured a different one, {@code DATE} round-tripped a day short — so
 * each order moved the stored date a day into the past, and on the pass where the lookup finally
 * missed, a second row appeared, the counter restarted at 1 and we handed out a number that already
 * existed. The unique index on {@code orders.order_number} then refused a real customer's order.
 *
 * <p>It took three numbers in one run to surface, and nothing in the suite generated three — the
 * consumer and Telegram paths use a UUID-based number and never touch this table — so it sat
 * unnoticed. Hence a test that deliberately goes well past three.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dailyseqit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
})
class DailyOrderSequenceServiceIntegrationTest {

    @Autowired private DailyOrderSequenceService service;
    @Autowired private DailyOrderSequenceRepository repository;
    @Autowired private EntityManager entityManager;

    private static int suffixOf(String orderNumber) {
        return Integer.parseInt(orderNumber.substring(orderNumber.lastIndexOf('-') + 1));
    }

    @Test
    @DisplayName("numbers keep counting up past the third order instead of restarting")
    void numbersStayUniqueAndMonotonic() {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            numbers.add(service.generateNextOrderNumber());
        }

        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers.stream().map(DailyOrderSequenceServiceIntegrationTest::suffixOf).toList())
                .isSorted();
        assertThat(numbers).allSatisfy(number -> assertThat(number)
                .startsWith("ORD-" + LocalDate.now().toString().replace("-", "") + "-"));
    }

    @Test
    @DisplayName("taking a number never moves the date it is filed under")
    void theStoredDateNeverMoves() {
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 5; i++) {
            service.generateNextOrderNumber();
        }

        // Exactly one row is filed under today, and it is still filed under today. Under the drift
        // there would be none: the row today's orders were counted on has wandered into the past, and
        // its replacement was filed a day short of today as well.
        assertThat(repository.findAll().stream().filter(s -> today.equals(s.getDate())).toList())
                .hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("the increment leaves the date alone and reports whether it found a row")
    void incrementingTouchesOnlyTheCounter() {
        // Transactional because the increment deliberately has no transaction of its own: it belongs
        // to the caller's, so a number cannot be consumed by an order that then rolls back.
        // A day nothing else in this class uses, so the counts are this test's own.
        LocalDate day = LocalDate.of(2027, 2, 14);

        // No row yet: the caller is told so, and decides to create one rather than silently doing
        // nothing. This branch is what stops a missing row being mistaken for a successful bump.
        assertThat(repository.incrementSequence(day)).isZero();

        repository.save(DailyOrderSequence.builder().date(day).currentSequence(0).build());
        entityManager.flush();
        // Drop the managed copy, which is the state the increment is written for: production takes
        // numbers without ever loading this entity, so nothing stale can be flushed back over the
        // statement's work. Keeping it here would test a situation the code never runs in.
        entityManager.clear();

        assertThat(repository.incrementSequence(day)).isEqualTo(1);
        assertThat(repository.incrementSequence(day)).isEqualTo(1);

        assertThat(repository.findSequenceByDate(day)).contains(2);
        assertThat(repository.findByDate(day)).get()
                .satisfies(sequence -> assertThat(sequence.getDate()).isEqualTo(day));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-03-31", "2024-10-27", "2025-01-01", "2026-06-15"})
    @DisplayName("the JVM and the database agree on which day a date is")
    void aDateRoundTripsUnchanged(String iso) {
        // The root cause was a DATE column that came back a day short, because java.sql.Date carries an
        // instant and the two sides of the conversion believed in different zones. Dates chosen across
        // the European DST switches, where an off-by-one hides most easily.
        LocalDate written = LocalDate.parse(iso);

        Long id = repository.save(DailyOrderSequence.builder()
                .date(written).currentSequence(0).build()).getId();
        repository.flush();

        assertThat(repository.findById(id).orElseThrow().getDate()).isEqualTo(written);
        assertThat(repository.findByDate(written)).isPresent();
    }
}
