package com.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 150, message = "Category name must not exceed 150 characters")
    private String name;

    private String description;

    private Boolean active;
}