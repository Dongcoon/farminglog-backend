-- Farmlog ERP 예시(더미) 시드 데이터
-- 딸기 시설재배 농가 예시 값 사용 (docs 기준)
-- 모든 데모 계정 비밀번호: farmlog1234!  (BCrypt 해시, 개발/로컬 전용. 운영에는 절대 사용 금지)
-- 이 파일은 database/schema.sql 적용 후 1회 실행한다. 이미 데이터가 있는 DB에는 재실행하지 않는다.

-- =========================================================
-- 0. 조직 (SYSTEM 조직 1개는 SYSTEM_ADMIN 소속용으로 사용)
-- =========================================================
INSERT INTO organization (id, name, org_type, status, created_by, created_at) VALUES
    (1, 'Farmlog 운영팀', 'SYSTEM', 'ACTIVE', 1, NOW(6)),
    (2, '이몽룡 농장', 'PERSONAL', 'ACTIVE', 2, NOW(6)),
    (3, '한들딸기 작목반', 'COOP', 'ACTIVE', 4, NOW(6));

-- =========================================================
-- 1. 사용자 (데모 계정, 비밀번호 공통: farmlog1234!)
-- =========================================================
INSERT INTO users (id, email, password_hash, display_name, status, created_at) VALUES
    (1, 'sysadmin@farmlog.test',  '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '시스템 운영자',     'ACTIVE', NOW(6)),
    (2, 'owner@farmlog.test',     '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '오세훈(농장주)',    'ACTIVE', NOW(6)),
    (3, 'worker@farmlog.test',    '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '김일꾼(작업자)',    'ACTIVE', NOW(6)),
    (4, 'orgadmin@farmlog.test',  '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '최조합(작목반관리자)', 'ACTIVE', NOW(6)),
    (5, 'manager@farmlog.test',   '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '박매니저(농장관리매니저)', 'ACTIVE', NOW(6)),
    (6, 'farmer2@farmlog.test',   '$2b$10$TOXy8HUmtR7mGq45owTG6Om7fzfCAUJIr.DFn2b90RRCQOiJYSu0O', '정농부(농장주2)',   'ACTIVE', NOW(6));

INSERT INTO organization_member (organization_id, user_id, role, status, created_at) VALUES
    (1, 1, 'SYSTEM_ADMIN', 'ACTIVE', NOW(6)),
    (2, 2, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (3, 4, 'ORG_ADMIN', 'ACTIVE', NOW(6)),
    (3, 6, 'FARM_OWNER', 'ACTIVE', NOW(6));

-- =========================================================
-- 2. 농장 (오세훈: 2개 농장 — 합산 리포트 데모용 / 정농부: 작목반 소속 1개 농장)
-- =========================================================
INSERT INTO farm (id, organization_id, farm_code, name, owner_user_id, address, lifecycle_status, status, created_by, created_at) VALUES
    (1, 2, 'F-0001', '한들 제1농장', 2, '충남 논산시 강경읍 1길 10', 'ACTIVE', 'ACTIVE', 2, NOW(6)),
    (2, 2, 'F-0002', '한들 제2농장', 2, '충남 논산시 강경읍 2길 20', 'ACTIVE', 'ACTIVE', 2, NOW(6)),
    (3, 3, 'F-0003', '푸른들 농장', 6, '충남 논산시 연무읍 3길 30', 'ACTIVE', 'ACTIVE', 6, NOW(6));

INSERT INTO farm_member (farm_id, user_id, role, status, created_at) VALUES
    (1, 2, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (1, 3, 'WORKER', 'ACTIVE', NOW(6)),
    (2, 2, 'FARM_OWNER', 'ACTIVE', NOW(6)),
    (3, 6, 'FARM_OWNER', 'ACTIVE', NOW(6));

INSERT INTO farm_care_assignment (organization_id, farm_id, manager_user_id, assigned_by, assignment_type, permission_scope, status, starts_at, created_at) VALUES
    (2, 1, 5, 2, 'DATA_CARE', 'REVIEW_AND_INPUT', 'ACTIVE', NOW(6), NOW(6)),
    (2, 2, 5, 2, 'DATA_CARE', 'REVIEW_AND_INPUT', 'ACTIVE', NOW(6), NOW(6));

-- =========================================================
-- 3. 하우스/구역
-- =========================================================
INSERT INTO farm_zone (id, organization_id, farm_id, name, zone_type, area_value, area_unit, display_order, active_yn, created_by, created_at) VALUES
    (1, 2, 1, '1동 하우스', 'GREENHOUSE', 600.00, 'm2', 1, 'Y', 2, NOW(6)),
    (2, 2, 1, '2동 하우스', 'GREENHOUSE', 500.00, 'm2', 2, 'Y', 2, NOW(6)),
    (3, 2, 2, '동편 하우스', 'GREENHOUSE', 800.00, 'm2', 1, 'Y', 2, NOW(6)),
    (4, 3, 3, '본동', 'GREENHOUSE', 700.00, 'm2', 1, 'Y', 6, NOW(6));

INSERT INTO farm_zone_assignment (organization_id, zone_id, farm_id, effective_from, active_yn, created_by, created_at) VALUES
    (2, 1, 1, '2025-01-01', 'Y', 2, NOW(6)),
    (2, 2, 1, '2025-01-01', 'Y', 2, NOW(6)),
    (2, 3, 2, '2025-01-01', 'Y', 2, NOW(6)),
    (3, 4, 3, '2025-01-01', 'Y', 6, NOW(6));

-- =========================================================
-- 4. 작물/품종/작기
-- =========================================================
INSERT INTO crop (id, name, active_yn) VALUES (1, '딸기', 'Y');

INSERT INTO crop_variety (id, crop_id, name, active_yn) VALUES
    (1, 1, '설향', 'Y'),
    (2, 1, '금실', 'Y'),
    (3, 1, '킹스베리', 'Y');

INSERT INTO farm_crop (farm_id, crop_id, display_order, active_yn, created_by, created_at) VALUES
    (1, 1, 1, 'Y', 2, NOW(6)),
    (2, 1, 1, 'Y', 2, NOW(6)),
    (3, 1, 1, 'Y', 6, NOW(6));

INSERT INTO farm_crop_variety (farm_id, crop_id, variety_id, display_order, active_yn, created_by, created_at) VALUES
    (1, 1, 1, 1, 'Y', 2, NOW(6)),
    (1, 1, 2, 2, 'Y', 2, NOW(6)),
    (1, 1, 3, 3, 'Y', 2, NOW(6)),
    (2, 1, 2, 1, 'Y', 2, NOW(6)),
    (3, 1, 1, 1, 'Y', 6, NOW(6));

INSERT INTO crop_season (id, organization_id, farm_id, crop_id, variety_id, name, start_date, status, created_by, created_at) VALUES
    (1, 2, 1, 1, 1, '2025-2026 촉성재배', '2025-09-01', 'ACTIVE', 2, NOW(6)),
    (2, 2, 2, 1, 2, '2025-2026 촉성재배', '2025-09-01', 'ACTIVE', 2, NOW(6)),
    (3, 3, 3, 1, 1, '2025-2026 촉성재배', '2025-09-01', 'ACTIVE', 6, NOW(6));

-- =========================================================
-- 5. 작업유형 (조직 공통 기본값)
-- =========================================================
INSERT INTO work_type (id, organization_id, farm_id, name, display_order, active_yn) VALUES
    (1, 2, NULL, '정식', 1, 'Y'),
    (2, 2, NULL, '유인', 2, 'Y'),
    (3, 2, NULL, '적화·적과', 3, 'Y'),
    (4, 2, NULL, '관수', 4, 'Y'),
    (5, 2, NULL, '포장', 5, 'Y'),
    (6, 3, NULL, '정식', 1, 'Y'),
    (7, 3, NULL, '관수', 2, 'Y');

-- =========================================================
-- 6. 거래처 / 자재
-- =========================================================
INSERT INTO customer (id, organization_id, farm_id, name, customer_type, phone, active_yn, created_by, created_at) VALUES
    (1, 2, 1, '서울공판장', 'WHOLESALE_MARKET', '02-1234-5678', 'Y', 2, NOW(6)),
    (2, 2, 1, '동네마트 강경점', 'RETAIL', '041-111-2222', 'Y', 2, NOW(6)),
    (3, 2, 2, '온라인 직거래', 'DIRECT', NULL, 'Y', 2, NOW(6));

INSERT INTO material (id, organization_id, farm_id, name, material_type, unit, active_yn, created_by, created_at) VALUES
    (1, 2, 1, '질산칼슘', 'FERTILIZER', 'kg', 'Y', 2, NOW(6)),
    (2, 2, 1, '탄저병 방제제', 'PESTICIDE', 'L', 'Y', 2, NOW(6)),
    (3, 2, 1, '포장박스 2kg', 'PACKAGING', 'ea', 'Y', 2, NOW(6));

-- =========================================================
-- 7. 예시 기록 (이번달/지난달 리포트 데모용)
-- =========================================================
INSERT INTO work_log (organization_id, farm_id, zone_id, crop_id, variety_id, season_id, work_date, work_type_id, worker_count, work_hours, created_by, created_at) VALUES
    (2, 1, 1, 1, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 2, 2, 4, 2, NOW(6)),
    (2, 1, 2, 1, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-10'), 4, 1, 2, 3, NOW(6)),
    (2, 2, 3, 1, 2, 2, DATE_FORMAT(CURDATE(), '%Y-%m-08'), 3, 2, 5, 2, NOW(6));

INSERT INTO pest_control_log (organization_id, farm_id, zone_id, crop_id, variety_id, season_id, apply_date, chemical_name, target_pest, dilution_ratio, amount_value, amount_unit, preharvest_interval_days, created_by, created_at) VALUES
    (2, 1, 1, 1, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-03'), '탄저병 방제제', '탄저병', '1:1000', 2.5, 'L', 3, 2, NOW(6)),
    (2, 2, 3, 1, 2, 2, DATE_FORMAT(CURDATE(), '%Y-%m-06'), '흰가루병 방제제', '흰가루병', '1:800', 1.8, 'L', 5, 2, NOW(6));

INSERT INTO harvest_log (organization_id, farm_id, zone_id, crop_id, variety_id, season_id, harvest_date, grade, quantity, unit, package_unit, created_by, created_at) VALUES
    (2, 1, 1, 1, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-12'), '특', 85.5, 'kg', '2kg박스', 2, NOW(6)),
    (2, 1, 2, 1, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-14'), '상', 62.0, 'kg', '2kg박스', 3, NOW(6)),
    (2, 2, 3, 1, 2, 2, DATE_FORMAT(CURDATE(), '%Y-%m-13'), '특', 74.2, 'kg', '2kg박스', 2, NOW(6)),
    (3, 3, 4, 1, 1, 3, DATE_FORMAT(CURDATE(), '%Y-%m-11'), '상', 40.0, 'kg', '2kg박스', 6, NOW(6));

INSERT INTO sales_log (organization_id, farm_id, customer_id, sales_date, item_name, quantity, unit, unit_price, gross_amount, fee_amount, net_amount, settlement_status, created_by, created_at) VALUES
    (2, 1, 1, DATE_FORMAT(CURDATE(), '%Y-%m-15'), '딸기(설향) 특', 80.0, 'kg', 18000, 1440000, 72000, 1368000, 'DONE', 2, NOW(6)),
    (2, 1, 2, DATE_FORMAT(CURDATE(), '%Y-%m-16'), '딸기(설향) 상', 40.0, 'kg', 15000, 600000, 0, 600000, 'DONE', 2, NOW(6)),
    (2, 2, 3, DATE_FORMAT(CURDATE(), '%Y-%m-15'), '딸기(금실) 특', 60.0, 'kg', 20000, 1200000, 0, 1200000, 'PENDING', 2, NOW(6));

-- =========================================================
-- 8. 사용자 UI 설정
-- =========================================================
INSERT INTO user_preference (user_id, view_scale, outdoor_mode_yn, last_selected_farm_id, created_at) VALUES
    (2, 'standard', 'N', 1, NOW(6));
