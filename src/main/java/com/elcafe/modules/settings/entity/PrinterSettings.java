package com.elcafe.modules.settings.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "printer_settings", indexes = {
        @Index(name = "idx_printer_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_printer_type", columnList = "printer_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class PrinterSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrinterType printerType;

    @Column(nullable = false, length = 200)
    private String printerName; // System printer name

    @Column(length = 100)
    private String ipAddress; // For network printers

    @Column
    private Integer port; // For network printers (default 9100)

    @Column(length = 20)
    private String connectionType; // USB, NETWORK, BLUETOOTH

    @Column(nullable = false)
    @Builder.Default
    private Integer paperWidth = 80; // mm (80mm for thermal printers)

    @Column(nullable = false)
    @Builder.Default
    private Integer fontSize = 12;

    @Column(nullable = false)
    @Builder.Default
    private Boolean autoPrint = false; // Auto print on order creation

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PrinterType {
        KITCHEN,     // Kitchen order printer
        CUSTOMER,    // Customer receipt printer
        LABEL,       // Label printer
        REPORT       // Report printer
    }
}
