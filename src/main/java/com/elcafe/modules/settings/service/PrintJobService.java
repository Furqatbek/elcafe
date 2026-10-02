package com.elcafe.modules.settings.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.kitchen.entity.KitchenStation;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.elcafe.modules.settings.websocket.PrintAgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintJobService {

    private final PrintJobRepository printJobRepository;
    private final PrintAgentWebSocketHandler printAgentWebSocketHandler;

    /**
     * How long an unprinted ticket stays worth printing.
     *
     * <p>Past this it is retired rather than queued. A kitchen ticket from yesterday's service is not
     * something anyone wants emerging from a printer this morning, and leaving it pending is what lets
     * a venue with no agent grow the table without limit.
     */
    @Value("${app.printing.unprinted-expire-after-hours:24}")
    private long unprintedExpireAfterHours;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Create a print job for a kitchen station ticket
     */
    @Transactional
    public PrintJob createStationPrintJob(Order order, KitchenStation station, List<OrderItem> items, PrinterSettings printer) {
        String printData = generateStationTicketData(order, station, items);

        PrintJob job = PrintJob.builder()
                .restaurant(order.getRestaurant())
                .printer(printer)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .stationName(station != null ? station.getName() : "Default")
                .printData(printData)
                .status(PrintJob.PrintJobStatus.PENDING)
                .build();

        job = printJobRepository.save(job);
        log.info("Created print job {} for order {} station {}", job.getId(), order.getOrderNumber(),
                station != null ? station.getName() : "Default");

        // Notify connected print agents
        notifyPrintAgents(order.getRestaurant().getId());

        return job;
    }

    /**
     * Create a print job for a legacy kitchen order (no station routing)
     */
    @Transactional
    public PrintJob createLegacyPrintJob(Order order, PrinterSettings printer) {
        String printData = generateLegacyTicketData(order);

        PrintJob job = PrintJob.builder()
                .restaurant(order.getRestaurant())
                .printer(printer)
                .jobType(PrinterSettings.PrinterType.KITCHEN)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .stationName("Kitchen")
                .printData(printData)
                .status(PrintJob.PrintJobStatus.PENDING)
                .build();

        job = printJobRepository.save(job);
        log.info("Created legacy print job {} for order {}", job.getId(), order.getOrderNumber());

        // Notify connected print agents
        notifyPrintAgents(order.getRestaurant().getId());

        return job;
    }

    /**
     * Get pending print jobs for a restaurant
     */
    public List<PrintJob> getPendingJobs(Long restaurantId) {
        return printJobRepository.findPendingJobsByRestaurant(restaurantId);
    }

    /**
     * Mark job as sent to print agent
     */
    @Transactional
    public void markJobSent(Long jobId, String agentId) {
        printJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(PrintJob.PrintJobStatus.SENT);
            job.setAgentId(agentId);
            printJobRepository.save(job);
            log.info("Print job {} marked as sent to agent {}", jobId, agentId);
        });
    }

    /**
     * Mark job as completed
     */
    @Transactional
    public void markJobCompleted(Long jobId) {
        printJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(PrintJob.PrintJobStatus.COMPLETED);
            job.setProcessedAt(LocalDateTime.now());
            printJobRepository.save(job);
            log.info("Print job {} completed", jobId);
        });
    }

    /** The restaurant a job belongs to (or null if the job is gone) — for the WS ownership check. */
    @Transactional(readOnly = true)
    public Long restaurantIdOfJob(Long jobId) {
        return printJobRepository.findById(jobId).map(j -> j.getRestaurant().getId()).orElse(null);
    }

    /**
     * Mark job as failed with exponential backoff retry.
     */
    @Transactional
    public void markJobFailed(Long jobId, String errorMessage) {
        printJobRepository.findById(jobId).ifPresent(job -> {
            job.incrementRetry();
            job.setErrorMessage(errorMessage);

            if (job.canRetry()) {
                job.setStatus(PrintJob.PrintJobStatus.RETRYING);
                log.info("Print job {} failed, scheduling retry {}/{} at {}",
                        jobId, job.getRetryCount(), job.getMaxRetries(), job.getNextRetryAt());
            } else {
                // Move to dead-letter queue
                job.moveToDlq("Max retries exceeded: " + errorMessage);
                log.error("Print job {} moved to DLQ after {} retries: {}",
                        jobId, job.getRetryCount(), errorMessage);

                // Notify admin of DLQ item
                notifyAdminOfDlqJob(job);
            }

            printJobRepository.save(job);
        });
    }

    /**
     * Get jobs ready for retry (past their backoff time).
     */
    public List<PrintJob> getJobsReadyForRetry(Long restaurantId) {
        return printJobRepository.findJobsReadyForRetry(restaurantId, LocalDateTime.now());
    }

    /**
     * Get jobs in dead-letter queue.
     */
    public List<PrintJob> getDeadLetterJobs(Long restaurantId) {
        return printJobRepository.findByRestaurant_IdAndStatus(restaurantId, PrintJob.PrintJobStatus.DEAD_LETTER);
    }

    /**
     * Retry a job from the dead-letter queue manually.
     */
    @Transactional
    public PrintJob retryDlqJob(Long jobId) {
        PrintJob job = printJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print job not found: " + jobId));

        if (!job.isInDlq()) {
            throw new BadRequestException("Job is not in dead-letter queue");
        }

        // Reset and retry
        job.setStatus(PrintJob.PrintJobStatus.PENDING);
        job.setRetryCount(0);
        job.setMaxRetries(3);
        job.setNextRetryAt(null);
        job.setMovedToDlqAt(null);
        job.setDlqReason(null);
        job.setErrorMessage(null);

        PrintJob savedJob = printJobRepository.save(job);
        log.info("Print job {} moved from DLQ back to pending", jobId);

        notifyPrintAgents(job.getRestaurant().getId());

        return savedJob;
    }

    /**
     * Permanently dismiss a job from the dead-letter queue.
     */
    @Transactional
    public void dismissDlqJob(Long jobId, String reason) {
        printJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(PrintJob.PrintJobStatus.CANCELLED);
            job.setErrorMessage("Dismissed from DLQ: " + reason);
            printJobRepository.save(job);
            log.info("Print job {} dismissed from DLQ: {}", jobId, reason);
        });
    }

    /**
     * Create a high-priority reprint job.
     */
    @Transactional
    public PrintJob createReprintJob(Long originalJobId) {
        PrintJob originalJob = printJobRepository.findById(originalJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print job not found: " + originalJobId));

        PrintJob reprintJob = PrintJob.builder()
                .restaurant(originalJob.getRestaurant())
                .printer(originalJob.getPrinter())
                .jobType(originalJob.getJobType())
                .orderId(originalJob.getOrderId())
                .orderNumber(originalJob.getOrderNumber())
                .stationName(originalJob.getStationName())
                .printData(originalJob.getPrintData())
                .status(PrintJob.PrintJobStatus.PENDING)
                .priority(PrintJob.Priority.HIGH)
                .build();

        reprintJob = printJobRepository.save(reprintJob);
        log.info("Created reprint job {} from original {}", reprintJob.getId(), originalJobId);

        notifyPrintAgents(originalJob.getRestaurant().getId());

        return reprintJob;
    }

    /**
     * Notify admin of a job moved to DLQ.
     */
    private void notifyAdminOfDlqJob(PrintJob job) {
        try {
            // Log as critical - in production, this would send to monitoring/alerting
            log.error("CRITICAL: Print job {} for order {} moved to dead-letter queue. " +
                            "Printer: {}, Station: {}, Error: {}",
                    job.getId(), job.getOrderNumber(),
                    job.getPrinter().getPrinterName(), job.getStationName(), job.getErrorMessage());
        } catch (Exception e) {
            log.error("Failed to notify admin of DLQ job", e);
        }
    }

    /**
     * Notify print agents of new jobs
     */
    private void notifyPrintAgents(Long restaurantId) {
        try {
            printAgentWebSocketHandler.notifyNewJobs(restaurantId);
        } catch (Exception e) {
            log.warn("Failed to notify print agents: {}", e.getMessage());
        }
    }

    /**
     * Generate ESC/POS ticket data for station ticket
     */
    private String generateStationTicketData(Order order, KitchenStation station, List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("=== HEADER ===\n");
        if (station != null) {
            sb.append("STATION:").append(station.getName().toUpperCase()).append("\n");
        } else {
            sb.append("STATION:KITCHEN\n");
        }
        sb.append("=== ORDER ===\n");
        sb.append("ORDER_NUMBER:").append(order.getOrderNumber()).append("\n");
        sb.append("DATE_TIME:").append(DATE_TIME_FORMATTER.format(order.getCreatedAt())).append("\n");

        if (order.getOrderType() != null) {
            sb.append("ORDER_TYPE:").append(order.getOrderType().toString().replace("_", " ")).append("\n");
        }

        String tableInfo = getTableNumberFromOrder(order);
        if (tableInfo != null && !tableInfo.isEmpty()) {
            sb.append("TABLE:").append(tableInfo).append("\n");
        }

        sb.append("=== ITEMS ===\n");
        sb.append("ITEM_COUNT:").append(items.size()).append("\n");

        for (OrderItem item : items) {
            sb.append("ITEM:").append(item.getQuantity()).append("x ").append(item.getProductName()).append("\n");
            if (item.getVariantName() != null && !item.getVariantName().isEmpty()) {
                sb.append("VARIANT:").append(item.getVariantName()).append("\n");
            }
            if (item.getSpecialInstructions() != null && !item.getSpecialInstructions().isEmpty()) {
                sb.append("INSTRUCTIONS:").append(item.getSpecialInstructions()).append("\n");
            }
        }

        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            sb.append("=== NOTES ===\n");
            sb.append("CUSTOMER_NOTES:").append(order.getCustomerNotes()).append("\n");
        }

        sb.append("=== FOOTER ===\n");
        sb.append("MESSAGE:HOZIR TAYYORLANG!\n");

        return sb.toString();
    }

    /**
     * Generate ESC/POS ticket data for legacy ticket
     */
    private String generateLegacyTicketData(Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== HEADER ===\n");
        sb.append("TITLE:OSHXONA BUYURTMASI\n");
        sb.append("=== ORDER ===\n");
        sb.append("ORDER_NUMBER:").append(order.getOrderNumber()).append("\n");
        sb.append("RESTAURANT:").append(order.getRestaurant().getName()).append("\n");
        sb.append("DATE_TIME:").append(DATE_TIME_FORMATTER.format(order.getCreatedAt())).append("\n");

        if (order.getOrderType() != null) {
            sb.append("ORDER_TYPE:").append(order.getOrderType().toString().replace("_", " ")).append("\n");
        }

        String tableInfo = getTableNumberFromOrder(order);
        if (tableInfo != null && !tableInfo.isEmpty()) {
            sb.append("TABLE:").append(tableInfo).append("\n");
            if (order.getDiningTable() != null && order.getDiningTable().getSection() != null) {
                sb.append("SECTION:").append(order.getDiningTable().getSection()).append("\n");
            }
        }

        sb.append("=== ITEMS ===\n");
        for (var item : order.getItems()) {
            sb.append("ITEM:").append(item.getQuantity()).append("x ").append(item.getProductName()).append("\n");
            if (item.getVariantName() != null && !item.getVariantName().isEmpty()) {
                sb.append("VARIANT:").append(item.getVariantName()).append("\n");
            }
            if (item.getSpecialInstructions() != null && !item.getSpecialInstructions().isEmpty()) {
                sb.append("INSTRUCTIONS:").append(item.getSpecialInstructions()).append("\n");
            }
        }

        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            sb.append("=== NOTES ===\n");
            sb.append("CUSTOMER_NOTES:").append(order.getCustomerNotes()).append("\n");
        }

        if (order.getDeliveryInfo() != null) {
            sb.append("=== DELIVERY ===\n");
            sb.append("CONTACT_NAME:").append(order.getDeliveryInfo().getContactName() != null ?
                    order.getDeliveryInfo().getContactName() : "N/A").append("\n");
            sb.append("CONTACT_PHONE:").append(order.getDeliveryInfo().getContactPhone() != null ?
                    order.getDeliveryInfo().getContactPhone() : "N/A").append("\n");
            if (order.getDeliveryInfo().getAddress() != null) {
                sb.append("ADDRESS:").append(order.getDeliveryInfo().getAddress()).append("\n");
            }
        }

        sb.append("=== FOOTER ===\n");
        sb.append("MESSAGE:HOZIR TAYYORLANG!\n");

        return sb.toString();
    }

    /**
     * Get table number from order
     * Uses the new getTables() helper method from Order entity
     */
    private String getTableNumberFromOrder(Order order) {
        // Use the new helper method to get all tables
        java.util.List<com.elcafe.modules.restaurant.entity.RestaurantTable> tables = order.getTables();
        if (tables != null && !tables.isEmpty()) {
            return tables.stream()
                    .map(com.elcafe.modules.restaurant.entity.RestaurantTable::getTableNumber)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        return null;
    }

    /**
     * Scheduled cleanup of old print jobs (runs daily at 3 AM)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "print-job-cleanup", lockAtLeastFor = "PT30S")
    @Transactional
    public void cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = printJobRepository.deleteOldJobs(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old print jobs", deleted);
        }
    }

    /**
     * Retire tickets nobody is ever going to print.
     *
     * <p>Retired rather than deleted, so the week-old cleanup above still sweeps them and there is a
     * record in between. Retired rather than left queued, because the nightly cleanup only ever touched
     * COMPLETED, FAILED and CANCELLED — so a venue configured for agent printing with no agent
     * installed accrued one PENDING row per order, for ever, with nothing to remove them.
     *
     * <p>Hourly, not nightly. These rows are counted as waiting tickets by the status card, the banner
     * and the alert; on a nightly sweep a venue would spend most of a day being told about a queue that
     * is no longer real.
     */
    @Scheduled(cron = "0 7 * * * *")
    @SchedulerLock(name = "print-job-expire-unprinted", lockAtLeastFor = "PT30S")
    @Transactional
    public void expireUnprintedJobs() {
        int expired = printJobRepository.expireUnprintedJobs(
                LocalDateTime.now().minusHours(unprintedExpireAfterHours),
                "Expired unprinted after " + unprintedExpireAfterHours + "h");
        if (expired > 0) {
            log.warn("Retired {} print job(s) that were never printed within {}h",
                    expired, unprintedExpireAfterHours);
        }
    }

    /**
     * Put jobs whose retry backoff has elapsed back in the queue, and tell the agent they are there.
     *
     * <p>This is the step that was missing. {@code markJobFailed} set RETRYING and computed a backoff,
     * and nothing ever read either — so a ticket that failed once was never sent again. It could not
     * even reach the dead-letter queue, because getting there needs {@code maxRetries} failures and a
     * job nobody re-sends cannot fail a second time.
     *
     * <p>Every 30 seconds rather than on the five-minute sweep: the backoff starts at two seconds, and
     * a kitchen ticket that waits five minutes to be tried again has missed the point of retrying.
     */
    @Scheduled(fixedRate = 30000)
    @SchedulerLock(name = "print-job-promote-retries", lockAtLeastFor = "PT10S")
    @Transactional
    public void promoteJobsReadyForRetry() {
        int promoted = printJobRepository.promoteJobsReadyForRetry(LocalDateTime.now());
        if (promoted == 0) {
            return;
        }
        log.info("Re-queued {} print job(s) whose retry backoff elapsed", promoted);
        // Re-queuing alone would leave them waiting for the agent's next reconnect, which on a healthy
        // connection may be hours away.
        printAgentWebSocketHandler.notifyAllAgents();
    }

    /**
     * Scheduled reset of stuck jobs (runs every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @SchedulerLock(name = "print-job-reset-stuck", lockAtLeastFor = "PT30S")
    @Transactional
    public void resetStuckJobs() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(2);
        int reset = printJobRepository.resetStuckJobs(timeout);
        if (reset > 0) {
            log.info("Reset {} stuck print jobs", reset);
        }

        // The ones the reset above refuses to touch, because their retries are spent. They were left
        // in SENT for ever; the dead-letter queue is where a person can see them and decide.
        int abandoned = printJobRepository.deadLetterAbandonedJobs(timeout, LocalDateTime.now(),
                "No answer from the print agent after all retries");
        if (abandoned > 0) {
            log.error("Moved {} print job(s) to the dead-letter queue — sent, never acknowledged, "
                    + "retries exhausted", abandoned);
        }
    }
}
