package com.farmlog.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CropSeasonCreateRequest(
        @NotNull Long cropId,
        Long varietyId,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startDate,
        LocalDate endDate
) {
}
