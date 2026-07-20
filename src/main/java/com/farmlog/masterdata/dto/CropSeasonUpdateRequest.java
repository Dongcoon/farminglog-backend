package com.farmlog.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** 누락=유지, 명시 null=삭제가 필요한 선택 필드는 setter 호출 여부를 함께 기록한다. */
public class CropSeasonUpdateRequest {
    private Long cropId;
    private Long varietyId;
    @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean varietyIdPresent;
    private boolean endDatePresent;

    public Long cropId() { return cropId; }
    public void setCropId(Long cropId) { this.cropId = cropId; }
    public Long varietyId() { return varietyId; }
    @JsonSetter("varietyId") public void setVarietyId(Long varietyId) { this.varietyId = varietyId; this.varietyIdPresent = true; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate startDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate endDate() { return endDate; }
    @JsonSetter("endDate") public void setEndDate(LocalDate endDate) { this.endDate = endDate; this.endDatePresent = true; }
    @JsonIgnore public boolean hasVarietyId() { return varietyIdPresent; }
    @JsonIgnore public boolean hasEndDate() { return endDatePresent; }
}
