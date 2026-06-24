package com.messaging;

import com.config.RabbitMQConfig;
import com.dto.message.EmailMessage;
import com.entity.OrderEntity;
import com.entity.OrderItemEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish message khi payment thành công
     * Thay thế: eventPublisher.publishEvent(new OrderPaidEvent(order, email))
     */
    public void sendOrderPaidMessage(OrderEntity order, String userEmail) {
        EmailMessage message = EmailMessage.builder()
                .orderId(order.getId())
                .userEmail(userEmail)
                .type("PAID")
                .totalPrice(order.getTotalPrice())
                .createdAt(order.getCreatedAt())
                .items(mapItems(order.getItems()))
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.ORDER_PAID_KEY,
                message);

        log.info("[RABBITMQ] OrderPaid message published: orderId={}", order.getId());
    }

    /**
     * Publish message khi payment thất bại
     * Thay thế: eventPublisher.publishEvent(new OrderFailedEvent(order, email, reason))
     */
    public void sendOrderFailedMessage(OrderEntity order, String userEmail, String reason) {
        EmailMessage message = EmailMessage.builder()
                .orderId(order.getId())
                .userEmail(userEmail)
                .type("FAILED")
                .totalPrice(order.getTotalPrice())
                .createdAt(order.getCreatedAt())
                .reason(reason)
                .items(mapItems(order.getItems()))
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.ORDER_FAILED_KEY,
                message);

        log.info("[RABBITMQ] OrderFailed message published: orderId={}", order.getId());
    }

    private List<EmailMessage.OrderItemData> mapItems(List<OrderItemEntity> items) {
        return items.stream()
                .map(i -> EmailMessage.OrderItemData.builder()
                        .productName(i.getProduct().getName())
                        .productSku(i.getProduct().getSku())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .build())
                .toList();
    }
}