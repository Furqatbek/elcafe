package com.elcafe.modules.customer.dto;

import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.order.enums.OrderSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivityDTO {
    private Long customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String city;
    private String tags;
    private Boolean active;

    // RFM Metrics
    private Integer recency; // Days since last order
    private Integer frequency; // Total number of orders
    private BigDecimal monetary; // Total amount spent
    private BigDecimal averageCheck; // Average order value

    // Additional Info
    private LocalDateTime lastOrderDate;
    private LocalDateTime registrationDate;
    private RegistrationSource registrationSource;
    private List<OrderSource> orderSources; // Unique order sources used

    // RFM Scores (1-5, where 5 is best)
    private Integer recencyScore;
    private Integer frequencyScore;
    private Integer monetaryScore;
    private String rfmSegment; // e.g., "Champions", "Loyal Customers", "At Risk", etc.
}
