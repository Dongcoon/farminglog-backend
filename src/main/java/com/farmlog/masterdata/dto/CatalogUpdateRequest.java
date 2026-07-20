package com.farmlog.masterdata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CatalogUpdateRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
        @Min(0) Integer displayOrder
) {
}
