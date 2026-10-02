package com.elcafe.modules.settings.scheduler;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.dto.PrintAgentStatusResponse;
import com.elcafe.modules.settings.service.PrintAgentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells somebody when a kitchen's tickets have stopped coming out.
 *
 * <p>The status card answers this for anyone already looking at Printer Settings, which during service
 * is nobody. This is the half that reaches a person who is not looking at a screen, over the owner's
 * Telegram — the same channel low stock already uses.
 *
 * <p><b>The trigger is tickets sitting unprinted, not an agent being absent.</b> That distinction is
 * the whole design. A venue that has never installed an agent, or has closed for the night, has no
 * agent and no problem; alerting it would teach everyone that these messages are noise. A venue with
 * orders on the rail and nothing on paper has a problem whatever the reason, and the reason only
 * shapes the wording.
 *
 * <p>Deliberately later than the on-screen warning. The card turns amber at
 * {@code app.printing.backlog-after-minutes}; this waits until
 * {@code app.printing.alert-after-minutes}, so the screen gets a chance first and the phone only rings
 * if nobody acted on it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintAgentAlertScheduler {

    /** How long a ticket must sit before this reaches for somebody's phone. */
    @Value("${app.printing.alert-after-minutes:10}")
    private long alertAfterMinutes;

    /** How long before the same venue can be told again while it is still broken. */
    @Value("${app.printing.alert-cooldown-minutes:30}")
    private long alertCooldownMinutes;

    private final PrintAgentStatusService printAgentStatusService;
    private final OwnerNotificationService notificationService;
    private final RestaurantRepository restaurantRepository;

    /**
     * When each venue was last told, and which are currently in trouble.
     *
     * <p>In memory, which costs one thing and is worth naming: a restart during an outage can repeat
     * one alert, and can lose the all-clear for an outage that resolves across it. Both are mild — a
     * duplicate message and a missing "fixed" — where the alternative is a table and a migration for
     * state whose entire lifetime is a few hours. The durable record of what was sent is the owner
     * notification log, which this does not replace.
     *
     * <p>It does assume the single replica this deployment already documents; two would each keep their
     * own idea of who has been told.
     */
    private final Map<Long, Instant> lastAlertedAt = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> inTrouble = new ConcurrentHashMap<>();

    /**
     * Every five minutes while a kitchen could plausibly be open.
     *
     * <p>Not overnight. These alerts bypass quiet hours on purpose — a venue mid-service at 22:30 must
     * be told now — and that is exactly why the window has an end: the same urgency at 04:00 is a phone
     * buzzing about a backlog nobody can act on until morning.
     */
    @Scheduled(cron = "0 */5 7-23 * * *")
    @SchedulerLock(name = "print-agent-unprinted-alerts", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void checkForUnprintedTickets() {
        List<Restaurant> restaurants = restaurantRepository.findByActiveTrue();

        for (Restaurant restaurant : restaurants) {
            try {
                checkVenue(restaurant);
            } catch (Exception e) {
                // One venue's bad data must not stop the rest being checked.
                log.error("Could not check unprinted tickets for restaurant {}: {}",
                        restaurant.getName(), e.getMessage());
            }
        }
    }

    void checkVenue(Restaurant restaurant) {
        Long venueId = restaurant.getId();
        PrintAgentStatusResponse status = printAgentStatusService.statusFor(venueId);

        Long waiting = status.getOldestQueuedMinutes();
        boolean stuck = waiting != null && waiting >= alertAfterMinutes && status.getQueuedJobs() > 0;

        if (!stuck) {
            clearedIfWasInTrouble(restaurant, status);
            return;
        }

        inTrouble.put(venueId, true);
        if (!cooldownElapsed(venueId)) {
            return;
        }

        notificationService.sendCriticalAlert(venueId, headline(status), details(status));
        lastAlertedAt.put(venueId, Instant.now());
        log.warn("Alerted restaurant {} — {} ticket(s) unprinted, oldest {} min, agent state {}",
                restaurant.getName(), status.getQueuedJobs(), waiting, status.getState());
    }

    /**
     * A venue told its kitchen was not printing is owed the news that it is again.
     *
     * <p>Without it the only way to find out is to open the screen the alert existed to avoid needing,
     * and somebody keeps worrying about a problem that ended an hour ago.
     */
    private void clearedIfWasInTrouble(Restaurant restaurant, PrintAgentStatusResponse status) {
        if (!Boolean.TRUE.equals(inTrouble.remove(restaurant.getId()))) {
            return;
        }
        lastAlertedAt.remove(restaurant.getId());
        notificationService.sendCriticalAlert(restaurant.getId(),
                "Printing has recovered",
                "Kitchen tickets are coming out again. Nothing is waiting to print.");
        log.info("Printing recovered for restaurant {} (state {})",
                restaurant.getName(), status.getState());
    }

    private boolean cooldownElapsed(Long venueId) {
        Instant last = lastAlertedAt.get(venueId);
        return last == null
                || Duration.between(last, Instant.now()).toMinutes() >= alertCooldownMinutes;
    }

    private String headline(PrintAgentStatusResponse status) {
        return switch (status.getState()) {
            // The agent is healthy, so the machine is not the problem and saying so saves a trip.
            case BACKLOG, ONLINE -> "Kitchen tickets are not printing";
            case STALE -> "Kitchen print agent has stopped responding";
            case OFFLINE -> "No print agent is connected to the kitchen";
        };
    }

    /**
     * Says where to look, because the two causes need two different people.
     *
     * <p>A connected agent in front of a jammed printer and a kitchen machine that is switched off read
     * the same from a queue length, and sending somebody to the wrong end of the room costs the orders
     * that pile up while they are there.
     */
    private String details(PrintAgentStatusResponse status) {
        String scale = String.format("%d ticket(s) waiting, oldest %d minutes.",
                status.getQueuedJobs(), status.getOldestQueuedMinutes());

        String where = switch (status.getState()) {
            case BACKLOG, ONLINE -> "The kitchen computer is online, so check the printer itself — "
                    + "paper, power, or a jam.";
            case STALE -> "We have stopped hearing from the kitchen computer. Check that it is on and "
                    + "has internet.";
            case OFFLINE -> "No print agent is connected. Check that the kitchen computer is on and the "
                    + "print agent is running.";
        };

        // Worth saying every time: a venue that thinks orders are being lost starts writing them out by
        // hand, and then the queue prints as well and the kitchen cooks everything twice.
        return scale + "\n\n" + where
                + "\n\nNothing is lost — these tickets will print as soon as the problem is fixed.";
    }
}
