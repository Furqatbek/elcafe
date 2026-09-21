package com.elcafe.modules.waiter.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.dto.*;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterKPIConfigRepository;
import com.elcafe.modules.waiter.repository.WaiterPerformanceRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaiterPerformanceService {

    private final WaiterPerformanceRepository performanceRepository;
    private final WaiterKPIConfigRepository kpiConfigRepository;
    private final WaiterRepository waiterRepository;
    private final RestaurantRepository restaurantRepository;
    private final WaiterCommissionRepository commissionRepository;

    // ==================== KPI Configuration ====================

    /**
     * Get all KPI configs for a restaurant
     */
    public List<WaiterKPIConfig> getKPIConfigs(Long restaurantId) {
        return kpiConfigRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    /**
     * Get effective KPI config for a waiter (waiter-specific or restaurant default)
     */
    public WaiterKPIConfig getEffectiveKPIConfig(Long waiterId, Long restaurantId) {
        List<WaiterKPIConfig> configs = kpiConfigRepository.findEffectiveConfig(waiterId, restaurantId);
        if (configs.isEmpty()) {
            // Return default config if none exists
            return createDefaultKPIConfig(restaurantId);
        }
        return configs.get(0); // First one is waiter-specific if exists, otherwise restaurant default
    }

    /**
     * Create or update KPI config
     */
    @Transactional
    public WaiterKPIConfig saveKPIConfig(Long restaurantId, WaiterKPIConfigRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        WaiterKPIConfig config;

        if (request.getId() != null) {
            config = kpiConfigRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("KPI config not found"));
        } else {
            config = new WaiterKPIConfig();
            config.setRestaurant(restaurant);
        }

        // Set waiter if specified
        if (request.getWaiterId() != null) {
            Waiter waiter = waiterRepository.findById(request.getWaiterId())
                    .orElseThrow(() -> new RuntimeException("Waiter not found"));
            config.setWaiter(waiter);
        }

        config.setName(request.getName());
        config.setTargetOrdersPerDay(request.getTargetOrdersPerDay());
        config.setTargetRevenuePerDay(request.getTargetRevenuePerDay());
        config.setTargetAvgTicket(request.getTargetAvgTicket());
        config.setTargetTablesPerShift(request.getTargetTablesPerShift());
        config.setTargetAvgServiceTimeMinutes(request.getTargetAvgServiceTimeMinutes());
        config.setMaxComplaintRatePercent(request.getMaxComplaintRatePercent());
        config.setMinCustomerRating(request.getMinCustomerRating());
        config.setTargetUpsellRatePercent(request.getTargetUpsellRatePercent());
        config.setTargetDessertAttachRatePercent(request.getTargetDessertAttachRatePercent());
        config.setTargetBeverageAttachRatePercent(request.getTargetBeverageAttachRatePercent());
        config.setBonusThresholdPercent(request.getBonusThresholdPercent());
        config.setBonusAmountPerThreshold(request.getBonusAmountPerThreshold());
        config.setActive(request.getActive() != null ? request.getActive() : true);

        return kpiConfigRepository.save(config);
    }

    /**
     * Delete KPI config
     */
    @Transactional
    public void deleteKPIConfig(Long configId) {
        kpiConfigRepository.deleteById(configId);
    }

    /**
     * Create default KPI config (not persisted)
     */
    private WaiterKPIConfig createDefaultKPIConfig(Long restaurantId) {
        return WaiterKPIConfig.builder()
                .targetOrdersPerDay(20)
                .targetRevenuePerDay(BigDecimal.valueOf(500))
                .targetAvgTicket(BigDecimal.valueOf(25))
                .targetTablesPerShift(10)
                .targetAvgServiceTimeMinutes(45)
                .maxComplaintRatePercent(BigDecimal.valueOf(2.0))
                .minCustomerRating(BigDecimal.valueOf(4.0))
                .targetUpsellRatePercent(BigDecimal.valueOf(15.0))
                .targetDessertAttachRatePercent(BigDecimal.valueOf(20.0))
                .targetBeverageAttachRatePercent(BigDecimal.valueOf(60.0))
                .bonusThresholdPercent(BigDecimal.valueOf(100.0))
                .bonusAmountPerThreshold(BigDecimal.valueOf(50.0))
                .build();
    }

    // ==================== Performance Tracking ====================

    /**
     * Record order completion for waiter performance
     */
    @Transactional
    public void recordOrderCompletion(Order order, Long waiterId, Long restaurantId) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        // Update order metrics
        performance.setTotalOrders(performance.getTotalOrders() + 1);
        BigDecimal orderTotal = order.getGrandTotal() != null ? order.getGrandTotal() : order.getTotal();
        performance.setTotalRevenue(performance.getTotalRevenue().add(orderTotal));

        // Update average ticket
        BigDecimal newAvgTicket = performance.getTotalRevenue()
                .divide(BigDecimal.valueOf(performance.getTotalOrders()), 2, RoundingMode.HALF_UP);
        performance.setAvgTicketValue(newAvgTicket);

        // Track service time if available (from creation to completion)
        if (order.getCreatedAt() != null && order.getCompletedAt() != null) {
            long serviceTimeMinutes = ChronoUnit.MINUTES.between(order.getCreatedAt(), order.getCompletedAt());
            updateServiceTime(performance, (int) serviceTimeMinutes);
        }

        // Recalculate KPI score
        calculateAndUpdateKPIScore(performance, waiterId, restaurantId);

        performanceRepository.save(performance);
        log.debug("Recorded order completion for waiter {} on {}", waiterId, today);
    }

    /**
     * Record table served
     */
    @Transactional
    public void recordTableServed(Long waiterId, Long restaurantId, int customerCount) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        performance.setTotalTablesServed(performance.getTotalTablesServed() + 1);
        performance.setTotalCustomersServed(performance.getTotalCustomersServed() + customerCount);

        performanceRepository.save(performance);
    }

    /**
     * Record tip received
     */
    @Transactional
    public void recordTip(Long waiterId, Long restaurantId, BigDecimal tipAmount) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        performance.setTotalTips(performance.getTotalTips().add(tipAmount));

        performanceRepository.save(performance);
    }

    /**
     * Record complaint
     */
    @Transactional
    public void recordComplaint(Long waiterId, Long restaurantId) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        performance.setComplaintsCount(performance.getComplaintsCount() + 1);
        calculateAndUpdateKPIScore(performance, waiterId, restaurantId);

        performanceRepository.save(performance);
    }

    /**
     * Record compliment
     */
    @Transactional
    public void recordCompliment(Long waiterId, Long restaurantId) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        performance.setComplimentsCount(performance.getComplimentsCount() + 1);
        calculateAndUpdateKPIScore(performance, waiterId, restaurantId);

        performanceRepository.save(performance);
    }

    /**
     * Record customer rating
     */
    @Transactional
    public void recordRating(Long waiterId, Long restaurantId, BigDecimal rating) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        // Update running average
        int newCount = performance.getRatingsCount() + 1;
        BigDecimal currentAvg = performance.getAvgCustomerRating() != null
                ? performance.getAvgCustomerRating() : BigDecimal.ZERO;
        BigDecimal newAvg = currentAvg
                .multiply(BigDecimal.valueOf(performance.getRatingsCount()))
                .add(rating)
                .divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        performance.setAvgCustomerRating(newAvg);
        performance.setRatingsCount(newCount);
        calculateAndUpdateKPIScore(performance, waiterId, restaurantId);

        performanceRepository.save(performance);
    }

    /**
     * Record void item
     */
    @Transactional
    public void recordVoidItem(Long waiterId, Long restaurantId, BigDecimal itemValue) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        performance.setVoidItemsCount(performance.getVoidItemsCount() + 1);
        performance.setVoidItemsValue(performance.getVoidItemsValue().add(itemValue));

        performanceRepository.save(performance);
    }

    /**
     * Record shift start
     */
    @Transactional
    public void recordShiftStart(Long waiterId, Long restaurantId) {
        LocalDate today = LocalDate.now();
        WaiterPerformance performance = getOrCreateTodayPerformance(waiterId, restaurantId, today);

        if (performance.getShiftStart() == null) {
            performance.setShiftStart(LocalDateTime.now());
            performanceRepository.save(performance);
        }
    }

    /**
     * Record shift end
     */
    @Transactional
    public void recordShiftEnd(Long waiterId, Long restaurantId) {
        LocalDate today = LocalDate.now();
        Optional<WaiterPerformance> optPerformance = performanceRepository
                .findByWaiterIdAndPerformanceDate(waiterId, today);

        if (optPerformance.isPresent()) {
            WaiterPerformance performance = optPerformance.get();
            performance.setShiftEnd(LocalDateTime.now());

            if (performance.getShiftStart() != null) {
                long hoursWorked = ChronoUnit.HOURS.between(performance.getShiftStart(), performance.getShiftEnd());
                performance.setHoursWorked(BigDecimal.valueOf(hoursWorked));
            }

            // Final KPI calculation
            calculateAndUpdateKPIScore(performance, waiterId, restaurantId);
            performanceRepository.save(performance);
        }
    }

    // ==================== Performance Reports ====================

    /**
     * Get today's performance for a waiter
     */
    public Optional<WaiterPerformance> getTodayPerformance(Long waiterId) {
        return performanceRepository.findByWaiterIdAndPerformanceDate(waiterId, LocalDate.now());
    }

    /**
     * Get performance for a specific date
     */
    public Optional<WaiterPerformance> getPerformance(Long waiterId, LocalDate date) {
        return performanceRepository.findByWaiterIdAndPerformanceDate(waiterId, date);
    }

    /**
     * Get performance history for a waiter
     */
    public Page<WaiterPerformance> getPerformanceHistory(Long waiterId, Pageable pageable) {
        return performanceRepository.findByWaiterIdOrderByPerformanceDateDesc(waiterId, pageable);
    }

    /**
     * Get performance summary for a date range
     */
    public WaiterPerformanceSummary getPerformanceSummary(Long waiterId, LocalDate startDate, LocalDate endDate) {
        List<WaiterPerformance> performances = performanceRepository
                .findByWaiterIdAndPerformanceDateBetweenOrderByPerformanceDateDesc(waiterId, startDate, endDate);

        // Fetch waiter's commission config
        Waiter waiter = waiterRepository.findById(waiterId).orElse(null);
        BigDecimal commissionPercent = waiter != null ? waiter.getCommissionPercent() : null;
        Boolean commissionEnabled = waiter != null ? waiter.getCommissionEnabled() : false;

        // Fetch commission data for the date range
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        BigDecimal totalCommission = commissionRepository.getTotalCommissionByWaiterAndDateRange(
                waiterId, startDateTime, endDateTime);

        if (performances.isEmpty()) {
            return WaiterPerformanceSummary.builder()
                    .waiterId(waiterId)
                    .startDate(startDate)
                    .endDate(endDate)
                    .totalOrders(0)
                    .totalRevenue(BigDecimal.ZERO)
                    .totalTips(BigDecimal.ZERO)
                    .avgTicketValue(BigDecimal.ZERO)
                    .avgKpiScore(BigDecimal.ZERO)
                    .totalBonusEarned(BigDecimal.ZERO)
                    .workingDays(0)
                    .complaintsCount(0)
                    .complimentsCount(0)
                    .totalCommission(totalCommission)
                    .commissionPercent(commissionPercent)
                    .commissionEnabled(commissionEnabled)
                    .build();
        }

        int totalOrders = performances.stream().mapToInt(WaiterPerformance::getTotalOrders).sum();
        BigDecimal totalRevenue = performances.stream()
                .map(WaiterPerformance::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTips = performances.stream()
                .map(WaiterPerformance::getTotalTips)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgTicket = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgKpi = performances.stream()
                .filter(p -> p.getKpiScore() != null)
                .map(WaiterPerformance::getKpiScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(performances.size()), 2, RoundingMode.HALF_UP);
        BigDecimal totalBonus = performances.stream()
                .map(WaiterPerformance::getBonusEarned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Aggregate complaints and compliments
        int totalComplaints = performances.stream().mapToInt(WaiterPerformance::getComplaintsCount).sum();
        int totalCompliments = performances.stream().mapToInt(WaiterPerformance::getComplimentsCount).sum();

        // Calculate average customer rating
        BigDecimal avgCustomerRating = performances.stream()
                .filter(p -> p.getAvgCustomerRating() != null)
                .map(WaiterPerformance::getAvgCustomerRating)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long ratedDays = performances.stream().filter(p -> p.getAvgCustomerRating() != null).count();
        if (ratedDays > 0) {
            avgCustomerRating = avgCustomerRating.divide(BigDecimal.valueOf(ratedDays), 2, RoundingMode.HALF_UP);
        }

        return WaiterPerformanceSummary.builder()
                .waiterId(waiterId)
                .startDate(startDate)
                .endDate(endDate)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .totalTips(totalTips)
                .avgTicketValue(avgTicket)
                .avgKpiScore(avgKpi)
                .totalBonusEarned(totalBonus)
                .workingDays(performances.size())
                .complaintsCount(totalComplaints)
                .complimentsCount(totalCompliments)
                .avgCustomerRating(ratedDays > 0 ? avgCustomerRating : null)
                .totalCommission(totalCommission)
                .commissionPercent(commissionPercent)
                .commissionEnabled(commissionEnabled)
                .dailyPerformances(performances)
                .build();
    }

    /**
     * Get leaderboard for a restaurant
     */
    public List<WaiterLeaderboardEntry> getLeaderboard(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = performanceRepository.getLeaderboard(restaurantId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        return results.stream()
                .map(row -> {
                    Long waiterId = (Long) row[0];

                    // Fetch commission data for this waiter
                    BigDecimal totalCommission = commissionRepository.getTotalCommissionByWaiterAndDateRange(
                            waiterId, startDateTime, endDateTime);

                    // Fetch waiter's commission config
                    Waiter waiter = waiterRepository.findById(waiterId).orElse(null);
                    BigDecimal commissionPercent = waiter != null ? waiter.getCommissionPercent() : null;
                    Boolean commissionEnabled = waiter != null ? waiter.getCommissionEnabled() : false;

                    return WaiterLeaderboardEntry.builder()
                            .waiterId(waiterId)
                            .waiterName((String) row[1])
                            .totalRevenue(toBigDecimal(row[2]))
                            .totalOrders(((Number) row[3]).intValue())
                            .avgRating(row[4] != null ? toBigDecimal(row[4]) : null)
                            .avgKpiScore(row[5] != null ? toBigDecimal(row[5]) : null)
                            .totalCommission(totalCommission)
                            .commissionPercent(commissionPercent)
                            .commissionEnabled(commissionEnabled)
                            .build();
                })
                .sorted((a, b) -> {
                    if (b.getAvgKpiScore() == null) return -1;
                    if (a.getAvgKpiScore() == null) return 1;
                    return b.getAvgKpiScore().compareTo(a.getAvgKpiScore());
                })
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    private WaiterPerformance getOrCreateTodayPerformance(Long waiterId, Long restaurantId, LocalDate date) {
        return performanceRepository.findByWaiterIdAndPerformanceDate(waiterId, date)
                .orElseGet(() -> {
                    Waiter waiter = waiterRepository.findById(waiterId)
                            .orElseThrow(() -> new RuntimeException("Waiter not found"));
                    Restaurant restaurant = restaurantRepository.findById(restaurantId)
                            .orElseThrow(() -> new RuntimeException("Restaurant not found"));

                    WaiterPerformance performance = WaiterPerformance.builder()
                            .waiter(waiter)
                            .restaurant(restaurant)
                            .performanceDate(date)
                            .build();
                    return performanceRepository.save(performance);
                });
    }

    private void updateServiceTime(WaiterPerformance performance, int serviceTimeMinutes) {
        int currentCount = performance.getTotalOrders() > 0 ? performance.getTotalOrders() - 1 : 0;
        int currentTotal = performance.getAvgServiceTimeMinutes() * currentCount;
        int newAvg = (currentTotal + serviceTimeMinutes) / performance.getTotalOrders();
        performance.setAvgServiceTimeMinutes(newAvg);

        if (performance.getMinServiceTimeMinutes() == null || serviceTimeMinutes < performance.getMinServiceTimeMinutes()) {
            performance.setMinServiceTimeMinutes(serviceTimeMinutes);
        }
        if (performance.getMaxServiceTimeMinutes() == null || serviceTimeMinutes > performance.getMaxServiceTimeMinutes()) {
            performance.setMaxServiceTimeMinutes(serviceTimeMinutes);
        }
    }

    private void calculateAndUpdateKPIScore(WaiterPerformance performance, Long waiterId, Long restaurantId) {
        WaiterKPIConfig kpi = getEffectiveKPIConfig(waiterId, restaurantId);

        BigDecimal totalScore = BigDecimal.ZERO;
        int metricsCount = 0;

        // Orders score (0-100)
        if (kpi.getTargetOrdersPerDay() != null && kpi.getTargetOrdersPerDay() > 0) {
            BigDecimal ordersScore = BigDecimal.valueOf(performance.getTotalOrders())
                    .divide(BigDecimal.valueOf(kpi.getTargetOrdersPerDay()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(150)); // Cap at 150%
            totalScore = totalScore.add(ordersScore);
            metricsCount++;
        }

        // Revenue score
        if (kpi.getTargetRevenuePerDay() != null && kpi.getTargetRevenuePerDay().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal revenueScore = performance.getTotalRevenue()
                    .divide(kpi.getTargetRevenuePerDay(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(150));
            totalScore = totalScore.add(revenueScore);
            metricsCount++;
        }

        // Average ticket score
        if (kpi.getTargetAvgTicket() != null && kpi.getTargetAvgTicket().compareTo(BigDecimal.ZERO) > 0
                && performance.getAvgTicketValue().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ticketScore = performance.getAvgTicketValue()
                    .divide(kpi.getTargetAvgTicket(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(150));
            totalScore = totalScore.add(ticketScore);
            metricsCount++;
        }

        // Service time score (lower is better)
        if (kpi.getTargetAvgServiceTimeMinutes() != null && kpi.getTargetAvgServiceTimeMinutes() > 0
                && performance.getAvgServiceTimeMinutes() > 0) {
            BigDecimal serviceScore = BigDecimal.valueOf(kpi.getTargetAvgServiceTimeMinutes())
                    .divide(BigDecimal.valueOf(performance.getAvgServiceTimeMinutes()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(150));
            totalScore = totalScore.add(serviceScore);
            metricsCount++;
        }

        // Rating score
        if (kpi.getMinCustomerRating() != null && performance.getAvgCustomerRating() != null) {
            BigDecimal ratingScore = performance.getAvgCustomerRating()
                    .divide(kpi.getMinCustomerRating(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .min(BigDecimal.valueOf(150));
            totalScore = totalScore.add(ratingScore);
            metricsCount++;
        }

        // Calculate final KPI score
        if (metricsCount > 0) {
            BigDecimal kpiScore = totalScore.divide(BigDecimal.valueOf(metricsCount), 2, RoundingMode.HALF_UP);
            performance.setKpiScore(kpiScore);

            // Calculate bonus
            if (kpi.getBonusThresholdPercent() != null && kpi.getBonusAmountPerThreshold() != null
                    && kpiScore.compareTo(kpi.getBonusThresholdPercent()) >= 0) {
                BigDecimal bonusMultiplier = kpiScore.divide(kpi.getBonusThresholdPercent(), 2, RoundingMode.DOWN);
                performance.setBonusEarned(kpi.getBonusAmountPerThreshold().multiply(bonusMultiplier));
            }
        }
    }

    /**
     * Helper method to safely convert database results to BigDecimal.
     * Some databases return Double for aggregate functions like SUM/AVG.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Double) {
            return BigDecimal.valueOf((Double) value);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
