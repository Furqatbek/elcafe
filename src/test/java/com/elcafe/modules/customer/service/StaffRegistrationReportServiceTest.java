package com.elcafe.modules.customer.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.dto.StaffRegistrationReport;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.repository.BonusTransactionRepository;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The report that makes the unverified till door reviewable.
 *
 * <p>Three things carry real weight. <b>Reachability</b> must count delivered SMS and nothing weaker —
 * the whole point is distinguishing forty real signups from forty typos, and counting "sent" would
 * flatter exactly the numbers under scrutiny. <b>The window</b> must be bounded, because this endpoint
 * returns customer rows and an unbounded date range turns an oversight report into a data export.
 * <b>Attribution</b> must survive a deleted employee, or the rows that most need reviewing vanish.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffRegistrationReportServiceTest {

    private static final Long TENANT = 4L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private SmsLogRepository smsLogRepository;
    @Mock private BonusTransactionRepository bonusTransactionRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private StaffRegistrationReportService service;

    @BeforeEach
    void setUp() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(customerRepository.findStaffRegistrationsInWindow(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(smsLogRepository.findCustomerIdsWithDeliveredSms(any())).thenReturn(List.of());
        when(bonusTransactionRepository.sumRegistrationBonusByCustomer(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    // -------------------------------------------------------------------------------------------
    // Attribution
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("registrations are attributed to the employee who made them, busiest first")
    void groupsByEmployeeBusiestFirst() {
        given(
                registration(1L, 10L, day(2026, 7, 20)),
                registration(2L, 10L, day(2026, 7, 20)),
                registration(3L, 10L, day(2026, 7, 21)),
                registration(4L, 11L, day(2026, 7, 21)));
        withStaff(user(10L, "Aziza", "Yusupova", UserRole.CASHIER),
                  user(11L, "Bek", "Toshev", UserRole.WAITER));

        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 21));

        assertThat(report.getTotalRegistrations()).isEqualTo(4);
        assertThat(report.getStaff()).hasSize(2);
        assertThat(report.getStaff().get(0).getStaffName()).isEqualTo("Aziza Yusupova");
        assertThat(report.getStaff().get(0).getRegistrations()).isEqualTo(3);
        assertThat(report.getStaff().get(0).getRole()).isEqualTo("CASHIER");
        assertThat(report.getStaff().get(1).getRegistrations()).isEqualTo(1);
    }

    /** "Forty guests on a quiet Tuesday" is the exact shape this report exists to make visible. */
    @Test
    @DisplayName("the daily breakdown keeps spikes visible instead of averaging them away")
    void dailyBreakdownPerEmployee() {
        given(
                registration(1L, 10L, day(2026, 7, 20)),
                registration(2L, 10L, day(2026, 7, 22)),
                registration(3L, 10L, day(2026, 7, 22)),
                registration(4L, 10L, day(2026, 7, 22)));
        withStaff(user(10L, "Aziza", "Yusupova", UserRole.CASHIER));

        List<StaffRegistrationReport.DailyCount> daily =
                service.report(day(2026, 7, 20), day(2026, 7, 22)).getStaff().get(0).getDaily();

        assertThat(daily).extracting(StaffRegistrationReport.DailyCount::getDate)
                .containsExactly(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22));
        assertThat(daily).extracting(StaffRegistrationReport.DailyCount::getRegistrations)
                .containsExactly(1, 3);
    }

    /**
     * The employee most worth reviewing is the one who has since left. Dropping their rows — or
     * blanking the name — would quietly hide exactly the history an audit is looking for.
     */
    @Test
    @DisplayName("a deleted employee still gets a row, identified by id")
    void deletedEmployeeStillReported() {
        given(registration(1L, 99L, day(2026, 7, 20)));
        when(userRepository.findAllById(any())).thenReturn(List.of()); // that user is gone

        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 20));

        assertThat(report.getStaff()).hasSize(1);
        assertThat(report.getStaff().get(0).getUserId()).isEqualTo(99L);
        assertThat(report.getStaff().get(0).getStaffName()).isEqualTo("#99");
        assertThat(report.getStaff().get(0).getRole()).isNull();
    }

    // -------------------------------------------------------------------------------------------
    // Reachability — the number that says whether the door works
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("reach counts only guests an SMS actually reached, not everyone with a phone")
    void reachCountsDeliveredOnly() {
        given(
                registration(1L, 10L, day(2026, 7, 20)),
                registration(2L, 10L, day(2026, 7, 20)),
                registration(3L, 10L, day(2026, 7, 20)),
                registration(4L, 10L, day(2026, 7, 20)));
        withStaff(user(10L, "Aziza", "Yusupova", UserRole.CASHIER));
        // Only one of the four numbers ever received anything.
        when(smsLogRepository.findCustomerIdsWithDeliveredSms(any())).thenReturn(List.of(2L));

        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 20));

        assertThat(report.getTotalReached()).isEqualTo(1);
        assertThat(report.getStaff().get(0).getReached()).isEqualTo(1);
        assertThat(report.getStaff().get(0).getReachRatePercent()).isEqualTo(25);
    }

    /**
     * The query, not the arithmetic. If this ever counted SENT — "the broker accepted it" — a wall of
     * typo'd numbers would report as fully reachable, which is the one answer the report must never give.
     */
    @Test
    @DisplayName("reachability asks for delivered SMS, and only for the guests in the window")
    void reachabilityQueryIsScopedToTheWindow() {
        given(registration(1L, 10L, day(2026, 7, 20)), registration(2L, 10L, day(2026, 7, 20)));

        service.report(day(2026, 7, 20), day(2026, 7, 20));

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(smsLogRepository).findCustomerIdsWithDeliveredSms(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    // -------------------------------------------------------------------------------------------
    // Money
    // -------------------------------------------------------------------------------------------

    /**
     * Read from the actual grants, never from headcount × today's configured amount — the config
     * changes, and the operator is checking what was really credited.
     */
    @Test
    @DisplayName("bonus totals come from the recorded grants, per employee")
    void bonusTotalsFromRecordedGrants() {
        given(
                registration(1L, 10L, day(2026, 7, 20)),
                registration(2L, 10L, day(2026, 7, 20)),
                registration(3L, 11L, day(2026, 7, 20)));
        withStaff(user(10L, "Aziza", "Yusupova", UserRole.CASHIER),
                  user(11L, "Bek", "Toshev", UserRole.WAITER));
        when(bonusTransactionRepository.sumRegistrationBonusByCustomer(any())).thenReturn(List.of(
                new Object[]{1L, new BigDecimal("10000")},
                new Object[]{2L, new BigDecimal("10000")},
                new Object[]{3L, new BigDecimal("5000")}));

        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 20));

        assertThat(report.getTotalBonusGranted()).isEqualByComparingTo("25000");
        assertThat(report.getStaff().get(0).getBonusGranted()).isEqualByComparingTo("20000");
        assertThat(report.getStaff().get(1).getBonusGranted()).isEqualByComparingTo("5000");
    }

    /** A guest whose bonus was skipped (config off, or already credited) contributes zero, not null. */
    @Test
    @DisplayName("a registration with no bonus recorded counts as zero rather than breaking the total")
    void missingBonusIsZero() {
        given(registration(1L, 10L, day(2026, 7, 20)));
        when(bonusTransactionRepository.sumRegistrationBonusByCustomer(any())).thenReturn(List.of());

        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 20));

        assertThat(report.getStaff().get(0).getBonusGranted()).isEqualByComparingTo("0");
        assertThat(report.getTotalBonusGranted()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------------------------
    // Window bounds
    // -------------------------------------------------------------------------------------------

    /**
     * This endpoint returns customer rows. Without a cap, "from 1970" turns an oversight report into a
     * full customer export for anyone who can read it.
     */
    @Test
    @DisplayName("an oversized window is refused rather than quietly truncated")
    void oversizedWindowIsRefused() {
        assertThatThrownBy(() -> service.report(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 7, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("92");
        verify(customerRepository, never()).findStaffRegistrationsInWindow(anyLong(), any(), any());
    }

    @Test
    @DisplayName("a backwards range is refused rather than returning nothing and looking clean")
    void backwardsRangeIsRefused() {
        assertThatThrownBy(() -> service.report(day(2026, 7, 20), day(2026, 7, 10)))
                .isInstanceOf(BadRequestException.class);
        verify(customerRepository, never()).findStaffRegistrationsInWindow(anyLong(), any(), any());
    }

    /**
     * The upper bound is exclusive on the NEXT day's midnight. An inclusive-looking
     * {@code <= end.atStartOfDay()} would silently drop every registration made after midnight on the
     * last day of the range — which is most of a dinner service.
     */
    @Test
    @DisplayName("the last day of the range includes its whole evening, not just its first instant")
    void endDateCoversTheWholeDay() {
        service.report(day(2026, 7, 20), day(2026, 7, 21));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(customerRepository).findStaffRegistrationsInWindow(eq(TENANT), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 7, 20).atStartOfDay(ZONE).toOffsetDateTime());
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 7, 22).atStartOfDay(ZONE).toOffsetDateTime());
    }

    // -------------------------------------------------------------------------------------------
    // Scoping
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a caller with no restaurant is refused rather than shown every restaurant's staff")
    void tenantlessCallerIsRefused() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);

        assertThatThrownBy(() -> service.report(day(2026, 7, 20), day(2026, 7, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("restaurant");
    }

    @Test
    @DisplayName("a quiet window reports zeroes instead of failing")
    void emptyWindowIsEmptyNotBroken() {
        StaffRegistrationReport report = service.report(day(2026, 7, 20), day(2026, 7, 20));

        assertThat(report.getTotalRegistrations()).isZero();
        assertThat(report.getTotalBonusGranted()).isEqualByComparingTo("0");
        assertThat(report.getStaff()).isEmpty();
        // Nothing to look up, so the follow-up queries are never made.
        verify(smsLogRepository, never()).findCustomerIdsWithDeliveredSms(any());
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    private void given(Customer... customers) {
        when(customerRepository.findStaffRegistrationsInWindow(anyLong(), any(), any()))
                .thenReturn(List.of(customers));
    }

    private void withStaff(User... users) {
        when(userRepository.findAllById(any())).thenReturn(List.of(users));
    }

    private static LocalDate day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }

    /** Registered at 19:30 local — deliberately in the evening, so a bad upper bound drops it. */
    private static Customer registration(Long id, Long staffId, LocalDate on) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setRestaurantId(TENANT);
        customer.setRegisteredByUserId(staffId);
        customer.setCreatedAt(on.atTime(19, 30).atZone(ZONE).toOffsetDateTime());
        return customer;
    }

    private static User user(Long id, String first, String last, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setRole(role);
        return user;
    }
}
