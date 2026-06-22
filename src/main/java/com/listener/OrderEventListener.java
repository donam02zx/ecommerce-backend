package com.listener;

import com.event.OrderFailedEvent;
import com.event.OrderPaidEvent;
import com.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final EmailService emailService;

    /**
     * Lắng nghe sự kiện OrderPaidEvent
     * Chạy sau khi transaction commit thành công (AFTER_COMMIT)
     */
    @Async("emailExecutor")  // 👈 Chạy trong thread pool
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        log.info("📩 Received OrderPaidEvent for order {}", event.getOrder().getId());
        emailService.sendOrderPaidEmail(event.getOrder(), event.getUserEmail());
    }

    /**
     * Lắng nghe sự kiện OrderFailedEvent
     * Chạy sau khi transaction commit thành công (AFTER_COMMIT)
     */
    @Async("emailExecutor")  // 👈 Chạy trong thread pool
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderFailedEvent(OrderFailedEvent event) {
        log.info("📩 Received OrderFailedEvent for order {}", event.getOrder().getId());
        emailService.sendOrderFailedEmail(event.getOrder(), event.getUserEmail(), event.getReason());
    }

    // Cách dùng @EventListener thông thường (không phụ thuộc transaction)
    /*
    @Async("emailExecutor")
    @EventListener
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        emailService.sendOrderPaidEmail(event.getOrder(), event.getUserEmail());
    }
    */
}