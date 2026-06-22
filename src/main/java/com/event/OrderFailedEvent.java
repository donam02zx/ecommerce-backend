package com.event;

import com.entity.OrderEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderFailedEvent {
    private final OrderEntity order;
    private final String userEmail;
    private final String reason;  // Lý do thất bại
}