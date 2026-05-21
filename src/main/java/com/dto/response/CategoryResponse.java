package com.dto.response;

import com.entity.CategoriesEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static CategoryResponse from(CategoriesEntity e) {
        return CategoryResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .active(e.getActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}