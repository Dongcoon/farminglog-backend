package com.farmlog.farm.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code farm_member} 테이블 매핑 엔티티. {@code role} 값은 plan/02_Development_Guide.md 7.1의
 * 역할 정의(FARM_OWNER/FARM_MANAGER/WORKER/VIEWER 등)를 그대로 사용한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmMemberEntity {

    public static final String ROLE_FARM_OWNER = "FARM_OWNER";
    public static final String ROLE_FARM_MANAGER = "FARM_MANAGER";
    public static final String ROLE_WORKER = "WORKER";
    public static final String ROLE_VIEWER = "VIEWER";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private Long farmId;
    private Long userId;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
}
