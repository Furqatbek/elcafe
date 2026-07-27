package com.elcafe.modules.customer.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.dto.StaffRegistrationReport;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.repository.BonusTransactionRepository;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Makes the till-side registration door reviewable.
 *
 * <p>The welcome bonus has two front doors. The online one proves the phone with an OTP before crediting
 * anything; the counter one does not, because a cashier cannot hold a queue while a guest reads back six
 * digits. The design accepted that trade explicitly, on the condition that the unverified door be
 * <em>visible</em>: {@code Customer.registeredByUserId} has been stamped since V182 precisely so that
 * "registrations per employee" could be answered later. Nothing read it until this service — which meant
 * the guard existed on paper and not in the product.
 *
 * <p>The report deliberately reports two different things:
 * <ul>
 *   <li><b>Registrations</b> — volume per employee per day. An outlier is meant to be visible at a
 *       glance rather than found in an audit six months later.</li>
 *   <li><b>Reached</b> — how many of those guests an SMS has actually been delivered to. This is the
 *       number that says whether the door is <em>working</em>. A mistyped digit still produces a
 *       customer with visit history and a credited bonus who can never be contacted again, and volume
 *       alone cannot tell that apart from genuine signups.</li>
 * </ul>
 *
 * <p><b>Days, not shifts.</b> The original note asked for "per employee per shift". A customer row
 * carries a timestamp, not a shift id, so attributing one to a shift would mean stamping {@code shift_id}
 * at registration — a schema change, and one that would only be right for restaurants whose shifts do
 * not overlap. Days are what the data actually supports, and they answer the example the note itself
 * gave ("forty guests on a quiet Tuesday").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffRegistrationReportService {

    /**
     * The window is capped because the query returns rows, not a count. Three months is longer than any
     * "is this cashier padding numbers" question needs, and short enough that the report can never be
     * turned into a full customer export by widening the dates.
     */
    private static final int MAX_WINDOW_DAYS = 92;
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** The clock the restaurant works in — a "day" on this report is the day the staff lived through. */
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Tashkent");

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final SmsLogRepository smsLogRepository;
    private final BonusTransactionRepository bonusTransactionRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional(readOnly = true)
    public StaffRegistrationReport report(LocalDate from, LocalDate to) {
        Long restaurantId = restaurantAuthorizationService.currentTenantReadScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "Staff registrations belong to a restaurant. Sign in with a restaurant-scoped account.");
        }

        LocalDate end = to != null ? to : LocalDate.now(REPORT_ZONE);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date must not be after the end date.");
        }
        if (start.plusDays(MAX_WINDOW_DAYS).isBefore(end)) {
            throw new BadRequestException(
                    "The window cannot exceed " + MAX_WINDOW_DAYS + " days. Narrow the dates.");
        }

        // `to` is inclusive to the operator, so the query's upper bound is the start of the next day —
        // an exclusive bound on a timestamp column, which is the only form that cannot drop the last
        // day's evening registrations.
        OffsetDateTime windowStart = start.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        OffsetDateTime windowEnd = end.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();

        List<Customer> registrations =
                customerRepository.findStaffRegistrationsInWindow(restaurantId, windowStart, windowEnd);

        if (registrations.isEmpty()) {
            return StaffRegistrationReport.builder()
                    .from(start).to(end)
                    .totalRegistrations(0).totalReached(0)
                    .totalBonusGranted(BigDecimal.ZERO)
                    .staff(List.of())
                    .build();
        }

        List<Long> customerIds = registrations.stream().map(Customer::getId).toList();
        Set<Long> reachedIds = new HashSet<>(smsLogRepository.findCustomerIdsWithDeliveredSms(customerIds));
        Map<Long, BigDecimal> bonusByCustomer = bonusByCustomer(customerIds);
        Map<Long, User> staffById = staffById(registrations);

        Map<Long, List<Customer>> byStaff = registrations.stream()
                .collect(Collectors.groupingBy(Customer::getRegisteredByUserId));

        List<StaffRegistrationReport.StaffRow> rows = byStaff.entrySet().stream()
                .map(entry -> buildRow(entry.getKey(), entry.getValue(), reachedIds, bonusByCustomer,
                        staffById.get(entry.getKey())))
                .sorted(Comparator.comparingInt(StaffRegistrationReport.StaffRow::getRegistrations)
                        .reversed()
                        .thenComparing(StaffRegistrationReport.StaffRow::getStaffName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return StaffRegistrationReport.builder()
                .from(start)
                .to(end)
                .totalRegistrations(registrations.size())
                .totalReached((int) customerIds.stream().filter(reachedIds::contains).count())
                .totalBonusGranted(bonusByCustomer.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .staff(rows)
                .build();
    }

    private StaffRegistrationReport.StaffRow buildRow(Long userId, List<Customer> theirs,
                                                      Set<Long> reachedIds,
                                                      Map<Long, BigDecimal> bonusByCustomer,
                                                      User staff) {
        int registrations = theirs.size();
        int reached = (int) theirs.stream().filter(c -> reachedIds.contains(c.getId())).count();
        BigDecimal bonus = theirs.stream()
                .map(c -> bonusByCustomer.getOrDefault(c.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Only days with registrations appear. Padding the range with zeroes would triple the payload
        // for a chart that reads the same either way, and the report is about where the spikes are.
        Map<LocalDate, Integer> perDay = new LinkedHashMap<>();
        for (Customer customer : theirs) {
            LocalDate day = customer.getCreatedAt().atZoneSameInstant(REPORT_ZONE).toLocalDate();
            perDay.merge(day, 1, Integer::sum);
        }
        List<StaffRegistrationReport.DailyCount> daily = new ArrayList<>();
        perDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> daily.add(StaffRegistrationReport.DailyCount.builder()
                        .date(e.getKey()).registrations(e.getValue()).build()));

        return StaffRegistrationReport.StaffRow.builder()
                .userId(userId)
                // A deleted or reassigned employee must still show their history — falling back to the
                // id keeps the row auditable instead of dropping it.
                .staffName(staff != null ? staff.getFullName() : "#" + userId)
                .role(staff != null && staff.getRole() != null ? staff.getRole().name() : null)
                .registrations(registrations)
                .reached(reached)
                .reachRatePercent(registrations == 0 ? null
                        : BigDecimal.valueOf(reached * 100L)
                                .divide(BigDecimal.valueOf(registrations), 0, RoundingMode.HALF_UP)
                                .intValue())
                .bonusGranted(bonus)
                .daily(daily)
                .build();
    }

    private Map<Long, BigDecimal> bonusByCustomer(List<Long> customerIds) {
        Map<Long, BigDecimal> byCustomer = new HashMap<>();
        for (Object[] row : bonusTransactionRepository.sumRegistrationBonusByCustomer(customerIds)) {
            if (row.length < 2 || row[0] == null) {
                continue;
            }
            byCustomer.put(((Number) row[0]).longValue(),
                    row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
        }
        return byCustomer;
    }

    private Map<Long, User> staffById(List<Customer> registrations) {
        List<Long> userIds = registrations.stream()
                .map(Customer::getRegisteredByUserId)
                .distinct()
                .toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }
}
