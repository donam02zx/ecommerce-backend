package com.service;

import com.entity.OrderEntity;
import com.entity.OrderItemEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async("emailExecutor")  // 👈 Chạy trong thread pool
    public void sendOrderPaidEmail(OrderEntity order, String userEmail) {
        try {
            log.info("📧 Sending order paid email for order {} to {}", order.getId(), userEmail);

            // Tạo context cho template
            Map<String, Object> variables = new HashMap<>();
            variables.put("order", order);
            variables.put("orderId", order.getId());
            variables.put("totalPrice", order.getTotalPrice());
            variables.put("items", order.getItems());
            variables.put("userEmail", userEmail);

            // Render HTML template
            String htmlContent = renderTemplate("email/order-paid", variables);

            // Gửi email
            sendHtmlEmail(userEmail, "Xác nhận đơn hàng #" + order.getId(), htmlContent);

            log.info("✅ Order paid email sent for order {}", order.getId());

        } catch (Exception e) {
            // ❌ KHÔNG THROW EXCEPTION RA NGOÀI
            // => KHÔNG ẢNH HƯỞNG ĐẾN TRANSACTION
            log.error("❌ Failed to send order paid email for order {}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }

    @Async("emailExecutor")
    public void sendOrderFailedEmail(OrderEntity order, String userEmail, String reason) {
        try {
            log.info("📧 Sending order failed email for order {} to {}", order.getId(), userEmail);

            Map<String, Object> variables = new HashMap<>();
            variables.put("order", order);
            variables.put("orderId", order.getId());
            variables.put("reason", reason);
            variables.put("userEmail", userEmail);

            String htmlContent = renderTemplate("email/order-failed", variables);
            sendHtmlEmail(userEmail, "Thanh toán đơn hàng #" + order.getId() + " thất bại", htmlContent);

            log.info("✅ Order failed email sent for order {}", order.getId());

        } catch (Exception e) {
            // ❌ KHÔNG THROW EXCEPTION RA NGOÀI
            log.error("❌ Failed to send order failed email for order {}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }

    private String renderTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent)
            throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);  // true = HTML

        mailSender.send(message);
    }
}