package com.farmlog.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * M-04 첫 농장 설정 마법사 / W-04 농장 등록 요청. {@code organizationId}는 클라이언트가 넘기지 않고
 * 서버가 인증된 사용자 본인의 개인(PERSONAL) 조직에서 자동으로 찾는다(plan/02_Development_Guide.md 7.3).
 */
public record FarmCreateRequest(

        @NotBlank(message = "농장 이름을 입력해주세요.")
        @Size(max = 100, message = "농장 이름은 100자 이하로 입력해주세요.")
        String name,

        @Size(max = 300, message = "주소는 300자 이하로 입력해주세요.")
        String address
) {
}
