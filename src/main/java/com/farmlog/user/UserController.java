package com.farmlog.user;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.user.dto.AccessContextResponse;
import com.farmlog.user.dto.UserPreferenceRequest;
import com.farmlog.user.dto.UserPreferenceResponse;
import com.farmlog.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 로그인한 사용자 본인의 프로필/환경설정 API. 전부 인증이 필요하며(SecurityConfig의
 * anyRequest().authenticated() 기본 규칙), 대상은 항상 토큰의 principal(userId)로 고정되므로 경로에
 * 별도 사용자 식별자를 받지 않는다(다른 사용자 리소스 접근 자체가 불가능한 구조).
 */
@RestController
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMyProfile(principal.userId());
    }

    @GetMapping("/preferences")
    public UserPreferenceResponse getMyPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMyPreferences(principal.userId());
    }

    @GetMapping("/access-context")
    public AccessContextResponse getAccessContext(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getAccessContext(principal.userId());
    }

    @PutMapping("/preferences")
    public UserPreferenceResponse updateMyPreferences(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody UserPreferenceRequest request) {
        return userService.updateMyPreferences(principal.userId(), request);
    }
}
