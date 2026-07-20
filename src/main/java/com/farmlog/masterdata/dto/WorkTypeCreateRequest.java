package com.farmlog.masterdata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkTypeCreateRequest(@NotBlank @Size(max = 100) String name, @Min(0) Integer displayOrder) {
}
