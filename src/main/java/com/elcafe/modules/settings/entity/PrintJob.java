package com.elcafe.modules.settings.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "print_jobs", indexes = {
        @Index(name = "idx_print_job_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_print_job_printer", columnList = "printer_id"),
        @Index(name = "idx_print_job_status", columnList = "status"),
        @Index(name = "idx_print_job_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id", nullable = false)
    private PrinterSettings printer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PrintJobStatus status = PrintJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrinterSettings.PrinterType jobType;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "station_name", length = 100)
    private String stationName;

    @Lob
    @Column(name = "print_data", nullable = false, columnDefinition = "TEXT")
    private String printData; // ESC/POS commands as Base64 or formatted text

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "agent_id", length = 100)
    private String agentId; // ID of the print agent that processed this job

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PrintJobStatus {
        PENDING,     // Waiting to be sent to print agent
        SENT,        // Sent to print agent
        PRINTING,    // Currently printing
        COMPLETED,   // Successfully printed
        FAILED,      // Failed after max retries
        CANCELLED    // Cancelled by user
    }

    public void incrementRetry() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }
}
