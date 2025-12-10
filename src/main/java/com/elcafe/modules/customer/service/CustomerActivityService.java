package com.elcafe.modules.customer.service;

import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.dto.CustomerActivityFilterDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerActivityService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    /**
     * Get all customers with their activity data (RFM analysis)
     */
    public List<CustomerActivityDTO> getAllCustomersActivity() {
        List<Customer> customers = customerRepository.findAll();
        List<CustomerActivityDTO> activityList = customers.stream()
                .map(this::calculateCustomerActivity)
                .collect(Collectors.toList());

        // Calculate RFM scores
        calculateRFMScores(activityList);

        return activityList;
    }

    /**
     * Get filtered customers with activity data
     */
    public List<CustomerActivityDTO> getFilteredCustomersActivity(CustomerActivityFilterDTO filter) {
        List<CustomerActivityDTO> allActivities = getAllCustomersActivity();

        return allActivities.stream()
                .filter(activity -> matchesFilter(activity, filter))
                .collect(Collectors.toList());
    }

    /**
     * Calculate activity metrics for a single customer
     */
    private CustomerActivityDTO calculateCustomerActivity(Customer customer) {
        // Get all customer orders
        List<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());

        // Calculate frequency (total orders)
        Integer frequency = orders.size();

        // Calculate monetary (total spent)
        BigDecimal monetary = orderRepository.sumTotalByCustomerId(customer.getId());
        if (monetary == null) {
            monetary = BigDecimal.ZERO;
        }

        // Calculate average check
        BigDecimal averageCheck = BigDecimal.ZERO;
        if (frequency > 0) {
            averageCheck = monetary.divide(BigDecimal.valueOf(frequency), 2, RoundingMode.HALF_UP);
        }

        // Calculate recency (days since last order)
        Integer recency = null;
        LocalDateTime lastOrderDate = null;
        if (!orders.isEmpty()) {
            lastOrderDate = orders.get(0).getCreatedAt();
            recency = (int) ChronoUnit.DAYS.between(lastOrderDate, LocalDateTime.now());
        }

        // Get unique order sources
        List<OrderSource> orderSources = orderRepository.findDistinctOrderSourcesByCustomerId(customer.getId());

        return CustomerActivityDTO.builder()
                .customerId(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .city(customer.getCity())
                .tags(customer.getTags())
                .active(customer.getActive())
                .recency(recency)
                .frequency(frequency)
                .monetary(monetary)
                .averageCheck(averageCheck)
                .lastOrderDate(lastOrderDate)
                .registrationDate(customer.getCreatedAt())
                .registrationSource(customer.getRegistrationSource())
                .orderSources(orderSources)
                .build();
    }

    /**
     * Calculate RFM scores for all customers
     * Scores range from 1 (worst) to 5 (best)
     */
    private void calculateRFMScores(List<CustomerActivityDTO> activities) {
        if (activities.isEmpty()) {
            return;
        }

        // Filter out customers with no orders for scoring
        List<CustomerActivityDTO> activitiesWithOrders = activities.stream()
                .filter(a -> a.getFrequency() != null && a.getFrequency() > 0)
                .collect(Collectors.toList());

        if (activitiesWithOrders.isEmpty()) {
            return;
        }

        // Calculate Recency scores (lower recency is better)
        List<Integer> recencies = activitiesWithOrders.stream()
                .map(CustomerActivityDTO::getRecency)
                .filter(r -> r != null)
                .sorted()
                .collect(Collectors.toList());

        // Calculate Frequency scores (higher frequency is better)
        List<Integer> frequencies = activitiesWithOrders.stream()
                .map(CustomerActivityDTO::getFrequency)
                .filter(f -> f != null && f > 0)
                .sorted()
                .collect(Collectors.toList());

        // Calculate Monetary scores (higher monetary is better)
        List<BigDecimal> monetaries = activitiesWithOrders.stream()
                .map(CustomerActivityDTO::getMonetary)
                .filter(m -> m != null && m.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .collect(Collectors.toList());

        // Assign scores to each customer
        for (CustomerActivityDTO activity : activities) {
            if (activity.getFrequency() == null || activity.getFrequency() == 0) {
                activity.setRecencyScore(1);
                activity.setFrequencyScore(1);
                activity.setMonetaryScore(1);
                activity.setRfmSegment("New/Inactive");
                continue;
            }

            // Recency score (reversed - lower days is better)
            int recencyScore = calculateScore(activity.getRecency(), recencies, true);
            activity.setRecencyScore(recencyScore);

            // Frequency score
            int frequencyScore = calculateScore(activity.getFrequency(), frequencies, false);
            activity.setFrequencyScore(frequencyScore);

            // Monetary score
            int monetaryScore = calculateMonetaryScore(activity.getMonetary(), monetaries);
            activity.setMonetaryScore(monetaryScore);

            // Determine RFM segment
            activity.setRfmSegment(determineRFMSegment(recencyScore, frequencyScore, monetaryScore));
        }
    }

    /**
     * Calculate score based on quintiles (1-5)
     */
    private int calculateScore(Integer value, List<Integer> sortedValues, boolean reverse) {
        if (value == null || sortedValues.isEmpty()) {
            return 1;
        }

        int size = sortedValues.size();
        int quintileSize = Math.max(1, size / 5);

        for (int i = 0; i < 5; i++) {
            int index = Math.min(quintileSize * (i + 1), size - 1);
            if (value <= sortedValues.get(index)) {
                return reverse ? (5 - i) : (i + 1);
            }
        }

        return reverse ? 1 : 5;
    }

    /**
     * Calculate monetary score based on quintiles
     */
    private int calculateMonetaryScore(BigDecimal value, List<BigDecimal> sortedValues) {
        if (value == null || sortedValues.isEmpty()) {
            return 1;
        }

        int size = sortedValues.size();
        int quintileSize = Math.max(1, size / 5);

        for (int i = 0; i < 5; i++) {
            int index = Math.min(quintileSize * (i + 1), size - 1);
            if (value.compareTo(sortedValues.get(index)) <= 0) {
                return i + 1;
            }
        }

        return 5;
    }

    /**
     * Determine RFM segment based on scores
     */
    private String determineRFMSegment(int recency, int frequency, int monetary) {
        // Champions: High RFM scores
        if (recency >= 4 && frequency >= 4 && monetary >= 4) {
            return "Champions";
        }

        // Loyal Customers: High frequency
        if (frequency >= 4) {
            return "Loyal Customers";
        }

        // Potential Loyalists: Recent customers with average frequency
        if (recency >= 4 && frequency >= 2 && frequency <= 3) {
            return "Potential Loyalists";
        }

        // Recent Customers: High recency but low frequency
        if (recency >= 4 && frequency <= 2) {
            return "Recent Customers";
        }

        // Promising: Recent customers with good monetary
        if (recency >= 3 && monetary >= 3) {
            return "Promising";
        }

        // Need Attention: Average scores
        if (recency >= 3 && frequency >= 2 && monetary >= 2) {
            return "Need Attention";
        }

        // About to Sleep: Below average recency
        if (recency == 2) {
            return "About to Sleep";
        }

        // At Risk: Low recency but high monetary/frequency
        if (recency <= 2 && (frequency >= 3 || monetary >= 3)) {
            return "At Risk";
        }

        // Can't Lose Them: Very low recency but high value
        if (recency == 1 && frequency >= 4 && monetary >= 4) {
            return "Can't Lose Them";
        }

        // Hibernating: Low recency and frequency
        if (recency <= 2 && frequency <= 2) {
            return "Hibernating";
        }

        // Lost: Very low scores
        if (recency == 1 && frequency <= 2) {
            return "Lost";
        }

        return "Others";
    }

    /**
     * Check if activity matches the filter criteria
     */
    private boolean matchesFilter(CustomerActivityDTO activity, CustomerActivityFilterDTO filter) {
        // Search term filter
        if (filter.getSearchTerm() != null && !filter.getSearchTerm().isEmpty()) {
            String search = filter.getSearchTerm().toLowerCase();
            boolean matches = false;

            if (activity.getFirstName() != null && activity.getFirstName().toLowerCase().contains(search)) {
                matches = true;
            }
            if (activity.getLastName() != null && activity.getLastName().toLowerCase().contains(search)) {
                matches = true;
            }
            if (activity.getEmail() != null && activity.getEmail().toLowerCase().contains(search)) {
                matches = true;
            }
            if (activity.getPhone() != null && activity.getPhone().toLowerCase().contains(search)) {
                matches = true;
            }
            if (activity.getCity() != null && activity.getCity().toLowerCase().contains(search)) {
                matches = true;
            }

            if (!matches) {
                return false;
            }
        }

        // Active status filter
        if (filter.getActive() != null && !filter.getActive().equals(activity.getActive())) {
            return false;
        }

        // Registration source filter
        if (filter.getRegistrationSource() != null && !filter.getRegistrationSource().equals(activity.getRegistrationSource())) {
            return false;
        }

        // Period filter (based on registration date)
        if (filter.getStartDate() != null && activity.getRegistrationDate() != null) {
            if (activity.getRegistrationDate().isBefore(filter.getStartDate())) {
                return false;
            }
        }
        if (filter.getEndDate() != null && activity.getRegistrationDate() != null) {
            if (activity.getRegistrationDate().isAfter(filter.getEndDate())) {
                return false;
            }
        }

        // Recency filter
        if (filter.getMinRecency() != null && activity.getRecency() != null) {
            if (activity.getRecency() < filter.getMinRecency()) {
                return false;
            }
        }
        if (filter.getMaxRecency() != null && activity.getRecency() != null) {
            if (activity.getRecency() > filter.getMaxRecency()) {
                return false;
            }
        }

        // Frequency filter
        if (filter.getMinFrequency() != null && activity.getFrequency() != null) {
            if (activity.getFrequency() < filter.getMinFrequency()) {
                return false;
            }
        }
        if (filter.getMaxFrequency() != null && activity.getFrequency() != null) {
            if (activity.getFrequency() > filter.getMaxFrequency()) {
                return false;
            }
        }

        // Monetary filter
        if (filter.getMinMonetary() != null && activity.getMonetary() != null) {
            if (activity.getMonetary().compareTo(filter.getMinMonetary()) < 0) {
                return false;
            }
        }
        if (filter.getMaxMonetary() != null && activity.getMonetary() != null) {
            if (activity.getMonetary().compareTo(filter.getMaxMonetary()) > 0) {
                return false;
            }
        }

        return true;
    }
}
