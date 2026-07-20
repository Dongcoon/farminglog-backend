package com.farmlog.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code organization_member} 테이블 매핑 엔티티.
 *
 * <p>{@code role} 값에 대한 근거는 {@link OrganizationEntity}와 plan/04_Open_Questions_Log.md의
 * "Phase 1에서 새로 발견된 사항" 표를 참고 — 회원가입 시 자동 생성되는 개인(PERSONAL) 조직의 소유자에게는
 * {@link #ROLE_OWNER}를 부여한다. plan/02_Development_Guide.md 7.1의 역할 정의에는 별도의 "OWNER"
 * 값이 없고 {@code FARM_OWNER}만 존재하며, {@code database/seed-data.sql}도 organization_member.role에
 * {@code FARM_OWNER}를 사용하므로 동일한 값을 재사용한다(신규 역할 값을 만들지 않음).</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberEntity {

    public static final String ROLE_OWNER = "FARM_OWNER";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private Long organizationId;
    private Long userId;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
