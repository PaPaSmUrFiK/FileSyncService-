package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringWebFluxTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.mail.internet.MimeMessage;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringWebFluxTemplateEngine templateEngine;

    @Override
    public Mono<Void> sendEmail(UUID userId, Notification notification) {
        // In a real system, we'd fetch the user's email address from UserService or
        // Auth.
        // For this implementation, we'll assume we have a way to get it or skip if not
        // found.
        // Also we'd add it to EmailQueue first.

        return Mono.fromRunnable(() -> {
            try {
                // Placeholder email for demonstration - in production fetch real email
                String toEmail = "user-" + userId + "@example.com";

                String templateName = getTemplateForType(notification.getNotificationType());

                Context context = new Context();
                context.setVariable("title", notification.getTitle());
                context.setVariable("message", notification.getMessage());
                // data properties could also be passed

                String htmlContent = templateEngine.process(templateName, context);

                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
                helper.setTo(toEmail);
                helper.setSubject(notification.getTitle());
                helper.setText(htmlContent, true);

                mailSender.send(mimeMessage);
                log.info("Email sent successfully to {}", toEmail);
            } catch (Exception e) {
                log.error("Failed to send email to user {}: {}", userId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> sendWelcomeEmail(UUID userId, String email) {
        return Mono.fromRunnable(() -> {
            try {
                Context context = new Context();
                context.setVariable("userName", "User");

                String htmlContent = templateEngine.process("welcome", context);

                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
                helper.setTo(email);
                helper.setSubject("Welcome to FileSync!");
                helper.setText(htmlContent, true);

                mailSender.send(mimeMessage);
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", email, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String getTemplateForType(String type) {
        return switch (type) {
            case "FILE_SHARED" -> "file-shared";
            case "CONFLICT_DETECTED" -> "conflict-detected";
            case "USER_BLOCKED" -> "account-blocked";
            case "QUOTA_CHANGED" -> "quota-changed";
            case "STORAGE_QUOTA_WARNING" -> "storage-quota-warning";
            default -> "notification-default";
        };
    }
}
