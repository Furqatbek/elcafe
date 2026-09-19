package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.DailyOrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyOrderSequenceRepository extends JpaRepository<DailyOrderSequence, Long> {

    /**
     * Claim the next number for a date, as one statement that writes one column.
     *
     * <p>Deliberately not "load the row, increment the field, save it". A JPA update writes every
     * column, including {@code date} — and {@code date} is both this table's key and the one value
     * that must never be rewritten, because rewriting it from a value that came back off by a day
     * turns a single bad read into permanent drift. See {@code DailyOrderSequenceService}.
     *
     * <p>No {@code clearAutomatically}: this runs inside the order's own transaction, and clearing the
     * persistence context there would detach the half-built order along with it. Nothing needs it —
     * the read below is a scalar projection, which always goes to the database.
     *
     * @return 1 if a row for that date existed and was bumped, 0 if there was none
     */
    @Modifying
    @Query("UPDATE DailyOrderSequence d SET d.currentSequence = d.currentSequence + 1 "
            + "WHERE d.date = :date")
    int incrementSequence(@Param("date") LocalDate date);

    /** The counter alone, read back after {@link #incrementSequence}. */
    @Query("SELECT d.currentSequence FROM DailyOrderSequence d WHERE d.date = :date")
    Optional<Integer> findSequenceByDate(@Param("date") LocalDate date);

    // No lock-and-load counterpart here on purpose. There used to be one, and every caller of it
    // ended the same way — load the row, change the counter, save the entity — which is precisely
    // the write that carried the date column along with it. The statement above is the only way
    // this counter moves.

    Optional<DailyOrderSequence> findByDate(LocalDate date);

    void deleteByDateBefore(LocalDate date);
}
