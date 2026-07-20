package com.farmlog.farm;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.security.JwtAccessDeniedHandler;
import com.farmlog.common.security.JwtAuthenticationEntryPoint;
import com.farmlog.common.security.JwtAuthenticationFilter;
import com.farmlog.common.security.JwtTokenProvider;
import com.farmlog.common.security.SecurityConfig;
import com.farmlog.farm.dto.FarmCreateRequest;
import com.farmlog.farm.dto.FarmResponse;
import com.farmlog.farm.dto.FarmZoneResponse;
import com.farmlog.farm.dto.FarmZoneUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /farms/** 엔드포인트의 HTTP 계층(인증 필터, 요청 검증, 에러 응답 포맷) 테스트. 실제
 * SecurityConfig/JwtAuthenticationFilter를 함께 로드해 필터 체인까지 검증하되, FarmService는 Mock으로
 * 대체해 DB 접근 없이 컨트롤러/보안 설정만 검증한다(Phase 1 AuthControllerWebTest와 동일한 패턴).
 *
 * <p>{@code getZones_crossFarmAccess_returns403Forbidden}은 FarmService(내부적으로
 * FarmAccessGuard)가 BusinessException(FORBIDDEN)을 던졌을 때 GlobalExceptionHandler가 실제로
 * HTTP 403을 반환하는지 확인한다 — FarmServiceTest/FarmAccessGuardTest에서 검증한 인가 로직이 HTTP
 * 계층까지 올바르게 이어지는지(9장 Phase 2 DoD)를 함께 증명한다.</p>
 */
@WebMvcTest(controllers = FarmController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtTokenProvider.class})
class FarmControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private FarmService farmService;

    private String accessTokenFor(Long userId) {
        return jwtTokenProvider.createAccessToken(userId, "farmer@example.com");
    }

    @Test
    void getMyFarms_withoutToken_returns401InvalidToken() throws Exception {
        mockMvc.perform(get("/farms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void getMyFarms_withToken_returns200WithRoleField() throws Exception {
        when(farmService.listMyFarms(42L)).thenReturn(List.of(
                new FarmResponse(1L, "딸기 농장", "F001", "전남 나주시", "ACTIVE", "ACTIVE", "FARM_OWNER")));

        mockMvc.perform(get("/farms").header("Authorization", "Bearer " + accessTokenFor(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].role").value("FARM_OWNER"));
    }

    @Test
    void createFarm_success_returns201WithFarmOwnerRole() throws Exception {
        when(farmService.createFarm(eq(42L), any()))
                .thenReturn(new FarmResponse(1L, "딸기 농장", null, "전남 나주시", "ACTIVE", "ACTIVE", "FARM_OWNER"));

        FarmCreateRequest request = new FarmCreateRequest("딸기 농장", "전남 나주시");

        mockMvc.perform(post("/farms")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FARM_OWNER"));
    }

    @Test
    void createFarm_blankName_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/farms")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getZones_crossFarmAccess_returns403Forbidden() throws Exception {
        // 농장 A 사용자가 농장 B(999L)의 구역 목록을 요청 — FarmAccessGuard가 던지는
        // BusinessException(FORBIDDEN)이 HTTP 403으로 정확히 변환되는지 확인.
        when(farmService.listZones(eq(42L), eq(999L), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "이 농장에 접근할 권한이 없습니다."));

        mockMvc.perform(get("/farms/999/zones").header("Authorization", "Bearer " + accessTokenFor(42L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getZones_success_returns200WithOrderedZones() throws Exception {
        when(farmService.listZones(eq(42L), eq(1L), eq(false))).thenReturn(List.of(
                new FarmZoneResponse(10L, "1동", "GREENHOUSE", new BigDecimal("500.00"), "m2", 0, true)));

        mockMvc.perform(get("/farms/1/zones").header("Authorization", "Bearer " + accessTokenFor(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("1동"))
                .andExpect(jsonPath("$[0].activeYn").value(true));
    }

    @Test
    void createZone_blankName_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/farms/1/zones")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createZone_negativeArea_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/farms/1/zones")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1동\",\"areaValue\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateZone_negativeDisplayOrder_returns400ValidationError() throws Exception {
        mockMvc.perform(patch("/farms/1/zones/10")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateZone_explicitNullArea_isPassedAsClearRequest() throws Exception {
        when(farmService.updateZone(eq(42L), eq(1L), eq(10L), any())).thenAnswer(invocation -> {
            FarmZoneUpdateRequest request = invocation.getArgument(3);
            assertThat(request.hasAreaValue()).isTrue();
            assertThat(request.hasAreaUnit()).isTrue();
            assertThat(request.areaValue()).isNull();
            assertThat(request.areaUnit()).isNull();
            return new FarmZoneResponse(10L, "1동", "FIELD", null, null, 0, true);
        });

        mockMvc.perform(patch("/farms/1/zones/10")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneType\":\"FIELD\",\"areaValue\":null,\"areaUnit\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneType").value("FIELD"))
                .andExpect(jsonPath("$.areaValue").doesNotExist())
                .andExpect(jsonPath("$.areaUnit").doesNotExist());
    }

    @Test
    void updateZone_blankZoneType_returns400ValidationError() throws Exception {
        mockMvc.perform(patch("/farms/1/zones/10")
                        .header("Authorization", "Bearer " + accessTokenFor(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneType\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deactivateZone_success_returns200WithActiveFalse() throws Exception {
        when(farmService.deactivateZone(42L, 1L, 10L)).thenReturn(
                new FarmZoneResponse(10L, "1동", "GREENHOUSE", null, null, 0, false));

        mockMvc.perform(patch("/farms/1/zones/10/deactivate")
                        .header("Authorization", "Bearer " + accessTokenFor(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeYn").value(false));
    }

    @Test
    void getZoneAssignments_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/farms/1/zone-assignments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }
}
