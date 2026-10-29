package com.org.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.org.ecommerce.config.AppProperties;

/**
 * Transactional email. Sent asynchronously so SMTP latency never blocks requests and failures do
 * not reveal whether an account exists. Links contain one-time tokens and are never logged.
 */
@Service
@EnableAsync
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    public void sendVerificationEmail(String to, String firstName, String token) {
        String link = properties.publicBaseUrl() + "/verify-email?token=" + token;
        send(to, "Verify your email address", """
                Hi %s,

                Please confirm your email address by opening this link (valid for 24 hours):

                %s

                If you did not create an account, you can ignore this message.
                """.formatted(firstName, link));
    }

    @Async
    public void sendPasswordResetEmail(String to, String firstName, String token) {
        String link = properties.publicBaseUrl() + "/reset-password?token=" + token;
        send(to, "Reset your password", """
                Hi %s,

                We received a request to reset your password. Open this link to choose a new one
                (valid for 30 minutes, single use):

                %s

                If you did not request this, you can ignore this message; your password is unchanged.
                """.formatted(firstName, link));
    }

    @Async
    public void sendOrderConfirmation(String to, String firstName, String orderNumber, String total) {
        send(to, "Order " + orderNumber + " confirmed", """
                Hi %s,

                Thank you for your order %s. Total paid: %s.
                You can follow its status in your account: %s/account/orders

                """.formatted(firstName, orderNumber, total, properties.publicBaseUrl()));
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.mail().from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send email '{}': {}", subject, e.getMessage());
        }
    }
}
