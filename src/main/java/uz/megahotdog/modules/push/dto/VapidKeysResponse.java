package uz.megahotdog.modules.push.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response DTO containing VAPID public key for client subscription.
 */
@Data
@AllArgsConstructor
public class VapidKeysResponse {
    private String publicKey;
}
