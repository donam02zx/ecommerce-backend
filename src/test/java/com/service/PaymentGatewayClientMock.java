package com.service;

import com.entity.OrderEntity;
import com.exception.PaymentTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary  // 👈 BỎ COMMENT DÒNG NÀY
public class PaymentGatewayClientMock extends PaymentGatewayClient {

    private int attemptCounter = 0;

    @Override
    @Retryable(
            value = {PaymentTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1500, multiplier = 1.5)
    )
    public String callPaymentGateway(OrderEntity order) {
        attemptCounter++;
        log.info("🧪 TEST: Processing payment for order {}, attempt {}/3",
                order.getId(), attemptCounter);

        // 👇 Fail 2 lần đầu, thành công lần 3
        if (attemptCounter <= 3) {
            log.warn("⏰ TEST: Simulating timeout, attempt {}", attemptCounter);
            throw new PaymentTimeoutException("Simulated timeout for testing");
        }

        log.info("✅ TEST: Payment success on attempt 3 for order {}", order.getId());
        attemptCounter = 0;
        return "PAY-TEST-123";
    }
}