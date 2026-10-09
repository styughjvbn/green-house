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
APP_ENV=dev ./gradlew bootRun

# frontend 실행
cd frontend
npm install
npm run dev
```

Linux/macOS에서는 개발 서버를 한 번에 실행할 수 있다.

```bash
./scripts/dev-start.sh
```

백엔드 코드를 변경한 뒤 실행 중인 프론트엔드와 DB는 유지하고 백엔드만 다시
시작하려면 다음 옵션을 사용한다. 백엔드가 실행 중이 아니면 새로 시작한다.

```bash
./scripts/dev-start.sh --restart-backend
```

프론트엔드 렌더링 성능처럼 production build 기준으로 확인해야 할 때는 다음 옵션을
사용한다. 이 모드는 `npm run build`가 성공한 후 `npm run start`로 프론트엔드를
실행하며, 기존 3000 포트를 재사용하지 않는다.

```bash
./scripts/dev-start.sh --frontend-production
```

백엔드 검증은 `cd backend && ./gradlew check bootJar --no-daemon`을 사용한다. 전체 Spring·ArchUnit 테스트의 JVM heap은 기본 2 GiB이며 `-PbackendTestHeap=3g`처럼 변경할 수 있다. 이 값은 테스트 worker에만 적용되고 애플리케이션 실행 heap을 바꾸지 않는다. OOM은 즉시 비정상 종료해 CI가 정체되지 않도록 한다. 임시 Gradle init script는 필요하지 않다.

## 2. 환경 변수

### Backend

```text
APP_ENV
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
SPRING_PROFILES_ACTIVE
JPA_DDL_AUTO
SERVER_PORT
FRONTEND_ORIGIN_PATTERNS
AUTH_ENABLED
SESSION_TIMEOUT
SESSION_COOKIE_MAX_AGE
DB_POOL_MAX_SIZE
DB_POOL_MIN_IDLE
DB_POOL_CONNECTION_TIMEOUT
HIBERNATE_JDBC_BATCH_SIZE
POSTGRES_REWRITE_BATCHED_INSERTS
FLYWAY_URL
FLYWAY_USERNAME
FLYWAY_PASSWORD
ORCHID_LEDGER_WRITER_VERSION
ORCHID_LEDGER_STARTUP_GUARD_ENABLED
```

운영에서 `JPA_DDL_AUTO`는 `validate`로 두고 스키마 변경은 Flyway 마이그레이션으로만 적용한다.
`HIBERNATE_JDBC_BATCH_SIZE`의 기본값은 `50`이며 PostgreSQL JDBC batch 재작성은 기본 활성화한다.
난 묶음 ledger가 `ACTIVE`인 DB에는 coverage의
최소 버전 이상인 `ORCHID_LEDGER_WRITER_VERSION`을 설정한다. 조건을 만족하지 못한
인스턴스는 startup guard에서 기동이 거부된다. guard 비활성화는 읽기 전용 대사 명령 내부에서만 사용한다. Legacy 모드는 제거됐으며 기본 writer version은 `2.0.0`이다. 원장 없는 기존 난 묶음이나 PREPARING coverage가 있으면 현재 release로 업무 서버를 시작하지 않고 ACTIVE 원장이 포함된 백업과 복원 절차를 확인한다.

인증을 적용하는 경우 다음 값을 별도로 관리한다.

```text
ADMIN_USERNAME
ADMIN_PASSWORD
WORKER_USERNAME
WORKER_PASSWORD
```

데모 배포에서는 다음 제한 값을 함께 관리한다.

```text
DEMO_MODE
DEMO_USERNAME
DEMO_REQUEST_LIMIT_PER_MINUTE
DEMO_MUTATION_LIMIT_PER_MINUTE
DEMO_MUTATION_LIMIT_PER_DAY
DEMO_MAX_REQUEST_BYTES
```

### Frontend

```text
APP_ENV
BACKEND_API_URL
API_BASE_URL
NEXT_PUBLIC_API_BASE_URL
```

`APP_ENV`는 실행 환경을 `dev` 또는 `prod`로 명시한다. 로컬 `dev-start.sh`는 `dev`를
강제하고 Kubernetes와 프론트 Docker 이미지는 `prod`를 명시한다. 값이 없는 프론트는
Next.js의 `NODE_ENV=development`일 때만 `dev`로 보정하며 그 외에는 `prod`로 처리한다.
백엔드는 값이 없으면 `prod`로 처리한다. `/mutation-lab` 메뉴·페이지와
`/api/orchid-group-mutations` 진단 API는 `dev`에서만 활성화된다. `DEMO_MODE`는 이 구분과
독립적인 데모 인증·제한 설정이다.

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

- `k8s/base/secret.yaml`의 운영 DB·로그인 비밀번호 변경
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

운영 백업을 이전 스키마로 복원한 뒤 현재 백엔드를 시작하면 Flyway가 현재 최신 버전까지 순서대로 갱신한다.

- V7은 작업 V2 구조를 생성한다.
- V8은 기존 `work_records`를 `work_operations`·대상·실행 데이터로 변환하고 원본 행을 보존한다. 변환 작업의 `request_key`는 `LEGACY_WORK_RECORD:{id}` 형식이다.
- V9는 작업 시점 데이터를 UTC 기준으로 정규화한다.
- V10~V11은 품종 선택 색상 필드와 제약을 추가·보정한다.
- V12는 운영 변경 감사용 `audit_events`와 조회 인덱스를 추가한다.
- V20은 기존 자리 이동·합식 실행 효과의 JSON 원본 목록을 `work_effect_orchid_groups`의 `SOURCE` 관계로 보강한다. 난 묶음 수량·상태와 기존 직접 계보 행은 변경하지 않는다.
- V21은 난 묶음 Mutation, complete state-chain Entry, 관계, coverage와 Work·Sales·Lineage 연결 필드를 최종 형태로 생성한다. 기존 난 묶음의 chain은 자동 생성하지 않는다. 당시 전용 importer로 적재한 원장을 보존하며, 이관 완료 후 importer는 제거했다.
- V22는 `ACTIVE` coverage에서 Mutation context 없는 난 묶음 INSERT·UPDATE와 모든 DELETE를 차단하고, 커밋 시 변경 revision에 대응하는 `CREATE` 또는 `CHANGE` Entry를 검증한다. `PREPARING`에서는 차단하지 않는다.
- V23은 `UNMAPPED`으로 남은 기존 난 묶음 중 의미가 명확한 스마트 따옴표 3·4인치 값만 표준 화분 코드로 보정한다. 다른 `UNMAPPED` 값은 자동 변환하지 않는다.
- V24는 품종·자재 코드용 sequence를 만든다. 기존 코드와 ID는 바꾸지 않고 기존 ID·숫자 코드의 최댓값 다음에서 발급을 시작한다. 삭제·실패한 트랜잭션으로 번호가 비어도 재사용하지 않는다.
- V28은 작업 취소 메타데이터와 보류된 현장 상태 동기화 유형을 추가하고 기존 `MULTI_CREATE` 유형을 제거한다.
- V29는 이동에 함께 등록한 폐기 작업의 부모·관계 필드를 추가하고 기존 JSON 관계를 정규화한다.
- V30은 입고 상태·출처를 정규화하고 중복 입고 결과 필드를 제거한다. 출처 backfill 뒤 지연 constraint를 즉시 검증한 다음 FK·컬럼을 제거해 PostgreSQL pending trigger 충돌을 방지한다. 작업 멱등 접수 결과의 소속 테이블도 생성·backfill한다.
- V31은 과거 이동 전 폐기 이력을 이동 후 잔여 폐기로 재배열한다. 재배열한 이력이 현재 말단이면 현재 난 묶음 상태도 마지막 원장 스냅샷에 맞추며, 수량·위치·revision·시각은 유지한다. 후속 변경이 있으면 해당 변경의 `before` 스냅샷을 재배열한 말단에 연결하되, 후속 결과와 현재 행은 덮어쓰지 않는다. 이어서 과거 1:1 이동의 원본 ID·속성·후속 참조를 복원하고, 다품종 폐기 작업을 품종별로 분리하며 폐기 실행 수량을 정규화한다. 자동 보정이 불명확한 관계는 적용을 중단하고 이 맥락 전체를 rollback한다.
- V32는 품종별·시스템 작업 제목을 자동 명명 규칙으로 정규화하고, 효과가 남아 있는 기존 `CANCELED` 작업을 `STOPPED`로 분리한다. 일반 작업의 사용자 제목은 보존한다.
- V33은 보정을 원본 작업의 감사 이벤트로 저장하도록 스키마를 변경하고 전용 멱등 접수 테이블을 추가한다.
  기존 보정 데이터 이관은 포함하지 않는다. 적용 대상 DB에 보정 작업·관계 또는 보정 상태가 있으면
  마이그레이션을 중단한다. 개발 DB에서 0건을 확인했으며 다른 환경은 배포 전에 별도로 확인한다.
- V34는 현재 실사 수량의 멱등 접수·감사 기록을 추가한다. 기존 수량이나 과거 작업을 backfill하지 않는다. 배포 시 migration 적용과 새 백엔드 기동 후 프론트를 함께 갱신한다. 실사 API는 기존 상태·위치 동기화 API의 재활성화가 아니다.
- V35는 0이 아닌 품목 수량·비음수 단가·BIGINT 곱셈과 저장 금액의 일치를 CHECK로 보호한다. 과거 음수 수량/금액 반품과 음수 전표 총액은 그대로 보존한다. `NOT VALID`로 추가하며 최신 백업 복원본에서 위반 대사와 validation을 검증한다. CHECK는 여러 품목의 합계를 검증하지 못하므로 전표 합계의 정확 연산은 Sales 도메인이 담당한다.
- V36은 경매 결과·반환 확인의 요청 receipt를 추가한다. V35까지의 결과·반환은 그대로 보존하고 요청 키를 추정 backfill하지 않는다. 새 API는 요청 키가 필수이므로 경매 입력을 잠시 중지하고 기존 백엔드의 쓰기를 멈춘 뒤 migration·새 백엔드·키를 유지하는 프론트 버전을 함께 배포한다. 구형 화면·연동의 키 없는 요청은 400으로 거절된다. 새 receipt 생성 후 구버전 writer로 rollback하면 중복 방어를 우회하므로 구버전 쓰기를 재개하지 않는다. receipt의 임의 삭제·TTL 정리와 응답 schema 변경은 replay 보존 검토 없이 수행하지 않는다. 기존 중복 결과·반환과 정산 영향은 별도 대사 대상이다.
- V37은 경매 이력에 nullable 수량 전후값 6개와 완전성·비음수 CHECK를 추가한다. 기존 이력·receipt·lot·정산·입금을 변경하지 않는다. 이력 테이블의 ALTER/CHECK 적용에는 잠금·검사 시간이 필요하므로 경매 쓰기를 중지한 상태에서 migration·새 백엔드·capability를 사용하는 프론트를 함께 배포한다. 새 Receipt 응답에는 신규 필드가 포함되며 구버전의 strict JSON mapper는 이를 읽지 못하므로, 새 Receipt 생성 이후 호환 검증 없이 구버전 writer를 재개하지 않는다. 기존 Receipt는 새 버전에서 그대로 재조회하며, 과거 누락 수량은 운영 자료 대사 없이 역산하지 않는다.
- V38은 Sales 소유 생성 접수 테이블을 추가하고 기존 전표·예약·출하를 변경하거나 요청 키를 backfill하지 않는다. migration과 새 백엔드 적용 후 생성 키를 사용하는 프론트를 배포한다. 구버전 writer는 새 header를 무시해 재전송을 신규 생성하므로 전환 시 해당 writer의 판매 생성을 중지하고, 접수가 생긴 뒤 구버전 writer로 재시도하지 않는다. 키 없는 기존 연동은 계속 별도 생성이므로 재전송 정책을 별도로 확인한다. 생성 접수는 임의 삭제·TTL 정리하지 않으며 영속 지문·응답 변경은 이전 버전 replay 검증을 포함한다. 성공 여부가 미확인인 브라우저 생성 키도 임의 초기화하지 않는다.
- V39는 Farm 소유 입고 생성 접수 테이블을 추가하고 기존 입고·품종·난 묶음·Work·Mutation을 변경하거나 생성 키를 backfill하지 않는다. migration·새 백엔드 적용 후 키를 유지하는 입고 프론트를 배포한다. 구버전 writer는 header를 무시해 재전송 입고·작업을 다시 생성하거나 배치 충돌을 반환하므로 전환 시 해당 writer의 입고 생성을 중지하고, 접수가 생긴 뒤 구버전으로 재시도하지 않는다. 키 없는 기존 연동은 별도 생성 계약을 유지한다. 접수의 임의 삭제/TTL과 브라우저의 미확인 키 초기화를 피하며 영속 지문·응답 변경은 이전 replay 호환을 검증한다.
- V40은 기존 Work Receipt에 선택형 최초 응답 snapshot과 신규 일반 생성 범위의 완료 쌍/응답 ID 일치 제약을 추가한다. 과거 Receipt의 지문·결과 ID·membership과 업무 행은 수정하지 않으며 미상 응답을 backfill하지 않는다. migration·새 백엔드 적용 후 키를 유지하는 일반 Work 등록 화면을 배포하고, header를 무시하는 구버전 writer의 해당 생성을 중지한다. 완료 접수 뒤 구버전 writer로 재시도하거나 접수·미확인 브라우저 키를 임의 삭제/TTL 정리하지 않는다. 기존 구조 변경·즉시 실행·포트의 ID 기반 replay는 유지한다. 지문·응답 schema 변경은 보존된 최초 응답의 호환 검증을 포함한다.
- V41은 확인된 참조·날짜 정렬·활성 배치·계보 라벨 조회를 위한 index를 추가한다. 업무 행·원장·접수를 backfill하거나 제약을 변경하지 않는다. 쓰기 중지 시간을 확보하고 아래의 적용 절차를 따른다.
- V42는 정산별 초기화의 반복 조회를 지원하는 양수 경매 결과의 날짜/ID partial index를 추가한다. 업무 행·원장·입금·제약을 변경하지 않으며 V41과 같은 쓰기 중지·transactional index 적용 절차를 따른다.
- 실사 수량 조정(`features.stock-count.enabled`)과 작업 기록 수량 정정(`features.work-quantity-correction.enabled`)은 기본값 `false`로 보류한다. 운영에서 활성화하지 않는다. V34와 기존 감사 기록은 보존하고, 작업일·상태 정정 및 결과 생성 취소는 계속 허용한다.

V24 전환 시 기존 코드 발급 방식과 새 방식이 동시에 쓰이지 않도록 이전 백엔드 인스턴스의 쓰기를 중지한 후 migration과 새 버전 기동을 진행한다. 신규 코드 생성 후 구버전으로 단순 rollback하지 않는다. 데이터 수입 등으로 코드를 직접 추가하는 운영 변경은 쓰기를 중지하고 코드 sequence가 추가된 숫자 코드보다 큰지 함께 확인한다.

### 조회 index 점검과 V41 적용

배포 전에 read-only 계정으로 실제 DB의 버전·통계·index를 확인한다. repository root에서 실행한다.

```bash
psql "$AUDIT_DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/performance/inspect-backend-indexes.sql
```

스크립트는 추정 행 수·ANALYZE 시점, FK의 전체 B-tree 선두 열 지원, index 유효성·사용 횟수·크기를 읽는다. partial/index INCLUDE 열은 전체 FK 지원으로 계산하지 않는다. 사용 횟수는 통계 초기화 이후 값이므로 0회라는 이유만으로 기존 index를 삭제하지 않는다. 필요한 실제 요청 조건의 실행 계획은 별도로 확인한다. 로컬 검증은 PostgreSQL 18의 합성 데이터이며 운영 PostgreSQL 버전·분포·통계의 검증을 대신하지 않는다.

V41은 `CREATE INDEX CONCURRENTLY`가 아닌 일반 index 생성 17개를 **하나의 Flyway transaction**에서 실행한다. 업무 writer와 batch를 중지하고 기존 쓰기 transaction 종료를 확인한 뒤 migration을 적용한다. 조회는 일반적으로 가능하지만 index 생성은 대상 테이블의 쓰기와 충돌하고 취득한 잠금을 commit까지 유지한다. `lock_timeout=5s`는 잠금 대기 제한, `statement_timeout=5min`은 각 SQL의 실행 제한이며 migration 전체 중지 시간을 보장하지 않는다. 큰 테이블은 실제 데이터 복제본에서 시간·디스크 여유를 먼저 확인한다.

실패·timeout이면 V41의 index 생성 전체가 rollback된다. 충돌·용량 원인을 해소하고 같은 release의 Flyway로 재시도한다. 기존 migration 파일을 수정하거나 일부 index를 수동으로 생성하여 실패를 우회하지 않는다. 성공 후 catalog의 정의·유효성을 확인하고 쓰기를 재개한다. 신규 index의 크기·WAL·수량 갱신의 HOT 비율도 관찰한다. quantity predicate를 사용하는 활성 배치 partial은 양수 수량 변경에도 HOT update에 영향을 줄 수 있다. 상세 비교와 미측정 범위는 [BE-035 실행 계획 검증](../backend-audit/archive/11-index-plan-validation.md)을 따른다.

### Sales 금액 원천 전환과 파생 정산 제거

V43–V49는 Direct 전용 거래·가격, 유효 배분 조회, 경매 대금, 후속 결정과 실제 반환 도착을 준비·전환한다.
V43의 기존 금액 원문·대사와 V49의 전환 직전 불일치 근거를 보존하며 검토 대상은 새 입금·금액 변경이 차단된다.
원장이나 Farm 수량으로 불일치를 자동 보정하지 않는다. 원천 거래가 누락되면 migration을 중단한다.

V47·V50은 기존 실제 경매 입금의 대상 참조를 경매 대금으로 바꾸고 입금 ID·금액·날짜·부모 연결·멱등키를 유지한다.
제공 지급액이나 매칭 확인을 추정하지 않으며 기존 확인된 대금을 변경하지 않는다.
누락된 대상·별칭이나 결과 소유권 충돌은 배포 전에 대사해야 하며 migration 실패 시 해당 migration이 rollback된다.

V50은 파생 정산 테이블을 제거한다. 쓰기를 중지하고 복제본에서 대사·migration을 검증한 뒤
새 백엔드와 대금 API를 사용하는 프론트를 함께 배포한다. 기존 정산 API·재계산·시작 초기화는 제공하지 않는다.
`app.settlement.rebuild-on-startup`은 현재 실행 효과가 없는 과거 옵션이다.
V50 적용 뒤 구버전 정산 writer로 단순 rollback하지 않는다. 되돌리려면 검증된 배포 전 백업과 해당 릴리스를 함께 복원한다.

### 저장 지문·스냅샷 형식 변경 기준

현재 Mutation 지문은 중첩 입력까지, Sales 생성과 일반 Work 세 생성 경로의 지문은 기존 요청 필드까지 v1 형식으로 고정한다. Work 자유 details는 기존 전체 JSON 비교를 유지한다. 저장 hash에 version 접두사를 추가하거나 기존 hash를 재계산하지 않는다. coverage의 engine/snapshot schema version(현재 각각 1), state-chain manifest schema version(현재 2), 최소 writer version은 각각 다른 계약이다. coverage metadata와 writer 기동 검사가 개별 Mutation·Work·Sales receipt의 다중 형식 reader를 제공하는 것은 아니다.

- 새 속성·직렬화 설정을 바꾸기 전에 어느 지문·응답·Entry·baseline/import manifest·취소 복원에 영향을 주는지 확인한다. 명령/중첩 값/snapshot 필드 fixture가 실패하면 기존 golden을 새 결과로 덮어쓰기 전에 형식 전환을 설계한다. 의미 있는 신규 값은 v1 projection에서 누락시켜 같은 요청으로 취급하지 않는다.
- 변경되는 저장 계약마다 구형/신형을 명확히 판별할 version과 구형 reader/replay 경로를 먼저 구현한다. version 없는 자료의 해석은 실제 지원 자료로 검증한 기존 형식에 한정한다. 과거 필드를 현재 Entity로 복원하거나 다른 hash를 순서대로 시도해 일치시키지 않는다. absent/null/default·수량·위치 정밀도·배열 순서·문자열 정규화와 새 값의 비교 의미를 명시한다.
- 실제 확장 배포 전에는 구형 저장 JSON/hash, 완료 receipt replay, effective head·대사·baseline/import fingerprint와 취소/보상 복원을 PostgreSQL에서 검증한다. 현재 fixture는 개발 시점의 계약이며 운영 과거 요청 corpus의 검증을 대체하지 않는다. 새 필드의 DB default/backfill이 원장 의미와 일치하는지도 별도로 검증한다.
- 형식이 다른 writer의 병행 실행은 호환 시험을 통과한 조합에만 허용한다. 그렇지 않으면 영향 쓰기를 중지하고 migration·reader/writer를 함께 전환한다. 기존 Farm 최소 writer 검사는 ACTIVE coverage 경계에 적용되므로 Work·Sales의 모든 쓰기 차단 수단으로 간주하지 않는다. 신규 형식 기록 이후 구버전 writer의 재개/rollback은 새 자료의 읽기·쓰기·재요청 보존이 입증될 때만 허용한다.

현재 변경은 v1 지문 고정과 회귀 방어만 제공한다. 신규 속성·version dispatcher·운영 자료 backfill을 도입한 배포 절차는 아니며, 실제 형식 확장 시 해당 구현과 배포 계획을 함께 작성한다.

V21~V23은 아직 운영에 배포되지 않은 기존 V21~V28 실험 migration을 V20 기준으로
통합한 이력이다. 지원하는 업그레이드 경로는 `V20 → V21~V23`이다. 통합 전
V21~V28을 적용했던 개발·rehearsal DB는 checksum repair나 수동 스키마 변경을 하지
않고 V20 운영 백업으로 다시 초기화한다. 이 통합본을 운영에 적용한 뒤에는 파일을
수정하거나 번호를 다시 사용하지 않는다.

V28 이후 migration은 모두 운영 미적용인 상태에서 기존 14개 파일을 7개 맥락으로
통합했다. V1~V27은 변경하지 않으며 지원하는 업그레이드 경로는 `V27 → V28~V34`다.

| 새 버전 | 맥락 | 통합 전 버전 |
|---|---|---|
| V28 | 작업 취소 기반·시스템 유형 | V28, V29 |
| V29 | 이동·폐기 작업 관계 | V30 |
| V30 | 입고·작업 멱등 접수 정규화 | V32, V33 |
| V31 | 과거 이동·폐기 이력 정규화 | V35~V38, V41 |
| V32 | 작업 제목·종료 상태 정규화 | V39, V40 |
| V33 | 원본 작업 보정 감사 이벤트 | V42 |
| V34 | 현재 실사 수량 감사 기록 | V43 |

각 맥락 안의 기존 SQL 단계는 유지한다. 폐기 실행 수량 보정(기존 V41)은 그 기준이
되는 과거 이력 보정의 마지막 단계로 통합한다. 작업 제목·상태 정규화는 해당 수량을
읽거나 변경하지 않는다. 과거 이력의 원본 버전 표기는 SQL 내부 단계의 출처이며 별도
Flyway 버전이 아니다.

통합 전 V28 이상을 적용한 개발·rehearsal DB는 checksum repair나 수동 history 수정으로
이어 쓰지 않고, V27 이하 운영 백업 또는 초기 데이터에서 다시 초기화한다. 기존 V28 이상
적용 DB로 새 백엔드를 바로 기동하지 않는다. 이 통합본을 운영에 적용한 뒤에는 파일을
수정하거나 번호를 재사용하지 않는다.

운영 custom dump로 로컬 개발 DB를 초기화할 때는 다음 스크립트를 사용한다. 백업을 생략하면 `temp/`의 최신 `*.dump.gz` 또는 `*.dump`를 선택한다. 스크립트는 로컬 DB만 허용하며 기존 백엔드를 종료하고, 복원 후 HTTP 서버 없이 Flyway 적용·Hibernate 스키마 검증·작업 V2 무결성 검사를 수행한다. V20 백업처럼 상태 원장이 없거나 PREPARING인 경우 복원 후 종료 코드 2로 전환 필요를 알린다. 아래 PLAN/IMPORT/VERIFY/ACTIVE 절차를 완료한 뒤 업무 서버를 시작한다.

```bash
# 확인 문구 입력 후 실행
./scripts/reset-dev-db.sh

# 백업 지정
./scripts/reset-dev-db.sh temp/green-house_20260717_120001.dump.gz

# CI 또는 반복 개발 작업
./scripts/reset-dev-db.sh --yes
```

### 난 묶음 원장 복구 기준

2026-10-06에 운영 전환이 끝난 state-chain importer·coverage 준비·활성화 CLI와 전용 profiling/정규화 도구를 제거했다. 현재 release는 원장이 포함된 `ACTIVE` 운영 백업을 복원하고 대사·schema·startup guard를 검증한다. 데이터 보정이나 원장 재생성을 복원 과정에 섞지 않는다.

전환 전 V20 등 과거 백업은 현재 release의 이관 대상이 아니다. 해당 시점의 코드와 당시 검증 자료는 Git 이력·[과거 전환 기록](archive/plans/orchid-engine-cutover-20260908.md)·[보관 manifest](archive/plans/orchid-state-chain-migration-manifest.json)에서 확인한다. 보관 자료는 현재 실행 도구가 아니다. Mutation/Entry·coverage·Work/Sales/Lineage의 기존 사실과 Flyway 이력은 계속 보존한다.

### 난 묶음 ledger 복원 DB rehearsal

운영 백업을 격리된 PostgreSQL에 복원한 뒤 read-only 대사를 실행한다. 운영 primary DB에는 실행하지 않는다.

```bash
cd backend
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerReconcile
```

명령은 Flyway를 비활성화하고 Hibernate schema validation과 read-only DB connection을
강제한다. 점검 계정은 대상 table과 sequence의 SELECT 권한이 필요하다. sequence 조회 권한이 없으면 Hibernate metadata에서 sequence가 보이지 않아 schema 누락으로 보고될 수 있다. 파생 정산 시작 재구축은 제거되었다. 다음 종료 코드를 사용한다.

- `0`: 현재 단계의 모든 대사 통과
- `1`: 연결·schema·실행 오류
- `2`: JSON 보고서에 정합성 오류가 존재함

보고서는 현재 난 묶음 invariant와 배치 범위, Collection 참조, Work 진행·효과 연결,
작성중 Sales allocation과 예약 수량, ledger revision·snapshot 연속성, baseline 행 수와
fingerprint를 포함한다. `PRE_BASELINE`, `BASELINE_PREPARING`, `ACTIVE` 단계를 진단할 수 있지만 현재 운영 복원본의 성공 기준은 `ACTIVE`, `ready=true`, `issues=[]`다. 결과와 소요 시간을 보관한다.

독립 대사는 read-only `REPEATABLE_READ` snapshot에서 현재 그룹·원장·업무 참조·전체 count를 읽는다. 그룹은 500행 scalar batch, 현재/삭제된 revision chain은 500행 fetch cursor로 순회한다. 긴 이력의 Entry/Mutation Entity를 전부 적재하지 않으며 fingerprint 형식과 오류 판정은 유지한다. 현재 그룹·baseline 값과 전체 오류, Work 참조/보정, fingerprint 직렬화는 계속 증가할 수 있다. fetch size는 JSON byte 크기나 전체 peak heap의 상한이 아니다. 대사 하나의 장기 snapshot은 유지되므로 큰 원장은 read 시간·heap/GC·vacuum 영향을 실제 복제본에서 확인한다.

이 명령은 state-chain 생성, coverage 활성화, 데이터 보정을 수행하지 않는다. 오류가 있으면 대상 ID와 코드로 원인을 조사한다. 최신 백업과 원자료를 보존하며 임의 baseline 생성이나 ACTIVE 강제 전환으로 오류를 숨기지 않는다.

### V27 OrchidGroup Audit provenance 보정

V27은 2026-09-14 운영 백업으로 확인한 ACTIVE state-chain만 대상으로 한다. Audit 5건에
`mutation_id`를 연결하고 그룹 272에 누락된 Audit CORRECTION rev2를 추가한다. 269개
OrchidGroup의 quantity, status, memo, variety, 위치 등 canonical 업무 상태는 바꾸지 않으며
그룹 272의 `state_revision`만 1에서 2로 증가한다.

운영 적용 전 최신 custom dump로 복원본 rehearsal을 실행한다.

```bash
./scripts/data-audit/rehearse-orchid-audit-provenance-migration.sh \
  temp/green-house_20260914_173317.dump.gz
```

스크립트는 임시 PostgreSQL 14 container에 백업을 복원하고 release Flyway를 적용한다.
전후 269개 canonical snapshot SHA-256 동일, Audit 연결, revision·snapshot chain,
current revision, Work 42건·Lineage 16건 연결, 그룹 272 전후 상태를 검증하고
`orchidLedgerReconcile`의 `ACTIVE`, `ready=true`, `issues=[]`까지 확인한다.

운영에서는 V27이 포함된 backend 이미지를 배포하면 시작 과정에서 Flyway가 자동 적용한다.
별도 `bootRun`이나 migration SQL 수동 실행은 하지 않는다.

```bash
kubectl -n "${NAMESPACE}" scale deployment/"${DEPLOYMENT}" --replicas=0
# V27 포함 RELEASE_IMAGE로 manifest를 갱신한다.
kubectl apply -k k8s/base
kubectl -n "${NAMESPACE}" rollout status deployment/"${DEPLOYMENT}" --timeout=300s
```

기존 Pod를 모두 종료한 뒤 새 이미지를 기동해야 한다. V27이 실패하면 Flyway transaction은
전체 rollback되고 새 Pod는 기동하지 못한다. Pod가 정상 기동된 뒤
`verify-orchid-audit-provenance.sql`과 `orchidLedgerReconcile`을 read-only로 실행한다.

다음 중 하나라도 다르면 V27은 예외로 중단한다.

- cutover ID·import fingerprint·ACTIVE coverage 또는 314/348/269/5 기준 건수 불일치
- Audit ID·대상·action·before/after payload 불일치
- 대상 Mutation·Entry·현재 revision 또는 Work/Lineage 연결 불일치
- 전후 canonical snapshot, revision 연속성, Entry snapshot 연결 불일치
- 예상 외 Mutation·Entry 변경 또는 Audit 연결 수 불일치

Flyway transaction 실패는 별도 복구 없이 원상태다. V27 commit 후 검증이 실패하면 DB에
수동 역 SQL을 적용하지 않는다. backend를 계속 중지하고 V27 직전 전체 backup을 새 DB에
복원한 뒤 V26 application/schema 조합으로 복구한다. 복원 이후 생성된 업무 데이터는
보존되지 않으므로 V27 검증을 마칠 때까지 writer를 재개하지 않는다. 원인 수정 후 새
복원본 rehearsal부터 다시 수행한다.

### ENGINE writer 수동 smoke test

운영 primary가 아닌 복원 또는 별도 테스트 PostgreSQL에서만 실행한다. 기존 데이터가
있으면 원장이 포함된 ACTIVE 백업을 복원하고 대사를 통과한 뒤 애플리케이션을 다음처럼 시작한다.

```bash
cd backend
ORCHID_LEDGER_WRITER_VERSION=2.0.0 \
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew bootRun
```

화면에서 다음 순서로 확인한다.

1. 난 묶음 단건 생성, 상세 수정, 이동과 새로 만든 묶음의 생성 취소
2. 즉시 배치 입고와 유리병 모종 포트 작업
3. 폐기, 자리 이동, 분갈이, 분주, 합식과 완료 결과 보정
4. 판매 전표 등록, 작성중 예약, 전표 수정·취소, 출고 완료와 출고 완료 취소
5. 같은 Work 실행 키와 즉시 작업 요청 키 재호출 시 수량·결과가 중복되지 않는지 확인

#### smoke test 사후 ledger 검증

화면 검증은 업무 결과를 확인하고, 사후 ledger 검증은 그 결과가 Mutation 이력과
기존 Work·Sales 기록에 빠짐없이 저장됐는지 확인한다. 검증 중 상태가 바뀌지 않도록
먼저 ENGINE 백엔드를 종료한 뒤 같은 DB에서 다음 명령을 실행한다.

```bash
cd backend
DEBUG=false \
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerReconcile --args="--debug=false --logging.level.org.hibernate.SQL=OFF"
```

Gradle task는 `farm/mutation/verification`의 대사 CLI를 실행한다. task 이름·옵션·종료 코드와 read-only 동작은 유지한다.

명령은 데이터를 변경하지 않고 다음 조건을 전수 검사한다.

- 최초 관측 그룹은 해당 coverage의 `BASELINE` Entry, 이후 생성 근거가 있는 그룹은
  `CREATE` Entry로 revision chain을 시작한다. 삭제된 그룹은 `DELETE`로 끝난다.
- 인접 Entry의 `stateRevisionAfter`와 다음 `stateRevisionBefore`가 연속된다.
- 이전 `afterState`와 다음 `beforeState`가 같고, 현재 난 묶음 상태와 마지막
  `afterState`가 같다.
- 수량·예약 수량, 화분 코드, 품종·구역, 배치 범위, Collection 참조가 유효하다.
- Work 진행·효과와 Sales 작성중 allocation이 현재 난 묶음 상태와 일치한다.
- Entry 없는 Mutation이나 Mutation ID·correlation ID가 한쪽만 저장된 연결이 없다.

성공 기준은 종료 코드 `0`과 다음 JSON 필드다.

```json
{
  "stage": "ACTIVE",
  "ready": true,
  "issues": []
}
```

`baselineGroupCount`는 complete chain에서 `BASELINE`으로 시작한 그룹 수이므로 smoke test 중
새 그룹을 만들더라도 증가하지 않는다. `orchidGroupCount`, `mutationCount`,
`entryCount`는 테스트 결과에 따라 증가하는 것이 정상이다. `baselineFingerprint`는
초기 기준을 나타내므로 유지되고 `currentStateFingerprint`는 상태 변경에 따라 바뀐다.

대사 통과 후 새 Work 효과와 판매 이동에서 반쪽짜리 연결이 없는지 SQL로 한 번 더
확인한다.

```bash
PGPASSWORD=greenhouse_rehearsal_test psql \
  -h 127.0.0.1 -p 5432 \
  -U greenhouse_rehearsal_test -d greenhouse_rehearsal
```

```sql
SELECT id
FROM work_applied_effects
WHERE (mutation_id IS NULL) <> (correlation_id IS NULL);

SELECT id
FROM sales_inventory_movements
WHERE (mutation_id IS NULL) <> (correlation_id IS NULL);
```

두 조회 결과는 비어 있어야 한다. 기록 전용 Work 효과의 두 값이 모두 `NULL`인 것은
정상이다. 결과가 있거나 대사 결과가 `ready=false`이면 `issues.code`와 `referenceId`로
원인을 확인하고 해당 테스트 DB를 보존한다. manifest 교체, ledger 직접 수정,
`ACTIVE` 전환으로 문제를 덮지 않는다. 현재 Engine 전용 HTTP 서버의 smoke test는 ACTIVE 백업을 복원한 폐기 가능한 DB 사본에서 수행한다. PREPARING 상태를 현재 release에서 재활성화하지 않는다.

### 운영 백업 복원 후 기동 검증

복원본의 read-only 대사가 `ACTIVE`, `ready=true`, `issues=[]`인지 확인한 뒤 동일 배포 후보로 HTTP 없는 기동 검증을 수행한다. 이 검증은 Flyway를 실행하지 않고 Hibernate schema validation과 원장 startup guard를 확인한다.

```bash
cd backend
./gradlew orchidLedgerStartupVerify
```

기동 검증의 Gradle task도 `farm/mutation/verification`의 CLI를 실행하며 writer 설정·startup guard는 `farm/mutation/config`가 소유한다. 접속 대상과 writer version은 앞의 복원본 접속 설정을 사용한다. 일반 서버에서 startup guard를 끄거나 원장 불일치를 강제 활성화로 우회하지 않는다. 실제 운영 재배포·복원은 최신 백업, 배포 후보, 데이터 보존 범위와 검증 결과를 확인하고 진행한다.

CLI 복원 회귀는 `./gradlew workE2eTest --tests '*OrchidGroupLedgerCliPostgresE2ETest' --no-daemon`으로 실행한다. Testcontainers PostgreSQL에 구성한 ACTIVE 원장을 custom dump로 백업·복원한 뒤 두 CLI의 성공과 모든 public 테이블·시퀀스의 보존을 검사한다. 실제 운영 백업 복원본으로 수행한 [2026-10-08 검증 근거](archive/plans/backend-architecture-restore-verification-20261008.md)는 별도로 보관한다.

### 데모 환경

데모 환경은 `k8s/overlays/demo`를 사용하지만 공개 데모를 운영 배포 사전 검증 환경으로
사용하지 않는다. overlay YAML에는 demo 이미지 tag를 고정하지 않는다. 이미지 배포는
데이터 리프레시와 분리해 기존 스크립트로만 수행한다.

```bash
NAMESPACE=green-house-demo \
APP_URL=https://green-house-demo.sjw-project.site \
./scripts/deploy/deploy.sh sha-xxxx
```

로그인 우회, 데모 인증 주체, 요청 제한은 `DEMO_MODE=true`에서만 활성화된다.

운영 Flyway가 완료된 DB의 history를 포함해 dump한 뒤 격리 비식별화, 후보 DB 검증,
blue/green rename과 자동 rollback을 수행하는 별도 systemd timer 절차는
`docs/features/demo-operations.md`를 따른다. 운영 원본 dump나 검증 전 dump를 demo DB에
직접 복구하지 않는다. demo 사용자가 만든 데이터는 정기 리프레시 때 삭제된다.

## 6. 운영 전 체크리스트

- [ ] DB 마이그레이션 정상 실행
- [ ] 15개 동/45개 배드/90개 구역 생성 확인
- [ ] 난 묶음 등록/수정/삭제 확인
- [ ] 난 묶음 이동 시 작업 이력 생성 확인
- [ ] 작업 이력 등록/조회 확인
- [ ] 판매 전표 생성/출력 확인
- [ ] 경매 lot 조회/상태 변경 확인
- [ ] 경매 대금 조회·입금 가능 여부·기존 입금 이력 확인
- [ ] 거래처 잔액 조회 확인
- [ ] 주요 변경 후 `audit_events`의 요청·사용자·변경 필드 기록 확인
- [ ] 백업 파일 생성 확인
- [ ] 서버 재시작 후 데이터 유지 확인

## 7. 장애 대응 원칙

- 운영 DB 직접 수정은 최소화한다.
- 수정 전 백업을 만든다.
- 전표, 작업 이력, 입금 이벤트, 경매 상태 이력은 삭제하지 않는다.
- 잘못된 데이터는 취소/보정 이력으로 처리한다.

감사 이벤트는 `scripts/data-audit/audit-event-analysis.sql`의 예시 쿼리로
최근 변경과 연속 보정 후보를 확인한다. `request_id`는 API 응답의
`X-Request-Id`와 서버 로그에도 같이 남으므로 장애 추적 키로 사용한다.

### 기존 데이터의 CHECK 제약 대사와 검증

V14·V21·V35의 재고 수량·예약·revision·판매 상태·품목 계산 CHECK는 기존 행을
보존하기 위해 `NOT VALID`로 추가했다. Flyway 성공과 과거 행 검증 완료를
구분한다. 설치된 CHECK의 정의와 `convalidated`는 아래 도구로 확인한다.
신규·갱신 행의 CHECK 적용과 validation의 잠금 의미는
[PostgreSQL ALTER TABLE](https://www.postgresql.org/docs/18/sql-altertable.html)을 따른다.

V35는 운영 미적용 상태에서 수정했다. 현재 품목 CHECK는 0이 아닌 수량·비음수 단가·정확한 수량×단가를 검사하며, 과거 음수 수량/금액과 음수 전표 총액을 보존한다. 전표 총액의 비음수 CHECK는 설치하지 않는다. 기존 V35를 적용한 개발/리허설 DB는 데이터를 보존한 별도 복원본에서 현재 migration을 재검증한다. checksum만 repair하면 설치 제약은 바뀌지 않으므로 이를 schema 전환으로 취급하지 않는다.

먼저 최신 백업을 복원한 격리 DB에서 대사·검증을 rehearsal한다. libpq service의
`greenhouse-audit`는 해당 DB와 SELECT 권한 계정에, `greenhouse-maintenance`는
같은 DB의 테이블 소유자/DDL 계정에 연결한다. 인증 설정은 기존 DB 접속 정책을
따른다. 운영 적용은 대상 DB·백업·대사 결과와 작업 시간을
확인한 후 같은 release의 스크립트로 수행한다.

```bash
mkdir -p temp
PGSERVICE=greenhouse-audit psql -XAtq \
  -f scripts/data-audit/audit-domain-constraints.sql \
  > temp/domain-constraints-report.jsonl
```

첫 JSON 행의 DB·계정·트랜잭션 시작 시각·snapshot 식별자를 확인한다.
나머지 5행은 설치된 CHECK 식을
직접 평가한 위반 건수와 ID 표본(최대 50개)을 포함한다. 설치된 정의가 해당 release의
migration과 일치하는지도 함께 확인한다. read-only
`REPEATABLE READ` 트랜잭션으로 같은 snapshot을 사용하며, 메모·품목 원문은
출력하지 않는다. 전체 건수는 표본 상한과 무관하게 센다. RLS로 일부 행만
보이는 계정은 정상 0건 대신 실행 오류가 발생한다.
이 동작은 [PostgreSQL row_security](https://www.postgresql.org/docs/18/runtime-config-client.html#GUC-ROW-SECURITY)의 `off` 의미를 따른다.

| 보고 상태 | 의미와 다음 단계 |
| --- | --- |
| `MISSING` / `NOT_CHECK` | 해당 제약 누락 또는 종류 불일치. schema drift를 조사한다. 위반 건수는 null이다. |
| `VIOLATIONS` | 기존 위반 행이 있다. 원장·예약·판매·입금·이력을 대사하고 보존 가능한 복구 정책을 먼저 정한다. |
| `UNVALIDATED` | 현재 snapshot에서 위반은 없지만 DB의 과거 행 검증은 미완료다. 개별 validation 대상으로 검토한다. |
| `VALIDATED` | 설치된 CHECK의 DB 검증이 완료됐고 현재 snapshot에서도 위반이 없다. |

대사 명령의 종료 코드 0은 조회 완료를 뜻한다. 보고된 5개 제약이 모두
`VALIDATED`인지 별도로 판단한다. 권한·잠금·실행 시간 초과 등 SQL 오류는
비정상 종료하며, 부분 보고만으로 전체 대사를 통과 처리하지 않는다.
제약 식의 FALSE만 위반으로 세므로 CHECK가 허용하는 NULL을 위반으로 간주하지
않는다. 이 결과는 allocation 합계·Mutation head·품목 합계·입금/잔액 등
교차 불변식 대사를 대신하지 않는다.

위반 복구와 재대사 후, 보고된 이름을 지정해 제약 하나씩 검증한다.

```bash
PGSERVICE=greenhouse-maintenance psql -XAtq \
  -v constraint=ck_orchid_groups_reserved_quantity \
  -f scripts/data-audit/validate-domain-constraint.sql
```

검증 도구는 공유 inventory의 5개 CHECK만 받는다. 제약 누락·다른 종류·미지정
이름은 실패하며, 기존 데이터 수정이나 제약 재생성은 수행하지 않는다.
이미 검증한 제약은 DDL을 반복하지 않는다. 한 번의 호출은 한 제약의 트랜잭션이며,
검증 결과는 commit 후 다시 조회한다. 앞서 다른 제약을 검증한 결과는 이후 호출의
실패로 취소되지 않으므로 최종 대사 보고에서 전체 상태를 재확인한다.

`VALIDATE CONSTRAINT`는 실제 검증 시점에 다시 행을 검사한다. 대사 이후 변경이
있어도 이전 보고만으로 validation을 생략하지 않는다. 대상 테이블의
`SHARE UPDATE EXCLUSIVE` 잠금은 일반 조회·수량 쓰기와 호환되지만 다른 DDL·일부
유지보수 작업과 경쟁한다. 잠금 대기는 3초, 각 SQL 실행은 5분 상한이다.
데이터 규모에 따라 전체 스캔 비용이 발생하므로 rehearsal 결과로 작업 시간을 잡는다.
실패 시 해당 호출의 validation은 rollback되고 기존 사실과 `NOT VALID` 보호는
유지된다. 수량·예약·금액을 임의로 0으로 만들거나 과거 snapshot을 현재 값으로
덮어쓰지 않는다. 이 운영 검증을 배포 시작 과정의 자동 Flyway migration에 넣지 않는다.

## 8. 난 묶음 관리 맵 성능 기준 측정

맵 리팩터링 전후 기준값은 운영·개발 DB가 아닌 `_map_e2e` 접미사의 전용 DB에서
측정한다.

```bash
cd frontend
npm run e2e:map:install
npm run e2e:map:prepare
npm run e2e:map:baseline
npm run e2e:map:debug
```

`e2e:map:baseline`은 DB 초기화와 seed, 백엔드 jar 실행, Next.js production build,
Playwright 실행을 순서대로 수행한다. 결과는
`frontend/e2e-results/map-performance-before.json`에 저장한다.

`e2e:map:debug`는 기존 `_map_e2e` DB를 재사용하고 migration과 seed를 실행하지
않는다. Chromium headed 모드와 Playwright Inspector를 사용하며, 축소된 반복 횟수로
디버깅한다.

### 원장 대량 처리 측정

Docker와 JDK 21, Python 3가 있는 환경에서 프로젝트 루트의 다음 명령을 실행한다.

```bash
python3 scripts/performance/run-domain-benchmark.py --profile standard
```

실행마다 Testcontainers의 격리 PostgreSQL에 합성 자료를 만들고
`backend/build/domain-benchmark/<실행 ID>/result.json`에 환경·처리 시간·메모리/GC·JDBC·transaction/잠금 관측을 저장한다.
결과 파일을 전달해 분석을 이어갈 수 있다. 먼저 환경 확인만 하려면 `--profile smoke --warmup 0 --samples 1 --heap 1g`를 사용한다.
파생 정산 측정 시나리오는 제거되었으며 현재 원장 대사를 측정한다.
profile별 데이터 크기, 실패/중단 결과와 관측 한계는
[정산·원장 측정 가이드](../backend-audit/12-domain-performance-measurement.md)를 따른다.
이 대량 측정은 기본 CI에 추가하지 않으며 실제 운영 DB 대사·validation을 대체하지 않는다.

## 9. CI 검증

`.github/workflows/verify.yml`에서 프론트 format·API 타입·테스트·lint·build, 기본 백엔드 검사·패키징, PostgreSQL 회귀·벤치마크, API 계약 drift를 별도 job으로 실행한다. 각 검사는 독립 단계로 끝까지 실행하고 결과를 Actions Summary에 표로 기록한다. 기본 백엔드 검사는 Java 포맷과 import 순서, 테스트 비활성화 방지 검사도 포함한다.
PostgreSQL job은 먼저 `docker info`로 실행 환경을 확인하고 Testcontainers가 만든 격리 DB에서
`workE2eTest`와 `workBenchmark -PworkBenchmarkEnforce=true`를 각각 실행한다. 운영 DB 접속 정보는 사용하지 않는다. 백엔드 테스트 보고서와 PostgreSQL 테스트·벤치마크 결과는 성공 여부와 관계없이 artifact로 업로드해 14일간 보관한다.
Docker가 없으면 PostgreSQL 검사는 실패한다. 벤치마크는 결과 의미와 쿼리 상한을 검사하고 시간·할당량은 참고값으로 기록한다.
Java 포맷 기준은 Spotless의 Google Java Format이다. `backend`에서 `./gradlew format`으로 적용하고 `./gradlew spotlessCheck`로 검사한다. CI의 `./gradlew check`에도 이 검사가 포함되며 소스를 자동 수정하지 않는다. Java는 2 spaces, Kotlin Gradle 스크립트는 기존 tab 4를 유지한다. VS Code는 `.vscode/extensions.json`의 Spotless Gradle·Gradle for Java 확장을 설치한 뒤 창을 다시 로드한다. Java 저장 포맷도 같은 Gradle 설정으로 처리하며 Red Hat Java 포맷은 끈다. 최초 전체 Java 포맷 적용은 기능 변경과 분리해 커밋한다.

### V52 과거 경매 출하 전표 이관

배포 전 백업을 보존하고 애플리케이션 writer를 중지한 상태에서 Flyway를 적용한다. V52는 기존 출하·lot를 읽고 누락된 전표·품목만 생성한다. 과거 데이터의 난 묶음 배분·snapshot을 추정하거나 재고 출고를 재실행하지 않는다. source 표의 읽기·쓰기를 migration 동안 잠그고 한 트랜잭션으로 적용하며, 데이터 충돌은 전체 rollback한다. Flyway 재실행은 완료 이관을 중복 실행하지 않는다. 기존 migration의 checksum을 변경하거나 이관 실패를 수동 성공 처리하지 않는다.

2026-10-09에 `temp/green-house_20261006_030001.dump.gz`(SHA-256 `06be57e9a0998738866b040bcdb28d7d4a9acd7fe655688069d37c595622eac5`)를 별도 PostgreSQL 18.4에 복원해 V34→V51→V52를 검증했다. V52로 경매 전표 64건·품목 566건(출하 수량 39,016개)이 생성됐고 전표 없는 출하 및 품목 없는 lot는 각각 0건이 됐다. V51 기준 기존 일반 전표 148건과 전표·품목·Flyway 이력을 제외한 public 54개 테이블 행(난 묶음 320개 포함)은 V52 후 동일했다. 기존 source 표의 상태값도 변경하지 않았다. 실제 적용할 백업에서도 동일한 대사와 재고·원장 불변을 확인한다. 이 복원 검증은 운영 DB 적용을 뜻하지 않는다.

복원본 HTTP 목록에서 64건을 조회하고 각 전표의 상세·인쇄 응답, 빈 재고 배분과 사용 가능한 재고 변경 action 부재를 확인했다. PostgreSQL 회귀는 기존 전표·품목 보존, 완료 migration 재실행, 수량·lot 연결·거래처·번호 충돌의 rollback, 이관 전표의 조회·출력 및 수정·상태 변경 차단을 검증한다. 백엔드 전체 779개·PostgreSQL 전체 회귀 824개(실패·오류·skip 0), 프론트 `npm run check`와 OpenAPI·생성 타입 갱신이 완료됐다.
