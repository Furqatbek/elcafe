package com.elcafe.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email (currently: password-reset links). The {@link JavaMailSender} bean is
 * auto-configured only when {@code spring.mail.host} is set, so it is injected optionally: when SMTP is
 * not configured the service logs a clear warning and sends nothing — it never silently pretends to have
 * delivered. Set {@code spring.mail.*} and {@code app.mail.reset-password-url} to turn it on.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender; // null when SMTP is not configured

    @Value("${app.mail.from:no-reply@example.com}")
    private String from;

    /** Front-end reset page; the token is appended as ?token=. */
    @Value("${app.mail.reset-password-url:}")
    private String resetPasswordUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    /** True if email can actually be delivered (SMTP configured). */
    public boolean isEnabled() {
        return mailSender != null;
    }

    public void sendPasswordReset(String toEmail, String resetToken) {
        String link = (resetPasswordUrl == null || resetPasswordUrl.isBlank())
                ? "(configure app.mail.reset-password-url) token=" + resetToken
                : resetPasswordUrl + (resetPasswordUrl.contains("?") ? "&" : "?") + "token=" + resetToken;

        if (mailSender == null) {
            log.warn("Password reset requested for a valid account but SMTP is not configured "
                    + "(spring.mail.host unset) — reset email NOT sent. Configure mail to enable this flow.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("Password reset request");
            message.setText("You requested a password reset.\n\nUse this link to set a new password "
                    + "(valid for 24 hours):\n" + link + "\n\nIf you did not request this, ignore this email.");
            mailSender.send(message);
            log.info("Password reset email sent");
        } catch (Exception e) {
            // Do not surface delivery failures to the caller (that would leak account existence / SMTP state).
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }
}
