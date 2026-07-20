package com.farmlog.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code POST /farms/{farmId}/zones} 요청. {@code zoneType}이 비어있으면 서비스 계층에서
 * {@code GREENHOUSE}로 기본값을 채운다.
 */
public record FarmZoneCreateRequest(

        @NotBlank(message = "구역 이름을 입력해주세요.")
        @Size(max = 100, message = "구역 이름은 100자 이하로 입력해주세요.")
        String name,

        @Size(max = 30, message = "구역 유형은 30자 이하로 입력해주세요.")
        String zoneType,

        @DecimalMin(value = "0.0", inclusive = true, message = "면적은 0 이상으로 입력해주세요.")
        @Digits(integer = 10, fraction = 2, message = "면적은 정수 10자리, 소수 2자리 이하로 입력해주세요.")
        BigDecimal areaValue,

        @Size(max = 20, message = "면적 단위는 20자 이하로 입력해주세요.")
        String areaUnit
) {
}
