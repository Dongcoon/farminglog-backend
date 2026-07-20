-- Farmlog ERP DB Schema
-- 기준: MariaDB, utf8mb4
-- 원본: docs/Farmlog_ERP_DB_DDL_Draft.sql (파밍로그 기획 문서 저장소)
-- DB Migration 도구는 사용하지 않는다. 스키마 변경은 이 파일을 직접 수정하고 db-change-log.md에 기록한다.
-- 애플리케이션 시작 시 자동 DDL 실행(ddl-auto)은 사용하지 않는다. 이 스크립트를 DB에 수동 적용한다.

-- =========================================================
-- 1. 원본 DDL 초안 테이블 (docs/Farmlog_ERP_DB_DDL_Draft.sql 그대로)
-- =========================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 계정 식별자',
    email VARCHAR(190) NOT NULL COMMENT '이메일 주소',
    password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시',
    display_name VARCHAR(100) NOT NULL COMMENT '사용자 표시 이름',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '사용자 계정 상태',
    last_login_at DATETIME(6) NULL COMMENT '마지막 로그인 일시',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 계정';

CREATE TABLE IF NOT EXISTS organization (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '조직 식별자',
    name VARCHAR(150) NOT NULL COMMENT '조직 이름',
    org_type VARCHAR(30) NOT NULL DEFAULT 'PERSONAL' COMMENT '조직 유형',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '조직 상태',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_organization_type_status (org_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='조직';

CREATE TABLE IF NOT EXISTS organization_member (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '조직 구성원 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    role VARCHAR(40) NOT NULL COMMENT '부여 역할',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '조직 구성원 상태',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_member (organization_id, user_id),
    KEY ix_org_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='조직 구성원';

CREATE TABLE IF NOT EXISTS farm (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_code VARCHAR(50) NULL COMMENT '조직 내 농장 코드',
    name VARCHAR(100) NOT NULL COMMENT '농장 이름',
    owner_user_id BIGINT NOT NULL COMMENT '농장주 사용자 식별자',
    main_crop_id BIGINT NULL COMMENT '대표 작물 식별자',
    address VARCHAR(300) NULL COMMENT '농장 주소',
    memo TEXT NULL COMMENT '농장 메모',
    lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '농장 생애주기 상태',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '농장 상태',
    structure_version BIGINT NOT NULL DEFAULT 0 COMMENT '농장 구조 버전',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_org_code (organization_id, farm_code),
    KEY ix_farm_org_status (organization_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장';

CREATE TABLE IF NOT EXISTS farm_member (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 구성원 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    role VARCHAR(40) NOT NULL COMMENT '부여 역할',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '농장 구성원 상태',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_member (farm_id, user_id),
    KEY ix_farm_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 구성원';

CREATE TABLE IF NOT EXISTS farm_care_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 데이터 관리 배정 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    manager_user_id BIGINT NOT NULL COMMENT '매니저 사용자 식별자',
    assigned_by BIGINT NOT NULL COMMENT '배정한 사용자 식별자',
    assignment_type VARCHAR(30) NOT NULL DEFAULT 'DATA_CARE' COMMENT '관리 배정 유형',
    permission_scope VARCHAR(50) NOT NULL DEFAULT 'REVIEW_AND_INPUT' COMMENT '관리 권한 범위',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '농장 데이터 관리 배정 상태',
    starts_at DATETIME(6) NOT NULL COMMENT '배정 시작 일시',
    ends_at DATETIME(6) NULL COMMENT '배정 종료 일시',
    memo TEXT NULL COMMENT '농장 데이터 관리 배정 메모',
    client_request_id VARCHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    revoked_at DATETIME(6) NULL COMMENT '회수 일시',
    revoked_by BIGINT NULL COMMENT '회수 사용자 식별자',
    revoked_reason VARCHAR(500) NULL COMMENT '회수 사유',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY ix_care_assignment_manager (manager_user_id, status),
    KEY ix_care_assignment_active_period (manager_user_id, farm_id, status, starts_at, ends_at),
    KEY ix_care_assignment_farm (farm_id, status),
    KEY ix_care_assignment_org (organization_id, status),
    UNIQUE KEY uk_care_assignment_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 데이터 관리 배정';

CREATE TABLE IF NOT EXISTS farm_zone (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 구역 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    name VARCHAR(100) NOT NULL COMMENT '농장 구역 이름',
    zone_type VARCHAR(30) NOT NULL DEFAULT 'GREENHOUSE' COMMENT '농장 구역 유형',
    area_value DECIMAL(12,2) NULL COMMENT '면적 값',
    area_unit VARCHAR(20) NULL COMMENT '면적 단위',
    display_order INT NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_farm_zone_farm (farm_id, active_yn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 구역';

CREATE TABLE IF NOT EXISTS farm_structure_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 구조 변경 이벤트 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    event_type VARCHAR(30) NOT NULL COMMENT '구조 변경 이벤트 유형',
    event_name VARCHAR(150) NOT NULL COMMENT '구조 변경 이벤트 이름',
    effective_date DATE NOT NULL COMMENT '구조 변경 기준 일자',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '농장 구조 변경 이벤트 상태',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_request_id CHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    confirm_request_id CHAR(36) NULL COMMENT '확정 멱등 요청 식별자',
    request_hash CHAR(64) NOT NULL COMMENT '요청 내용 해시',
    confirm_request_hash CHAR(64) NULL COMMENT '확정 요청 해시',
    preview_hash CHAR(64) NULL COMMENT '미리보기 상태 해시',
    confirmed_state_hash CHAR(64) NULL COMMENT '확정 상태 해시',
    preview_expires_at DATETIME(6) NULL COMMENT '미리보기 만료 일시',
    request_snapshot_json JSON NOT NULL COMMENT '요청 스냅샷 JSON',
    impact_json JSON NOT NULL COMMENT '구조 변경 영향 JSON',
    conflicts_json JSON NOT NULL COMMENT '기준정보 충돌 목록 JSON',
    warnings_json JSON NOT NULL COMMENT '구조 변경 경고 목록 JSON',
    report_policy VARCHAR(30) NULL COMMENT '과거 기록 집계 정책',
    period_start DATE NULL COMMENT '기간 시작 일자',
    period_end_exclusive DATE NULL COMMENT '배타적 기간 종료 일자',
    reverses_event_id BIGINT NULL COMMENT '이 이벤트가 취소하는 원본 이벤트 식별자',
    reversed_by_event_id BIGINT NULL COMMENT '이 이벤트를 취소한 이벤트 식별자',
    reason TEXT NULL COMMENT '구조 변경 사유',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    confirmed_by BIGINT NULL COMMENT '확정 사용자 식별자',
    confirmed_at DATETIME(6) NULL COMMENT '확정 일시',
    canceled_by BIGINT NULL COMMENT '취소 사용자 식별자',
    canceled_at DATETIME(6) NULL COMMENT '취소 일시',
    PRIMARY KEY (id),
    KEY ix_farm_struct_event_org_date (organization_id, event_type, effective_date),
    KEY ix_farm_struct_event_status (organization_id, status),
    UNIQUE KEY uk_structure_preview_request (created_by, client_request_id),
    UNIQUE KEY uk_structure_confirm_request (confirmed_by, confirm_request_id),
    KEY ix_structure_replay (organization_id, confirmed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 구조 변경 이벤트';

CREATE TABLE IF NOT EXISTS farm_structure_event_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 구조 변경 항목 식별자',
    event_id BIGINT NOT NULL COMMENT '구조 변경 이벤트 식별자',
    item_type VARCHAR(30) NOT NULL COMMENT '구조 변경 항목 유형',
    source_farm_id BIGINT NULL COMMENT '원본 농장 식별자',
    target_farm_id BIGINT NULL COMMENT '대상 농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    period_start DATE NULL COMMENT '기간 시작 일자',
    period_end_exclusive DATE NULL COMMENT '배타적 기간 종료 일자',
    resource_type VARCHAR(40) NULL COMMENT '기준정보 자원 유형',
    source_resource_id BIGINT NULL COMMENT '원본 기준정보 식별자',
    target_resource_id BIGINT NULL COMMENT '대상 기준정보 식별자',
    action VARCHAR(40) NULL COMMENT '수행 작업 코드',
    before_json JSON NULL COMMENT '변경 전 상태 JSON',
    after_json JSON NULL COMMENT '변경 후 상태 JSON',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (id),
    KEY ix_farm_struct_item_event (event_id, item_type),
    KEY ix_farm_struct_item_source (source_farm_id),
    KEY ix_farm_struct_item_target (target_farm_id),
    KEY ix_farm_struct_item_zone (zone_id),
    KEY ix_structure_item_replay (event_id, item_type, source_farm_id, zone_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 구조 변경 항목';

CREATE TABLE IF NOT EXISTS farm_zone_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 구역 소속 이력 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    zone_id BIGINT NOT NULL COMMENT '농장 구역 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    effective_from DATE NOT NULL COMMENT '소속 유효 시작 일자',
    effective_to DATE NULL COMMENT '소속 유효 종료 일자',
    change_event_id BIGINT NULL COMMENT '소속 변경 이벤트 식별자',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    current_marker TINYINT AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) PERSISTENT COMMENT '현재 소속 행 고유성 표시자',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY ix_zone_assignment_zone_period (zone_id, effective_from, effective_to),
    KEY ix_zone_assignment_farm_period (farm_id, effective_from),
    KEY ix_zone_assignment_event (change_event_id)
    ,UNIQUE KEY uk_zone_assignment_current (zone_id, current_marker)
    ,UNIQUE KEY uk_zone_assignment_from (zone_id, effective_from)
    ,CONSTRAINT ck_zone_assignment_period CHECK (effective_to IS NULL OR effective_to > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 구역 소속 이력';

CREATE TABLE IF NOT EXISTS crop (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '작물 카탈로그 식별자',
    name VARCHAR(100) NOT NULL COMMENT '작물 카탈로그 이름',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='작물 카탈로그';

CREATE TABLE IF NOT EXISTS crop_variety (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '품종 카탈로그 식별자',
    crop_id BIGINT NOT NULL COMMENT '작물 식별자',
    name VARCHAR(100) NOT NULL COMMENT '품종 카탈로그 이름',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_variety (crop_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='품종 카탈로그';

-- crop/crop_variety는 전역 불변 카탈로그다. 농장별 표시/활성 상태는 아래 연결에서만 변경한다.
CREATE TABLE IF NOT EXISTS farm_crop (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장별 작물 연결 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    crop_id BIGINT NOT NULL COMMENT '작물 식별자',
    display_order INT NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_crop (farm_id, crop_id),
    KEY ix_farm_crop_active (farm_id, active_yn, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장별 작물 연결';

CREATE TABLE IF NOT EXISTS farm_crop_variety (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장별 품종 연결 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    crop_id BIGINT NOT NULL COMMENT '작물 식별자',
    variety_id BIGINT NOT NULL COMMENT '품종 식별자',
    display_order INT NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_crop_variety (farm_id, variety_id),
    KEY ix_farm_crop_variety_active (farm_id, crop_id, active_yn, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장별 품종 연결';

CREATE TABLE IF NOT EXISTS crop_season (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '작기 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    crop_id BIGINT NOT NULL COMMENT '작물 식별자',
    variety_id BIGINT NULL COMMENT '품종 식별자',
    name VARCHAR(100) NOT NULL COMMENT '작기 이름',
    start_date DATE NOT NULL COMMENT '시작 일자',
    end_date DATE NULL COMMENT '종료 일자',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '작기 상태',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_crop_season_farm (farm_id, status, start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='작기';

CREATE TABLE IF NOT EXISTS work_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '작업 유형 식별자',
    organization_id BIGINT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NULL COMMENT '농장 식별자',
    name VARCHAR(100) NOT NULL COMMENT '작업 유형 이름',
    display_order INT NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    created_by BIGINT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY ix_work_type_farm (farm_id, active_yn),
    UNIQUE KEY uk_work_type_farm_name (farm_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='작업 유형';

CREATE TABLE IF NOT EXISTS work_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '작업 기록 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    crop_id BIGINT NULL COMMENT '작물 식별자',
    variety_id BIGINT NULL COMMENT '품종 식별자',
    season_id BIGINT NULL COMMENT '작기 식별자',
    work_date DATE NOT NULL COMMENT '작업 일자',
    work_type_id BIGINT NOT NULL COMMENT '작업 유형 식별자',
    worker_count DECIMAL(8,2) NULL COMMENT '작업 인원 수',
    work_hours DECIMAL(8,2) NULL COMMENT '작업 시간',
    memo TEXT NULL COMMENT '작업 기록 메모',
    created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' COMMENT '생성 당시 사용자 역할',
    care_assignment_id BIGINT NULL COMMENT '데이터 관리 배정 식별자',
    farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '농가 확인 상태',
    farmer_confirmed_by BIGINT NULL COMMENT '농가 확인 사용자 식별자',
    farmer_confirmed_at DATETIME(6) NULL COMMENT '농가 확인 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_request_id VARCHAR(36) NULL COMMENT '클라이언트 멱등 요청 식별자',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_work_log_farm_date (farm_id, work_date),
    KEY ix_work_log_org_date (organization_id, work_date),
    KEY ix_work_log_zone_date (zone_id, work_date),
    KEY ix_work_log_list (farm_id, deleted_at, work_date, id),
    UNIQUE KEY uk_work_log_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='작업 기록';

CREATE TABLE IF NOT EXISTS pest_control_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '방제 기록 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    crop_id BIGINT NULL COMMENT '작물 식별자',
    variety_id BIGINT NULL COMMENT '품종 식별자',
    season_id BIGINT NULL COMMENT '작기 식별자',
    apply_date DATE NOT NULL COMMENT '적용 일자',
    chemical_name VARCHAR(150) NOT NULL COMMENT '사용 약제명',
    target_pest VARCHAR(150) NULL COMMENT '대상 병해충',
    dilution_ratio VARCHAR(50) NULL COMMENT '희석 배수',
    amount_value DECIMAL(12,2) NULL COMMENT '사용량',
    amount_unit VARCHAR(20) NULL COMMENT '사용량 단위',
    preharvest_interval_days INT NULL COMMENT '수확 전 안전 사용 일수',
    memo TEXT NULL COMMENT '방제 기록 메모',
    created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' COMMENT '생성 당시 사용자 역할',
    care_assignment_id BIGINT NULL COMMENT '데이터 관리 배정 식별자',
    farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '농가 확인 상태',
    farmer_confirmed_by BIGINT NULL COMMENT '농가 확인 사용자 식별자',
    farmer_confirmed_at DATETIME(6) NULL COMMENT '농가 확인 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_request_id VARCHAR(36) NULL COMMENT '클라이언트 멱등 요청 식별자',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_pest_log_farm_date (farm_id, apply_date),
    KEY ix_pest_log_org_date (organization_id, apply_date),
    KEY ix_pest_log_list (farm_id, deleted_at, apply_date, id),
    UNIQUE KEY uk_pest_log_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='방제 기록';

CREATE TABLE IF NOT EXISTS harvest_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '수확 기록 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    crop_id BIGINT NULL COMMENT '작물 식별자',
    variety_id BIGINT NULL COMMENT '품종 식별자',
    season_id BIGINT NULL COMMENT '작기 식별자',
    harvest_date DATE NOT NULL COMMENT '수확 일자',
    grade VARCHAR(50) NULL COMMENT '수확 등급',
    quantity DECIMAL(12,2) NOT NULL COMMENT '수량',
    unit VARCHAR(20) NOT NULL DEFAULT 'kg' COMMENT '수량 단위',
    package_unit VARCHAR(50) NULL COMMENT '포장 단위',
    memo TEXT NULL COMMENT '수확 기록 메모',
    created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' COMMENT '생성 당시 사용자 역할',
    care_assignment_id BIGINT NULL COMMENT '데이터 관리 배정 식별자',
    farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '농가 확인 상태',
    farmer_confirmed_by BIGINT NULL COMMENT '농가 확인 사용자 식별자',
    farmer_confirmed_at DATETIME(6) NULL COMMENT '농가 확인 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_request_id VARCHAR(36) NULL COMMENT '클라이언트 멱등 요청 식별자',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_harvest_farm_date (farm_id, harvest_date),
    KEY ix_harvest_zone_date (zone_id, harvest_date),
    KEY ix_harvest_variety_date (variety_id, harvest_date),
    KEY ix_harvest_log_list (farm_id, deleted_at, harvest_date, id),
    UNIQUE KEY uk_harvest_log_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='수확 기록';

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '거래처 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NULL COMMENT '농장 식별자',
    name VARCHAR(150) NOT NULL COMMENT '거래처 이름',
    customer_type VARCHAR(40) NOT NULL DEFAULT 'OTHER' COMMENT '거래처 유형',
    phone VARCHAR(50) NULL COMMENT '전화번호',
    memo TEXT NULL COMMENT '거래처 메모',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_customer_farm (farm_id, active_yn),
    UNIQUE KEY uk_customer_farm_name (farm_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='거래처';

CREATE TABLE IF NOT EXISTS sales_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '판매 기록 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    customer_id BIGINT NULL COMMENT '거래처 식별자',
    sales_date DATE NOT NULL COMMENT '판매 일자',
    item_name VARCHAR(150) NULL COMMENT '판매 품목명',
    quantity DECIMAL(12,2) NOT NULL COMMENT '수량',
    unit VARCHAR(20) NOT NULL DEFAULT 'kg' COMMENT '수량 단위',
    unit_price DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '단가',
    gross_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '총 판매 금액',
    fee_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '수수료 금액',
    net_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '순 판매 금액',
    net_amount_overridden_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '순 판매 금액 수동 수정 여부',
    settlement_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '정산 상태',
    memo TEXT NULL COMMENT '판매 기록 메모',
    created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' COMMENT '생성 당시 사용자 역할',
    care_assignment_id BIGINT NULL COMMENT '데이터 관리 배정 식별자',
    farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '농가 확인 상태',
    farmer_confirmed_by BIGINT NULL COMMENT '농가 확인 사용자 식별자',
    farmer_confirmed_at DATETIME(6) NULL COMMENT '농가 확인 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_request_id VARCHAR(36) NULL COMMENT '클라이언트 멱등 요청 식별자',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_sales_farm_date (farm_id, sales_date),
    KEY ix_sales_org_date (organization_id, sales_date),
    KEY ix_sales_customer_date (customer_id, sales_date),
    KEY ix_sales_log_list (farm_id, deleted_at, sales_date, id),
    UNIQUE KEY uk_sales_log_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='판매 기록';

CREATE TABLE IF NOT EXISTS attachment_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '첨부 파일 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NULL COMMENT '농장 식별자',
    ref_type VARCHAR(50) NOT NULL COMMENT '참조 대상 유형',
    ref_id BIGINT NOT NULL COMMENT '참조 대상 식별자',
    original_file_name VARCHAR(255) NOT NULL COMMENT '원본 파일 이름',
    stored_file_name VARCHAR(255) NOT NULL COMMENT '저장 파일 이름',
    content_type VARCHAR(100) NOT NULL COMMENT '파일 미디어 유형',
    file_size BIGINT NOT NULL COMMENT '파일 크기 바이트',
    storage_type VARCHAR(30) NOT NULL DEFAULT 'LOCAL' COMMENT '파일 저장 방식',
    storage_path VARCHAR(500) NOT NULL COMMENT '저장 경로',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    client_file_id VARCHAR(36) NULL COMMENT '클라이언트 파일 식별자',
    PRIMARY KEY (id),
    KEY ix_attach_ref (ref_type, ref_id),
    KEY ix_attach_farm (farm_id, created_at),
    UNIQUE KEY uk_attach_client_file (farm_id, ref_type, ref_id, client_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='첨부 파일';

CREATE TABLE IF NOT EXISTS export_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '내보내기 작업 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    export_type VARCHAR(30) NOT NULL COMMENT '내보내기 파일 형식',
    scopes_json JSON NOT NULL COMMENT '내보내기 포함 범위 JSON',
    confirmation_filter VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '농가 확인 상태 필터',
    client_request_id VARCHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    period_start DATE NOT NULL COMMENT '기간 시작 일자',
    period_end DATE NOT NULL COMMENT '조회 종료 일자',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED' COMMENT '내보내기 작업 상태',
    file_id BIGINT NULL COMMENT '생성된 첨부 파일 식별자',
    error_code VARCHAR(50) NULL COMMENT '오류 코드',
    error_message TEXT NULL COMMENT '오류 메시지',
    requested_by BIGINT NOT NULL COMMENT '요청 사용자 식별자',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 일시',
    started_at DATETIME(6) NULL COMMENT '처리 시작 일시',
    completed_at DATETIME(6) NULL COMMENT '처리 완료 일시',
    expires_at DATETIME(6) NULL COMMENT '만료 일시',
    lease_until DATETIME(6) NULL COMMENT '작업 선점 만료 일시',
    claim_token VARCHAR(36) NULL COMMENT '작업 선점 토큰',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '처리 시도 횟수',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_export_job_request (farm_id, client_request_id),
    KEY ix_export_farm_requested (farm_id, requested_at),
    KEY ix_export_status_requested (status, requested_at),
    KEY ix_export_status_lease (status, lease_until),
    KEY ix_export_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='내보내기 작업';

CREATE TABLE IF NOT EXISTS photo_upload_batch (
    id VARCHAR(36) NOT NULL COMMENT '사진 업로드 묶음 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    owner_user_id BIGINT NOT NULL COMMENT '농장주 사용자 식별자',
    care_assignment_id BIGINT NULL COMMENT '데이터 관리 배정 식별자',
    client_request_id VARCHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    issue_date DATE NOT NULL COMMENT '이슈 기준 일자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    estimated_record_type VARCHAR(30) NOT NULL COMMENT '추정 기록 유형',
    memo TEXT NULL COMMENT '사진 업로드 묶음 메모',
    file_count INT NOT NULL COMMENT '업로드 파일 수',
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADING' COMMENT '사진 업로드 묶음 상태',
    commit_request_id VARCHAR(36) NULL COMMENT '업로드 확정 요청 식별자',
    issue_id BIGINT NULL COMMENT '데이터 품질 이슈 식별자',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    committed_at DATETIME(6) NULL COMMENT '업로드 확정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_batch_request (farm_id, owner_user_id, client_request_id),
    KEY ix_photo_batch_expiry (status, expires_at),
    KEY ix_photo_batch_care_assignment (care_assignment_id),
    CONSTRAINT fk_photo_batch_care_assignment FOREIGN KEY (care_assignment_id) REFERENCES farm_care_assignment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사진 업로드 묶음';

CREATE TABLE IF NOT EXISTS photo_upload_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사진 업로드 파일 식별자',
    batch_id VARCHAR(36) NOT NULL COMMENT '사진 업로드 묶음 식별자',
    client_file_id VARCHAR(36) NOT NULL COMMENT '클라이언트 파일 식별자',
    original_file_name VARCHAR(255) NOT NULL COMMENT '원본 파일 이름',
    stored_file_name VARCHAR(255) NOT NULL COMMENT '저장 파일 이름',
    content_type VARCHAR(100) NOT NULL COMMENT '파일 미디어 유형',
    file_size BIGINT NOT NULL COMMENT '파일 크기 바이트',
    staging_path VARCHAR(500) NOT NULL COMMENT '임시 저장 경로',
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED' COMMENT '사진 업로드 파일 상태',
    uploaded_at DATETIME(6) NOT NULL COMMENT '업로드 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_batch_file (batch_id, client_file_id),
    KEY ix_photo_file_batch (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사진 업로드 파일';

CREATE TABLE IF NOT EXISTS data_quality_issue (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '데이터 품질 이슈 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    issue_date DATE NULL COMMENT '이슈 기준 일자',
    issue_type VARCHAR(50) NOT NULL COMMENT '이슈 유형',
    issue_status VARCHAR(30) NOT NULL DEFAULT 'OPEN' COMMENT '이슈 처리 상태',
    severity VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '이슈 심각도',
    title VARCHAR(200) NOT NULL COMMENT '이슈 제목',
    description TEXT NULL COMMENT '상세 설명',
    detected_by VARCHAR(30) NOT NULL DEFAULT 'SYSTEM' COMMENT '이슈 탐지 주체',
    assigned_manager_user_id BIGINT NULL COMMENT '담당 매니저 사용자 식별자',
    related_ref_type VARCHAR(50) NULL COMMENT '관련 기록 유형',
    related_ref_id BIGINT NULL COMMENT '관련 기록 식별자',
    resolution_ref_type VARCHAR(50) NULL COMMENT '해결 기록 유형',
    resolution_ref_id BIGINT NULL COMMENT '해결 기록 식별자',
    farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '농가 확인 상태',
    farmer_confirmed_by BIGINT NULL COMMENT '농가 확인 사용자 식별자',
    farmer_confirmed_at DATETIME(6) NULL COMMENT '농가 확인 일시',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    closed_by BIGINT NULL COMMENT '종료 사용자 식별자',
    closed_at DATETIME(6) NULL COMMENT '종료 일시',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    PRIMARY KEY (id),
    KEY ix_data_quality_farm_status (farm_id, issue_status, issue_date),
    KEY ix_data_quality_manager_status (assigned_manager_user_id, issue_status),
    KEY ix_data_quality_ref (related_ref_type, related_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='데이터 품질 이슈';

CREATE TABLE IF NOT EXISTS data_followup_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '데이터 후속 조치 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    issue_id BIGINT NULL COMMENT '데이터 품질 이슈 식별자',
    manager_user_id BIGINT NOT NULL COMMENT '매니저 사용자 식별자',
    farmer_user_id BIGINT NULL COMMENT '농가 사용자 식별자',
    contact_method VARCHAR(30) NOT NULL COMMENT '연락 방법',
    followup_status VARCHAR(30) NOT NULL DEFAULT 'CONTACTED' COMMENT '후속 조치 상태',
    content_summary TEXT NOT NULL COMMENT '후속 조치 내용 요약',
    evidence_file_id BIGINT NULL COMMENT '증빙 파일 식별자',
    next_action_at DATETIME(6) NULL COMMENT '다음 조치 예정 일시',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    client_request_id VARCHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    PRIMARY KEY (id),
    KEY ix_followup_issue (issue_id, created_at),
    KEY ix_followup_farm (farm_id, created_at),
    KEY ix_followup_manager (manager_user_id, created_at),
    UNIQUE KEY uk_followup_request (issue_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='데이터 후속 조치';

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '감사 로그 식별자',
    organization_id BIGINT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NULL COMMENT '농장 식별자',
    actor_user_id BIGINT NULL COMMENT '작업 수행 사용자 식별자',
    action VARCHAR(80) NOT NULL COMMENT '수행 작업 코드',
    target_type VARCHAR(80) NOT NULL COMMENT '작업 대상 유형',
    target_id BIGINT NULL COMMENT '작업 대상 식별자',
    ip_address VARCHAR(80) NULL COMMENT '요청 IP 주소',
    user_agent VARCHAR(500) NULL COMMENT '요청 사용자 에이전트',
    detail_json JSON NULL COMMENT '감사 상세 정보 JSON',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (id),
    KEY ix_audit_org_created (organization_id, created_at),
    KEY ix_audit_farm_created (farm_id, created_at),
    KEY ix_audit_actor_created (actor_user_id, created_at),
    KEY ix_audit_created_id (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='감사 로그';

-- =========================================================
-- 2. 확장 테이블 (원본 DDL 초안에 없음, 개발 가이드 반영으로 Phase 0에서 추가)
--    plan/04_Open_Questions_Log.md에 근거 기록
-- =========================================================

-- 비료/양액 기록 (developer design 6장에 테이블명은 언급되나 DDL 초안 누락)
CREATE TABLE IF NOT EXISTS fertilizer_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '비료 및 양액 기록 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    zone_id BIGINT NULL COMMENT '농장 구역 식별자',
    crop_id BIGINT NULL COMMENT '작물 식별자',
    variety_id BIGINT NULL COMMENT '품종 식별자',
    season_id BIGINT NULL COMMENT '작기 식별자',
    apply_date DATE NOT NULL COMMENT '적용 일자',
    material_name VARCHAR(150) NOT NULL COMMENT '사용 자재명',
    amount_value DECIMAL(12,2) NULL COMMENT '사용량',
    amount_unit VARCHAR(20) NULL COMMENT '사용량 단위',
    memo TEXT NULL COMMENT '비료 및 양액 기록 메모',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_fertilizer_farm_date (farm_id, apply_date),
    KEY ix_fertilizer_org_date (organization_id, apply_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='비료 및 양액 기록';

-- 자재 기준정보 (W-04 기준정보 관리 > 자재 탭)
CREATE TABLE IF NOT EXISTS material (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '자재 기준정보 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NULL COMMENT '농장 식별자',
    name VARCHAR(150) NOT NULL COMMENT '자재 기준정보 이름',
    material_type VARCHAR(40) NOT NULL DEFAULT 'ETC' COMMENT '자재 유형',
    unit VARCHAR(20) NULL COMMENT '수량 단위',
    memo TEXT NULL COMMENT '자재 기준정보 메모',
    active_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '활성 여부',
    created_by BIGINT NOT NULL COMMENT '생성 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_by BIGINT NULL COMMENT '수정 사용자 식별자',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    deleted_at DATETIME(6) NULL COMMENT '소프트 삭제 일시',
    PRIMARY KEY (id),
    KEY ix_material_farm (farm_id, active_yn),
    UNIQUE KEY uk_material_farm_name (farm_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='자재 기준정보';

-- 사용자 UI 설정 (viewScale, outdoorMode 등) — GET/PUT /users/me/preferences
CREATE TABLE IF NOT EXISTS user_preference (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 환경설정 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    view_scale VARCHAR(20) NOT NULL DEFAULT 'standard' COMMENT '화면 보기 배율',
    outdoor_mode_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '야외 화면 모드 사용 여부',
    reduce_motion_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '동작 줄이기 사용 여부',
    last_selected_farm_id BIGINT NULL COMMENT '마지막 선택 농장 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_preference_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 환경설정';

-- Refresh Token 저장/폐기 (developer design 9장 — 서버 저장/폐기 가능 구조)
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '갱신 토큰 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    token_hash VARCHAR(255) NOT NULL COMMENT '토큰 해시',
    issued_at DATETIME(6) NOT NULL COMMENT '토큰 발급 일시',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    revoked_at DATETIME(6) NULL COMMENT '회수 일시',
    replaced_by_token_id BIGINT NULL COMMENT '대체 갱신 토큰 식별자',
    ip_address VARCHAR(80) NULL COMMENT '요청 IP 주소',
    user_agent VARCHAR(500) NULL COMMENT '요청 사용자 에이전트',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='갱신 토큰';

-- 비밀번호 재설정 토큰 (M-03) — SMTP 미구성 시 콘솔 로그로 대체 발송
CREATE TABLE IF NOT EXISTS password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '비밀번호 재설정 토큰 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    token_hash VARCHAR(255) NOT NULL COMMENT '토큰 해시',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    used_at DATETIME(6) NULL COMMENT '사용 완료 일시',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY ix_password_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='비밀번호 재설정 토큰';

-- 농장 사용자 초대 (W-07 사용자·권한 관리, 신규 제안 API)
CREATE TABLE IF NOT EXISTS farm_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '농장 초대 식별자',
    organization_id BIGINT NOT NULL COMMENT '소속 조직 식별자',
    farm_id BIGINT NOT NULL COMMENT '농장 식별자',
    email VARCHAR(190) NOT NULL COMMENT '이메일 주소',
    role VARCHAR(40) NOT NULL COMMENT '부여 역할',
    invite_token_hash VARCHAR(255) NOT NULL COMMENT '초대 토큰 해시',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '농장 초대 상태',
    invited_by BIGINT NOT NULL COMMENT '초대한 사용자 식별자',
    invited_at DATETIME(6) NOT NULL COMMENT '초대 일시',
    responded_at DATETIME(6) NULL COMMENT '초대 응답 일시',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    accepted_user_id BIGINT NULL COMMENT '초대를 수락한 사용자 식별자',
    client_request_id VARCHAR(36) NOT NULL COMMENT '클라이언트 멱등 요청 식별자',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_invitation_token (invite_token_hash),
    KEY ix_farm_invitation_farm (farm_id, status),
    KEY ix_farm_invitation_email (email, status),
    UNIQUE KEY uk_farm_invitation_request (farm_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='농장 초대';
