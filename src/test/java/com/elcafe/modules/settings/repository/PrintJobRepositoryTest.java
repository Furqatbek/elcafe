package com.elcafe.modules.settings.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.entity.PrinterSettings;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PrintJobRepositoryTest {

    @Autowired private PrintJobRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private PrinterSettings printer;
    private PrintJob pendingJob;
    private PrintJob sentJob;
    private PrintJob completedJob;
    private PrintJob deadLetterJob;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("R");
        restaurant.setAddress("A");
        restaurant.setActive(true);
        em.persist(restaurant);

        printer = PrinterSettings.builder()
                .restaurant(restaurant)
                .printerName("Kitchen Printer")
                .printerType(PrinterSettings.PrinterType.KITCHEN)
                .connectionType("NETWORK")
                .enabled(true)
                .build();
        em.persist(printer);

        pendingJob = PrintJob.builder()
                .restaurant(restaurant)
                .printer(printer)
                .status(PrintJob.PrintJobStatus.PENDING)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .printData("pending-content")
                .retryCount(0)
                .maxRetries(3)
                .priority(PrintJob.Priority.NORMAL)
                .build();
        em.persist(pendingJob);

        sentJob = PrintJob.builder()
                .restaurant(restaurant)
                .printer(printer)
                .status(PrintJob.PrintJobStatus.SENT)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .printData("sent-content")
                .retryCount(1)
                .maxRetries(3)
                .priority(PrintJob.Priority.NORMAL)
                .build();
        em.persist(sentJob);

        completedJob = PrintJob.builder()
                .restaurant(restaurant)
                .printer(printer)
                .status(PrintJob.PrintJobStatus.COMPLETED)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .printData("completed-content")
                .retryCount(0)
                .maxRetries(3)
                .priority(PrintJob.Priority.NORMAL)
                .build();
        em.persist(completedJob);

        deadLetterJob = PrintJob.builder()
                .restaurant(restaurant)
                .printer(printer)
                .status(PrintJob.PrintJobStatus.DEAD_LETTER)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .printData("dlq-content")
                .retryCount(3)
                .maxRetries(3)
                .dlqReason("Max retries exceeded")
                .priority(PrintJob.Priority.NORMAL)
                .build();
        em.persist(deadLetterJob);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findPendingJobsByRestaurant - returns PENDING jobs with printer fetched")
    void findPendingJobsByRestaurant() {
        List<PrintJob> jobs = repo.findPendingJobsByRestaurant(restaurant.getId());

        assertEquals(1, jobs.size());
        assertEquals(PrintJob.PrintJobStatus.PENDING, jobs.get(0).getStatus());
        assertEquals("pending-content", jobs.get(0).getPrintData());
        // printer should be eagerly fetched via JOIN FETCH
        assertNotNull(jobs.get(0).getPrinter());
        assertEquals("Kitchen Printer", jobs.get(0).getPrinter().getPrinterName());
    }

    @Test
    @DisplayName("findRecentJobs - returns jobs created after given timestamp")
    void findRecentJobs() {
        // Move all jobs to 2 days ago first, then move some back to recent
        em.createNativeQuery("UPDATE print_jobs SET created_at = :ts WHERE restaurant_id = :rid")
                .setParameter("ts", LocalDateTime.now().minusDays(2))
                .setParameter("rid", restaurant.getId())
                .executeUpdate();
        // Move only pendingJob and sentJob to recent (within 1 hour)
        em.createNativeQuery("UPDATE print_jobs SET created_at = :ts WHERE id IN (:id1, :id2)")
                .setParameter("ts", LocalDateTime.now().minusMinutes(5))
                .setParameter("id1", pendingJob.getId())
                .setParameter("id2", sentJob.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<PrintJob> jobs = repo.findRecentJobs(restaurant.getId(), since);

        assertEquals(2, jobs.size());
        assertTrue(jobs.stream().noneMatch(j -> j.getId().equals(completedJob.getId())));
        assertTrue(jobs.stream().noneMatch(j -> j.getId().equals(deadLetterJob.getId())));
    }

    @Test
    @DisplayName("findRetryableJobs - returns PENDING/SENT jobs with retryCount < maxRetries")
    void findRetryableJobs() {
        List<PrintJob> jobs = repo.findRetryableJobs();

        // pendingJob (PENDING, 0 < 3) and sentJob (SENT, 1 < 3) qualify
        assertEquals(2, jobs.size());
        assertTrue(jobs.stream().allMatch(j ->
                j.getStatus() == PrintJob.PrintJobStatus.PENDING ||
                j.getStatus() == PrintJob.PrintJobStatus.SENT));
        assertTrue(jobs.stream().allMatch(j -> j.getRetryCount() < j.getMaxRetries()));
    }

    @Test
    @DisplayName("countDeadLetterJobs - counts only DEAD_LETTER status jobs")
    void countDeadLetterJobs() {
        long count = repo.countDeadLetterJobs(restaurant.getId());

        assertEquals(1, count);
    }

    @Test
    @Transactional
    @DisplayName("deleteOldJobs - deletes completed/failed/cancelled jobs before cutoff")
    void deleteOldJobs() {
        // Move completedJob's createdAt to 30 days ago
        em.createNativeQuery("UPDATE print_jobs SET created_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusDays(30))
                .setParameter("id", completedJob.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        LocalDateTime before = LocalDateTime.now().minusDays(7);
        int deleted = repo.deleteOldJobs(before);

        assertEquals(1, deleted);
        // Verify completedJob is gone
        assertFalse(repo.findById(completedJob.getId()).isPresent());
        // Verify other jobs still exist
        assertTrue(repo.findById(pendingJob.getId()).isPresent());
    }

    @Test
    @Transactional
    @DisplayName("resetStuckJobs - resets SENT jobs to PENDING when stuck past timeout")
    void resetStuckJobs() {
        // Move sentJob's updatedAt to 30 minutes ago
        em.createNativeQuery("UPDATE print_jobs SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusMinutes(30))
                .setParameter("id", sentJob.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        LocalDateTime timeout = LocalDateTime.now().minusMinutes(15);
        int reset = repo.resetStuckJobs(timeout);

        assertEquals(1, reset);
        em.clear();

        PrintJob updated = repo.findById(sentJob.getId()).orElseThrow();
        assertEquals(PrintJob.PrintJobStatus.PENDING, updated.getStatus());
        assertEquals(2, updated.getRetryCount()); // was 1, now incremented to 2
    }
}
