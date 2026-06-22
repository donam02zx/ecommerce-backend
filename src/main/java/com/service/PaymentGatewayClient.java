package com.service;

import com.entity.OrderEntity;
import com.exception.PaymentBusinessException;
import com.exception.PaymentNetworkException;
import com.exception.PaymentTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class PaymentGatewayClient {

    private final Random random = new Random();

    @Retryable(
            value = {PaymentTimeoutException.class, PaymentNetworkException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1500, multiplier = 1.5)
    )
    public String callPaymentGateway(OrderEntity order) {
        log.info("💳 Processing payment for order {}", order.getId());

        int randomValue = random.nextInt(10);

        log.info("Random {} ", randomValue);

        // 70% success
        if (randomValue <= 8) {
            String ref = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("✅ Payment success for order {}, ref: {}", order.getId(), ref);
            return ref;
        }

        // 15% timeout → retry
        if (randomValue <= 8) {
            log.warn("⏰ Payment timeout for order {}", order.getId());
            throw new PaymentTimeoutException("Payment gateway timeout");
        }

        // 10% network error → retry
        if (randomValue == 9) {
            log.warn("📡 Payment network error for order {}", order.getId());
            throw new PaymentNetworkException("Network error when calling payment gateway");
        }

        // 5% business error → KHÔNG retry
        throw new PaymentBusinessException("Card expired or invalid", "CARD_EXPIRED");
    }

    @Recover
    public String recoverPayment(PaymentTimeoutException e, OrderEntity order) {
        log.error("❌ Payment failed after 3 retries for order {}: {}", order.getId(), e.getMessage());
        // 👉 THROW BUSINESS EXCEPTION
        throw new PaymentBusinessException(
                "Payment failed after 3 retries: " + e.getMessage(),
                "RETRY_FAILED"
        );
    }

    @Recover
    public String recoverPayment(PaymentNetworkException e, OrderEntity order) {
        log.error("❌ Payment failed after 3 retries for order {}: {}", order.getId(), e.getMessage());
        throw new PaymentBusinessException(
                "Payment failed after 3 retries: " + e.getMessage(),
                "RETRY_FAILED"
        );
    }
}