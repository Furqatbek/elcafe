package com.elcafe.modules.settings.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "receipt_templates", indexes = {
        @Index(name = "idx_receipt_template_restaurant", columnList = "restaurant_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    /** Brand name shown in the receipt header */
    @Column(length = 100)
    private String restaurantName;

    /** Optional tagline / slogan under the brand name */
    @Column(length = 200)
    private String tagline;

    /** Contact phone shown in footer */
    @Column(length = 50)
    private String phone;

    /** Website shown in footer */
    @Column(length = 100)
    private String website;

    /** Thank-you / closing message in footer */
    @Column(length = 200)
    private String footerMessage;

    /** Currency abbreviation (e.g. UZS, USD) */
    @Column(length = 10)
    @Builder.Default
    private String currency = "UZS";

    /** Show QR code section on receipt */
    @Column(nullable = false)
    @Builder.Default
    private Boolean showQrCode = true;

    /** URL embedded in the QR code */
    @Column(length = 500)
    private String qrUrl;

    /** Title above QR code */
    @Column(length = 100)
    private String qrTitle;

    /** Subtitle below QR code */
    @Column(length = 100)
    private String qrSubtitle;

    /** Receipt paper width in mm (58 or 80) */
    @Column(nullable = false)
    @Builder.Default
    private Integer paperWidthMm = 58;

    /** Kitchen ticket header line (e.g. OSHXONA BUYURTMASI) */
    @Column(length = 100)
    private String kitchenHeaderText;

    /** Kitchen ticket footer call-to-action (e.g. HOZIR TAYYORLANG!) */
    @Column(length = 100)
    private String kitchenFooterText;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
