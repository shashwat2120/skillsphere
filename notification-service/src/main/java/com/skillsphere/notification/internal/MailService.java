package com.skillsphere.notification.internal;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Renders and sends transactional email.
 *
 * <p>Every message is built from a Thymeleaf template rather than assembled
 * from strings, so nothing user-supplied is concatenated into HTML. A learner
 * whose display name is {@code <script>…} must not turn a notification into an
 * injection vector for every other recipient of that thread.
 *
 * <p>Failures are logged and swallowed rather than rethrown. This method is
 * always reached asynchronously from an event listener, so throwing would only
 * kill a background thread — and a bounced welcome email must never roll back
 * the registration that triggered it. Delivery is retried by Modulith's event
 * registry, which keeps the publication row incomplete until a listener
 * succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;

    public void send(String to, String subject, String template, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("appBaseUrl", properties.appBaseUrl());
            String html = templateEngine.process("mail/" + template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_NO, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Sent '{}' mail to {}", template, mask(to));

        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException ex) {
            // Logged with the template name but never the recipient in full —
            // logs are widely readable and an address is personal data.
            log.error("Failed to send '{}' mail to {}", template, mask(to), ex);
            throw new MailDeliveryException("Could not send " + template + " mail", ex);
        }
    }

    /**
     * Masks an address for logging: {@code alice@example.com} becomes
     * {@code a***e@example.com}. Enough to correlate a delivery problem with a
     * support report, without printing personal data into every log aggregator
     * the team happens to use.
     */
    private static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        if (local.length() <= 2) {
            return "***@" + parts[1];
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }

    /** Signals a delivery failure so the event registry leaves the publication incomplete and retries. */
    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
