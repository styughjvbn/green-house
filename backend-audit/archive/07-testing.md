# Backend test suite의 회귀 방어력 평가

> 보관 문서: 당시 코드·감사·검증 이력이며 현재 구현의 기준이 아니다. 미해결/운영 검증/보류 상태는 [현재 작업 목록](../10-remediation-progress.md)을 따른다.

평가일: 2026-10-03. 기준 commit: `80106917232a671b5a489ad8e59d37b63a06dffe` (`develop`).

현재 백엔드 구현 기준, test source/config, Gradle task, CI, 실제 assertion·fixture·동시 실행 제어를 조사했다. 테스트 개수나 클래스명 대신 **어떤 오류가 생겼을 때 실패하는지, 실제 DB/transaction 경계를 통과하는지, 독립적인 기대값을 검사하는지**로 평가했다. 구현·테스트 코드는 수정하지 않았다.

## 1. 판정

**Work·Mutation·입금의 원자성/중복 요청 방어는 실질적이다. 전체 suite 통과가 Sales·Auction의 업무 연속성이나 모든 성능 회귀까지 보장하지는 않는다.**

| 평가 대상 | 현재 방어력 | 근거와 제한 |
| --- | --- | --- |
| Domain unit | 유효하지만 범위 편차 큼 | Work capability·수량 balance·Auction 금액 overflow의 독립 기대값이 있음. OrchidGroup invariant 단독 테스트는 두 조건에 집중 |
| H2 integration | application/HTTP 계약 방어에 유효 | 실제 Spring wiring·MockMvc·JPA 조립. Flyway/PG trigger·실제 잠금 검증은 아님 |
| PostgreSQL integration | 강한 편 | 실제 Flyway·commit·constraint·동시 요청·실패 주입 후 저장 상태 확인. 모든 클래스가 HTTP E2E는 아님 |
| Architecture | 명시한 구조 위반 방어에 강함 | compiled dependency·직접 writer·Clock 우회 gate. query parser/전체 업무 규칙/새 writer method 자동 탐지는 아님 |
| Concurrency | 핵심 pair에 강함, 전역 순서에는 부분적 | 실제 lock wait를 만드는 사례가 있음. 시작 barrier만 있는 사례와 호출 단계가 다른 교차 변경은 별도 |
| Rollback | 여러 핵심 경계에서 강함 | flush 이후 failure와 receipt/audit/mutation/work/stock/ledger 복구를 확인. 테스트 외부 transaction이 루트 경계를 가리는 경우 주의 |
| Idempotency | Work·Mutation·payment에 강함 | replay/다른 payload/동시 replay/실패 후 retry 검증. caller의 신규 업무 identity, Auction 자동 차수/반환 replay는 빠짐 |
| DB constraint/migration | 중요한 production 경계에 유효 | negative INSERT/UPDATE, FK, UNIQUE, write fence, 구버전 upgrade/실패 보존. 모든 CHECK 조합을 검증하는 목록은 없음 |
| Query-count | 목록 N+1 방어에 강함 | 크기 1·10·50 및 500/501 경계 검사, 일부 Entity load 0. 쓰기 mapper·하위 fan-out은 놓침 |
| Benchmark | 제한된 query gate | 시간은 관측값이고 성능 SLA gate가 아님. 쓰기·잠금 대기·메모리 최대값·운영 plan 미검증 |
| 유지보수성 | 개선 여지 있음 | 공통 fixture가 있지만 SQL/payload/lock helper 중복, migration head 고정 assertion, 단건 HTTP timeout 부재 |

객관적인 mutation score나 coverage 수치를 측정하지 않았으므로 숫자 점수는 부여하지 않는다. `JaCoCo`·PIT/동등한 mutation-testing task도 현재 Gradle/CI에서 확인하지 못했다. 이 사실 자체를 suite 품질 저하의 증거로 보지는 않는다.

## 2. 실행 환경별 분포와 CI gate

구체 테스트 클래스 150개를 annotation과 상속 관계로 분류했다. abstract base·fixture·seeder는 제외했다. 클래스명이 `Test`여도 실제 `@SpringBootTest`이면 통합 테스트로 분류했다.

| 범주 | 클래스 | 실제 경계 | 이번 확인 |
| --- | ---: | --- | --- |
| 순수 Java·Mockito·가벼운 context 검사 | 54 | Entity/policy/assembler/port 계약; 일부 Spring context 조건 검사 포함 | 직전 같은 HEAD의 기본 `test` 성공 결과 재사용 |
| Spring/H2 integration | 56 | full context·service·MockMvc·JPA; H2 PostgreSQL mode | 같은 기본 `test` 결과 |
| Architecture | 3 | source/bytecode/annotation inventory | 같은 기본 `test` 결과 |
| PostgreSQL E2E·integration·migration | 35 | Testcontainers PostgreSQL 18·Flyway·HTTP 또는 service/JDBC | 이번에 전체 task 실행 |
| PostgreSQL benchmark | 2 | HTTP 또는 service 직접 측정 | 직전 같은 HEAD의 강제 query-limit 실행 결과 재사용 |

기본 suite 113개 클래스·525개 invocation, PostgreSQL suite 35개 클래스·177개 invocation, benchmark 2개 invocation이 각각 실패/오류/skip 0이었다. parameterized 실행을 포함한 수치이며 서로 다른 도메인 시나리오 수와 동일하지 않다.

[application-test.properties](../../backend/src/test/resources/application-test.properties)는 random UUID H2 DB, `create-drop`, `flyway.enabled=false`, `open-in-view=false`다. [AbstractBackendIntegrationTest](../../backend/src/test/java/com/greenhouse/backend/AbstractBackendIntegrationTest.java)는 SpringBoot+MockMvc를 사용한다. HTTP 요청처럼 보이는 MockMvc 호출도 테스트 메서드의 `@Transactional`에 참여할 수 있다.

[WorkE2ETestBase](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkE2ETestBase.java)와 [application-e2e.yml](../../backend/src/test/resources/application-e2e.yml)은 RANDOM_PORT·실제 PostgreSQL·Flyway enabled·Hibernate validate·OSIV false를 사용한다. 기본 auth는 꺼져 있다. HTTP는 서버의 별도 thread/transaction을 통과하지만, Partner/Sales service 직접 호출과 migration DB 검증도 같은 task에 포함된다. 이름의 `workE2eTest`가 검증 범위를 정확히 설명하지는 않는다.

[build.gradle.kts](../../backend/build.gradle.kts)의 기본 `test/check`는 `work-e2e`, `work-benchmark`를 제외한다. 로컬 기본 테스트만으로 PostgreSQL 통과를 주장할 수 없다. [.github/workflows/verify.yml](../../.github/workflows/verify.yml)은 별도 PostgreSQL job에서 Docker를 요구하고 전체 E2E와 `workBenchmark -PworkBenchmarkEnforce=true`를 실행한다. `continue-on-error`로 개별 결과를 수집하지만 마지막 단계에서 실패를 다시 실패시키므로 조용히 통과시키는 구성은 아니다. 보고서는 14일 보관한다. branch protection에서 이 job을 필수로 설정했는지는 저장소 파일만으로 확인할 수 없다.

## 3. 실제로 회귀를 막는 테스트

### 3.1 Domain unit와 계약 기대값

- [WorkTypeCapabilitiesTest](../../backend/src/test/java/com/greenhouse/backend/work/domain/operation/WorkTypeCapabilitiesTest.java)는 code/template/active/system 조합에 독립적으로 작성한 기대 map·CSV를 적용한다. production capability를 그대로 호출해서 기대값을 만드는 검사보다 강하다.
- [WorkQuantityBalancePolicyTest](../../backend/src/test/java/com/greenhouse/backend/work/domain/correction/WorkQuantityBalancePolicyTest.java)는 입력·결과·손실·증가의 관계와 `Long.MAX_VALUE` 범위를 검사한다. [AuctionResultPolicyTest](../../backend/src/test/java/com/greenhouse/backend/auction/domain/AuctionResultPolicyTest.java)는 attempt 유형별 결과 합계/이력, 명시적 차수 중복, 금액 overflow와 부분 반환을 검사한다.
- [OrchidGroupInvariantTest](../../backend/src/test/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroupInvariantTest.java)는 생성 취소 재활성화와 예약보다 작은 수량 수정 거부를 검사한다. 이름만 보고 상태·배치·예약·모든 수량 경계를 포괄한다고 판단하면 안 된다. 다른 통합 테스트의 보완 범위를 함께 봐야 한다.
- [StructureChangeStrategyRegistryTest](../../backend/src/test/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistryTest.java), `WorkEffectProcessorTest`의 handler 누락/중복 검사는 새 유형 추가 시 wiring 누락을 기동 전에 실패시킨다. 해당 handler의 DB 효과가 올바른지는 PostgreSQL/통합 검증이 담당한다.

### 3.2 Rollback와 transaction boundary

| 대표 테스트 | 실패 지점과 저장 상태 검사 | 방어하는 회귀 |
| --- | --- | --- |
| [OrchidGroupAuditRollbackIntegrationTest.auditFailureRollsBackCorrection](../../backend/src/test/java/com/greenhouse/backend/OrchidGroupAuditRollbackIntegrationTest.java) | audit recorder 예외, 호출 뒤 Repository에서 원래 수량 확인; 테스트 메서드 자체의 외부 transaction 없음 | 감사 실패에도 수량만 먼저 저장되는 문제 |
| [WorkTransformationParityPostgresE2ETest.invalidSecondPlacementRollsBackSourcesResultsLineageAndEffects](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkTransformationParityPostgresE2ETest.java) | 두 번째 결과 배치 충돌, source 수량/상태/revision·effect·lineage·mutation count 확인 | 구조 변경의 중간 결과/원장만 남는 문제 |
| [WorkIdempotencyPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkIdempotencyPostgresE2ETest.java) | 즉시 실행 실패 및 batch 두 번째 기록 실패 후 receipt/membership/operation·수량 확인; 수정 retry 성공 | 실패한 요청 key가 남거나 앞 기록만 확정되는 문제 |
| [WorkBatchCancellationPostgresE2ETest.failureAfterMutationFlushRollsBackGroupsWorkAndAudit](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkBatchCancellationPostgresE2ETest.java) | Work 상태 전환에 임시 CHECK 추가; 실패 후 원본/결과·보상 Mutation·audit 확인 | 보상만 저장되고 Work 취소가 실패하는 부분 적용 |
| [WorkCorrectionAuditPostgresE2ETest.failedBulkCorrectionRollsBackReceiptAuditAndAllQuantityChanges](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java) | 후속 그룹 갱신에 임시 CHECK, receipt/audit/수량 변화 복구 | 앞 그룹 보정/감사만 저장되는 문제 |
| [SalesInventoryPostgresE2ETest.laterFailureRollsBackStockSnapshotsShipmentsAndMovements](../../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesInventoryPostgresE2ETest.java) | 출하 후 flush와 의도적인 후속 예외; 재고/예약·전표 snapshot·출하·movement·reconciliation 확인 | Farm/Sales/Auction의 transaction 분리 |
| [PartnerSettlementPostgresE2ETest.aLaterFailureRollsBackTheSalesPaymentAndAllLedgerEffects](../../backend/src/test/java/com/greenhouse/backend/work/e2e/PartnerSettlementPostgresE2ETest.java) | 입금 후 flush/예외; paidAmount·event·balance·audit 없음 확인 | 입금 대상/원장/잔액/감사의 부분 commit |

단순 `assertThrows`를 넘어 **실패 이후 새 조회의 결과**를 검사하는 점이 강점이다. 임시 CHECK를 `finally`에서 제거하고 실제 production schema의 실패를 사용하는 사례도 유효하다.

반면 외부 `TransactionTemplate`에서 실패를 만드는 테스트는 하위 service가 caller transaction에 합류하는지 입증한다. 최상위 service의 `@Transactional`을 제거해도 테스트의 외부 transaction 덕분에 통과할 수 있다. H2의 test-level `@Transactional`도 root annotation 누락·lazy loading 경계·commit 시 constraint를 가릴 수 있다. 이 경우 단독 application 호출 또는 실제 HTTP 이후 별도 transaction 조회가 추가되어야 한다. 현재 HTTP PostgreSQL 시험이 상당 부분 보완하지만 모든 쓰기 유스케이스에 대응한다고 확인하지는 않았다.

### 3.3 Idempotency

Work는 같은 실행 key의 완료/부분 replay, 숫자 표현 `6`/`6.00`, 다른 payload 거부, legacy fingerprint 없음의 fail-closed, batch 결과 ID 순서, 동시 즉시 요청 한 operation/effect/receipt, 실패 receipt rollback 후 재시도를 검사한다. `WorkCorrectionAuditPostgresE2ETest`는 취소 후 재시도에서도 저장된 audit를 반환하는 조건을 보호한다.

[OrchidGroupMutationEngineIntegrationTest](../../backend/src/test/java/com/greenhouse/backend/OrchidGroupMutationEngineIntegrationTest.java)는 같은 source replay와 다른 payload 거부, revision chain을 검사한다. [MutationFingerprintCompatibilityTest](../../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/MutationFingerprintCompatibilityTest.java)는 저장된 fingerprint golden 값과 선택 ID 정규화를 보호한다. 이는 단순 구현 문자열 비교가 아니라 **기존 receipt/원장 replay 호환성**에 필요한 gate다.

[PaymentLedgerContractIntegrationTest](../../backend/src/test/java/com/greenhouse/backend/settlement/application/PaymentLedgerContractIntegrationTest.java)는 amount/date 변경 key 재사용 거부, caller transaction 요구와 rollback을 검사한다. PostgreSQL의 동시 입금·완납 replay·정산 입금도 실제 저장 event/잔액 중복을 검사한다.

이 방어는 **올바른 caller key가 들어오는 경우**에 강하다. 새 업무 변경에 과거 key를 잘못 발급하는 문제와 key가 없는 Auction 자동 차수 요청은 다른 층의 회귀다. Engine replay unit만으로 막을 수 없다.

### 3.4 DB constraint와 migration

- `WorkIdempotencyPostgresE2ETest.databaseRejectsSameEffectIdentityWithADifferentKind`는 raw INSERT로 kind가 달라도 동일 effect identity 중복을 DB가 거부하는지 확인한다.
- `PartnerSettlementPostgresE2ETest.scalarPartnerReferencesRetainTheExistingForeignKeys`, `SalesInventoryPostgresE2ETest.scalarGroupIdsStillRequireExistingFarmRowsInPostgres`는 module 경계의 scalar ID 전환 뒤에도 실제 FK가 남는지 negative 저장으로 검사한다.
- [OrchidGroupMutationPostgresE2ETest.enforcesTheActiveLedgerWriteFenceAndRollsBackMutationContext](../../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationPostgresE2ETest.java)는 ACTIVE 전환 후 직접 UPDATE/INSERT/DELETE 거부와 Engine 정상 동작·context rollback을 검사한다. H2로 대체할 수 없는 PostgreSQL trigger 경계다.
- [ConsolidatedWorkMigrationPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/ConsolidatedWorkMigrationPostgresE2ETest.java)는 별도 DB를 V27까지 올리고 기존 기록을 삽입한 뒤 최신 migration과 receipt/inbound origin 보존·validate를 검사한다. 다른 movement/discard/title migration 시험도 과거 자료를 넣는 upgrade 경로다.
- [WorkCorrectionMigrationPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionMigrationPostgresE2ETest.java)는 V32의 처리 불가능한 역사 데이터를 최신 migration이 거부하고 기존 row/column을 보존하는지 검사한다. 빈 DB 부팅만 확인하는 시험보다 강하다.

모든 Flyway CHECK를 한 항목씩 제거했을 때 실패하는지 검증한 것은 아니다. 수량/예약·금액·revision·snapshot 조합의 **DB constraint와 application validation 각각**에 대응하는 negative 사례 목록이 필요하다. migration 재실행은 Flyway history/validate 경로이며 이미 실행한 SQL 파일 자체의 임의 재실행 허용과 혼동하지 않는다.

## 4. Concurrency 검증의 강도

| 방식 | 실제 사례 | 판정 |
| --- | --- | --- |
| 수동 connection으로 row lock을 잡고 worker를 기다리게 함; `pg_stat_activity` lock wait 확인 후 release | `WorkUndoSafetyPostgresE2ETest.completeTargetAndCancelSerializeWithoutDeadlock`, correction의 cancel vs 신규 Work 등록, Inbound 취소/실행, metadata 수정 vs 취소/완료 | overlap을 DB에서 확인하는 강한 시험. 순서별로 실행하는 parameter 사례도 있음 |
| lock owner가 transaction을 끝내기 전 두 번째 transaction의 `lock_timeout` 실패, 종료 후 재획득 | `PartnerSettlementPostgresE2ETest.holdsPartnerLocksUntilTheCallingTransactionCompletes` | row lock 유지 시간과 caller transaction 참여를 실제로 보호 |
| spy barrier를 첫 그룹 lock 직전에 넣음 | `SalesInventoryPostgresE2ETest.concurrentReservationsLockGroupsInIdOrderAndRejectOverbooking` | 두 생성 요청의 경쟁을 의도적으로 만들고 과예약 거부·정렬·DB 상태 검사. implementation 위치에는 결합됨 |
| ready/start latch 또는 executor 병렬 제출 후 최종 row/count/수량 확인 | Work 즉시 실행, 같은 위치 생성/공유 원본 transform, 입금/정산 초기화, stock count, 전표 번호 | 중요한 경쟁 결과 검사. 병렬 제출이나 latch가 실제 충돌 지점 도달을 보장하는 것은 아님 |
| worker 시작 신호 후 200ms 동안 future가 완료되지 않는지 확인 | `OrchidGroupMutationRoutingPostgresE2ETest.creationCancellationWaitsForTheGroupLock` | lock 제거 시 실패할 가능성은 있지만 thread가 느리기만 해도 통과할 수 있는 약한 negative 관찰 |

대부분 bounded latch/future get, final DB count·수량·상태·reconciliation을 사용하며 thread만 두 개 실행하고 끝내는 시험은 아니다. `Thread.sleep(20/25)`는 주로 lock-state polling 사이에 쓰므로 임의 sleep으로 성공을 기대하는 방식과 구분한다.

남는 한계:

- helper의 `oppositeInputOrdersAcquireTheSameSortedLocks`와 판매 생성 두 그룹 역순 시험은 **한 번에 받는 집합 정렬**을 검사한다. old reservation 해제 → new reservation 획득처럼 다른 단계에 걸친 전체 lock 순서는 검증하지 않는다. Sales G1↔G2 교차 수정 deadlock은 이 차이를 보여준다.
- 특정 pair를 한 차례 실행하는 시험은 다른 winner/order·3개 이상의 aggregate·서로 다른 transaction 경로 전체를 증명하지 않는다. 중요한 pair는 중간 barrier 또는 row-lock wait를 사용해 양쪽 순서를 재현할 가치가 있다.
- `pg_stat_activity` polling은 해당 DB의 전체 lock waiter 수다. test parallelization을 추가하면 다른 요청을 셀 수 있다. worker backend PID/application_name/query 식별로 범위를 좁혀야 한다.
- public HTTP 오류·rollback·최종 invariant를 함께 검사해야 한다. 단순 timeout 부재나 `200/409` 조합만으로 deadlock/중복 effect 부재를 판단하지 않는다.

## 5. Architecture test의 역할과 사각지대

| 테스트 | 강한 gate | 범위 한계 |
| --- | --- | --- |
| [ModularArchitectureTests](../../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java) | module/layer/feature package·declared DAG·Repository 소유·금지 layer import·테스트 비활성화 방지 | source regex 검사에 syntax/alias/comment 한계. package/import 순서는 구조/format gate이며 도메인 정확성 검사는 아님 |
| [ModuleBoundaryInventoryTest](../../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java) | ArchUnit compiled dependency로 FQCN/generic 보완; Entity/Repository/Q/HTTP DTO/Controller 타 모듈 의존; Clock 없는 시스템 시간 호출; annotation query의 foreign root | query regex는 SQL parser가 아님. association alias·동적 JDBC/Native SQL·외부 XML은 별도 검토 필요 |
| [OrchidGroupWriterArchitectureTest](../../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupWriterArchitectureTest.java) | 직접 상태 mutation/constructor/Repository writer를 Engine 및 recovery inventory로 제한; legacy routing 클래스 재도입 거부 | method 이름 목록 기반. 새로운 상태 변경 method를 목록에 추가하지 않으면 같은 모듈에서 우회 호출할 여지가 있음. DB raw write/fence 보장과는 별개 |

세 TSV inventory의 현재 예외는 0이고, 사라진 예외를 남겨도 exact 비교가 실패한다. 예외를 무제한 추가해 경계를 승인하는 구조가 아니다. compiled/source 검사를 함께 사용하는 것은 실질적인 방어다.

구체 클래스 이름을 고정하는 writer inventory는 리팩터링에 민감하지만 **의도된 architectural contract**다. 반면 transaction/readOnly/lock ordering·domain rule 한 곳 정의·snapshot atomicity가 모두 이 gate에 포함된다고 넓혀 해석하면 안 된다.

## 6. Query-count와 benchmark

세부 측정값과 누락 경로는 [06-performance.md](06-performance.md)를 참조한다. 테스트 품질 관점에서의 판정은 다음과 같다.

- `CoreQueryRegressionTest`, collection/settlement/partner 회귀는 seed 후 flush/clear·statistics reset, 규모 1·10·50, state API 500/501 batch boundary로 N+1과 잘린 목록을 검사한다. 동작과 count를 함께 검사해 쿼리만 줄이고 의미를 바꾸는 회귀도 방어한다.
- PostgreSQL Farm map/정산 page/Analytics는 query 수 외에 Entity load 0도 검사한다. projection 변경의 실질적 방어다. `WorkCompletedRecordBatchPostgresE2ETest`는 일반 즉시 완료 120 targets ≤32로 per-target replay 조회를 방어한다.
- 정확한 3/4/5/7 SQL 기대값은 새롭게 필요한 일괄 조회가 추가되면 실패할 수 있다. 자동으로 완화하거나 삭제하기보다 계약 변경인지 확인해야 한다. N과 무관한 증가식을 보호하는 검사는 단일 fixture의 절대 count보다 정보가 많다.
- Work benchmark는 roots 100×targets 20의 목록/상세와 이력 1건만 측정한다. 실제로 SQL 7회이면서 target Entity 2,000개가 적재돼도 통과했다. query-count만으로 메모리/반환 행 상한을 판단할 수 없다.
- Search benchmark는 501/5,001 matching partners의 마지막 1행 page를 검사한다. 전체 검색 의미·배치 횟수는 잘 보호하지만 ID 집합을 모두 IN에 넣는 비용 증가 자체는 성공 조건으로 허용한다. 경매 검색어의 공백 증가·하위 attempts fan-out은 빠진다.
- PostgreSQL 진단에서 Auction 변경 응답은 시도 1/10/50개에 SQL 5/14/54, 직접 계보는 연결 1/10/50개에 11/38/158이었다. 기존 GET/query benchmark는 이 실제 N+1을 잡지 못했다. 배치 profile의 난 묶음 과다 적재도 SQL 1회로 통과 가능한 문제다.
- `getPrepareStatementCount`는 Hibernate의 준비 횟수다. `JdbcTemplate` 직접 SQL, batch execute/네트워크 round trip, 반환 rows, plan CPU/lock wait는 별도다. SessionFactory 전역 statistics는 병렬 테스트에서 요청별 계측으로 쓸 수 없다.

Work benchmark의 query 상한은 강제 옵션을 쓴 CI에서 gate다. median/p95는 기록만 하고, 검색 시간은 규모별 1회 관측이며 allocation은 호출 스레드에 한정된다. 따라서 benchmark 성공을 부하·운영 SLA 검증으로 보고하면 안 된다.

## 7. Brittle·implementation-coupled test

| 사례 | 평가 | 개선 방향 |
| --- | --- | --- |
| `OrchidGroupStateChainMigrationPostgresE2ETest`가 migration 21~34 전체 목록 exact 비교; `ConsolidatedWorkMigrationPostgresE2ETest`가 최신 34·7개 migration 고정 | V35 추가만으로 실패할 수 있음. 과거 구간의 의미 보존과 현재 head 검사가 결합됨 | 과거 upgrade 구간과 최신 schema gate를 구분하고 새 migration 의도에 맞춰 갱신 |
| `OrchidGroupIntegrationTests.calculatesOrchidGroupAgeYearFromInboundDate`의 `LocalDate.now()`와 farmToday(systemUTC) | 시스템 timezone·업무일 기준이 섞임. 현재 fixture의 여유일이 우연한 실패를 줄여도 결정적 시간 검사는 아님 | fixed Clock·Korean business date·년생 경계 날짜를 명시 |
| `OrchidGroupMutationRouting...creationCancellationWaitsForTheGroupLock`의 200ms 미완료 | scheduler 지연을 row lock과 혼동할 수 있음 | 실제 해당 worker의 lock wait를 관측하고 release 전후 상태 비교 |
| `WorkBatchCancellation...failureAfterMutationFlush...`의 HTTP status ≥400 + unchanged state | 요청이 의도한 후속 CHECK 이전에 거절돼도 통과할 수 있음 | 실패 주입 지점 도달과 예상 failure category를 확인한 뒤 DB 복구 검사 |
| `WorkEffectProcessorTest`의 `verifyNoMoreInteractions`, relation assembler의 구체 Repository stub | 호출 단계 변경에 민감; Mockito 성공은 실제 query/transaction 동작을 증명하지 않음 | 중요한 replay/불필요한 재실행 금지는 유지하고 결과 계약·DB 회귀로 보완 |
| `SalesSlipOutboundServiceTest`의 `InOrder`와 snapshot 시각/ID 검사 | lock → snapshot → inventory 순서는 도메인적으로 필요하므로 유효한 coupling | 같은 순서를 PG 저장값/rollback으로도 보완. helper 호출 자체를 무조건 public contract로 고정하지 않음 |
| reflection으로 ID/과거 금액/active/balance를 설정하거나 raw SQL로 깨진 과거 자료 구성 | 고립 unit/복구 scenario의 명시적 fixture로는 적절함. 정상 입력 경로 증명은 아님 | 비정상/과거 fixture 의도를 표시하고 정상 writer 테스트와 구분 |
| `work/detail-contract.json`, `sales/print-contract.json`의 전체 JSON exact 비교 | 호환 계약을 동결하는 유효한 golden test. 무조건 brittle로 분류하지 않음 | additive field/정렬/과거 필드 의미 변경을 계약 검토와 함께 반영 |
| fingerprint golden 값·정규화 검사 | 기존 저장 key의 replay 호환성에 필요한 안정성 검사 | 동일 계산기로 기대값을 재생성해 실패를 없애지 않음 |

현재 코드에서 실제 flaky failure를 관찰한 것은 아니다. 위 항목은 구조적 민감도/false-positive 가능성이다. `System.nanoTime`/UUID로 생성하는 고유 fixture 이름 자체를 시간 도메인 검증 오류와 혼동하지 않는다.

`WorkE2ETestBase`의 HttpClient·HttpRequest에는 connect/request timeout이 없고 공통 JUnit timeout도 찾지 못했다. 일부 concurrency future는 제한이 있지만 일반 get/post는 응답이 오지 않으면 오래 대기할 수 있다. `SalesSlipNumberPostgresE2ETest`의 `invokeAll/future.get`도 시간 제한이 없다. 테스트별 예상 시간을 고려한 timeout이 필요하며 단순히 timeout을 짧게 낮춰 해결할 문제는 아니다.

## 8. Fixture 중복과 격리

공통 [FarmTestFixtures](../../backend/src/test/java/com/greenhouse/backend/farm/support/FarmTestFixtures.java), [FarmFixtureIntegrationTest](../../backend/src/test/java/com/greenhouse/backend/FarmFixtureIntegrationTest.java), [MovementTestSupport](../../backend/src/test/java/com/greenhouse/backend/support/MovementTestSupport.java), [WorkTestDataSeeder](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkTestDataSeeder.java), [OrchidGroupStateChainTestSupport](../../backend/src/test/java/com/greenhouse/backend/OrchidGroupStateChainTestSupport.java)가 있다. fixture가 전혀 없는 suite는 아니다. Movement helper가 실제 Work record workflow를 호출하는 것은 writer 우회만으로 기능을 검증하는 문제를 줄인다.

남는 비용과 위험:

- group/variety/layout 생성, 구조 변경 JSON, baseline/ACTIVE cutover 설정, CountDownLatch/future/polling helper가 여러 클래스에 반복된다. DTO/원장 정책 변경 때 fixture 여러 곳을 동시에 고쳐야 한다. 단순 중복 줄이기보다 정상·legacy·corrupt fixture를 구분한 작은 builder가 유용하다.
- `WorkTestDataSeeder`와 PG helper는 `MIN(id)`, `OFFSET 1`, 기존 work code·farm seed에 의존한다. farm seed 위치가 바뀌면 업무 의미와 관계없이 실패할 수 있다. 반면 `FarmTestFixtures`는 caller가 소유한 layout을 만든다.
- `FarmFixtureIntegrationTest`는 매 test에 동 15개×다이 3개×구역 2개를 만든다. 전체 구조 검색에는 의미가 있지만 단건 규칙 검증에도 같은 큰 fixture를 쓰면 원인 파악과 유지비가 커진다.
- raw SQL seed와 test baseline은 production writer의 모든 불변식을 거치지 않는다. baseline snapshot을 production snapshot 함수로 만드는 helper만으로 snapshot 계산의 정확성을 증명할 수 없다. 독립 기대값이 있는 snapshot/속성/수량 검사가 함께 필요하며 현재 Sales snapshot·Work transformation parity 테스트가 이를 일부 보완한다.
- reset의 `RESTART IDENTITY`는 Hibernate pooled sequence와 충돌할 수 있다. 이미 `resetKeepingSequences`와 관련 주석/PG batch 시험이 있어 위험을 인식하고 있다. 무조건 통일하거나 sequence 초기화를 추가하면 안 된다.
- H2 random DB/rollback, PG class별 `DirtiesContext`와 reset은 격리를 돕는다. 동시에 테스트 메서드의 전역 TRUNCATE·statistics·임시 CHECK 변경 때문에 suite를 병렬화하려면 별도 DB/schema/PID 단위 격리가 필요하다.

## 9. 누락된 중요 시나리오

같은 HEAD의 기존 suite가 통과한 상태에서 [03-domain-consistency.md](03-domain-consistency.md), [06-performance.md](06-performance.md)의 추가 진단이 다음 문제를 재현했다. 따라서 단순한 추측보다 강한 **현재 회귀 방어의 빈틈**이다. `/tmp` 진단은 저장소의 영구 regression gate가 아니며, 결함을 관찰하도록 작성한 probe의 성공도 정상 동작을 뜻하지 않는다.

| 우선순위 | 추가해야 할 scenario | 기존 시험이 놓치는 이유 | 기대할 방어 |
| --- | --- | --- | --- |
| 1 | 작성중 Sales의 품목 spec만 수정, 전표 memo 수정과 비교, 동일 금액 allocation 변경, 연속 두 번 수정 → 출고/취소 | 현재 재예약 시험은 금액이 바뀌는 1회 수정. caller version 기반 key 재사용을 놓침 (`DC-01`) | allocation 합계와 reservation 일치, 신규 업무에 새 mutation identity, stock/원장/replay 보존 |
| 1 | 직접 판매 `quantity×unitPrice`와 여러 item 합계의 int 경계 | Auction overflow unit은 있어도 Sales 산술·DB 저장을 보호하지 않음 (`DC-02`) | 범위 초과 거부와 reservation/slip/mutation 전체 rollback |
| 1 | 병해충/판매 불가 상태에서 선택·예약·수정·즉시 출고 | 수량 충분 조건과 일반 상태 fixture만으로 domain saleability를 증명 못함 (`DC-03`) | 조회 capability와 writer policy 일치; 불가 요청의 side effect 0 |
| 1 | Auction 결과 입력 → 수동 WAITING → 전표 취소, 결과 입력 vs 취소 양쪽 순서 | 현재 상태 또는 일반 취소만 검사 (`DC-04`) | 과거 attempt/result/history 보존, 최신 DB 사실 재확인, 수량 복구 원자성 |
| 1 | `attemptNo=null` 부분 낙찰/부분 반환 동일 요청 재전송·동시 재전송 | 명시적 차수 UNIQUE·1회 부분 반환 시험은 자동 차수/누적 replay를 막지 않음 (`DC-05`) | 한 번의 사실·수량 반영 또는 명시적 충돌 계약 |
| 1 | 서로 다른 partner의 G1↔G2 allocation 교차 수정; old release 뒤 두 요청 barrier | 생성 요청의 sorted ID 시험은 다단계 lock cycle을 검사하지 않음 (`DC-06`) | 정해진 순서로 진행/충돌 반환, deadlock 방지, 승자/패자 reservation 정합성 |
| 2 | 같은 lot 상태에서 수량 변경, 결과 금액→정산→입금 이후 조정 | 상태 전이 history만 세면 같은 상태의 사실 변경 누락을 놓침 (`DC-07`) | 변경 사실 기록, 원본/정산 snapshot·입금 잔액 정책 유지 |
| 2 | Auction write·직접 계보의 독립된 참조 1/10/50개 | GET/query fixture가 write mapper·legacy mapper를 거치지 않음 (`PERF-01/02`) | N에 선형 증가하지 않는 SQL, 필요한 조회 graph 명시 |
| 2 | Work summary target·profile group/capacity fan-out 증가 | query-count 고정이면 Entity 과다 적재도 통과 (`PERF-03/04`) | Entity/row/bytes 상한, SQL과 의미 함께 검사 |
| 2 | 동일 구역의 많은 구조 변경/inbound 결과, 미연결 정산 최초 대량 처리 | 일반 record의 120 target 시험과 연결 완료 fast path만으로 쓰기 비용을 추론할 수 없음 | SQL/flush/잠금 유지·heap과 원자성 함께 검증 |

추가 확인 후보는 프로필 동시 편집, 모든 수량/금액 CHECK의 raw SQL negative 조합, 새 Domain mutation method의 writer gate 포함 여부, 인증 켠 핵심 쓰기 workflow의 접근 계약이다. 이 후보들의 버그를 모두 재현했다고 주장하지 않는다. auth suite는 로그인/세션/익명 거부·WORKER admin API 금지를 별도로 검사하지만, auth 꺼진 PG suite와 합쳐 모든 권한 조합을 검증하는 것은 아니다.

핵심 개선은 테스트 수 증가보다 **업무 단계의 조합**이다. reserve→metadata edit→allocation edit→outbound→cancel, 결과→상태 보정→취소→정산/입금 같은 sequence의 매 단계에서 수량·금액·history·snapshot·receipt invariant를 확인해야 한다. 전 경로에 property-based 도구를 먼저 도입할 필요는 없으며, 재현된 sequence부터 명시적인 회귀로 옮기는 것이 우선이다.

## 10. 실행 결과와 적용 우선순위

이번 실행:

```bash
cd backend
./gradlew workE2eTest
```

**35개 클래스·177개 테스트, failure/error/skipped=0, BUILD SUCCESSFUL (3m 50s).** 실제 PostgreSQL 18·Flyway·migration·동시성·롤백·constraint·query 회귀를 전수 실행했다. 임시 audit probe는 포함하지 않았다. 로그: `/tmp/green-house-testing-audit-postgres.log`; XML: `backend/build/test-results/workE2eTest`.

같은 HEAD의 직전 성공 검증을 재사용했다: 기본 `./gradlew test --rerun-tasks` 113개 클래스·525개 성공, `workBenchmark -PworkBenchmarkEnforce=true` 2개 성공, frontend `npm run check` 성공. 이후 변경은 audit 문서뿐이므로 기본 suite/benchmark/frontend를 다시 실행하지 않았다. CI `check bootJar`·OpenAPI 생성 job·branch protection 설정·반복 soak·PIT/coverage·모든 결함 수정 후 재검증은 이번에 실행하지 않았다.

우선순위:

1. DC-01/02/03/04/05/06의 실제 재현을 영구 regression으로 이관하고 구현 수정 전후 같은 결과를 확인한다. 결함 조건을 assert한 감사 probe를 그대로 정상 regression으로 복사하지 않는다.
2. 기존 rollback/lock/idempotency 시험을 유지하면서 root transaction 단독 실행·충돌 지점 도달·실패 이후 DB 상태를 보강한다.
3. write/legacy mapper query-count와 Entity 적재 gate를 추가한다. 일반 목록 benchmark 상한을 단순히 낮추는 것으로 대체하지 않는다.
4. fixed Clock·HTTP timeout·migration 구간/head 분리·작은 fixture/worker별 lock 관측을 정비한다. 업무 기대값과 저장 호환 golden은 유지한다.

테스트 문서의 기본/PG/benchmark 분리는 실제 구성과 일치한다. 다만 현재 성공 결과를 전체 domain consistency나 모든 backend 성능의 보장으로 해석할 수 없다. 이번 평가는 누락과 개선 후보를 기록한 것이며 코드/테스트 보완 완료를 뜻하지 않는다.
