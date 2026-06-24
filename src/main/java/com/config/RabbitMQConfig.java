package com.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Queue names ────────────────────────────────────────────────────────
    public static final String ORDER_PAID_QUEUE   = "order.paid.queue";
    public static final String ORDER_FAILED_QUEUE = "order.failed.queue";
    public static final String ORDER_PAID_DLQ     = "order.paid.dlq";
    public static final String ORDER_FAILED_DLQ   = "order.failed.dlq";

    // ── Exchange names ─────────────────────────────────────────────────────
    public static final String EMAIL_EXCHANGE     = "email.exchange";
    public static final String DLX_EXCHANGE       = "email.dlx";

    // ── Routing keys ───────────────────────────────────────────────────────
    public static final String ORDER_PAID_KEY     = "order.paid";
    public static final String ORDER_FAILED_KEY   = "order.failed";

    // ── Exchange ───────────────────────────────────────────────────────────
    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(EMAIL_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // ── Main Queues (với DLX config để retry tự động) ─────────────────────
    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_PAID_KEY + ".dead")
                .build();
    }

    @Bean
    public Queue orderFailedQueue() {
        return QueueBuilder.durable(ORDER_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_FAILED_KEY + ".dead")
                .build();
    }

    // ── Dead Letter Queues (chứa message fail sau 3 lần retry) ────────────
    @Bean
    public Queue orderPaidDLQ() {
        return QueueBuilder.durable(ORDER_PAID_DLQ).build();
    }

    @Bean
    public Queue orderFailedDLQ() {
        return QueueBuilder.durable(ORDER_FAILED_DLQ).build();
    }

    // ── Bindings ───────────────────────────────────────────────────────────
    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(emailExchange()).with(ORDER_PAID_KEY);
    }

    @Bean
    public Binding orderFailedBinding() {
        return BindingBuilder.bind(orderFailedQueue()).to(emailExchange()).with(ORDER_FAILED_KEY);
    }

    @Bean
    public Binding orderPaidDLQBinding() {
        return BindingBuilder.bind(orderPaidDLQ()).to(deadLetterExchange()).with(ORDER_PAID_KEY + ".dead");
    }

    @Bean
    public Binding orderFailedDLQBinding() {
        return BindingBuilder.bind(orderFailedDLQ()).to(deadLetterExchange()).with(ORDER_FAILED_KEY + ".dead");
    }

    // ── JSON converter ─────────────────────────────────────────────────────
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}