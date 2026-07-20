package com.farmlog.masterdata.dto;

import java.time.LocalDate;

public record CropSeasonResponse(Long id, Long cropId, Long varietyId, String name,
                                 LocalDate startDate, LocalDate endDate, String status) {
}
