package com.farmlog.user.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code user_preference} 테이블 매핑 엔티티 (viewScale/outdoorMode/reduceMotion 등 UI 환경설정).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceEntity {

    private Long id;
    private Long userId;
    private String viewScale;
    private String outdoorModeYn;
    private String reduceMotionYn;
    private Long lastSelectedFarmId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
