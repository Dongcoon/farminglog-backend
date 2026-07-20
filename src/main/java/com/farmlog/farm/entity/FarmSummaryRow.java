package com.farmlog.farm.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code GET /farms} 응답 조립을 위한 {@code farm} + {@code farm_member} 조인 결과 행. 단일 테이블에
 * 대응하는 다른 엔티티와 달리, "로그인 사용자가 ACTIVE로 소속된 농장 + 그 농장에서의 role" 조회 전용
 * 프로젝션이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmSummaryRow {

    private Long id;
    private String name;
    private String farmCode;
    private String address;
    private String lifecycleStatus;
    private String status;
    private String role;
}
