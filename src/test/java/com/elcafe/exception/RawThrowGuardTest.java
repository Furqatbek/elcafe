package com.elcafe.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EH-1.6 regression fence for the raw-throw sweep (docs/ERROR_HANDLING_PLAN.md): the 2026-07-12
 * sweep retyped ~460 raw throws to typed exceptions so business failures present with the right
 * status instead of a generic 500. This test keeps the codebase there:
 *
 * <ul>
 *   <li>Controllers must never throw raw {@code RuntimeException}/{@code IllegalArgumentException}/
 *       {@code IllegalStateException} — they are the HTTP boundary and must speak the contract.</li>
 *   <li>Services must not throw message-only {@code new RuntimeException("…")} — a human-written
 *       message means user-facing intent, and as a RuntimeException it presents as a generic 500
 *       that hides the message. Cause-wrapping ({@code new RuntimeException(msg, e)} /
 *       {@code new RuntimeException(e)}) stays allowed: those are internal faults where a
 *       500-generic is exactly right.</li>
 * </ul>
 *
 * If you trip this fence: use ResourceNotFoundException (404), ConflictException (409),
 * BadRequestException (400), ForbiddenException (403) — or add the file to the allowlist ONLY for
 * infra clients whose exception contract is pinned elsewhere (circuit breakers, providers).
 */
class RawThrowGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/elcafe");

    /** Infra clients with pinned RuntimeException contracts (CircuitBreakerWiringTest) or internal faults. */
    private static final Set<String> SERVICE_ALLOWLIST = Set.of(
            "modules/sms/service/SmsService.java",
            "modules/customer/service/GeocodingService.java",
            "modules/referral/service/ReferralService.java",
            "modules/financial/service/FinancialMigrationService.java"
    );

    private static final Pattern RAW_THROW =
            Pattern.compile("throw new (RuntimeException|IllegalArgumentException|IllegalStateException)\\s*\\(");
    /** Message-only literal form: `new RuntimeException("...")` with no cause argument. */
    private static final Pattern MESSAGE_ONLY_RUNTIME =
            Pattern.compile("new RuntimeException\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\)");
    /** orElseThrow lambdas count as throws too. */
    private static final Pattern LAMBDA_RAW =
            Pattern.compile("->\\s*new (RuntimeException|IllegalArgumentException|IllegalStateException)\\s*\\(");

    @Test
    @DisplayName("controllers contain zero raw RuntimeException/IllegalArgument/IllegalState throws")
    void controllersAreRawThrowFree() throws IOException {
        List<String> offenders = scan(p -> p.getFileName().toString().endsWith("Controller.java"),
                src -> RAW_THROW.matcher(src).find() || LAMBDA_RAW.matcher(src).find());
        assertThat(offenders)
                .withFailMessage("Controllers must throw typed exceptions (com.elcafe.exception.*), found raw throws in:%n%s",
                        String.join("\n", offenders))
                .isEmpty();
    }

    @Test
    @DisplayName("services contain no message-only `new RuntimeException(\"…\")` (hides the message as a 500)")
    void servicesHaveNoMessageOnlyRuntimeExceptions() throws IOException {
        List<String> offenders = scan(
                p -> p.getFileName().toString().endsWith("Service.java")
                        && SERVICE_ALLOWLIST.stream().noneMatch(a -> p.toString().replace('\\', '/').endsWith(a)),
                src -> MESSAGE_ONLY_RUNTIME.matcher(src).find());
        assertThat(offenders)
                .withFailMessage("Message-only RuntimeException presents as a generic 500 and hides the message. "
                        + "Use a typed exception instead (or allowlist an infra client):%n%s",
                        String.join("\n", offenders))
                .isEmpty();
    }

    private List<String> scan(java.util.function.Predicate<Path> fileFilter,
                              java.util.function.Predicate<String> offends) throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(fileFilter)
                    .filter(p -> {
                        try {
                            return offends.test(Files.readString(p));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }
}
