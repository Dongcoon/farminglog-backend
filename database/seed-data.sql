-- Farmlog 로컬/개발 전용 데모 데이터
-- 적용 순서: database/schema.sql -> database/seed-data.sql
-- 운영 DB에는 적용하지 않는다.
--
-- 모든 데모 계정 공통 비밀번호: FarmLog!2026
-- BCrypt 해시($2b$, cost 10)는 Spring Security BCryptPasswordEncoder와 호환된다.
--
-- 대표 로그인 계정
--   농장주:       owner@farmlog.test
--   작업자:       worker@farmlog.test
--   농장 관리자:  farmmanager@farmlog.test
--   데이터 매니저: manager@farmlog.test
--   조직 관리자:  orgadmin@farmlog.test
--   시스템 관리자: sysadmin@farmlog.test
--
-- 이 스크립트는 고정 PK를 사용하지 않는다. 이메일/농장 코드/멱등키로 기존 데모 행을
-- 재사용하므로 이전 seed가 일부만 적용된 로컬 DB에서도 다시 실행할 수 있다.

START TRANSACTION;

SET @demo_password_hash = '$2b$10$bihQBQNyCROHgawzKNNDuu1QE2BG0S5.FFrmkYFXdY7Gik61Qrao2';
SET @current_month_start = CAST(DATE_FORMAT(CURDATE(), '%Y-%m-01') AS DATE);
SET @previous_month_start = DATE_SUB(@current_month_start, INTERVAL 1 MONTH);
SET @active_season_start = DATE_SUB(CURDATE(), INTERVAL 120 DAY);
SET @active_season_end = DATE_ADD(CURDATE(), INTERVAL 120 DAY);

-- =========================================================
-- 1. 로그인 계정
-- =========================================================
INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('sysadmin@farmlog.test', @demo_password_hash, '시스템 운영자', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @sysadmin_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('owner@farmlog.test', @demo_password_hash, '오세훈 농장주', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @owner_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('worker@farmlog.test', @demo_password_hash, '김하늘 작업자', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @worker_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('farmmanager@farmlog.test', @demo_password_hash, '윤서준 농장 관리자', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @farm_manager_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('manager@farmlog.test', @demo_password_hash, '박지우 데이터 매니저', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @care_manager_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('orgadmin@farmlog.test', @demo_password_hash, '최민준 작목반 관리자', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @org_admin_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('farmer2@farmlog.test', @demo_password_hash, '정다은 농장주', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @farmer2_user_id = LAST_INSERT_ID();

INSERT INTO users (email, password_hash, display_name, status, created_at)
VALUES ('viewer@farmlog.test', @demo_password_hash, '한유진 조회 사용자', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), password_hash = VALUES(password_hash),
    display_name = VALUES(display_name), status = 'ACTIVE', deleted_at = NULL;
SET @viewer_user_id = LAST_INSERT_ID();

-- =========================================================
-- 2. 조직과 역할
-- =========================================================
INSERT INTO organization (name, org_type, status, created_by, created_at)
SELECT 'Farmlog 운영팀', 'SYSTEM', 'ACTIVE', @sysadmin_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM organization
    WHERE name = 'Farmlog 운영팀' AND org_type = 'SYSTEM' AND deleted_at IS NULL
);
SELECT id INTO @system_org_id
FROM organization
WHERE name = 'Farmlog 운영팀' AND org_type = 'SYSTEM' AND deleted_at IS NULL
ORDER BY id LIMIT 1;

INSERT INTO organization (name, org_type, status, created_by, created_at)
SELECT '이몽룡 농장', 'PERSONAL', 'ACTIVE', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM organization
    WHERE name = '이몽룡 농장' AND org_type = 'PERSONAL' AND deleted_at IS NULL
);
SELECT id INTO @owner_org_id
FROM organization
WHERE name = '이몽룡 농장' AND org_type = 'PERSONAL' AND deleted_at IS NULL
ORDER BY id LIMIT 1;

INSERT INTO organization (name, org_type, status, created_by, created_at)
SELECT '한들딸기 작목반', 'COOP', 'ACTIVE', @org_admin_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM organization
    WHERE name = '한들딸기 작목반' AND org_type = 'COOP' AND deleted_at IS NULL
);
SELECT id INTO @coop_org_id
FROM organization
WHERE name = '한들딸기 작목반' AND org_type = 'COOP' AND deleted_at IS NULL
ORDER BY id LIMIT 1;

INSERT INTO organization_member (organization_id, user_id, role, status, created_at)
VALUES
    (@system_org_id, @sysadmin_user_id, 'SYSTEM_ADMIN', 'ACTIVE', NOW(6)),
    (@owner_org_id, @owner_user_id, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (@coop_org_id, @org_admin_user_id, 'ORG_ADMIN', 'ACTIVE', NOW(6)),
    (@coop_org_id, @farmer2_user_id, 'FARM_OWNER', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE role = VALUES(role), status = 'ACTIVE';

-- =========================================================
-- 3. 작물 카탈로그
-- =========================================================
INSERT INTO crop (name, active_yn)
VALUES ('딸기', 'Y')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), active_yn = 'Y';
SET @strawberry_crop_id = LAST_INSERT_ID();

INSERT INTO crop_variety (crop_id, name, active_yn)
VALUES (@strawberry_crop_id, '설향', 'Y')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), active_yn = 'Y';
SET @seolhyang_variety_id = LAST_INSERT_ID();

INSERT INTO crop_variety (crop_id, name, active_yn)
VALUES (@strawberry_crop_id, '금실', 'Y')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), active_yn = 'Y';
SET @geumsil_variety_id = LAST_INSERT_ID();

INSERT INTO crop_variety (crop_id, name, active_yn)
VALUES (@strawberry_crop_id, '킹스베리', 'Y')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), active_yn = 'Y';
SET @kingsberry_variety_id = LAST_INSERT_ID();

-- =========================================================
-- 4. 농장과 구성원
-- =========================================================
INSERT INTO farm (
    organization_id, farm_code, name, owner_user_id, main_crop_id,
    address, memo, lifecycle_status, status, created_by, created_at
)
VALUES (
    @owner_org_id, 'F-0001', '한들 제1농장', @owner_user_id, @strawberry_crop_id,
    '충남 논산시 강경읍 채운로 128', '설향 중심의 스마트팜 시설재배 농장',
    'ACTIVE', 'ACTIVE', @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), owner_user_id = VALUES(owner_user_id),
    main_crop_id = VALUES(main_crop_id), address = VALUES(address), memo = VALUES(memo),
    lifecycle_status = 'ACTIVE', status = 'ACTIVE', deleted_at = NULL;
SET @farm1_id = LAST_INSERT_ID();

INSERT INTO farm (
    organization_id, farm_code, name, owner_user_id, main_crop_id,
    address, memo, lifecycle_status, status, created_by, created_at
)
VALUES (
    @owner_org_id, 'F-0002', '한들 제2농장', @owner_user_id, @strawberry_crop_id,
    '충남 논산시 연무읍 득안대로 45', '금실 품종과 직거래 출하를 운영하는 농장',
    'ACTIVE', 'ACTIVE', @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), owner_user_id = VALUES(owner_user_id),
    main_crop_id = VALUES(main_crop_id), address = VALUES(address), memo = VALUES(memo),
    lifecycle_status = 'ACTIVE', status = 'ACTIVE', deleted_at = NULL;
SET @farm2_id = LAST_INSERT_ID();

INSERT INTO farm (
    organization_id, farm_code, name, owner_user_id, main_crop_id,
    address, memo, lifecycle_status, status, created_by, created_at
)
VALUES (
    @coop_org_id, 'F-0003', '푸른들 농장', @farmer2_user_id, @strawberry_crop_id,
    '충남 부여군 세도면 청포로 77', '작목반 공동 출하에 참여하는 딸기 농장',
    'ACTIVE', 'ACTIVE', @farmer2_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), owner_user_id = VALUES(owner_user_id),
    main_crop_id = VALUES(main_crop_id), address = VALUES(address), memo = VALUES(memo),
    lifecycle_status = 'ACTIVE', status = 'ACTIVE', deleted_at = NULL;
SET @farm3_id = LAST_INSERT_ID();

INSERT INTO farm_member (farm_id, user_id, role, status, created_at)
VALUES
    (@farm1_id, @owner_user_id, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (@farm1_id, @worker_user_id, 'WORKER', 'ACTIVE', NOW(6)),
    (@farm1_id, @farm_manager_user_id, 'FARM_MANAGER', 'ACTIVE', NOW(6)),
    (@farm1_id, @viewer_user_id, 'VIEWER', 'ACTIVE', NOW(6)),
    (@farm2_id, @owner_user_id, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (@farm2_id, @farm_manager_user_id, 'FARM_MANAGER', 'ACTIVE', NOW(6)),
    (@farm3_id, @farmer2_user_id, 'FARM_OWNER', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE role = VALUES(role), status = 'ACTIVE';

INSERT INTO farm_care_assignment (
    organization_id, farm_id, manager_user_id, assigned_by,
    assignment_type, permission_scope, status, starts_at, ends_at, memo,
    client_request_id, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @care_manager_user_id, @owner_user_id,
    'DATA_CARE', 'REVIEW_AND_INPUT', 'ACTIVE', DATE_SUB(NOW(6), INTERVAL 30 DAY), NULL,
    '주 2회 기록 검수 및 누락 데이터 보완',
    '10000000-0000-4000-8000-000000000001', NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), manager_user_id = VALUES(manager_user_id),
    assigned_by = VALUES(assigned_by), status = 'ACTIVE', starts_at = VALUES(starts_at),
    ends_at = NULL, memo = VALUES(memo), revoked_at = NULL, revoked_by = NULL,
    revoked_reason = NULL;
SET @farm1_care_assignment_id = LAST_INSERT_ID();

INSERT INTO farm_care_assignment (
    organization_id, farm_id, manager_user_id, assigned_by,
    assignment_type, permission_scope, status, starts_at, ends_at, memo,
    client_request_id, created_at
)
VALUES (
    @owner_org_id, @farm2_id, @care_manager_user_id, @owner_user_id,
    'DATA_CARE', 'REVIEW_AND_INPUT', 'ACTIVE', DATE_SUB(NOW(6), INTERVAL 20 DAY), NULL,
    '판매 및 수확 기록 월말 정리 지원',
    '10000000-0000-4000-8000-000000000002', NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), manager_user_id = VALUES(manager_user_id),
    assigned_by = VALUES(assigned_by), status = 'ACTIVE', starts_at = VALUES(starts_at),
    ends_at = NULL, memo = VALUES(memo), revoked_at = NULL, revoked_by = NULL,
    revoked_reason = NULL;
SET @farm2_care_assignment_id = LAST_INSERT_ID();

-- =========================================================
-- 5. 하우스/구역과 작기
-- =========================================================
INSERT INTO farm_zone (
    organization_id, farm_id, name, zone_type, area_value, area_unit,
    display_order, active_yn, created_by, created_at
)
SELECT @owner_org_id, @farm1_id, '1동 하우스', 'GREENHOUSE', 600.00, 'm2', 1, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM farm_zone WHERE farm_id = @farm1_id AND name = '1동 하우스' AND deleted_at IS NULL
);
SELECT id INTO @farm1_zone1_id FROM farm_zone
WHERE farm_id = @farm1_id AND name = '1동 하우스' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO farm_zone (
    organization_id, farm_id, name, zone_type, area_value, area_unit,
    display_order, active_yn, created_by, created_at
)
SELECT @owner_org_id, @farm1_id, '2동 하우스', 'GREENHOUSE', 500.00, 'm2', 2, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM farm_zone WHERE farm_id = @farm1_id AND name = '2동 하우스' AND deleted_at IS NULL
);
SELECT id INTO @farm1_zone2_id FROM farm_zone
WHERE farm_id = @farm1_id AND name = '2동 하우스' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO farm_zone (
    organization_id, farm_id, name, zone_type, area_value, area_unit,
    display_order, active_yn, created_by, created_at
)
SELECT @owner_org_id, @farm2_id, '동편 하우스', 'GREENHOUSE', 800.00, 'm2', 1, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM farm_zone WHERE farm_id = @farm2_id AND name = '동편 하우스' AND deleted_at IS NULL
);
SELECT id INTO @farm2_zone1_id FROM farm_zone
WHERE farm_id = @farm2_id AND name = '동편 하우스' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO farm_zone (
    organization_id, farm_id, name, zone_type, area_value, area_unit,
    display_order, active_yn, created_by, created_at
)
SELECT @coop_org_id, @farm3_id, '본동 하우스', 'GREENHOUSE', 700.00, 'm2', 1, 'Y', @farmer2_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM farm_zone WHERE farm_id = @farm3_id AND name = '본동 하우스' AND deleted_at IS NULL
);
SELECT id INTO @farm3_zone1_id FROM farm_zone
WHERE farm_id = @farm3_id AND name = '본동 하우스' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO farm_zone_assignment (
    organization_id, zone_id, farm_id, effective_from, active_yn, created_by, created_at
)
VALUES
    (@owner_org_id, @farm1_zone1_id, @farm1_id, DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 'Y', @owner_user_id, NOW(6)),
    (@owner_org_id, @farm1_zone2_id, @farm1_id, DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 'Y', @owner_user_id, NOW(6)),
    (@owner_org_id, @farm2_zone1_id, @farm2_id, DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 'Y', @owner_user_id, NOW(6)),
    (@coop_org_id, @farm3_zone1_id, @farm3_id, DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 'Y', @farmer2_user_id, NOW(6))
ON DUPLICATE KEY UPDATE farm_id = VALUES(farm_id), active_yn = 'Y', effective_to = NULL;

INSERT INTO farm_crop (farm_id, crop_id, display_order, active_yn, created_by, created_at)
VALUES
    (@farm1_id, @strawberry_crop_id, 1, 'Y', @owner_user_id, NOW(6)),
    (@farm2_id, @strawberry_crop_id, 1, 'Y', @owner_user_id, NOW(6)),
    (@farm3_id, @strawberry_crop_id, 1, 'Y', @farmer2_user_id, NOW(6))
ON DUPLICATE KEY UPDATE display_order = VALUES(display_order), active_yn = 'Y';

INSERT INTO farm_crop_variety (
    farm_id, crop_id, variety_id, display_order, active_yn, created_by, created_at
)
VALUES
    (@farm1_id, @strawberry_crop_id, @seolhyang_variety_id, 1, 'Y', @owner_user_id, NOW(6)),
    (@farm1_id, @strawberry_crop_id, @geumsil_variety_id, 2, 'Y', @owner_user_id, NOW(6)),
    (@farm1_id, @strawberry_crop_id, @kingsberry_variety_id, 3, 'Y', @owner_user_id, NOW(6)),
    (@farm2_id, @strawberry_crop_id, @geumsil_variety_id, 1, 'Y', @owner_user_id, NOW(6)),
    (@farm3_id, @strawberry_crop_id, @seolhyang_variety_id, 1, 'Y', @farmer2_user_id, NOW(6))
ON DUPLICATE KEY UPDATE display_order = VALUES(display_order), active_yn = 'Y';

INSERT INTO crop_season (
    organization_id, farm_id, crop_id, variety_id, name,
    start_date, end_date, status, created_by, created_at
)
SELECT @owner_org_id, @farm1_id, @strawberry_crop_id, @seolhyang_variety_id,
       '설향 데모 작기', @active_season_start, @active_season_end,
       'ACTIVE', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM crop_season WHERE farm_id = @farm1_id AND name = '설향 데모 작기' AND deleted_at IS NULL
);
UPDATE crop_season
SET start_date = @active_season_start, end_date = @active_season_end, status = 'ACTIVE', deleted_at = NULL
WHERE farm_id = @farm1_id AND name = '설향 데모 작기';
SELECT id INTO @farm1_season_id FROM crop_season
WHERE farm_id = @farm1_id AND name = '설향 데모 작기' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO crop_season (
    organization_id, farm_id, crop_id, variety_id, name,
    start_date, end_date, status, created_by, created_at
)
SELECT @owner_org_id, @farm2_id, @strawberry_crop_id, @geumsil_variety_id,
       '금실 데모 작기', @active_season_start, @active_season_end,
       'ACTIVE', @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM crop_season WHERE farm_id = @farm2_id AND name = '금실 데모 작기' AND deleted_at IS NULL
);
UPDATE crop_season
SET start_date = @active_season_start, end_date = @active_season_end, status = 'ACTIVE', deleted_at = NULL
WHERE farm_id = @farm2_id AND name = '금실 데모 작기';
SELECT id INTO @farm2_season_id FROM crop_season
WHERE farm_id = @farm2_id AND name = '금실 데모 작기' AND deleted_at IS NULL ORDER BY id LIMIT 1;

INSERT INTO crop_season (
    organization_id, farm_id, crop_id, variety_id, name,
    start_date, end_date, status, created_by, created_at
)
SELECT @coop_org_id, @farm3_id, @strawberry_crop_id, @seolhyang_variety_id,
       '공동출하 데모 작기', @active_season_start, @active_season_end,
       'ACTIVE', @farmer2_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM crop_season WHERE farm_id = @farm3_id AND name = '공동출하 데모 작기' AND deleted_at IS NULL
);
UPDATE crop_season
SET start_date = @active_season_start, end_date = @active_season_end, status = 'ACTIVE', deleted_at = NULL
WHERE farm_id = @farm3_id AND name = '공동출하 데모 작기';
SELECT id INTO @farm3_season_id FROM crop_season
WHERE farm_id = @farm3_id AND name = '공동출하 데모 작기' AND deleted_at IS NULL ORDER BY id LIMIT 1;

-- =========================================================
-- 6. 작업 유형, 거래처, 자재
-- =========================================================
INSERT INTO work_type (organization_id, farm_id, name, display_order, active_yn, created_by, created_at)
SELECT @owner_org_id, NULL, '유인', 1, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM work_type WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '유인');
SELECT id INTO @work_type_training_id FROM work_type
WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '유인' ORDER BY id LIMIT 1;

INSERT INTO work_type (organization_id, farm_id, name, display_order, active_yn, created_by, created_at)
SELECT @owner_org_id, NULL, '적화·적과', 2, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM work_type WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '적화·적과');
SELECT id INTO @work_type_thinning_id FROM work_type
WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '적화·적과' ORDER BY id LIMIT 1;

INSERT INTO work_type (organization_id, farm_id, name, display_order, active_yn, created_by, created_at)
SELECT @owner_org_id, NULL, '관수', 3, 'Y', @owner_user_id, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM work_type WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '관수');
SELECT id INTO @work_type_watering_id FROM work_type
WHERE organization_id = @owner_org_id AND farm_id IS NULL AND name = '관수' ORDER BY id LIMIT 1;

INSERT INTO work_type (organization_id, farm_id, name, display_order, active_yn, created_by, created_at)
SELECT @coop_org_id, NULL, '선별·포장', 1, 'Y', @farmer2_user_id, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM work_type WHERE organization_id = @coop_org_id AND farm_id IS NULL AND name = '선별·포장');
SELECT id INTO @work_type_packaging_id FROM work_type
WHERE organization_id = @coop_org_id AND farm_id IS NULL AND name = '선별·포장' ORDER BY id LIMIT 1;

INSERT INTO customer (
    organization_id, farm_id, name, customer_type, phone, memo,
    active_yn, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, '서울공판장', 'WHOLESALE_MARKET', '02-3435-1000',
    '월·수·금 새벽 경매 출하', 'Y', @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), phone = VALUES(phone), memo = VALUES(memo), active_yn = 'Y';
SET @customer_market_id = LAST_INSERT_ID();

INSERT INTO customer (
    organization_id, farm_id, name, customer_type, phone, memo,
    active_yn, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, '논산로컬푸드 직매장', 'RETAIL', '041-730-7788',
    '매주 화요일 오전 납품', 'Y', @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), phone = VALUES(phone), memo = VALUES(memo), active_yn = 'Y';
SET @customer_local_id = LAST_INSERT_ID();

INSERT INTO customer (
    organization_id, farm_id, name, customer_type, phone, memo,
    active_yn, created_by, created_at
)
VALUES (
    @owner_org_id, @farm2_id, '팜마켓 온라인몰', 'DIRECT', '070-4555-2026',
    '2kg 선물세트 중심 직거래', 'Y', @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), phone = VALUES(phone), memo = VALUES(memo), active_yn = 'Y';
SET @customer_online_id = LAST_INSERT_ID();

INSERT INTO material (
    organization_id, farm_id, name, material_type, unit, memo,
    active_yn, created_by, created_at
)
VALUES
    (@owner_org_id, @farm1_id, '딸기 전용 양액 A', 'FERTILIZER', 'L', '관주용 농축 양액', 'Y', @owner_user_id, NOW(6)),
    (@owner_org_id, @farm1_id, '탄저병 방제제', 'PESTICIDE', 'L', '안전사용기준 준수', 'Y', @owner_user_id, NOW(6)),
    (@owner_org_id, @farm1_id, '딸기 포장박스 2kg', 'PACKAGING', 'ea', '온라인·직매장 공용 포장', 'Y', @owner_user_id, NOW(6))
ON DUPLICATE KEY UPDATE material_type = VALUES(material_type), unit = VALUES(unit), memo = VALUES(memo), active_yn = 'Y';

-- =========================================================
-- 7. 이번 달/지난달 영농 기록
-- =========================================================
-- 농장주가 입력한 작업 기록
INSERT INTO work_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    work_date, work_type_id, worker_count, work_hours, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(7, DAY(CURDATE()) - 1) DAY),
    @work_type_training_id, 2, 4.5, '생육이 빠른 줄부터 유인끈 높이를 조정함',
    'FARM_OWNER', 'NOT_REQUIRED', '20000000-0000-4000-8000-000000000001',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    work_date = VALUES(work_date), worker_count = VALUES(worker_count), work_hours = VALUES(work_hours),
    memo = VALUES(memo), created_role = VALUES(created_role), deleted_at = NULL;

-- 작업자가 입력한 작업 기록
INSERT INTO work_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    work_date, work_type_id, worker_count, work_hours, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone2_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(3, DAY(CURDATE()) - 1) DAY),
    @work_type_thinning_id, 3, 5.0, '기형과와 소과를 제거하고 화방별 착과 수를 정리함',
    'WORKER', 'NOT_REQUIRED', '20000000-0000-4000-8000-000000000002',
    @worker_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    work_date = VALUES(work_date), worker_count = VALUES(worker_count), work_hours = VALUES(work_hours),
    memo = VALUES(memo), created_role = VALUES(created_role), deleted_at = NULL;

-- 데이터 매니저가 입력해 농장주 확인을 기다리는 작업 기록
INSERT INTO work_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    work_date, work_type_id, worker_count, work_hours, memo,
    created_role, care_assignment_id, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm2_id, @farm2_zone1_id, @strawberry_crop_id, @geumsil_variety_id, @farm2_season_id,
    CURDATE(), @work_type_watering_id, 1, 1.5,
    '농장주 통화 내용을 바탕으로 오전 관수량을 대리 입력함',
    'FARM_CARE_MANAGER', @farm2_care_assignment_id, 'PENDING',
    '20000000-0000-4000-8000-000000000003', @care_manager_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    work_date = VALUES(work_date), memo = VALUES(memo), created_role = VALUES(created_role),
    care_assignment_id = VALUES(care_assignment_id), farmer_confirm_status = 'PENDING',
    farmer_confirmed_by = NULL, farmer_confirmed_at = NULL, deleted_at = NULL;

-- 지난달 비교용 작업 기록
INSERT INTO work_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    work_date, work_type_id, worker_count, work_hours, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_ADD(@previous_month_start, INTERVAL 10 DAY),
    @work_type_watering_id, 1, 2.0, '지난달 리포트 비교용 관수 기록',
    'FARM_OWNER', 'NOT_REQUIRED', '20000000-0000-4000-8000-000000000004',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE work_date = VALUES(work_date), memo = VALUES(memo), deleted_at = NULL;

INSERT INTO pest_control_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    apply_date, chemical_name, target_pest, dilution_ratio, amount_value, amount_unit,
    preharvest_interval_days, memo, created_role, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(5, DAY(CURDATE()) - 1) DAY),
    '탄저병 방제제', '탄저병', '1:1000', 2.5, 'L', 3,
    '환기 후 잎과 관부 중심으로 고르게 살포함',
    'FARM_OWNER', 'NOT_REQUIRED', '30000000-0000-4000-8000-000000000001',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    apply_date = VALUES(apply_date), memo = VALUES(memo), created_role = VALUES(created_role), deleted_at = NULL;

INSERT INTO pest_control_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    apply_date, chemical_name, target_pest, dilution_ratio, amount_value, amount_unit,
    preharvest_interval_days, memo, created_role, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm2_id, @farm2_zone1_id, @strawberry_crop_id, @geumsil_variety_id, @farm2_season_id,
    DATE_ADD(@previous_month_start, INTERVAL 7 DAY),
    '흰가루병 방제제', '흰가루병', '1:800', 1.8, 'L', 5,
    '지난달 리포트 비교용 방제 기록',
    'FARM_OWNER', 'NOT_REQUIRED', '30000000-0000-4000-8000-000000000002',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE apply_date = VALUES(apply_date), memo = VALUES(memo), deleted_at = NULL;

INSERT INTO harvest_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    harvest_date, grade, quantity, unit, package_unit, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(4, DAY(CURDATE()) - 1) DAY),
    '특', 85.50, 'kg', '2kg 박스', '당도 12 Brix 이상 물량을 우선 선별함',
    'FARM_OWNER', 'NOT_REQUIRED', '40000000-0000-4000-8000-000000000001',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    harvest_date = VALUES(harvest_date), quantity = VALUES(quantity), memo = VALUES(memo), deleted_at = NULL;

INSERT INTO harvest_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    harvest_date, grade, quantity, unit, package_unit, memo,
    created_role, care_assignment_id, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @farm1_zone2_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(2, DAY(CURDATE()) - 1) DAY),
    '상', 62.00, 'kg', '2kg 박스', '현장 수기 장부 사진을 확인해 매니저가 대리 입력함',
    'FARM_CARE_MANAGER', @farm1_care_assignment_id, 'PENDING',
    '40000000-0000-4000-8000-000000000002', @care_manager_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), harvest_date = VALUES(harvest_date), quantity = VALUES(quantity),
    memo = VALUES(memo), created_role = VALUES(created_role), care_assignment_id = VALUES(care_assignment_id),
    farmer_confirm_status = 'PENDING', farmer_confirmed_by = NULL,
    farmer_confirmed_at = NULL, deleted_at = NULL;
SET @manager_harvest_id = LAST_INSERT_ID();

INSERT INTO harvest_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    harvest_date, grade, quantity, unit, package_unit, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm2_id, @farm2_zone1_id, @strawberry_crop_id, @geumsil_variety_id, @farm2_season_id,
    DATE_ADD(@previous_month_start, INTERVAL 12 DAY),
    '특', 74.20, 'kg', '2kg 박스', '지난달 리포트 비교용 수확 기록',
    'FARM_OWNER', 'NOT_REQUIRED', '40000000-0000-4000-8000-000000000003',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE harvest_date = VALUES(harvest_date), quantity = VALUES(quantity), deleted_at = NULL;

INSERT INTO harvest_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    harvest_date, grade, quantity, unit, package_unit, memo,
    created_role, farmer_confirm_status, client_request_id, created_by, created_at
)
VALUES (
    @coop_org_id, @farm3_id, @farm3_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm3_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(6, DAY(CURDATE()) - 1) DAY),
    '상', 40.00, 'kg', '2kg 박스', '작목반 공동 출하용 수확 물량',
    'FARM_OWNER', 'NOT_REQUIRED', '40000000-0000-4000-8000-000000000004',
    @farmer2_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE harvest_date = VALUES(harvest_date), quantity = VALUES(quantity), deleted_at = NULL;

INSERT INTO sales_log (
    organization_id, farm_id, customer_id, sales_date, item_name,
    quantity, unit, unit_price, gross_amount, fee_amount, net_amount,
    settlement_status, memo, created_role, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @customer_market_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(3, DAY(CURDATE()) - 1) DAY),
    '딸기 설향 특품', 80.00, 'kg', 18000, 1440000, 72000, 1368000,
    'DONE', '가락시장 경매 출하, 수수료 5% 반영',
    'FARM_OWNER', 'NOT_REQUIRED', '50000000-0000-4000-8000-000000000001',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    sales_date = VALUES(sales_date), gross_amount = VALUES(gross_amount), fee_amount = VALUES(fee_amount),
    net_amount = VALUES(net_amount), settlement_status = VALUES(settlement_status), memo = VALUES(memo), deleted_at = NULL;

INSERT INTO sales_log (
    organization_id, farm_id, customer_id, sales_date, item_name,
    quantity, unit, unit_price, gross_amount, fee_amount, net_amount,
    settlement_status, memo, created_role, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm1_id, @customer_local_id, CURDATE(),
    '딸기 설향 상급', 35.00, 'kg', 15000, 525000, 0, 525000,
    'DONE', '로컬푸드 직매장 납품',
    'FARM_OWNER', 'NOT_REQUIRED', '50000000-0000-4000-8000-000000000002',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    sales_date = VALUES(sales_date), gross_amount = VALUES(gross_amount), net_amount = VALUES(net_amount),
    settlement_status = VALUES(settlement_status), memo = VALUES(memo), deleted_at = NULL;

INSERT INTO sales_log (
    organization_id, farm_id, customer_id, sales_date, item_name,
    quantity, unit, unit_price, gross_amount, fee_amount, net_amount,
    settlement_status, memo, created_role, farmer_confirm_status,
    client_request_id, created_by, created_at
)
VALUES (
    @owner_org_id, @farm2_id, @customer_online_id,
    DATE_ADD(@previous_month_start, INTERVAL 15 DAY),
    '딸기 금실 선물세트', 60.00, 'kg', 20000, 1200000, 36000, 1164000,
    'DONE', '지난달 리포트 비교용 온라인 판매 기록',
    'FARM_OWNER', 'NOT_REQUIRED', '50000000-0000-4000-8000-000000000003',
    @owner_user_id, NOW(6)
)
ON DUPLICATE KEY UPDATE
    sales_date = VALUES(sales_date), gross_amount = VALUES(gross_amount), fee_amount = VALUES(fee_amount),
    net_amount = VALUES(net_amount), settlement_status = VALUES(settlement_status), deleted_at = NULL;

-- 비료/양액 기록은 별도 멱등키가 없어 월별 고정 메모로 중복을 방지한다.
INSERT INTO fertilizer_log (
    organization_id, farm_id, zone_id, crop_id, variety_id, season_id,
    apply_date, material_name, amount_value, amount_unit, memo, created_by, created_at
)
SELECT
    @owner_org_id, @farm1_id, @farm1_zone1_id, @strawberry_crop_id, @seolhyang_variety_id, @farm1_season_id,
    DATE_SUB(CURDATE(), INTERVAL LEAST(8, DAY(CURDATE()) - 1) DAY),
    '딸기 전용 양액 A', 12.50, 'L',
    CONCAT('[DEMO-', DATE_FORMAT(CURDATE(), '%Y-%m'), '] EC 1.2, pH 5.8 기준 관주'),
    @owner_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM fertilizer_log
    WHERE farm_id = @farm1_id
      AND memo = CONCAT('[DEMO-', DATE_FORMAT(CURDATE(), '%Y-%m'), '] EC 1.2, pH 5.8 기준 관주')
      AND deleted_at IS NULL
);

-- =========================================================
-- 8. 데이터 품질 이슈와 매니저 후속 조치
-- =========================================================
INSERT INTO data_quality_issue (
    organization_id, farm_id, zone_id, issue_date, issue_type, issue_status,
    severity, title, description, detected_by, assigned_manager_user_id,
    related_ref_type, related_ref_id, farmer_confirm_status, created_by, created_at
)
SELECT
    @owner_org_id, @farm1_id, @farm1_zone2_id, CURDATE(), 'VALUE_CHECK', 'IN_PROGRESS',
    'NORMAL', '수확량 원장 대조 필요',
    '매니저가 입력한 2동 하우스 수확량을 농장 수기 원장과 최종 대조해야 합니다.',
    'FARM_CARE_MANAGER', @care_manager_user_id,
    'HARVEST', @manager_harvest_id, 'PENDING', @care_manager_user_id, NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM data_quality_issue
    WHERE farm_id = @farm1_id
      AND title = '수확량 원장 대조 필요'
      AND issue_status IN ('OPEN', 'IN_PROGRESS')
);
SELECT id INTO @quality_issue_id
FROM data_quality_issue
WHERE farm_id = @farm1_id AND title = '수확량 원장 대조 필요'
ORDER BY id DESC LIMIT 1;

INSERT INTO data_followup_log (
    organization_id, farm_id, issue_id, manager_user_id, farmer_user_id,
    contact_method, followup_status, content_summary, next_action_at,
    created_at, client_request_id
)
VALUES (
    @owner_org_id, @farm1_id, @quality_issue_id, @care_manager_user_id, @owner_user_id,
    'PHONE', 'CONTACTED',
    '농장주와 통화해 수확량 원본 장부 사진을 재확인하기로 했습니다.',
    DATE_ADD(NOW(6), INTERVAL 1 DAY), NOW(6),
    '60000000-0000-4000-8000-000000000001'
)
ON DUPLICATE KEY UPDATE
    followup_status = VALUES(followup_status), content_summary = VALUES(content_summary),
    next_action_at = VALUES(next_action_at);

-- =========================================================
-- 9. 사용자 UI 설정
-- =========================================================
INSERT INTO user_preference (
    user_id, view_scale, outdoor_mode_yn, reduce_motion_yn,
    last_selected_farm_id, created_at
)
VALUES
    (@owner_user_id, 'standard', 'N', 'N', @farm1_id, NOW(6)),
    (@worker_user_id, 'large', 'Y', 'N', @farm1_id, NOW(6)),
    (@farm_manager_user_id, 'standard', 'N', 'N', @farm1_id, NOW(6)),
    (@care_manager_user_id, 'standard', 'N', 'N', @farm1_id, NOW(6)),
    (@farmer2_user_id, 'standard', 'N', 'N', @farm3_id, NOW(6))
ON DUPLICATE KEY UPDATE
    view_scale = VALUES(view_scale), outdoor_mode_yn = VALUES(outdoor_mode_yn),
    reduce_motion_yn = VALUES(reduce_motion_yn), last_selected_farm_id = VALUES(last_selected_farm_id);

COMMIT;

-- 적용 결과 빠른 확인용(실행 클라이언트의 결과 창에 표시됨)
SELECT email, display_name, status
FROM users
WHERE email LIKE '%@farmlog.test'
ORDER BY email;

SELECT f.id, f.farm_code, f.name, u.email AS owner_email
FROM farm f
JOIN users u ON u.id = f.owner_user_id
WHERE f.farm_code IN ('F-0001', 'F-0002', 'F-0003')
ORDER BY f.farm_code;
