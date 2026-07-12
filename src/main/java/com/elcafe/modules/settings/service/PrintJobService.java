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
    }
}
