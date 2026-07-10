package com.elcafe.scheduling;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the E0 scale decision: every {@code @Scheduled} job that touches shared state
 * must carry {@code @SchedulerLock}, so a second app instance (deliberate scale-out or the transient
 * overlap of a rolling deploy) cannot double-run it — double SMS/Telegram sends, double salary
 * payments, duplicate print jobs.
 *
 * <p>Scans every Spring component under {@code com.elcafe} for {@code @Scheduled} methods. A new cron
 * added without a lock fails here, forcing the author to either lock it or consciously add it to the
 * node-local allowlist below.
 */
class SchedulerLockGuardTest {

    /**
     * Jobs that sweep this node's OWN in-memory state — every instance must run them locally, so
     * locking them would be a bug (all but one node would leak). Additions need the same justification.
     */
    private static final Set<String> NODE_LOCAL_ALLOWLIST = Set.of(
            "com.elcafe.config.RateLimitConfig#evictRateLimitBuckets",
            "com.elcafe.modules.auth.service.LoginAttemptService#evictStale");

    private record Job(String id, Method method, SchedulerLock lock) {}

    private List<Job> scanScheduledJobs() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<Job> jobs = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.elcafe")) {
            Class<?> clazz = Class.forName(bd.getBeanClassName());
            for (Method m : clazz.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(m, Scheduled.class)) {
                    jobs.add(new Job(clazz.getName() + "#" + m.getName(), m,
                            AnnotatedElementUtils.findMergedAnnotation(m, SchedulerLock.class)));
                }
            }
        }
        return jobs;
    }

    @Test
    @DisplayName("every @Scheduled job is @SchedulerLock'd or on the node-local allowlist")
    void everyScheduledJobIsLockedOrAllowlisted() throws Exception {
        List<Job> jobs = scanScheduledJobs();
        // Sanity: the scan actually found the fleet (30 locked + 2 node-local at the time of writing).
        assertThat(jobs).as("classpath scan should find the scheduled jobs").hasSizeGreaterThanOrEqualTo(30);

        List<String> unlocked = jobs.stream()
                .filter(j -> j.lock() == null && !NODE_LOCAL_ALLOWLIST.contains(j.id()))
                .map(Job::id)
                .toList();
        assertThat(unlocked)
                .as("@Scheduled without @SchedulerLock — lock it, or (only for jobs sweeping "
                        + "node-local in-memory state) add it to NODE_LOCAL_ALLOWLIST with justification")
                .isEmpty();

        List<String> wronglyLocked = jobs.stream()
                .filter(j -> j.lock() != null && NODE_LOCAL_ALLOWLIST.contains(j.id()))
                .map(Job::id)
                .toList();
        assertThat(wronglyLocked)
                .as("node-local jobs must NOT be locked (each instance has to run its own sweep)")
                .isEmpty();
    }

    @Test
    @DisplayName("lock names are unique and fit the shedlock.name column (64 chars)")
    void lockNamesAreUniqueAndFitColumn() throws Exception {
        List<String> names = scanScheduledJobs().stream()
                .map(Job::lock)
                .filter(l -> l != null)
                .map(SchedulerLock::name)
                .toList();

        assertThat(names).allSatisfy(n -> {
            assertThat(n).as("lock name must be set").isNotBlank();
            assertThat(n.length()).as("lock name '%s' must fit VARCHAR(64)", n).isLessThanOrEqualTo(64);
        });
        Set<String> seen = new HashSet<>();
        List<String> dupes = names.stream().filter(n -> !seen.add(n)).toList();
        assertThat(dupes).as("duplicate lock names would serialize unrelated jobs against each other").isEmpty();
    }
}
