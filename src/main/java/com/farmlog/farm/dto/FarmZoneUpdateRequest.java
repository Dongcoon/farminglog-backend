package com.farmlog.farm.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code PATCH /farms/{farmId}/zones/{zoneId}} 부분 수정 요청.
 *
 * <p>선택 값인 면적/단위는 JSON 필드 누락(기존 값 유지)과 명시적 {@code null}(기존 값 삭제)을
 * 구분해야 한다. 별도 nullable 래퍼 의존성 대신 Jackson setter 호출 여부만 기록해 PATCH 의미를
 * 명확하게 유지한다.</p>
 */
public class FarmZoneUpdateRequest {

    @Size(max = 100, message = "구역 이름은 100자 이하로 입력해주세요.")
    @Pattern(regexp = ".*\\S.*", message = "구역 이름을 입력해주세요.")
    private String name;

    @Size(max = 30, message = "구역 유형은 30자 이하로 입력해주세요.")
    @Pattern(regexp = ".*\\S.*", message = "구역 유형을 입력해주세요.")
    private String zoneType;

    @DecimalMin(value = "0.0", inclusive = true, message = "면적은 0 이상으로 입력해주세요.")
    @Digits(integer = 10, fraction = 2, message = "면적은 정수 10자리, 소수 2자리 이하로 입력해주세요.")
    private BigDecimal areaValue;

    @Size(max = 20, message = "면적 단위는 20자 이하로 입력해주세요.")
    private String areaUnit;

    @PositiveOrZero(message = "정렬 순서는 0 이상으로 입력해주세요.")
    private Integer displayOrder;

    private boolean areaValuePresent;
    private boolean areaUnitPresent;

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String zoneType() {
        return zoneType;
    }

    public void setZoneType(String zoneType) {
        this.zoneType = zoneType;
    }

    public BigDecimal areaValue() {
        return areaValue;
    }

    @JsonSetter("areaValue")
    public void setAreaValue(BigDecimal areaValue) {
        this.areaValue = areaValue;
        this.areaValuePresent = true;
    }

    public String areaUnit() {
        return areaUnit;
    }

    @JsonSetter("areaUnit")
    public void setAreaUnit(String areaUnit) {
        this.areaUnit = areaUnit;
        this.areaUnitPresent = true;
    }

    public Integer displayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    @JsonIgnore
    public boolean hasAreaValue() {
        return areaValuePresent;
    }

    @JsonIgnore
    public boolean hasAreaUnit() {
        return areaUnitPresent;
    }
}
