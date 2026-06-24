package com.messaging;

import com.config.RabbitMQConfig;
import com.dto.message.EmailMessage;
import com.rabbitmq.client.Channel;
import com.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailService emailService;

    /**
     * Consume message từ order.paid.queue
     * Manual ack: chỉ ack khi gửi email thành công
     * Fail → nack → RabbitMQ retry (tối đa 3 lần theo config)
     * Hết retry → message vào Dead Letter Queue
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_PAID_QUEUE)
    public void handleOrderPaid(EmailMessage message,
                                Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[CONSUMER] Received OrderPaid message: orderId={}", message.getOrderId());
        try {
            emailService.sendOrderPaidEmail(message);
            channel.basicAck(deliveryTag, false); // ✅ Ack — xử lý thành công
            log.info("[CONSUMER] OrderPaid email sent: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[CONSUMER] Failed to send OrderPaid email: orderId={}, error={}",
                    message.getOrderId(), e.getMessage());
            // nack + requeue=false → RabbitMQ retry theo config
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Consume message từ order.failed.queue
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_FAILED_QUEUE)
    public void handleOrderFailed(EmailMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[CONSUMER] Received OrderFailed message: orderId={}", message.getOrderId());
        try {
            emailService.sendOrderFailedEmail(message);
            channel.basicAck(deliveryTag, false);
            log.info("[CONSUMER] OrderFailed email sent: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("[CONSUMER] Failed to send OrderFailed email: orderId={}, error={}",
                    message.getOrderId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Dead Letter Queue consumer — xử lý message fail hết retry
     * Log lại để alert/monitor, không gửi email nữa
     */
    @RabbitListener(queues = {RabbitMQConfig.ORDER_PAID_DLQ, RabbitMQConfig.ORDER_FAILED_DLQ})
    public void handleDeadLetter(EmailMessage message,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("[DLQ] Message failed after all retries: orderId={}, type={}, email={}",
                message.getOrderId(), message.getType(), message.getUserEmail());
        // TODO: alert team, save to DB for manual reprocess
        channel.basicAck(deliveryTag, false);
    }
}