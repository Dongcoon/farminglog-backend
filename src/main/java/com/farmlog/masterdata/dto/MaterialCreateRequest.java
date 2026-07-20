package com.farmlog.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaterialCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 40) String materialType,
        @Size(max = 20) String unit,
        String memo
) {
}
