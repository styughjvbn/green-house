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
ORCHID_LEDGER_WRITER_MODE
ORCHID_LEDGER_WRITER_VERSION
ORCHID_LEDGER_STARTUP_GUARD_ENABLED
```

운영에서 `JPA_DDL_AUTO`는 `validate`로 두고 스키마 변경은 Flyway 마이그레이션으로만 적용한다.
`HIBERNATE_JDBC_BATCH_SIZE`의 기본값은 `50`이며 PostgreSQL JDBC batch 재작성은 기본 활성화한다.
난 묶음 ledger가 `ACTIVE`인 DB에는 `ORCHID_LEDGER_WRITER_MODE=ENGINE`과 coverage의
최소 버전 이상인 `ORCHID_LEDGER_WRITER_VERSION`을 설정한다. 조건을 만족하지 못한
인스턴스는 startup guard에서 기동이 거부된다. guard 비활성화는 전용 점검·cutover
명령 내부에서만 사용한다.

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
- V21~V22는 난 묶음 Mutation ledger와 coverage를 추가하고 동시에 하나의 `PREPARING` 또는 `ACTIVE` coverage만 존재하도록 제한한다. 기존 난 묶음의 baseline은 자동 생성하지 않는다.
- V23은 `ACTIVE` coverage에서 Mutation context 없는 난 묶음 INSERT·UPDATE와 모든 DELETE를 차단하고, 커밋 시 변경 revision에 대응하는 MutationEntry를 검증한다. `PREPARING`에서는 아직 차단하지 않는다.
- V24는 `UNMAPPED`으로 남은 기존 난 묶음 중 의미가 명확한 스마트 따옴표 3·4인치 값만 표준 화분 코드로 보정한다. 다른 `UNMAPPED` 값은 자동 변환하지 않는다.

운영 custom dump로 로컬 개발 DB를 초기화할 때는 다음 스크립트를 사용한다. 백업을 생략하면 `temp/`의 최신 `*.dump.gz` 또는 `*.dump`를 선택한다. 스크립트는 로컬 DB만 허용하며 기존 백엔드를 종료하고, 복원 후 Flyway 적용·Hibernate 스키마 검증·작업 V2 무결성 검사를 수행한다.

```bash
# 확인 문구 입력 후 실행
./scripts/reset-dev-db.sh

# 백업 지정
./scripts/reset-dev-db.sh temp/green-house_20260717_120001.dump.gz

# CI 또는 반복 개발 작업
./scripts/reset-dev-db.sh --yes
```

### 난 묶음 과거 이력 migration profiling

완전한 과거 이력 이관 설계와 rehearsal은 baseline을 만들기 전에 수행한다. 이미
`ACTIVE`로 사용한 rehearsal DB는 재사용하지 않고 최신 운영 백업으로 다시
초기화한다. profiling SQL은 read-only이며 `orchid_groups`나 source 이력을 변경하지
않는다.

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

2026-08-21 백업 profiling에서는 난 묶음 269개 중 29개만 생성 근거가 있었고 240개는
합성 ORIGIN이 필요했다. 상태 변경 Work 효과 42건과 link 76개, Lineage 16개는 모두
결정적으로 변환 가능했고 합성 origin 수량 240개도 모두 0 이상으로 역산됐다.
그룹 234의 `+12` 차이는 운영자가 실제 수량 보정으로 확인했으므로
`ATTESTED CORRECTION` 이관 대상이다. 기존 판매 전표 148건·품목 868건은 모두 시스템 운영 전
자료를 바탕으로 등록한 참고 정보이므로 `LEGACY_REFERENCE_ONLY`로 분류하고 Orchid
Mutation 이관에서 제외한다. 현재 백업에서 미분류 수량 gap은 0건이다.
이 결과의 해석과 이관 모델은
`docs/adr/ADR-002-orchid-group-historical-migration.md`를 따른다.

### 난 묶음 과거 이력 migration rehearsal

`reset-dev-db.sh`로 복원하면 현재 코드의 Flyway V25와 Hibernate validation까지
적용된다. 다음 operator command는 반드시 `PRE_BASELINE` 복원 DB에서 실행한다.
`source-cutoff`은 파일 수정 시각이 아니라 백업 생성 완료 시각의 UTC 값이다.

먼저 백업과 manifest fingerprint, 고정 run key를 준비한다.

```bash
sha256sum temp/green-house_20260821_030001.dump.gz
uuidgen
```

첫 실행은 plan만 생성한다. `apply=false`도 재현 가능한 plan과 현재 상태 fingerprint를
DB의 `DRY_RUN` run으로 기록하지만 Mutation·Evidence와 원본 link는 만들지 않는다.

```bash
cd backend
RUN_KEY='<RUN_KEY>'
SOURCE_CUTOFF='<BACKUP_COMPLETED_AT_UTC>'
BACKUP_SHA256='<BACKUP_SHA256>'
CUTOVER_BUSINESS_DATE='<CUTOVER_BUSINESS_DATE>'
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidHistoryMigrate --args="--run-key=${RUN_KEY} --source-cutoff=${SOURCE_CUTOFF} --backup-fingerprint=${BACKUP_SHA256} --manifest=../scripts/data-audit/orchid-history-migration-manifest.json --effective-business-date=${CUTOVER_BUSINESS_DATE} --apply=false --confirmation=PLAN:${RUN_KEY}"
```

plan의 source count가 profiling SQL과 같고 다음 gate를 만족할 때만 같은 run key로
적재한다. 더 최신 백업에서 판매 참고자료나 운영자 확인 보정이 달라졌다면 먼저
manifest와 ADR을 승인된 사실에 맞게 갱신한다.

- `UNCLASSIFIED_GAPS=0`
- Work·Audit·ORIGIN·ATTESTED 건수가 profiling 결과와 일치
- `LEGACY_REFERENCE_SALES_*`가 profiling의 참고 판매 자료 건수와 일치
- operator가 Sales allocation·inventory movement·난 묶음 snapshot 0건을 실제 DB에서 확인
- `MUTATIONS`와 `EVIDENCE`가 예상 건수와 일치

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidHistoryMigrate --args="--run-key=${RUN_KEY} --source-cutoff=${SOURCE_CUTOFF} --backup-fingerprint=${BACKUP_SHA256} --manifest=../scripts/data-audit/orchid-history-migration-manifest.json --effective-business-date=${CUTOVER_BUSINESS_DATE} --apply=true --confirmation=IMPORT:${RUN_KEY}"
```

성공 기준은 `verification.ready=true`, 미연결 Work·Lineage와 entry 없는 Mutation이
모두 0이고 현재 상태 fingerprint가 plan 때와 같은 것이다. 같은 명령을 다시 실행해
`importedMutations=0`, 모든 Mutation이 `replayedMutations`로 반환되는지도 확인한다.
마지막으로 `orchidLedgerReconcile`의 `PRE_BASELINE`, `ready=true`, `issues=[]`를 확인한
후에만 baseline rehearsal로 진행한다.

2026-08-21 백업 rehearsal 결과는 Mutation 287건, Evidence 321건, Work 연결 42건,
Lineage 연결 16건이었다. 재실행은 신규 0건·replay 287건이었고 현재 상태 fingerprint는
이관 전후 동일했다.

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

이 명령은 baseline 생성, coverage 활성화, 데이터 보정을 수행하지 않는다. 오류가
있으면 대상 ID와 코드로 원인을 보정한 뒤 새 복원본에서 rehearsal을 다시 시작한다.

baseline 쓰기 rehearsal은 DB writer 계정과 고정한 cutover key로 실행한다. 500개 ID
단위의 결정적 batch key를 사용하므로 동일 명령을 재실행하면 완료 batch는 기존
결과를 반환하고 나머지를 이어서 적재한다. 재개 시에는 `state_revision IS NULL`인
그룹만 baseline 대상으로 선택한다. 이미 baseline된 그룹과 ENGINE smoke test에서
CREATE Entry·revision 1로 생성된 그룹은 다시 baseline하지 않는다.

```bash
cd backend
CUTOVER_KEY='00000000-0000-0000-0000-000000000000'
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerCutover --args="\
  --cutover-key=${CUTOVER_KEY} \
  --effective-business-date=2026-08-20 \
  --minimum-writer-version=1.0.0 \
  --current-writer-version=1.0.0 \
  --activate=false \
  --confirmation=BASELINE:${CUTOVER_KEY}"
```

`--activate=true`는 애플리케이션 쓰기를 중단하고 구버전 인스턴스를 모두 종료한
상태에서만 사용한다. 명령은 난 묶음 테이블을 잠근 뒤 최종 대사를 다시 수행하며,
확인 문구도 `ACTIVATE:{cutoverKey}`로 바뀐다. ACTIVE 이후에는 DB fence를 해제하거나
LEGACY writer로 fallback하지 않고 roll-forward한다.

현재 구현은 Farm·Work·Sales·Inbound의 알려진 난 묶음 write path를 하나의
`LEGACY|ENGINE` 스위치로 라우팅한다. `ACTIVE` 전에는 모든 운영 writer가 inventory에
식별되어 Engine을 지원하고, 스위치 밖의 미확인 직접 writer가 없어야 한다. 이는
legacy 호환 코드를 먼저 삭제한다는 뜻은 아니다. 호환 분기는 전환 안정화 기간에
남겨 둘 수 있지만 모든 실행 인스턴스는 `ENGINE`으로 고정하고 DB fence로 실행을
차단한다. 안정화 후 routing flag와 legacy 직접 writer를 별도 릴리스에서 제거한다.

운영 DB의 `--activate=true` 실행 전에는 최신 운영 백업과 배포 후보 코드로 baseline,
ENGINE 전체 회귀·smoke 및 아래 `ACTIVE` 전환 rehearsal까지 통과해야 한다.

### ENGINE writer 수동 smoke test

운영 primary가 아닌 복원 또는 별도 테스트 PostgreSQL에서만 실행한다. 기존 데이터가
있으면 위 `orchidLedgerCutover --activate=false` 명령으로 baseline을 먼저 만든다.
그 뒤 애플리케이션을 다음처럼 시작한다.

```bash
cd backend
ORCHID_LEDGER_WRITER_MODE=ENGINE \
ORCHID_LEDGER_WRITER_VERSION=1.0.0 \
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

- cutover 당시 존재한 그룹은 해당 coverage의 `BASELINE` Entry, 이후 생성된 그룹은
  `CREATE` Entry로 revision chain을 시작한다.
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

`baselineGroupCount`는 운영 백업에서 baseline으로 시작한 그룹 수이므로 smoke test 중
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
원인을 확인하고 해당 테스트 DB를 보존한다. baseline 재실행, ledger 직접 수정,
`ACTIVE` 전환으로 문제를 덮지 않는다. smoke test 중에도 coverage는 `PREPARING`으로
유지한다. 이 단계가 통과하면 운영 primary가 아닌 폐기 가능한 DB 사본에서 실제
전환 절차를 한 번 더 rehearsal한다.

#### ACTIVE 전환 rehearsal

ENGINE 백엔드를 종료해 쓰기를 막고, 구버전 인스턴스와 실행 중 transaction이 없는지
확인한다. baseline에 사용한 cutover key와 배포 후보 writer version으로 활성화한다.

```bash
cd backend
CUTOVER_KEY='00000000-0000-0000-0000-000000000000'
DATABASE_URL=jdbc:postgresql://localhost:5432/greenhouse_rehearsal \
DATABASE_USERNAME=greenhouse_rehearsal_test \
DATABASE_PASSWORD=greenhouse_rehearsal_test \
./gradlew orchidLedgerCutover --args="\
  --cutover-key=${CUTOVER_KEY} \
  --effective-business-date=2026-08-20 \
  --minimum-writer-version=1.0.0 \
  --current-writer-version=1.0.0 \
  --activate=true \
  --confirmation=ACTIVATE:${CUTOVER_KEY}"
```

그 뒤 `ORCHID_LEDGER_WRITER_MODE=ENGINE`과 최소 version 이상의
`ORCHID_LEDGER_WRITER_VERSION`으로 백엔드를 시작한다. 앞의 smoke test, reconciliation,
Work/Sales 연결 SQL을 다시 수행하며 성공 기준은 다음과 같다.

- reconciliation의 `stage=ACTIVE`, `ready=true`, `issues=[]`
- ENGINE 시작 성공과 `LEGACY` 또는 최소 version 미만 인스턴스의 startup guard 실패
- Mutation context 없는 `orchid_groups` 직접 INSERT·UPDATE와 모든 DELETE의 DB fence 차단
- 전환 후 생성·수정과 Work·Sales 효과의 새 revision 및 Mutation 연결 정상

이 rehearsal DB는 `ACTIVE` 이후 LEGACY 테스트에 재사용하지 않는다. 실패하면 DB를
보존해 원인을 조사하고 새 복원본에서 전체 절차를 다시 수행한다. 운영 primary는
동일 절차의 결과와 소요 시간이 승인된 뒤에만 전환한다.

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
