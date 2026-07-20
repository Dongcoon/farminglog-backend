# Farmlog API 배포 가이드

이번 개발 목표는 로컬 개발 환경(Docker Compose)까지이며, 실 서버 배포는 범위 밖이다. 이 문서는 로컬 실행 방법과, 향후 실 서버에 배포할 때 그대로 따라 할 수 있는 절차 초안을 함께 담는다.

## 1. 로컬 Docker Compose 실행 (현재 범위)

### 1.1 전제 조건

- Docker Desktop (Windows/Mac) 또는 Docker Engine + Docker Compose plugin (Linux)
- 기존 서버 MariaDB 접속 정보(Host/Port/DB명/User/Password) — 사내에서 전달받은 값 사용
- 방화벽/보안그룹에서 이 PC(개발 환경)가 해당 MariaDB 포트(기본 3306)에 접근 가능해야 한다.

### 1.2 .env 설정 항목

`backend/.env.example`을 복사해 `backend/.env`를 만들고 아래 키를 채운다. **`.env` 파일은 절대 커밋하지 않는다.**

| 키 | 설명 |
|---|---|
| `DB_HOST` | MariaDB 호스트 |
| `DB_PORT` | MariaDB 포트 (기본 3306) |
| `DB_NAME` | 사용할 데이터베이스명 |
| `DB_USER` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 (민감 정보, 값 없이 키만 커밋됨) |
| `DB_POOL_SIZE` | HikariCP 커넥션 풀 크기 |
| `JWT_SECRET` | JWT 서명 키 (운영/공유 환경에서는 반드시 임의의 긴 문자열로 교체) |
| `JWT_ACCESS_EXPIRATION_MINUTES` | Access Token 만료(분) |
| `JWT_REFRESH_EXPIRATION_DAYS` | Refresh Token 만료(일) |
| `FILE_UPLOAD_DIR` | 첨부파일 로컬 저장 경로 |
| `FILE_MAX_SIZE_MB` | 업로드 파일 최대 용량(MB) |
| `EXPORT_ROOT_DIR` | Excel/PDF 생성 파일 저장 경로(Docker 기본 볼륨: `/app/exports`) |
| `EXPORT_RETENTION_DAYS` | 완료 파일 보관 일수 |
| `EXPORT_XLSX_MAX_ROWS` / `EXPORT_PDF_MAX_ROWS` | 형식별 최대 출력 행 수 |
| `EXPORT_LEASE_MINUTES` / `EXPORT_MAX_ATTEMPTS` | worker lease와 최대 재시도 횟수 |
| `EXPORT_POLL_DELAY_MS` | export worker polling 간격(ms) |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트엔드 Origin (콤마 구분) |
| `SERVER_PORT` | API 서버 포트 (기본 8080) |
| `SPRING_PROFILES_ACTIVE` | local / dev / pilot / prod |

### 1.3 최초 DB 스키마 적용 (1회)

DB Migration 도구를 사용하지 않으므로, 최초 1회 아래 SQL을 대상 DB(`DB_NAME`)에 직접 실행한다.

```bash
mariadb -h <DB_HOST> -P <DB_PORT> -u <DB_USER> -p <DB_NAME> < backend/database/schema.sql
mariadb -h <DB_HOST> -P <DB_PORT> -u <DB_USER> -p <DB_NAME> < backend/database/seed-data.sql   # 예시 데이터, 선택
```

기존 서버의 다른 서비스와 DB를 공유하는 경우, 실행 전 반드시 테이블명 충돌 여부를 확인한다.

애플리케이션은 schema나 seed를 자동 적용하지 않는다. `seed-data.sql`은 테스트용 예시 데이터가 필요한 경우에만 수동 실행한다.

외부 DB 대신 격리된 로컬 MariaDB를 사용하려면 `.env`의 `DB_HOST=farmlog-mariadb`, `DB_USER=root`와 비어 있지 않은 `DB_PASSWORD`를 설정한 뒤 아래처럼 실행한다. schema는 **새 DB 볼륨의 최초 초기화 시에만** 자동 적용되며 seed는 적용하지 않는다.

```bash
docker compose --profile local-db up -d --build
```

### 1.4 실행/중지

```bash
cd app/backend
docker compose up -d --build   # farmlog-api, farmlog-web, farmlog-nginx 기동
docker compose logs -f farmlog-api
docker compose ps              # api/web/nginx가 healthy인지 확인
docker compose down            # 중지
```

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- Nginx 경유 통합 접근: http://localhost
- 업로드와 출력 파일은 각각 `farmlog-uploads`, `farmlog-exports` named volume에 보존된다.
- `docker compose down`은 named volume을 지우지 않는다. `down -v`는 파일과 로컬 DB까지 삭제하므로 백업·대상을 확인하지 않고 실행하지 않는다.

### 1.5 IntelliJ로 직접 실행(Docker 없이)

`README.md`의 "로컬 실행 — IntelliJ" 절 참고.

## 2. (향후용) Linux + Docker Compose + Nginx 실 서버 배포 절차 초안

> 이번 범위에서는 실제로 서버에 배포하지 않는다. 아래는 향후 그대로 따라 할 수 있도록 남기는 절차 초안이다(`docs/Farmlog_ERP_Developer_Design.md` 11장 기준).

1. Linux 서버(예: Ubuntu 22.04 LTS)에 Docker, Docker Compose plugin 설치
2. 배포 대상 서버에 저장소 clone (`backend`, `web` 각각) 또는 CI에서 빌드한 이미지를 레지스트리에 push 후 pull
3. 서버 전용 `.env` 파일 별도 작성 (`SPRING_PROFILES_ACTIVE=prod`, 운영 DB 접속 정보, 강력한 `JWT_SECRET`)
4. `docker compose up -d --build`로 기동
5. Nginx에서 도메인/HTTPS(Let's Encrypt 등) 설정 — 이번 범위 밖이므로 실제 인증서 발급은 하지 않음
6. 헬스체크: `GET /api/v1/actuator/health`
7. 로그/모니터링 연동(선택)

## 3. 백업/롤백 유의사항

DB Migration 도구를 사용하지 않으므로 스키마 변경은 전부 수동 관리한다.

- 운영 DB에 스키마 변경을 반영하기 전 반드시 `mysqldump`(또는 `mariadb-dump`)로 백업한다.
- 스키마 변경 SQL은 `database/schema.sql`에 직접 반영하고, 적용한 SQL 내용과 결과를 `database/db-change-log.md`에 기록한다(형식은 해당 파일 상단 참고).
- 배포/릴리즈 시점마다 `database/snapshots/schema_snapshot_YYYYMMDD.sql`로 현재 스키마 스냅샷을 남긴다.
- 롤백이 필요하면 백업본으로 복구 후, `db-change-log.md`에 롤백 사유와 조치를 기록한다.
- 파일 저장소(로컬 볼륨, `uploads/`)도 DB와 별도로 정기 백업 대상에 포함한다.
