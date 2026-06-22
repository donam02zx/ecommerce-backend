package com.event;

import com.entity.OrderEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderPaidEvent {
    private final OrderEntity order;
    private final String userEmail;
}