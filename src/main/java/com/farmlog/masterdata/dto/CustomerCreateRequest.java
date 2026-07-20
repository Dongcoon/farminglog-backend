package com.farmlog.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 40) String customerType,
        @Size(max = 50) String phone,
        String memo
) {
}
