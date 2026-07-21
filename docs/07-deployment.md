# 배포 및 운영

## 1. 로컬 실행

필수 도구:

- Docker / Docker Compose
- JDK 21
- Node.js / npm

실행 흐름:

```bash
# DB 실행
docker compose up -d db

# backend 실행
cd backend
./gradlew bootRun

# frontend 실행
cd frontend
npm install
npm run dev
```

## 2. 환경 변수

### Backend

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
SPRING_PROFILES_ACTIVE
JPA_DDL_AUTO
FRONTEND_ORIGIN_PATTERNS
AUTH_ENABLED
```

인증을 적용하는 경우 다음 값을 별도로 관리한다.

```text
ADMIN_USERNAME
ADMIN_PASSWORD
WORKER_USERNAME
WORKER_PASSWORD
```

### Frontend

```text
BACKEND_API_URL
API_BASE_URL
NEXT_PUBLIC_API_BASE_URL
```

## 3. 초기 데이터

초기 seed 기준:

- 15개 동
- 45개 물리 배드
- 90개 기본 논리 구역
- 기본 작업 유형
- 기본 거래처/테스트 데이터

운영 데이터가 들어간 이후에는 seed 재실행으로 기존 데이터가 훼손되지 않도록 주의한다.

## 4. 운영 배포

초기 운영 후보:

- 농장 내부 미니 PC
- 개인 서버
- 소형 클라우드 VM

권장 구성:

```text
Nginx
 ├─ frontend
 └─ backend

PostgreSQL
```

외부 접속이 필요하면 HTTPS를 적용한다. 내부망 전용으로 쓸 경우에도 DB 백업은 반드시 분리 보관한다.

### mini-pc k3s 배포

mini-pc k3s 운영 환경은 `k8s/base/` 매니페스트를 기준으로 한다.

```text
Traefik Ingress
 ├─ /api → backend
 └─ / → frontend

PostgreSQL
 └─ mini-pc host PostgreSQL
```

도메인은 `https://green-house.sjw-project.site/`를 사용한다. DB는 k3s 내부 Pod로 띄우지 않고 host PostgreSQL을 `Service`/`Endpoints`로 참조한다.

적용 전 확인:

- `k8s/base/secret.yaml`의 운영 비밀번호 변경
- `k8s/base/postgres-host-service.yaml`의 host PostgreSQL IP 확인
- backend/frontend 이미지 태그 변경
- Traefik TLS secret 이름 확인
- 운영 환경에서 Swagger/OpenAPI 비활성화 확인

이미지는 GHCR private package로 발행한다. `.github/workflows/publish-ghcr.yml`은 backend와 frontend 이미지를 각각 빌드해 다음 이름으로 push한다.

```text
ghcr.io/<owner>/<repo>-backend
ghcr.io/<owner>/<repo>-frontend
```

태그는 branch, git tag, `sha-<commit>`, 기본 브랜치의 `latest`를 사용한다. private image를 k3s에서 pull하려면 `green-house` namespace에 `ghcr-secret`을 먼저 생성한다.

## 5. 백업

PostgreSQL 백업 예시:

```bash
pg_dump -U greenhouse greenhouse > backup_$(date +%Y%m%d).sql
```

권장 정책:

- 매일 1회 백업
- 최근 7일 보관
- 월 1회 장기 보관
- 다른 PC 또는 외장 디스크에 복사
- 복구 테스트 주기적으로 수행

운영 백업을 V6 이하 스키마로 복원한 뒤 현재 백엔드를 시작하면 Flyway가 최신 스키마까지 순서대로 갱신한다. V7은 작업 V2 구조를 생성하고, V8은 기존 `work_records`를 `work_operations`·대상·실행 데이터로 변환하며 원본 행은 감사용으로 보존한다. 변환 작업의 `request_key`는 `LEGACY_WORK_RECORD:{id}` 형식이므로 같은 이력이 중복 생성되지 않는다.

운영 custom dump로 로컬 개발 DB를 초기화할 때는 다음 스크립트를 사용한다. 백업을 생략하면 `temp/`의 최신 `*.dump.gz` 또는 `*.dump`를 선택한다. 스크립트는 로컬 DB만 허용하며 기존 백엔드를 종료하고, 복원 후 Flyway 적용·Hibernate 스키마 검증·작업 V2 무결성 검사를 수행한다.

```bash
# 확인 문구 입력 후 실행
./scripts/reset-dev-db.sh

# 백업 지정
./scripts/reset-dev-db.sh temp/green-house_20260717_120001.dump.gz

# CI 또는 반복 개발 작업
./scripts/reset-dev-db.sh --yes
```

## 6. 운영 전 체크리스트

- [ ] DB 마이그레이션 정상 실행
- [ ] 15개 동/45개 배드/90개 구역 생성 확인
- [ ] 난 묶음 등록/수정/삭제 확인
- [ ] 난 묶음 이동 시 작업 이력 생성 확인
- [ ] 작업 이력 등록/조회 확인
- [ ] 판매 전표 생성/출력 확인
- [ ] 경매 lot 조회/상태 변경 확인
- [ ] 경매 정산 생성/입금 확인 확인
- [ ] 거래처 잔액 조회 확인
- [ ] 백업 파일 생성 확인
- [ ] 서버 재시작 후 데이터 유지 확인

## 7. 장애 대응 원칙

- 운영 DB 직접 수정은 최소화한다.
- 수정 전 백업을 만든다.
- 전표, 작업 이력, 입금 이벤트, 경매 상태 이력은 삭제하지 않는다.
- 잘못된 데이터는 취소/보정 이력으로 처리한다.
