package com.service;

import com.dto.message.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public void sendOrderPaidEmail(EmailMessage message) {
        log.info("📧 Sending order paid email: orderId={}, to={}",
                message.getOrderId(), message.getUserEmail());

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId",    message.getOrderId());
        variables.put("totalPrice", message.getTotalPrice());
        variables.put("items",      message.getItems());
        variables.put("userEmail",  message.getUserEmail());
        // Format Instant → String trước khi pass vào Thymeleaf
        variables.put("createdAt",  message.getCreatedAt() != null
                ? FMT.format(message.getCreatedAt()) : "N/A");

        String html = renderTemplate("email/order-paid", variables);
        sendHtmlEmail(message.getUserEmail(),
                "Xác nhận đơn hàng #" + message.getOrderId(), html);

        log.info("✅ Order paid email sent: orderId={}", message.getOrderId());
    }

    public void sendOrderFailedEmail(EmailMessage message) {
        log.info("📧 Sending order failed email: orderId={}, to={}",
                message.getOrderId(), message.getUserEmail());

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId",    message.getOrderId());
        variables.put("totalPrice", message.getTotalPrice());
        variables.put("reason",     message.getReason());
        variables.put("userEmail",  message.getUserEmail());
        variables.put("createdAt",  message.getCreatedAt() != null
                ? FMT.format(message.getCreatedAt()) : "N/A");

        String html = renderTemplate("email/order-failed", variables);
        sendHtmlEmail(message.getUserEmail(),
                "Thanh toán đơn hàng #" + message.getOrderId() + " thất bại", html);

        log.info("✅ Order failed email sent: orderId={}", message.getOrderId());
    }

    private String renderTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "ECommerce Store");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            // throw ra để Consumer nack → RabbitMQ retry
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}