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

Linux/macOS에서는 개발 서버를 한 번에 실행할 수 있다.

```bash
./scripts/dev-start.sh
```

프론트엔드 렌더링 성능처럼 production build 기준으로 확인해야 할 때는 다음 옵션을
사용한다. 이 모드는 `npm run build`가 성공한 후 `npm run start`로 프론트엔드를
실행하며, 기존 3000 포트를 재사용하지 않는다.

```bash
./scripts/dev-start.sh --frontend-production
```

## 2. 환경 변수

### Backend

```text
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
인스턴스는 startup guard에서 기동이 거부된다. guard 비활성화는 전용 점검·cutover
명령 내부에서만 사용한다. Legacy 모드는 제거됐으며 기본 writer version은 `2.0.0`이다. 원장 없는 난 묶음이나 PREPARING coverage가 남아 있으면 먼저 복구 CLI로 전환을 완료한다.

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
- V21은 난 묶음 Mutation, complete state-chain Entry, 관계, coverage와 Work·Sales·Lineage 연결 필드를 최종 형태로 생성한다. 기존 난 묶음의 chain은 자동 생성하지 않으며 manifest importer가 적재한다.
- V22는 `ACTIVE` coverage에서 Mutation context 없는 난 묶음 INSERT·UPDATE와 모든 DELETE를 차단하고, 커밋 시 변경 revision에 대응하는 `CREATE` 또는 `CHANGE` Entry를 검증한다. `PREPARING`에서는 차단하지 않는다.
- V23은 `UNMAPPED`으로 남은 기존 난 묶음 중 의미가 명확한 스마트 따옴표 3·4인치 값만 표준 화분 코드로 보정한다. 다른 `UNMAPPED` 값은 자동 변환하지 않는다.
- V24는 품종·자재 코드용 sequence를 만든다. 기존 코드와 ID는 바꾸지 않고 기존 ID·숫자 코드의 최댓값 다음에서 발급을 시작한다. 삭제·실패한 트랜잭션으로 번호가 비어도 재사용하지 않는다.

V24 전환 시 기존 코드 발급 방식과 새 방식이 동시에 쓰이지 않도록 이전 백엔드 인스턴스의 쓰기를 중지한 후 migration과 새 버전 기동을 진행한다. 신규 코드 생성 후 구버전으로 단순 rollback하지 않는다. 데이터 수입 등으로 코드를 직접 추가하는 운영 변경은 쓰기를 중지하고 코드 sequence가 추가된 숫자 코드보다 큰지 함께 확인한다.

V21~V23은 아직 운영에 배포되지 않은 기존 V21~V28 실험 migration을 V20 기준으로
통합한 이력이다. 지원하는 업그레이드 경로는 `V20 → V21~V23`이다. 통합 전
V21~V28을 적용했던 개발·rehearsal DB는 checksum repair나 수동 스키마 변경을 하지
않고 V20 운영 백업으로 다시 초기화한다. 이 통합본을 운영에 적용한 뒤에는 파일을
수정하거나 번호를 다시 사용하지 않는다.

운영 custom dump로 로컬 개발 DB를 초기화할 때는 다음 스크립트를 사용한다. 백업을 생략하면 `temp/`의 최신 `*.dump.gz` 또는 `*.dump`를 선택한다. 스크립트는 로컬 DB만 허용하며 기존 백엔드를 종료하고, 복원 후 HTTP 서버 없이 Flyway 적용·Hibernate 스키마 검증·작업 V2 무결성 검사를 수행한다. V20 백업처럼 상태 원장이 없거나 PREPARING인 경우 복원 후 종료 코드 2로 전환 필요를 알린다. 아래 PLAN/IMPORT/VERIFY/ACTIVE 절차를 완료한 뒤 업무 서버를 시작한다.

```bash
# 확인 문구 입력 후 실행
./scripts/reset-dev-db.sh

# 백업 지정
./scripts/reset-dev-db.sh temp/green-house_20260717_120001.dump.gz

# CI 또는 반복 개발 작업
./scripts/reset-dev-db.sh --yes
```

### 난 묶음 complete state-chain artifact 확인

complete state-chain rehearsal은 `ACTIVE` 전 최신 운영 백업에서 수행한다. 이미
`ACTIVE`로 사용한 rehearsal DB는 재사용하지 않는다. profiling SQL과 profiler는
read-only이며 `orchid_groups`나 source 이력을 변경하지 않는다.

```bash
PGPASSWORD=greenhouse_rehearsal_test psql \
  -h 127.0.0.1 -p 5432 \
  -U greenhouse_rehearsal_test -d greenhouse_rehearsal \
  -f scripts/data-audit/orchid-history-migration-profile.sql
```

최소 확인 항목은 다음과 같다.

- `EXPECTED_WORK_EFFECT_LINK_MISMATCHES`의 두 결과가 모두 0
- `LINEAGE_MATCHABILITY`의 `no_match=0`, `ambiguous=0`
- `WORK_EFFECT_SOURCE_IDENTITIES`의 누락·중복 identity가 0
- `SYNTHETIC_ORIGIN_QUANTITY_FEASIBILITY`의 `invalid_origins=0`
- `SOURCE_REFERENCE_INTEGRITY`의 모든 결과가 0
- 생성 근거가 있는 그룹의 quantity replay 불일치를 `ATTESTED` 보정 또는 GAP으로 분류
- 시스템 운영 전 참고 전표는 `LEGACY_REFERENCE_ONLY`로 분류하고 Orchid Mutation에서 제외

2026-08-26 백업의 초기 운영 지표에서는 현존 난 묶음 269개 중 직접 생성 근거가 있는
그룹이 29개였고 240개는 최초 관측 근거가 필요했다. 전체 백업 구간을 재구성한 최종
manifest는 최초 신뢰 백업의 `BASELINE` 114개와 이후 `CREATE` 161개 chain으로 확정됐다.
상태 변경 Work 효과 42건과 link 76개, Lineage 16개는 모두 결정적으로 변환 가능했다.
그룹 234의 `+12` 차이는 운영자가 실제 수량 보정으로 확인했으므로
`ATTESTED CORRECTION` 이관 대상이다. 기존 판매 전표 148건·품목 868건은 모두 시스템 운영 전
자료를 바탕으로 등록한 참고 정보이므로 `LEGACY_REFERENCE_ONLY`로 분류하고 Orchid
Mutation 이관에서 제외한다. 현재 백업에서 미분류 수량 gap은 0건이다.
이 결과의 해석과 이관 모델은
`docs/adr/ADR-002-orchid-group-historical-migration.md`를 따른다.

### 난 묶음 complete state-chain migration rehearsal

profiler가 만든 schema 1 manifest를 현재 Engine 계약인 schema 2로 정규화한다. 정규화는
원인을 새로 추론하지 않으며 revision kind, 필드명과 canonical 값만 변환한다.

```bash
python3 scripts/data-audit/normalize_orchid_state_chain_manifest.py \
  --input temp/migration/migration_manifest.json \
  --output scripts/data-audit/orchid-state-chain-migration-manifest.json

sha256sum temp/migration/migration_manifest.json \
  scripts/data-audit/orchid-state-chain-migration-manifest.json
```

최종 manifest는 `migration_ready=true`, `blocking_issues=[]`여야 한다. 더 최신 백업의
현재 ID 집합이나 마지막 snapshot이 달라지면 importer가 거부하므로 해당 백업으로
profiler artifact를 다시 만들고 검토해야 한다.

고정 cutover key로 read-only 검증을 먼저 실행한다.

```bash
cd backend
CUTOVER_KEY='<CUTOVER_KEY>'
CUTOVER_BUSINESS_DATE='<CUTOVER_BUSINESS_DATE>'
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidStateChainMigrate --args="\
  --cutover-key=${CUTOVER_KEY} \
  --manifest=../scripts/data-audit/orchid-state-chain-migration-manifest.json \
  --effective-business-date=${CUTOVER_BUSINESS_DATE} \
  --minimum-writer-version=2.0.0 \
  --apply=false \
  --confirmation=PLAN:${CUTOVER_KEY}"
```

`PRE_BASELINE`, `ready=true`, `issues=[]`이고 manifest의 Mutation·Entry·현존·삭제 건수가
승인값과 같을 때만 적재한다.

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidStateChainMigrate --args="\
  --cutover-key=${CUTOVER_KEY} \
  --manifest=../scripts/data-audit/orchid-state-chain-migration-manifest.json \
  --effective-business-date=${CUTOVER_BUSINESS_DATE} \
  --minimum-writer-version=2.0.0 \
  --apply=true \
  --confirmation=IMPORT:${CUTOVER_KEY}"
```

같은 import를 다시 실행해 `importedMutationCount=0`과 전체
`replayedMutationCount`를 확인한다. 2026-08-28 rehearsal 결과는 Mutation 314,
Entry 348, 현존 그룹 269, 삭제 tombstone 그룹 6, Work/Lineage 연결 42/16이며
재실행 신규 0·replay 314였다. 같은 복원 DB의 VERIFY와 ACTIVE 전환도 통과했고,
최종 reconciliation은 `ACTIVE`, `ready=true`, `issues=[]`였다.

### 난 묶음 ledger 복원 DB rehearsal

Mutation Engine 전환 전에는 운영 백업을 격리된 PostgreSQL에 복원한 뒤 read-only
대사를 실행한다. 운영 primary DB에는 실행하지 않는다.

```bash
cd backend
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerReconcile
```

명령은 Flyway를 비활성화하고 Hibernate schema validation과 read-only DB connection을
강제한다. 시작 시 정산 재구축도 실행하지 않는다. 다음 종료 코드를 사용한다.

- `0`: 현재 단계의 모든 대사 통과
- `1`: 연결·schema·실행 오류
- `2`: JSON 보고서에 정합성 오류가 존재함

보고서는 현재 난 묶음 invariant와 배치 범위, Collection 참조, Work 진행·효과 연결,
작성중 Sales allocation과 예약 수량, ledger revision·snapshot 연속성, baseline 행 수와
fingerprint를 포함한다. `PRE_BASELINE`, `BASELINE_PREPARING`, `ACTIVE` 단계별로 같은
명령을 반복해 결과와 소요 시간을 보관한다.

이 명령은 state-chain 생성, coverage 활성화, 데이터 보정을 수행하지 않는다. 오류가
있으면 대상 ID와 코드로 원인을 보정한 뒤 새 복원본에서 rehearsal을 다시 시작한다.

complete state-chain import 뒤 결과는 `BASELINE_PREPARING`, `ready=true`, `issues=[]`여야
한다. `orchidLedgerCutover --activate=false --confirmation=VERIFY:{cutoverKey}`는 데이터를
만들지 않고 동일 cutover의 import 완료와 대사 결과만 재확인한다. 운영 primary에서는
import 전부터 애플리케이션 쓰기를 중단하고 구버전 인스턴스를 모두 종료해야 한다.
`PREPARING`에는 DB fence가 없으며 import 시작 후 `LEGACY` backend는 startup guard가
기동을 거부한다. ACTIVE 이후에는 LEGACY writer로 fallback하지 않고 roll-forward한다.

현재 구현은 Farm·Work·Sales·Inbound의 알려진 난 묶음 write path를 하나의
`LEGACY|ENGINE` 스위치로 라우팅한다. `ACTIVE` 전에는 모든 운영 writer가 inventory에
식별되어 Engine을 지원하고, 스위치 밖의 미확인 직접 writer가 없어야 한다. 이는
legacy 호환 코드를 먼저 삭제한다는 뜻은 아니다. 호환 분기는 전환 안정화 기간에
남겨 둘 수 있지만 모든 실행 인스턴스는 `ENGINE`으로 고정하고 DB fence로 실행을
차단한다. 안정화 후 routing flag와 legacy 직접 writer를 별도 릴리스에서 제거한다.

운영 DB의 `--activate=true` 실행 전에는 최신 운영 백업과 배포 후보 코드로 state-chain import,
ENGINE 전체 회귀·smoke 및 아래 `ACTIVE` 전환 rehearsal까지 통과해야 한다.

### ENGINE writer 수동 smoke test

운영 primary가 아닌 복원 또는 별도 테스트 PostgreSQL에서만 실행한다. 기존 데이터가
있으면 위 `orchidStateChainMigrate` 명령으로 complete chain을 먼저 적재한다.
그 뒤 애플리케이션을 다음처럼 시작한다.

```bash
cd backend
ORCHID_LEDGER_WRITER_VERSION=2.0.0 \
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew bootRun
```

화면에서 다음 순서로 확인한다.

1. 난 묶음 단건·다중 생성, 상세 수정, 이동과 새로 만든 묶음의 생성 취소
2. 즉시 배치 입고와 유리병 모종 포트 작업
3. 폐기, 자리 이동, 분갈이, 분주, 합식, 다중 생성·취소와 완료 결과 보정
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
  "stage": "BASELINE_PREPARING",
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
`ACTIVE` 전환으로 문제를 덮지 않는다. 현재 Engine 전용 HTTP 서버의 smoke test는 다음 ACTIVE 전환을 마친 폐기 가능한
DB 사본에서 수행한다. PREPARING에서는 유지보수 CLI로 적재·대사만 진행한다.

#### ACTIVE 전환 rehearsal

ENGINE 백엔드를 종료해 쓰기를 막고, 구버전 인스턴스와 실행 중 transaction이 없는지
확인한다. state-chain import에 사용한 cutover key와 배포 후보 writer version으로 활성화한다.

```bash
cd backend
CUTOVER_KEY='00000000-0000-0000-0000-000000000000'
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerCutover --args="\
  --cutover-key=${CUTOVER_KEY} \
  --effective-business-date=2026-08-20 \
  --minimum-writer-version=2.0.0 \
  --current-writer-version=2.0.0 \
  --activate=true \
  --confirmation=ACTIVATE:${CUTOVER_KEY}"
```

그 뒤 최소 version 이상의
`ORCHID_LEDGER_WRITER_VERSION`으로 백엔드를 시작한다. 앞의 smoke test, reconciliation,
Work/Sales 연결 SQL을 다시 수행하며 성공 기준은 다음과 같다.

- reconciliation의 `stage=ACTIVE`, `ready=true`, `issues=[]`
- Engine 시작 성공과 원장 미완성·최소 version 미만 인스턴스의 startup guard 실패
- Mutation context 없는 `orchid_groups` 직접 INSERT·UPDATE와 모든 DELETE의 DB fence 차단
- 전환 후 생성·수정과 Work·Sales 효과의 새 revision 및 Mutation 연결 정상

이 rehearsal DB는 `ACTIVE` 이후 LEGACY 테스트에 재사용하지 않는다. 실패하면 DB를
보존해 원인을 조사하고 새 복원본에서 전체 절차를 다시 수행한다. 운영 primary는
동일 절차의 결과와 소요 시간이 승인된 뒤에만 전환한다.

### 운영 primary cutover runbook

이 절차는 위 complete state-chain migration, ENGINE smoke test와 `ACTIVE` rehearsal을 최신 운영
백업에서 모두 통과한 뒤 한 번만 실행한다. 모든 명령은 최종 배포 이미지와 같은 commit의
checkout에서 실행하고 JSON 결과, SQL 결과, 백업 fingerprint와 시작·종료 시각을 보관한다.

사전에 다음 값을 확정한다.

```bash
NAMESPACE=green-house
DEPLOYMENT=green-house-backend
CUTOVER_KEY='<APPROVED_CUTOVER_UUID>'
EFFECTIVE_BUSINESS_DATE='<YYYY-MM-DD>'
WRITER_VERSION='2.0.0'
RELEASE_IMAGE='ghcr.io/styughjvbn/green-house-backend:sha-<PUBLISHED_COMMIT>'
export DATABASE_URL='jdbc:postgresql://<PRODUCTION_DB_HOST>:5432/greenhouse'
export DATABASE_USERNAME='<PRODUCTION_DB_WRITER>'
read -rs DATABASE_PASSWORD
export DATABASE_PASSWORD
```

`RELEASE_IMAGE`는 GHCR에 실제 발행된 태그여야 한다. 현재 repository manifest의 고정
태그를 그대로 신뢰하지 않고 다음 결과와 `k8s/base/backend-deployment.yaml`의 image가
일치하는지 확인한다. GHCR workflow는 `main` push 또는 수동 실행에서만 이미지를 발행한다.

```bash
docker manifest inspect "${RELEASE_IMAGE}" >/dev/null
kubectl -n "${NAMESPACE}" get deployment "${DEPLOYMENT}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n "${NAMESPACE}" get configmap green-house-config \
  -o jsonpath='{.data.ORCHID_LEDGER_WRITER_VERSION}{"\n"}'
```

새 이미지는 준비만 하고 아직 업무 서버로 기동하지 않는다. Engine 전용 서버는
원장 없는 난 묶음이나 PREPARING coverage가 있으면 기동을 거부한다. 기존 서버를
중지한 뒤 백업·스키마 적용·이력 적재·ACTIVE를 완료하고 새 이미지를 배포한다.

아래 명령은 운영 DB에 접속 가능한 관리 호스트에서 실행한다. JDBC URL과 `PGHOST`,
`PGPORT`, `PGDATABASE`, `PGUSER`는 반드시 같은 운영 DB를 가리켜야 한다. 예시의
placeholder를 실제 값으로 바꾸고 명령 실패 시 다음 단계로 진행하지 않는다.

유지보수 시작을 공지하고 backend를 완전히 종료한다. state-chain import 이후에는 Legacy를 다시
기동하지 않는다.

```bash
kubectl -n "${NAMESPACE}" scale deployment/"${DEPLOYMENT}" --replicas=0
kubectl -n "${NAMESPACE}" wait --for=delete pod \
  -l app.kubernetes.io/name=green-house,app.kubernetes.io/component=backend \
  --timeout=180s
```

DB에서 다른 application transaction이 없는지 확인한다. 결과는 비어 있어야 한다.

```sql
SELECT pid, usename, state, xact_start, query
FROM pg_stat_activity
WHERE datname = current_database()
  AND pid <> pg_backend_pid()
  AND xact_start IS NOT NULL;
```

이 상태에서 전환 직전의 DB 전체를 custom dump로 보존한다. PostgreSQL 18을 읽을 수
있는 클라이언트를 사용한다. 다음 명령은 저장소 루트에서 실행한다.

```bash
export PGHOST='<PRODUCTION_DB_HOST>' PGPORT=5432 PGDATABASE=greenhouse
export PGUSER="${DATABASE_USERNAME}"
export PGPASSWORD="${DATABASE_PASSWORD}"
BACKUP_DIR="$(pwd)/temp/cutover-production"
mkdir -p "${BACKUP_DIR}"
FINAL_BACKUP="${BACKUP_DIR}/before-engine-$(date -u +%Y%m%dT%H%M%SZ).dump"
pg_dump --format=custom --file="${FINAL_BACKUP}"
pg_restore --list "${FINAL_BACKUP}" > "${FINAL_BACKUP}.list"
sha256sum "${FINAL_BACKUP}" > "${FINAL_BACKUP}.sha256"
```

명령의 종료 코드가 모두 0이고 dump가 정상적으로 읽혀야 한다. 이 파일은 전환 실패
시점의 복구 지점이므로 운영 DB와 다른 저장 위치에도 보존한다. 파일 checksum은
백업 파일 무결성 확인용이며, 별도로 생성한 두 dump의 논리 데이터 동일성을 뜻하지 않는다.

2026-08-26 이후 업무 데이터 변경이 없음을 확인했다면 검증된 기존 manifest를 후보로
사용할 수 있다. 아래 PLAN에서 현재 그룹 ID·최종 상태와 Work/Lineage 근거가 일치하고
전체 대사를 통과해야 재사용한다. 불일치가 있으면 적재하지 않고 최종 백업을 기준으로
profiler artifact를 재생성·검토한다. normalizer는 과거 이력을 새로 추론하는 도구가 아니다.

이관 CLI는 Flyway를 자동 실행하지 않는다. 먼저 같은 release checkout의 백엔드를
**HTTP 없이** 실행해 미적용 스키마를 반영한다. V20이면 V21~V26이 적용되며, 이미
적용한 버전은 건너뛴다. 업무 서버의 startup guard는 계속 활성화하고, 아래 유지보수
프로세스에서만 비활성화한다.

```bash
(cd backend && ./gradlew bootRun --no-daemon --args='--spring.main.web-application-type=none --app.orchid-ledger.startup-guard-enabled=false --app.settlement.rebuild-on-startup=false')
(cd backend && ./gradlew orchidLedgerReconcile --args='--debug=false --logging.level.org.hibernate.SQL=OFF')
```

스키마 검증이 성공하고 대사 결과가 `stage=PRE_BASELINE`, `ready=true`, `issues=[]`여야
한다. 이미 PREPARING 또는 ACTIVE라면 신규 전환으로 취급하지 말고 기존 cutover key와
manifest로 재개·검증한다.

같은 release checkout에서 plan, import, 재실행과 `ACTIVE`를 순서대로 수행한다. 전체
과정에서 backend replicas는 계속 0이어야 한다.

```bash
cd backend
python3 ../scripts/data-audit/normalize_orchid_state_chain_manifest.py \
  --input ../temp/migration/migration_manifest.json \
  --output ../scripts/data-audit/orchid-state-chain-migration-manifest.json

./gradlew orchidStateChainMigrate --args="\
  --cutover-key=${CUTOVER_KEY} \
  --manifest=../scripts/data-audit/orchid-state-chain-migration-manifest.json \
  --effective-business-date=${EFFECTIVE_BUSINESS_DATE} \
  --minimum-writer-version=${WRITER_VERSION} \
  --apply=false \
  --confirmation=PLAN:${CUTOVER_KEY}"

./gradlew orchidStateChainMigrate --args="\
  --cutover-key=${CUTOVER_KEY} \
  --manifest=../scripts/data-audit/orchid-state-chain-migration-manifest.json \
  --effective-business-date=${EFFECTIVE_BUSINESS_DATE} \
  --minimum-writer-version=${WRITER_VERSION} \
  --apply=true \
  --confirmation=IMPORT:${CUTOVER_KEY}"

# 위 import 명령을 한 번 더 실행해 신규 0·전체 replay를 확인한다.
./gradlew orchidLedgerCutover --args="\
  --cutover-key=${CUTOVER_KEY} \
  --effective-business-date=${EFFECTIVE_BUSINESS_DATE} \
  --minimum-writer-version=${WRITER_VERSION} \
  --current-writer-version=${WRITER_VERSION} \
  --activate=false \
  --confirmation=VERIFY:${CUTOVER_KEY}"

./gradlew orchidLedgerCutover --args="\
  --cutover-key=${CUTOVER_KEY} \
  --effective-business-date=${EFFECTIVE_BUSINESS_DATE} \
  --minimum-writer-version=${WRITER_VERSION} \
  --current-writer-version=${WRITER_VERSION} \
  --activate=true \
  --confirmation=ACTIVATE:${CUTOVER_KEY}"
```

import와 VERIFY 결과는 `stage=BASELINE_PREPARING`, 활성화 결과는 `stage=ACTIVE`이고 모두
`ready=true`, `issues=[]`여야 한다. 0826과 동일한 데이터라면 Mutation 314, Entry 348,
현존 그룹 269, 삭제 tombstone 그룹 6, 재실행 신규 0·replay 314가 검증 기준이다.

배포 직전에 `orchidLedgerReconcile`을 한 번 더 실행해 `stage=ACTIVE`, `ready=true`,
`issues=[]`를 확인한다. 그 뒤에만 `k8s/base/configmap.yaml`의 writer version을 `2.0.0`으로,
backend deployment를 검증한 `RELEASE_IMAGE`로 갱신하여 적용한다. writer mode 설정은 없다.
ConfigMap만 변경하면 기존 Pod 환경 변수는 갱신되지 않으므로 새 Pod 기동을 반드시
확인한다. base manifest의 `replicas: 1` 적용이 backend를 다시 기동한다.

```bash
cd ..
kubectl apply -k k8s/base
kubectl -n "${NAMESPACE}" rollout status deployment/"${DEPLOYMENT}" --timeout=300s
kubectl -n "${NAMESPACE}" get deployment "${DEPLOYMENT}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n "${NAMESPACE}" get configmap green-house-config \
  -o jsonpath='{.data.ORCHID_LEDGER_WRITER_VERSION}{"\n"}'
```

기동 후 `orchidLedgerReconcile`의 `stage=ACTIVE`, `ready=true`, `issues=[]`, Work/Sales
연결 SQL 빈 결과와 ENGINE smoke test를 확인한 뒤 유지보수를 종료한다.

`ACTIVE` 전 실패하면 backend를 0으로 유지하고 동일 cutover key로 resume하거나 최종
백업 전체를 복원한다. 시작된 `PREPARING` coverage를 수동 삭제하거나 Legacy backend를
재기동하지 않는다. `ACTIVE` 후에는 `LEGACY` flag로 되돌리지 않는다. 일반 장애는
roll-forward하고, 복구가 불가능하면 전환 직전 DB 전체 백업과 그에 맞는 Legacy
application/config를 함께 복원한다.

### 데모 환경

데모 환경은 `k8s/overlays/demo`를 사용하며 운영과 동일한 이미지 SHA를 배포한다.
로그인 우회, 데모 인증 주체, 요청 제한은 `DEMO_MODE=true`에서만 활성화된다.

운영 PC의 DB role 생성, Secret 적용, 초기화와 모니터링 절차는
`docs/features/demo-operations.md`를 따른다. 비식별화가 끝나지 않은 운영 백업을 데모
DB에 직접 복구하지 않는다.

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

## 9. CI 검증

`.github/workflows/verify.yml`에서 기본 백엔드 검사·패키징(`./gradlew check bootJar --no-daemon`)과 PostgreSQL 회귀·벤치마크를 별도 job으로 실행한다. 기본 검사는 Java 포맷과 import 순서, 테스트 비활성화 방지 검사도 포함한다.
PostgreSQL job은 먼저 `docker info`로 실행 환경을 확인하고 Testcontainers가 만든 격리 DB에서
`./gradlew workE2eTest workBenchmark -PworkBenchmarkEnforce=true --no-daemon`을 실행한다. 운영 DB 접속 정보는 사용하지 않는다.
Docker가 없으면 PostgreSQL 검사는 실패한다. 벤치마크는 결과 의미와 쿼리 상한을 검사하고 시간·할당량은 참고값으로 기록한다.
포맷 수정은 `backend`에서 `./gradlew format`으로 실행하며 CI는 소스를 자동 수정하지 않는다.
