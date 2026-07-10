package com.elcafe.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.status.Status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the {@code logback-spring.xml} console-format switch (audit F3): {@code LOG_FORMAT=json} must
 * emit one JSON object per line carrying level, logger, message, and the MDC (requestId from
 * {@code RequestIdFilter}, tenantId from {@code TenantContext}); any other value — including unset —
 * must keep the plain Spring Boot console. Loads the real config file into a fresh, throwaway
 * {@link LoggerContext} (plain Joran parses it: the file deliberately uses no spring-only tags), so a
 * malformed config or a broken conditional fails here instead of at production boot.
 */
class LogFormatSwitchTest {

    @TempDir
    Path tempDir;

    private LoggerContext load(String logFormat) throws Exception {
        LoggerContext ctx = new LoggerContext();
        ctx.setMDCAdapter(new LogbackMDCAdapter());
        // The file appender include consumes LOG_FILE with no fallback default; at runtime Boot always
        // sets it from logging.file.name. The tests point it at a scratch file.
        ctx.putProperty("LOG_FILE", tempDir.resolve("test.log").toString());
        if (logFormat != null) {
            ctx.putProperty("LOG_FORMAT", logFormat);
        }
        JoranConfigurator joran = new JoranConfigurator();
        joran.setContext(ctx);
        joran.doConfigure(getClass().getResource("/logback-spring.xml"));
        List<String> errors = ctx.getStatusManager().getCopyOfStatusList().stream()
                .filter(s -> s.getLevel() == Status.ERROR)
                .map(Status::toString)
                .toList();
        assertThat(errors).as("logback-spring.xml must configure without errors").isEmpty();
        return ctx;
    }

    private LoggingEvent event(LoggerContext ctx, Level level, String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), ctx.getLogger("com.elcafe.Smoke"), level, message, null, null);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    @SuppressWarnings("unchecked")
    private Encoder<ILoggingEvent> consoleEncoder(LoggerContext ctx) {
        Appender<ILoggingEvent> console = ctx.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
        assertThat(console).as("root must have a CONSOLE appender").isInstanceOf(ConsoleAppender.class);
        return ((ConsoleAppender<ILoggingEvent>) console).getEncoder();
    }

    @Test
    @DisplayName("LOG_FORMAT=json: console emits parseable JSON with level, logger, message, and the MDC keys")
    void jsonModeEmitsStructuredLines() throws Exception {
        LoggerContext ctx = load("json");
        try {
            Encoder<ILoggingEvent> encoder = consoleEncoder(ctx);
            assertThat(encoder).isInstanceOf(LogstashEncoder.class);

            String line = new String(encoder.encode(event(ctx, Level.WARN, "structured line",
                    Map.of("requestId", "req-123", "tenantId", "42"))), StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(line);
            assertThat(json.get("level").asText()).isEqualTo("WARN");
            assertThat(json.get("logger_name").asText()).isEqualTo("com.elcafe.Smoke");
            assertThat(json.get("message").asText()).isEqualTo("structured line");
            // The two correlation keys the aggregation story depends on (accept criteria of F3).
            assertThat(json.get("requestId").asText()).isEqualTo("req-123");
            assertThat(json.get("tenantId").asText()).isEqualTo("42");

            // The rolling file is the on-box grep artifact and stays plain even in json mode.
            Appender<ILoggingEvent> file = ctx.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE");
            assertThat(file).isInstanceOf(RollingFileAppender.class);
            assertThat(((RollingFileAppender<ILoggingEvent>) file).getEncoder())
                    .isNotInstanceOf(LogstashEncoder.class);
        } finally {
            ctx.stop();
        }
    }

    @Test
    @DisplayName("LOG_FORMAT unset: console stays plain text (the pre-F3 default)")
    void defaultStaysPlain() throws Exception {
        assumeTrue(System.getenv("LOG_FORMAT") == null,
                "this test asserts the unset default; the environment overrides it");
        LoggerContext ctx = load(null);
        try {
            Encoder<ILoggingEvent> encoder = consoleEncoder(ctx);
            assertThat(encoder).isNotInstanceOf(LogstashEncoder.class);

            String line = new String(encoder.encode(event(ctx, Level.INFO, "plain line", Map.of())),
                    StandardCharsets.UTF_8);
            assertThat(line).contains("plain line").doesNotStartWith("{");
        } finally {
            ctx.stop();
        }
    }

    @Test
    @DisplayName("LOG_FORMAT value other than json falls back to plain, not to a broken config")
    void unknownValueFallsBackToPlain() throws Exception {
        LoggerContext ctx = load("logfmt");
        try {
            assertThat(consoleEncoder(ctx)).isNotInstanceOf(LogstashEncoder.class);
        } finally {
            ctx.stop();
        }
    }
}
