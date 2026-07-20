# Farmlog API (backend)

딸기 시설재배 농가 대상 농장관리 SaaS "팜로그"의 백엔드 API. Spring Boot 3 + Java 17 + MyBatis + MariaDB 기반 모듈형 모놀리스.

근거 문서(저장소 밖, `파밍로그/` 기획 문서 폴더): `plan/02_Development_Guide.md`, `plan/01_Screen_Structure_Diagram.md`, `docs/Farmlog_ERP_DB_DDL_Draft.sql`.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.3.x |
| 보안 | Spring Security + JWT(jjwt) |
| DB 접근 | MyBatis |
| DB | MariaDB (기존 서버 연결, `.env`로 접속 정보 주입) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 엑셀 | Apache POI |
| PDF | OpenHTMLtoPDF |

## 사전 준비물

- JDK 17 (IntelliJ 사용 시 IntelliJ가 자동으로 다운로드/관리 가능)
- 기존 서버 MariaDB 접속 정보 (`.env` 참고)
- (Docker Compose로 실행할 경우) Docker Desktop

## 로컬 실행 — IntelliJ

1. `app/backend` 폴더를 IntelliJ에서 `Open`으로 연다 (Gradle 프로젝트 자동 인식).
2. `File > Project Structure > SDK`에서 JDK 17을 지정한다.
3. `.env.example`을 복사해 `.env`를 만들고 `DB_PASSWORD` 등 실제 값을 채운다(이미 `.env`가 있다면 `DB_PASSWORD`만 채우면 된다).
   - IntelliJ는 `.env`를 자동으로 읽지 않으므로, Run/Debug Configuration의 `Environment variables`에 `.env` 내용을 직접 입력하거나, [EnvFile 플러그인](https://plugins.jetbrains.com/plugin/7861-envfile)을 설치해 `.env`를 연결한다.
4. `FarmlogApiApplication`을 실행한다. 기본 포트는 `8080`, 컨텍스트 경로는 `/api/v1`이다.
5. 최초 1회, DB에 스키마/시드 데이터가 없다면 `database/schema.sql` → `database/seed-data.sql` 순서로 DB에 직접 실행한다(자동 DDL 실행 없음).
6. Swagger UI: http://localhost:8080/api/v1/swagger-ui.html

상세 실행 가이드(포트, 환경변수, 트러블슈팅 포함)는 저장소 밖 `파밍로그/plan/` 문서 및 `DEPLOYMENT.md`를 참고한다.

## 로컬 실행 — Docker Compose

```bash
cp .env.example .env   # 이미 있다면 생략, DB_PASSWORD만 채우기
docker compose up -d --build
```

기본 구성은 기존 MariaDB에 연결한다. 격리된 로컬 DB가 필요하면 `.env`의 `DB_HOST=farmlog-mariadb`로 바꾸고 `docker compose --profile local-db up -d --build`를 사용한다. 상세한 schema/seed 및 볼륨 정책은 `DEPLOYMENT.md`를 참고한다.

## 폴더 구조

```text
backend/
├── src/main/java/com/farmlog/   # 도메인별 패키지 (auth, user, farm, worklog, ...)
├── src/main/resources/
│   ├── application.yml, application-{local,dev,pilot,prod}.yml
│   └── mapper/*.xml             # MyBatis Mapper XML
├── database/
│   ├── schema.sql                # DB Migration 도구 미사용, 이 파일이 스키마 원본
│   ├── seed-data.sql             # 예시(더미) 데이터
│   └── db-change-log.md          # 스키마 변경 이력
├── docker-compose.yml
└── DEPLOYMENT.md
```

## DB 스키마 관리 원칙

DB Migration 도구(Flyway/Liquibase)를 사용하지 않는다. 스키마 변경은 `database/schema.sql`을 직접 수정하고 `database/db-change-log.md`에 이력을 남긴다. 애플리케이션은 시작 시 자동으로 DDL을 실행하지 않는다.

## API 문서

Swagger UI: `/api/v1/swagger-ui.html` (local/dev/pilot 프로파일에서만 노출, prod 프로파일에서는 비활성화)

## 테스트

```bash
./gradlew clean test
```

현재 Phase 1~9 API(인증·농장·기록·리포트·멤버/매니저·첨부·운영자·구조 변경·조직 대시보드)까지 구현되어 있다. 파일럿 수동 리허설과 실제 Docker/DB 기동 결과는 저장소 밖 `docs/Farmlog_ERP_Pilot_Rehearsal_Checklist.md`에 성공과 미측정을 구분해 기록한다.
