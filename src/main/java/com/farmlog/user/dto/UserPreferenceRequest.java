package com.farmlog.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * PUT /users/me/preferences 요청. 존재하지 않으면 새로 생성(upsert)한다.
 * viewScale은 5.1의 4단계(standard/large/xlarge/max) 중 하나여야 한다.
 */
public record UserPreferenceRequest(

        @NotNull(message = "보기 크기를 선택해주세요.")
        @Pattern(regexp = "standard|large|xlarge|max", message = "보기 크기는 standard/large/xlarge/max 중 하나여야 합니다.")
        String viewScale,

        @NotNull(message = "야외 모드 값을 지정해주세요.")
        Boolean outdoorMode,

        @NotNull(message = "동작 감소 모드 값을 지정해주세요.")
        Boolean reduceMotion
) {
}
