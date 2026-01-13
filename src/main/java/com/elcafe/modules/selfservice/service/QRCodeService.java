package com.elcafe.modules.selfservice.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.Table;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.TableRepository;
import com.elcafe.modules.selfservice.dto.CreateQRCodeRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.enums.QRCodeType;
import com.elcafe.modules.selfservice.repository.QRCodeRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing QR codes for self-service ordering.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodeService {

    private final QRCodeRepository qrCodeRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableRepository tableRepository;

    @Value("${app.selfservice.base-url:http://localhost:3000/order}")
    private String selfServiceBaseUrl;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    /**
     * Create a new QR code.
     */
    @Transactional
    public QRCode createQRCode(CreateQRCodeRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Table table = null;
        if (request.getTableId() != null) {
            table = tableRepository.findById(request.getTableId())
                    .orElseThrow(() -> new RuntimeException("Table not found"));

            // Check if table already has a QR code
            Optional<QRCode> existing = qrCodeRepository.findByTableId(request.getTableId());
            if (existing.isPresent()) {
                throw new RuntimeException("Table already has a QR code assigned");
            }
        }

        String code = generateUniqueCode();

        QRCode qrCode = QRCode.builder()
                .restaurant(restaurant)
                .table(table)
                .code(code)
                .shortUrl(buildMenuUrl(restaurant.getId(), code))
                .name(request.getName() != null ? request.getName() :
                        (table != null ? "Table " + table.getTableNumber() : "QR-" + code))
                .description(request.getDescription())
                .qrType(request.getQrType() != null ? request.getQrType() : QRCodeType.TABLE)
                .expiresAt(request.getExpiresAt())
                .build();

        return qrCodeRepository.save(qrCode);
    }

    /**
     * Generate QR codes for all tables in a restaurant.
     */
    @Transactional
    public List<QRCode> generateForAllTables(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        List<Table> tables = tableRepository.findByRestaurantId(restaurantId);

        return tables.stream()
                .filter(table -> qrCodeRepository.findByTableId(table.getId()).isEmpty())
                .map(table -> {
                    String code = generateUniqueCode();
                    return qrCodeRepository.save(QRCode.builder()
                            .restaurant(restaurant)
                            .table(table)
                            .code(code)
                            .shortUrl(buildMenuUrl(restaurantId, code))
                            .name("Table " + table.getTableNumber())
                            .qrType(QRCodeType.TABLE)
                            .build());
                })
                .toList();
    }

    /**
     * Get QR code by code string.
     */
    public Optional<QRCode> getByCode(String code) {
        return qrCodeRepository.findByCode(code);
    }

    /**
     * Get QR code by ID.
     */
    public Optional<QRCode> getById(Long id) {
        return qrCodeRepository.findById(id);
    }

    /**
     * Get all QR codes for a restaurant.
     */
    public Page<QRCode> getByRestaurant(Long restaurantId, Pageable pageable) {
        return qrCodeRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
    }

    /**
     * Get active QR codes for a restaurant.
     */
    public List<QRCode> getActiveByRestaurant(Long restaurantId) {
        return qrCodeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    /**
     * Record a scan of the QR code.
     */
    @Transactional
    public void recordScan(String code) {
        qrCodeRepository.findByCode(code).ifPresent(qrCode -> {
            qrCode.recordScan();
            qrCodeRepository.save(qrCode);
        });
    }

    /**
     * Toggle QR code active status.
     */
    @Transactional
    public QRCode toggleActive(Long id) {
        QRCode qrCode = qrCodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("QR code not found"));
        qrCode.setIsActive(!qrCode.getIsActive());
        return qrCodeRepository.save(qrCode);
    }

    /**
     * Delete a QR code.
     */
    @Transactional
    public void delete(Long id) {
        qrCodeRepository.deleteById(id);
    }

    /**
     * Generate QR code image as Base64 PNG.
     */
    public String generateQRCodeImage(String code, int width, int height) {
        try {
            QRCode qrCode = qrCodeRepository.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("QR code not found"));

            String url = qrCode.getShortUrl();

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code image: {}", e.getMessage());
            throw new RuntimeException("Failed to generate QR code image", e);
        }
    }

    /**
     * Get statistics for QR codes.
     */
    public QRCodeStats getStats(Long restaurantId) {
        long activeCount = qrCodeRepository.countActiveByRestaurant(restaurantId);
        Long totalScans = qrCodeRepository.getTotalScans(restaurantId);

        return QRCodeStats.builder()
                .activeQRCodes(activeCount)
                .totalScans(totalScans != null ? totalScans : 0L)
                .build();
    }

    /**
     * Generate a unique code for QR.
     */
    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (qrCodeRepository.existsByCode(code));
        return code;
    }

    /**
     * Build the menu URL for a QR code.
     */
    private String buildMenuUrl(Long restaurantId, String code) {
        return selfServiceBaseUrl + "/menu/" + restaurantId + "/" + code;
    }

    @lombok.Builder
    @lombok.Data
    public static class QRCodeStats {
        private long activeQRCodes;
        private long totalScans;
    }
}
