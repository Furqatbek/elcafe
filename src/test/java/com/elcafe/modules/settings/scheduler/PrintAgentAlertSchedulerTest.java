package com.elcafe.modules.settings.scheduler;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.dto.PrintAgentStatusResponse;
import com.elcafe.modules.settings.service.PrintAgentStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The alert that reaches somebody who is not looking at a screen.
 *
 * <p>Its value is destroyed by two opposite failures and both are easy to write. Alert too readily —
 * on a venue with no agent and no orders, or once per sweep for an hour — and everybody learns the
 * messages are noise, after which a real outage is ignored too. Alert too narrowly and a kitchen that
 * stopped printing says nothing.
 *
 * <p>So what is pinned here is mostly the silence: when it must not fire, and how often it may.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintAgentAlertSchedulerTest {

    private static final Long VENUE = 3L;

    @Mock private PrintAgentStatusService statusService;
    @Mock private OwnerNotificationService notificationService;
    @Mock private RestaurantRepository restaurantRepository;

    private PrintAgentAlertScheduler scheduler;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        scheduler = new PrintAgentAlertScheduler(statusService, notificationService, restaurantRepository);
        ReflectionTestUtils.setField(scheduler, "alertAfterMinutes", 10L);
        ReflectionTestUtils.setField(scheduler, "alertCooldownMinutes", 30L);

        restaurant = Restaurant.builder().name("Callback Cafe").build();
        restaurant.setId(VENUE);
    }

    private void venueStatus(PrintAgentStatusResponse.State state, long queued, Long oldestMinutes) {
        when(statusService.statusFor(VENUE)).thenReturn(PrintAgentStatusResponse.builder()
                .state(state)
                .queuedJobs(queued)
                .oldestQueuedMinutes(oldestMinutes)
                .build());
    }

    @Test
    @DisplayName("tickets stuck past the threshold reach somebody's phone")
    void stuckTickets_alert() {
        venueStatus(PrintAgentStatusResponse.State.OFFLINE, 6, 25L);

        scheduler.checkVenue(restaurant);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendCriticalAlert(eq(VENUE), title.capture(), body.capture());
        assertThat(title.getValue()).contains("No print agent");
        assertThat(body.getValue()).contains("6 ticket(s)").contains("25 minutes");
        // A venue that believes orders are being lost starts writing them out by hand, and then the
        // queue prints as well and the kitchen cooks everything twice.
        assertThat(body.getValue()).contains("Nothing is lost");
    }

    @Test
    @DisplayName("a venue with no agent and nothing waiting is never nagged")
    void noAgentNoTickets_isSilent() {
        // Most venues on most days: no agent installed, or closed for the night. The trigger is tickets
        // sitting unprinted, not an agent being absent — alerting here is how an alert becomes noise.
        venueStatus(PrintAgentStatusResponse.State.OFFLINE, 0, null);

        scheduler.checkVenue(restaurant);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("a ticket in flight for a minute is not an outage")
    void briefWait_isSilent() {
        venueStatus(PrintAgentStatusResponse.State.ONLINE, 2, 1L);

        scheduler.checkVenue(restaurant);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("a venue is told once per outage, not once per sweep")
    void repeatedSweeps_alertOnce() {
        venueStatus(PrintAgentStatusResponse.State.BACKLOG, 4, 20L);

        // The scheduler runs every five minutes. An hour-long outage must not be twelve messages, or
        // the thirteenth — about something else — is already being ignored.
        scheduler.checkVenue(restaurant);
        scheduler.checkVenue(restaurant);
        scheduler.checkVenue(restaurant);

        verify(notificationService, times(1)).sendCriticalAlert(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("a healthy agent in front of a dead printer says so, and sends nobody to the computer")
    void backlog_pointsAtThePrinter() {
        venueStatus(PrintAgentStatusResponse.State.BACKLOG, 3, 15L);

        scheduler.checkVenue(restaurant);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendCriticalAlert(eq(VENUE), anyString(), body.capture());
        // The two causes need two different people, and the walk to the wrong end of the room costs
        // whatever piles up while somebody takes it.
        assertThat(body.getValue()).contains("paper, power, or a jam");
        assertThat(body.getValue()).doesNotContain("is on and has internet");
    }

    @Test
    @DisplayName("an agent that went quiet sends somebody to the computer instead")
    void stale_pointsAtTheComputer() {
        venueStatus(PrintAgentStatusResponse.State.STALE, 3, 15L);

        scheduler.checkVenue(restaurant);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendCriticalAlert(eq(VENUE), anyString(), body.capture());
        assertThat(body.getValue()).contains("kitchen computer");
        assertThat(body.getValue()).doesNotContain("paper, power, or a jam");
    }

    @Test
    @DisplayName("a venue told it was broken is told when it is fixed")
    void recovery_isAnnounced() {
        venueStatus(PrintAgentStatusResponse.State.OFFLINE, 5, 30L);
        scheduler.checkVenue(restaurant);

        venueStatus(PrintAgentStatusResponse.State.ONLINE, 0, null);
        scheduler.checkVenue(restaurant);

        // Otherwise the only way to learn it recovered is to open the screen this alert existed to
        // avoid needing, and somebody keeps worrying about a problem that ended an hour ago.
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).sendCriticalAlert(eq(VENUE), title.capture(), anyString());
        assertThat(title.getAllValues().get(1)).contains("recovered");
    }

    @Test
    @DisplayName("a venue that was never in trouble gets no all-clear")
    void healthyVenue_getsNothing() {
        venueStatus(PrintAgentStatusResponse.State.ONLINE, 0, null);

        scheduler.checkVenue(restaurant);
        scheduler.checkVenue(restaurant);

        verify(notificationService, never()).sendCriticalAlert(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("after recovering, the next outage alerts again rather than being held by the cooldown")
    void newOutageAfterRecovery_alertsAgain() {
        venueStatus(PrintAgentStatusResponse.State.OFFLINE, 5, 30L);
        scheduler.checkVenue(restaurant);

        venueStatus(PrintAgentStatusResponse.State.ONLINE, 0, null);
        scheduler.checkVenue(restaurant);

        venueStatus(PrintAgentStatusResponse.State.OFFLINE, 2, 12L);
        scheduler.checkVenue(restaurant);

        // Recovery clears the cooldown as well as the state. A second outage inside half an hour is a
        // worse sign than the first, and would have been exactly the one suppressed.
        verify(notificationService, times(3)).sendCriticalAlert(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("one venue's failure does not stop the others being checked")
    void oneBadVenue_doesNotStopTheSweep() {
        Restaurant other = Restaurant.builder().name("Second Venue").build();
        other.setId(99L);
        when(restaurantRepository.findByActiveTrue()).thenReturn(java.util.List.of(restaurant, other));
        when(statusService.statusFor(VENUE)).thenThrow(new RuntimeException("venue has no coordinates"));
        when(statusService.statusFor(99L)).thenReturn(PrintAgentStatusResponse.builder()
                .state(PrintAgentStatusResponse.State.OFFLINE).queuedJobs(3).oldestQueuedMinutes(40L)
                .build());

        scheduler.checkForUnprintedTickets();

        verify(notificationService).sendCriticalAlert(eq(99L), anyString(), anyString());
    }

    @Test
    @DisplayName("the sweep reads every active venue")
    void sweep_coversActiveVenues() {
        when(restaurantRepository.findByActiveTrue()).thenReturn(java.util.List.of(restaurant));
        venueStatus(PrintAgentStatusResponse.State.ONLINE, 0, null);

        scheduler.checkForUnprintedTickets();

        verify(statusService).statusFor(VENUE);
        verify(notificationService, never()).sendCriticalAlert(anyLong(), any(), any());
    }
}
