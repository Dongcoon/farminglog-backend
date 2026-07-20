package com.farmlog.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * M-01 회원가입 요청. 이메일+비밀번호만 사용하고 이메일 인증 절차는 생략한다(plan/02_Development_Guide.md 4.4).
 */
public record SignUpRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식을 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "이름(표시 이름)을 입력해주세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해주세요.")
        String displayName
) {
}
