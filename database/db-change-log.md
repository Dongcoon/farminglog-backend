# DB 변경 이력

## 2026-07-20 — 전체 스키마 한글 메타데이터 COMMENT

- 대상: `database/schema.sql`의 34개 테이블과 PK/KEY/CONSTRAINT를 제외한 실제 컬럼 489개 전체
- 변경 SQL 요약: 모든 컬럼 정의에 `COMMENT '한글 설명'`, 모든 InnoDB 테이블 옵션에 `COMMENT='한글 설명'`을 추가했다.
- 적용 사유: `SHOW FULL COLUMNS`와 `information_schema`만으로도 농업 도메인 의미를 확인하고 운영·분석 시 컬럼 오해를 줄이기 위함이다.
- 의미 보존: COMMENT를 제거한 DDL의 SHA-256은 변경 직전 스키마와 동일하며 타입, NULL, DEFAULT, 생성식, 키, 인덱스, CHECK 제약은 변경하지 않았다.
- 적용 환경: 소스 반영(실 DB 미적용)
- 백업 여부: 기존 DB 적용 전 대상 환경별 백업 필요

`CREATE TABLE IF NOT EXISTS`는 이미 존재하는 테이블의 COMMENT를 갱신하지 않는다. 기존 DB에는 백업 후
`schema.sql`의 동일한 전체 컬럼 정의를 사용한 `ALTER TABLE ... MODIFY COLUMN ... COMMENT`와
`ALTER TABLE ... COMMENT='...'` 문장을 적용해야 한다. 컬럼 타입을 생략한 `MODIFY`는 허용하지 않으며,
운영 반영 전후 아래 쿼리 결과가 모두 0건인지 확인한다.

```sql
SELECT COUNT(*) AS tables_without_comment
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'users','organization','organization_member','farm','farm_member','farm_care_assignment',
    'farm_zone','farm_structure_event','farm_structure_event_item','farm_zone_assignment','crop',
    'crop_variety','farm_crop','farm_crop_variety','crop_season','work_type','work_log',
    'pest_control_log','harvest_log','customer','sales_log','attachment_file','export_job',
    'photo_upload_batch','photo_upload_file','data_quality_issue','data_followup_log','audit_log',
    'fertilizer_log','material','user_preference','refresh_token','password_reset_token','farm_invitation')
  AND COALESCE(table_comment, '') = '';

SELECT COUNT(*) AS columns_without_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
    'users','organization','organization_member','farm','farm_member','farm_care_assignment',
    'farm_zone','farm_structure_event','farm_structure_event_item','farm_zone_assignment','crop',
    'crop_variety','farm_crop','farm_crop_variety','crop_season','work_type','work_log',
    'pest_control_log','harvest_log','customer','sales_log','attachment_file','export_job',
    'photo_upload_batch','photo_upload_file','data_quality_issue','data_followup_log','audit_log',
    'fertilizer_log','material','user_preference','refresh_token','password_reset_token','farm_invitation')
  AND COALESCE(column_comment, '') = '';
```

## 2026-07-20 — Phase 8 농장 구조 변경·투영·취소

- 대상: `farm`, `farm_zone`, `farm_zone_assignment`, `farm_structure_event`, `farm_structure_event_item`
- 사유: 일반 쓰기와 병합/분리를 parent 잠금으로 직렬화하고, immutable preview·확정·취소 및 CURRENT/EVENT 리포트 투영을 지원한다.
- 적용 환경: 소스 반영(실 DB 미적용)
- 적용 전: 반드시 DB를 백업하고 아래 중복 검증 쿼리 결과가 0건인지 확인한다. 동일 시작일 중복은 임의 삭제하지 않고 운영 이력을 확인한 뒤 수동 정리한다.

```sql
SELECT zone_id, COUNT(*) FROM farm_zone_assignment
WHERE effective_to IS NULL GROUP BY zone_id HAVING COUNT(*) > 1;
SELECT zone_id, effective_from, COUNT(*) FROM farm_zone_assignment
GROUP BY zone_id, effective_from HAVING COUNT(*) > 1;
SELECT id, zone_id, effective_from, effective_to FROM farm_zone_assignment
WHERE effective_to IS NOT NULL AND effective_to <= effective_from;

ALTER TABLE farm ADD COLUMN structure_version BIGINT NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE farm_zone ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER active_yn;
ALTER TABLE farm_zone_assignment
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER active_yn,
    ADD COLUMN current_marker TINYINT
        AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) PERSISTENT,
    ADD UNIQUE KEY uk_zone_assignment_current (zone_id, current_marker),
    ADD UNIQUE KEY uk_zone_assignment_from (zone_id, effective_from),
    ADD CONSTRAINT ck_zone_assignment_period
        CHECK (effective_to IS NULL OR effective_to > effective_from);

ALTER TABLE farm_structure_event
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN client_request_id CHAR(36) NULL AFTER version,
    ADD COLUMN confirm_request_id CHAR(36) NULL AFTER client_request_id,
    ADD COLUMN request_hash CHAR(64) NULL AFTER confirm_request_id,
    ADD COLUMN confirm_request_hash CHAR(64) NULL AFTER request_hash,
    ADD COLUMN preview_hash CHAR(64) NULL AFTER confirm_request_hash,
    ADD COLUMN confirmed_state_hash CHAR(64) NULL AFTER preview_hash,
    ADD COLUMN preview_expires_at DATETIME(6) NULL AFTER confirmed_state_hash,
    ADD COLUMN request_snapshot_json JSON NULL AFTER preview_expires_at,
    ADD COLUMN impact_json JSON NULL AFTER request_snapshot_json,
    ADD COLUMN conflicts_json JSON NULL AFTER impact_json,
    ADD COLUMN warnings_json JSON NULL AFTER conflicts_json,
    ADD COLUMN report_policy VARCHAR(30) NULL AFTER warnings_json,
    ADD COLUMN period_start DATE NULL AFTER report_policy,
    ADD COLUMN period_end_exclusive DATE NULL AFTER period_start,
    ADD COLUMN reverses_event_id BIGINT NULL AFTER period_end_exclusive,
    ADD COLUMN reversed_by_event_id BIGINT NULL AFTER reverses_event_id;

-- 레거시 event도 event별 UUID와 고정 key-order JSON을 가진 완전한 snapshot으로 보정한다.
-- 기존 DRAFT는 created_at+24시간을 만료시각으로 정하며, API에서 지난 DRAFT를 EXPIRED로 파생한다.
UPDATE farm_structure_event e
SET e.client_request_id = COALESCE(e.client_request_id, UUID()),
    e.preview_expires_at = CASE WHEN e.status='DRAFT'
        THEN COALESCE(e.preview_expires_at, DATE_ADD(e.created_at, INTERVAL 24 HOUR))
        ELSE e.preview_expires_at END,
    e.report_policy = COALESCE(e.report_policy,
        CASE WHEN EXISTS(SELECT 1 FROM farm_structure_event_item i
                         WHERE i.event_id=e.id AND i.include_history_yn='Y')
             THEN 'ALL_HISTORY' ELSE 'RECORDED_ONLY' END),
    e.request_snapshot_json = COALESCE(e.request_snapshot_json, JSON_OBJECT(
        'legacyEventId',e.id,'eventType',e.event_type,'eventName',e.event_name,
        'effectiveDate',DATE_FORMAT(e.effective_date,'%Y-%m-%d'),
        'reason',e.reason,'migrated',TRUE)),
    e.impact_json = COALESCE(e.impact_json, JSON_OBJECT(
        'sourceFarmCount',0,'targetMode','EXISTING','zoneCount',0,
        'recordCounts',JSON_OBJECT('work',0,'pestControl',0,'harvest',0,'sales',0,'fertilizer',0),
        'masterCounts',JSON_OBJECT('cropVariety',0,'season',0,'workType',0,'customer',0,'material',0),
        'memberCopyCount',0,'memberExcludedCount',0,'zoneLessSalesCount',0)),
    e.conflicts_json = COALESCE(e.conflicts_json, JSON_ARRAY()),
    e.warnings_json = COALESCE(e.warnings_json, JSON_ARRAY());

UPDATE farm_structure_event
SET request_hash = SHA2(CAST(request_snapshot_json AS CHAR CHARACTER SET utf8mb4),256)
WHERE request_hash IS NULL;

-- NOT NULL 전 검증: 결과는 반드시 0건이어야 한다.
SELECT COUNT(*) AS invalid_structure_event_backfill
FROM farm_structure_event
WHERE client_request_id IS NULL OR request_hash IS NULL
   OR request_snapshot_json IS NULL OR impact_json IS NULL
   OR conflicts_json IS NULL OR warnings_json IS NULL;

ALTER TABLE farm_structure_event
    MODIFY COLUMN client_request_id CHAR(36) NOT NULL,
    MODIFY COLUMN request_hash CHAR(64) NOT NULL,
    MODIFY COLUMN request_snapshot_json JSON NOT NULL,
    MODIFY COLUMN impact_json JSON NOT NULL,
    MODIFY COLUMN conflicts_json JSON NOT NULL,
    MODIFY COLUMN warnings_json JSON NOT NULL,
    ADD UNIQUE KEY uk_structure_preview_request (created_by, client_request_id),
    ADD UNIQUE KEY uk_structure_confirm_request (confirmed_by, confirm_request_id),
    ADD KEY ix_structure_replay (organization_id, confirmed_at, id);

ALTER TABLE farm_structure_event_item
    ADD COLUMN period_end_exclusive DATE NULL AFTER period_end,
    ADD COLUMN resource_type VARCHAR(40) NULL AFTER include_history_yn,
    ADD COLUMN source_resource_id BIGINT NULL AFTER resource_type,
    ADD COLUMN target_resource_id BIGINT NULL AFTER source_resource_id,
    ADD COLUMN action VARCHAR(40) NULL AFTER target_resource_id,
    ADD COLUMN before_json JSON NULL AFTER action,
    ADD COLUMN after_json JSON NULL AFTER before_json,
    ADD KEY ix_structure_item_replay (event_id, item_type, source_farm_id, zone_id);

UPDATE farm_structure_event_item
SET period_end_exclusive = DATE_ADD(period_end, INTERVAL 1 DAY)
WHERE period_end IS NOT NULL AND period_end_exclusive IS NULL;

-- exclusive 이관 검증 결과가 0건인 것을 확인한 같은 운영 절차에서 legacy를 제거한다.
SELECT COUNT(*) AS invalid_period_backfill FROM farm_structure_event_item
WHERE period_end IS NOT NULL
  AND (period_end_exclusive IS NULL OR period_end_exclusive <> DATE_ADD(period_end, INTERVAL 1 DAY));
ALTER TABLE farm_structure_event_item
    DROP COLUMN period_end,
    DROP COLUMN include_history_yn,
    DROP COLUMN memo;
```

## 2026-07-20 — Phase 7 운영자 조회 감사 정렬

- 대상: `audit_log`
- 사유: 운영자 감사 조회의 기본 정렬인 `created_at, id`와 안정적인 페이지 tie-breaker를 지원한다.
- 적용 환경: 소스 반영(실 DB 미적용)
- 적용 전: 대상 DB를 백업하고 `SHOW INDEX FROM audit_log`에서 같은 인덱스가 없는지 확인한다.

```sql
ALTER TABLE audit_log ADD KEY ix_audit_created_id (created_at, id);
```

## 2026-07-20 — Phase 6 구성원·돌봄 배정·데이터 품질·사진 첨부

- 대상: `farm_member`, `farm_invitation`, `farm_care_assignment`, `data_quality_issue`, `data_followup_log`, `attachment_file`, `photo_upload_batch`, `photo_upload_file`
- 사유: 낙관적 잠금, 초대/후속조치 멱등성, 기간 기반 돌봄 권한, 사진 임시 업로드·커밋·정리를 지원한다.
- 적용 환경: 소스 반영(실 DB 미적용)
- 적용 전: 대상 DB 백업 후 아래 `UPDATE` 결과와 UUID 중복 여부를 확인한다.

```sql
ALTER TABLE farm_member ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE farm_care_assignment
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER memo,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER client_request_id,
    ADD COLUMN revoked_at DATETIME(6) NULL AFTER version,
    ADD COLUMN revoked_by BIGINT NULL AFTER revoked_at,
    ADD COLUMN revoked_reason VARCHAR(500) NULL AFTER revoked_by;
UPDATE farm_care_assignment SET client_request_id=UUID() WHERE client_request_id IS NULL;
ALTER TABLE farm_care_assignment
    MODIFY COLUMN client_request_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_care_assignment_request (farm_id, client_request_id),
    ADD KEY ix_care_assignment_active_period (manager_user_id, farm_id, status, starts_at, ends_at);

ALTER TABLE farm_invitation
    ADD COLUMN accepted_user_id BIGINT NULL AFTER expires_at,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER accepted_user_id,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER client_request_id;
UPDATE farm_invitation SET client_request_id=UUID() WHERE client_request_id IS NULL;
ALTER TABLE farm_invitation
    MODIFY COLUMN client_request_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_farm_invitation_request (farm_id, client_request_id);

ALTER TABLE data_quality_issue ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER closed_at;
ALTER TABLE data_followup_log ADD COLUMN client_request_id VARCHAR(36) NULL AFTER created_at;
UPDATE data_followup_log SET client_request_id=UUID() WHERE client_request_id IS NULL;
ALTER TABLE data_followup_log
    MODIFY COLUMN client_request_id VARCHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_followup_request (issue_id, client_request_id);

ALTER TABLE attachment_file
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER deleted_at,
    ADD COLUMN client_file_id VARCHAR(36) NULL AFTER version,
    ADD UNIQUE KEY uk_attach_client_file (farm_id, ref_type, ref_id, client_file_id);

CREATE TABLE photo_upload_batch (
    id VARCHAR(36) PRIMARY KEY, organization_id BIGINT NOT NULL, farm_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL, care_assignment_id BIGINT NULL, client_request_id VARCHAR(36) NOT NULL,
    issue_date DATE NOT NULL, zone_id BIGINT NULL, estimated_record_type VARCHAR(30) NOT NULL,
    memo TEXT NULL, file_count INT NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'UPLOADING',
    commit_request_id VARCHAR(36) NULL, issue_id BIGINT NULL, expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL, committed_at DATETIME(6) NULL,
    UNIQUE KEY uk_photo_batch_request (farm_id, owner_user_id, client_request_id),
    KEY ix_photo_batch_expiry (status, expires_at), KEY ix_photo_batch_care_assignment (care_assignment_id),
    CONSTRAINT fk_photo_batch_care_assignment FOREIGN KEY (care_assignment_id) REFERENCES farm_care_assignment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE photo_upload_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id VARCHAR(36) NOT NULL, client_file_id VARCHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL, stored_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL, staging_path VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED', uploaded_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_photo_batch_file (batch_id, client_file_id), KEY ix_photo_file_batch (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 2026-07-20 — Phase 5 월간 리포트·비동기 내보내기

- 대상: `export_job`, `attachment_file`, `audit_log`
- 사유: 범위/검증 필터가 포함된 멱등 요청, 비동기 lease·재시도, 7일 보관 및 감사 추적을 지원한다.
- 적용 전: 반드시 `mariadb-dump`로 백업하고 `SHOW COLUMNS FROM export_job`, `SHOW INDEX FROM export_job` 결과를 보관한다.
- 적용 후: `scopes_json` JSON 유효성, `(farm_id, client_request_id)` 중복, 만료/lease 인덱스를 확인한다.

기존 `target_type`은 JSON 배열 한 항목으로 이전한다. 기간이나 요청 UUID가 없던 레거시 행은 요청일을 기간으로, 새 UUID를 멱등 키로 보완한 뒤 NOT NULL 제약을 적용한다.

```sql
ALTER TABLE export_job
    ADD COLUMN scopes_json JSON NULL AFTER export_type,
    ADD COLUMN confirmation_filter VARCHAR(20) NOT NULL DEFAULT 'ALL' AFTER scopes_json,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER confirmation_filter,
    ADD COLUMN error_code VARCHAR(50) NULL AFTER file_id,
    ADD COLUMN started_at DATETIME(6) NULL AFTER requested_at,
    ADD COLUMN expires_at DATETIME(6) NULL AFTER completed_at,
    ADD COLUMN lease_until DATETIME(6) NULL AFTER expires_at,
    ADD COLUMN claim_token VARCHAR(36) NULL AFTER lease_until,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER claim_token,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER attempt_count;

UPDATE export_job
SET scopes_json = JSON_ARRAY(target_type),
    client_request_id = UUID(),
    period_start = COALESCE(period_start, DATE(requested_at)),
    period_end = COALESCE(period_end, DATE(requested_at)),
    updated_at = COALESCE(completed_at, requested_at)
WHERE scopes_json IS NULL;

ALTER TABLE export_job
    MODIFY COLUMN scopes_json JSON NOT NULL,
    MODIFY COLUMN client_request_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN period_start DATE NOT NULL,
    MODIFY COLUMN period_end DATE NOT NULL,
    DROP COLUMN target_type,
    ADD UNIQUE KEY uk_export_job_request (farm_id, client_request_id),
    ADD KEY ix_export_status_requested (status, requested_at),
    ADD KEY ix_export_status_lease (status, lease_until),
    ADD KEY ix_export_status_expires (status, expires_at);
```

---

DB Migration 도구(Flyway/Liquibase 등)는 사용하지 않는다. 스키마 변경은 `database/schema.sql`을 직접 수정하고, 아래 형식으로 변경 이력을 남긴다. 운영 DB에 반영하기 전에는 반드시 `mysqldump`(또는 `mariadb-dump`)로 백업하고, 적용한 SQL과 결과를 이 문서에 기록한다.

형식:

```
## YYYY-MM-DD — 변경 요약
- 대상 테이블/컬럼:
- 변경 SQL 요약:
- 적용 사유:
- 적용 환경: local / dev / pilot / prod
- 백업 여부:
```

---

## 2026-07-20 — Phase 4 기록 provenance·멱등·동시성 계약

- 대상 테이블/컬럼: `work_log`, `pest_control_log`, `harvest_log`, `sales_log`
- 변경 SQL 요약: 생성 역할/매니저 배정/농가 확인 스냅샷, version, client_request_id 및 농장별 고유 키·목록 인덱스를 추가했다. 판매 품목명을 nullable로 바꾸고 순금액 수동 수정 여부를 추가했다.
- 적용 사유: W-03/M-05~M-11 기록 관리, 모바일 재시도 멱등성, 매니저 입력 역사 배지, 낙관적 잠금 지원
- 적용 환경: 소스 반영(실 DB 미적용)
- 백업 여부: 기존 DB 적용 전 백업 필요
- 비고: 판매 정산 상태를 `PENDING|DONE`으로 통일

기존 DB는 `CREATE TABLE IF NOT EXISTS`만 다시 실행해도 컬럼과 인덱스가 추가되지 않는다. 반드시 먼저
`mariadb-dump`/`mysqldump`로 백업하고, `SHOW COLUMNS`와 `SHOW INDEX`로 아래 대상이 아직 없는지 확인한 뒤
순서대로 적용한다. 아래 DDL은 이미 컬럼/인덱스가 있는 DB에 중복 실행하지 않는다(MariaDB DDL은 암묵적으로
커밋될 수 있으므로 각 문장 성공 여부도 기록한다).

```sql
ALTER TABLE work_log
    ADD COLUMN created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' AFTER memo,
    ADD COLUMN care_assignment_id BIGINT NULL AFTER created_role,
    ADD COLUMN farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER care_assignment_id,
    ADD COLUMN farmer_confirmed_by BIGINT NULL AFTER farmer_confirm_status,
    ADD COLUMN farmer_confirmed_at DATETIME(6) NULL AFTER farmer_confirmed_by,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER farmer_confirmed_at,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER version,
    ADD KEY ix_work_log_list (farm_id, deleted_at, work_date, id),
    ADD UNIQUE KEY uk_work_log_request (farm_id, client_request_id);

ALTER TABLE pest_control_log
    ADD COLUMN created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' AFTER memo,
    ADD COLUMN care_assignment_id BIGINT NULL AFTER created_role,
    ADD COLUMN farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER care_assignment_id,
    ADD COLUMN farmer_confirmed_by BIGINT NULL AFTER farmer_confirm_status,
    ADD COLUMN farmer_confirmed_at DATETIME(6) NULL AFTER farmer_confirmed_by,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER farmer_confirmed_at,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER version,
    ADD KEY ix_pest_log_list (farm_id, deleted_at, apply_date, id),
    ADD UNIQUE KEY uk_pest_log_request (farm_id, client_request_id);

ALTER TABLE harvest_log
    ADD COLUMN created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' AFTER memo,
    ADD COLUMN care_assignment_id BIGINT NULL AFTER created_role,
    ADD COLUMN farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER care_assignment_id,
    ADD COLUMN farmer_confirmed_by BIGINT NULL AFTER farmer_confirm_status,
    ADD COLUMN farmer_confirmed_at DATETIME(6) NULL AFTER farmer_confirmed_by,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER farmer_confirmed_at,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER version,
    ADD KEY ix_harvest_log_list (farm_id, deleted_at, harvest_date, id),
    ADD UNIQUE KEY uk_harvest_log_request (farm_id, client_request_id);

ALTER TABLE sales_log
    MODIFY COLUMN item_name VARCHAR(150) NULL,
    ADD COLUMN net_amount_overridden_yn CHAR(1) NOT NULL DEFAULT 'N' AFTER net_amount,
    ADD COLUMN created_role VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN' AFTER memo,
    ADD COLUMN care_assignment_id BIGINT NULL AFTER created_role,
    ADD COLUMN farmer_confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER care_assignment_id,
    ADD COLUMN farmer_confirmed_by BIGINT NULL AFTER farmer_confirm_status,
    ADD COLUMN farmer_confirmed_at DATETIME(6) NULL AFTER farmer_confirmed_by,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER farmer_confirmed_at,
    ADD COLUMN client_request_id VARCHAR(36) NULL AFTER version,
    ADD KEY ix_sales_log_list (farm_id, deleted_at, sales_date, id),
    ADD UNIQUE KEY uk_sales_log_request (farm_id, client_request_id);

UPDATE sales_log
SET settlement_status = 'DONE'
WHERE settlement_status = 'SETTLED';
```

## 2026-07-20 — Phase 3 농장별 기준정보 격리

- 대상 테이블/컬럼: `farm_crop`, `farm_crop_variety` 신규; `work_type` 감사 컬럼; 농장 로컬 기준정보 고유 키
- 변경 SQL 요약: 전역 작물/품종 카탈로그와 농장별 활성/표시 상태를 분리하는 연결 테이블을 추가하고, `work_type`에 생성·수정자/시각을 추가했다. `work_type/customer/material`에는 `(farm_id, name)` 고유 키를 추가했다.
- 적용 사유: 전역 카탈로그 직접 수정 방지, 농장 간 상태 격리, 딸기 템플릿의 동시 요청 포함 멱등 적용 보장
- 적용 환경: 소스 반영(실 DB 미적용)
- 백업 여부: 적용 전 대상 환경별 백업 필요
- 비고: 예시 시드에 농장별 연결을 추가하고 `서울common공판장` 오타를 `서울공판장`으로 바로잡음

## 2026-07-16 — Phase 0 초기 스키마 생성

- 대상 테이블: `docs/Farmlog_ERP_DB_DDL_Draft.sql` 원본 23개 테이블 전체
- 추가 테이블(원본 DDL 초안에 없어 이번에 신규 추가):
  - `fertilizer_log` — 비료/양액 기록. `Farmlog_ERP_Developer_Design.md` 6장 테이블 목록에는 언급되나 DDL 초안 누락되어 있어 동일 패턴(work_log류)으로 추가
  - `material` — 자재 기준정보. W-04 기준정보 관리 화면(자재 탭) 구현에 필요하나 DDL 초안에 없어 work_type과 동일 패턴으로 추가
  - `user_preference` — `GET/PUT /users/me/preferences`(보기 크기/야외 모드 저장) 구현에 필요
  - `refresh_token` — Refresh Token 서버 저장/폐기 구조(개발 가이드 4.4, developer design 9장) 구현에 필요
  - `password_reset_token` — 비밀번호 재설정(M-03) 토큰 관리에 필요
  - `farm_invitation` — 사용자 초대(W-07, 신규 제안 API) 구현에 필요
- 적용 사유: Phase 0 부트스트랩, 초기 스키마 확정
- 적용 환경: local (기존 서버 MariaDB에 연결, DB명은 `.env`의 `DB_NAME` 기준)
- 백업 여부: 최초 적용이므로 해당 없음. 최초 적용 전 대상 DB에 동일 이름의 테이블이 없는지 반드시 확인할 것(`CREATE TABLE IF NOT EXISTS` 사용했으나 기존 서버에 동일 DB를 다른 목적으로 쓰고 있다면 충돌 가능)
- 비고: `plan/04_Open_Questions_Log.md`에 위 6개 테이블 추가 사실을 별도 기록함
