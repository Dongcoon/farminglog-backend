package com.farmlog.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code organization} 테이블 매핑 엔티티.
 *
 * <p>정식 조직 관리(초대, 구성원 변경, 작목반/농협 대시보드 등)는 별도 {@code organization} 모듈에서
 * 이후 Phase에 구현한다. 이 클래스는 Phase 1 회원가입 시 개인 조직(PERSONAL)을 자동 생성하는 데 필요한
 * 최소한의 필드만 담는다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {

    public static final String ORG_TYPE_PERSONAL = "PERSONAL";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private String name;
    private String orgType;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
