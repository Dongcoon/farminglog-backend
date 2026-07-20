package com.farmlog.masterdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CropSeasonEntity {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private Long id;
    private Long organizationId;
    private Long farmId;
    private Long cropId;
    private Long varietyId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
