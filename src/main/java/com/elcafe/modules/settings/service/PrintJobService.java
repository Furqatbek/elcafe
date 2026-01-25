package com.elcafe.modules.settings.service;

import com.elcafe.modules.kitchen.entity.KitchenStation;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    /**
     * Mark job as failed
     */
    @Transactional
    public void markJobFailed(Long jobId, String errorMessage) {
        printJobRepository.findById(jobId).ifPresent(job -> {
            job.incrementRetry();
            if (job.canRetry()) {
                job.setStatus(PrintJob.PrintJobStatus.PENDING);
                log.info("Print job {} failed, will retry ({}/{})", jobId, job.getRetryCount(), job.getMaxRetries());
            } else {
                job.setStatus(PrintJob.PrintJobStatus.FAILED);
                log.error("Print job {} failed permanently after {} retries", jobId, job.getRetryCount());
            }
            job.setErrorMessage(errorMessage);
            printJobRepository.save(job);
        });
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
     */
    private String getTableNumberFromOrder(Order order) {
        if (order.getDiningTable() != null && order.getDiningTable().getTableNumber() != null) {
            return order.getDiningTable().getTableNumber();
        }
        if (order.getTableIds() != null && !order.getTableIds().isEmpty()) {
            return order.getTableIds();
        }
        return null;
    }

    /**
     * Scheduled cleanup of old print jobs (runs daily at 3 AM)
     */
    @Scheduled(cron = "0 0 3 * * *")
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
    @Transactional
    public void resetStuckJobs() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(2);
        int reset = printJobRepository.resetStuckJobs(timeout);
        if (reset > 0) {
            log.info("Reset {} stuck print jobs", reset);
        }
    }
}
