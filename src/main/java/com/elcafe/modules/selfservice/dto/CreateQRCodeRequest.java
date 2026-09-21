package com.elcafe.modules.selfservice.dto;

import com.elcafe.modules.selfservice.enums.QRCodeType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateQRCodeRequest {
    private Long restaurantId;
    private Long tableId;
    private String name;
    private String description;
    private QRCodeType qrType = QRCodeType.TABLE;
    private LocalDateTime expiresAt;
}
