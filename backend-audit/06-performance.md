# Backend persistence·성능 평가

평가일: 2026-10-03. 기준 commit: `80106917232a671b5a489ad8e59d37b63a06dffe` (`develop`).

기존 benchmark와 query-count 회귀 테스트를 먼저 조사하고, application → module API → Repository → Entity/mapper → Flyway 순서로 실제 비용을 비교했다. 현재 `docs/04-architecture.md`의 백엔드 조회 기준과 API guide/index/domain rules를 적용했다. 구현·스키마 변경 없이 평가 문서만 추가했다.

## 1. 평가 결과

주요 페이지 조회는 root pagination과 일괄 조립을 적용했고, 실제 PostgreSQL benchmark와 기존 회귀 테스트도 통과했다. 그러나 **쿼리 수가 고정됐다는 사실만으로 적재량·응답 크기·쓰기 비용까지 제한되지는 않는다.** 경매 변경 응답과 기존 직접 계보에서 N+1을 재현했고, 배치 프로필과 Work 요약 목록에서 불필요한 Entity 적재를 확인했다.

| ID | 우선순위 | 결과 | 근거 수준 |
| --- | --- | --- | --- |
| PERF-01 | 중 | 경매 변경 응답의 시도별 resultLines lazy loading | PostgreSQL 재현: 시도 1·10·50개에서 SQL 5·14·54회 |
| PERF-02 | 중 | 기존 직접 계보 mapper의 위치별 lazy loading | PostgreSQL 재현: 연결 1·10·50개에서 SQL 11·38·158회 |
| PERF-03 | 중 | 배치 프로필의 불필요한 난 묶음 적재·복수 collection join | PostgreSQL 재현: SQL 1회로 난 묶음 1·10·50개 적재; join 곱셈은 코드 확인 |
| PERF-04 | 중 | Work 요약 페이지가 모든 대상 Entity를 적재 | PostgreSQL 재현: root 100개·대상 2,000개, SQL 7회·대상 2,000개 적재 |
| PERF-05 | 중 | 품종 요약·자동 그룹의 전체 Entity 적재와 Java 집계 | 코드 확인; 품종 페이지 크기가 하위 난 묶음 수를 제한하지 않음 |
| PERF-06 | 중 | 거래처 검색의 반복 scalar 조회·큰 IN·경매 검색어별 증폭 | 기존 PostgreSQL benchmark 재실행으로 비용 증가 확인 |
| PERF-07 | 높음 | Mutation 배치 배치검증이 같은 구역을 반복 조회·순회 | 코드 확인; 구역/묶음 잠금 유지 중 비용 증가. 운영 지연은 미측정 |
| PERF-08 | 중 | Work 구조 변경·Inbound 및 Sales 쓰기의 중간 응답/상태 재조회 | 코드 확인; 즉시 완료 일반 작업 회귀 테스트로는 보호되지 않음 |
| PERF-09 | 중 | root 상한 밖의 무제한 목록·하위 이력·후보 스캔 | 코드 확인; 일부 전체 농장 조회는 의도된 계약 |
| PERF-10 | 중 | FK 조회·날짜 정렬·부분 문자열 검색의 index 위험 | Flyway/쿼리 비교; 운영 실행계획은 미측정 |
| PERF-11 | 중 | 정산 초기 재구성·원장 검증의 배치 조회가 전체 메모리/트랜잭션을 제한하지 않음 | 코드 확인; 정산 재실행 fast path는 기존 테스트로 보호 |

높음은 여러 쓰기 요청이 공유하는 잠금의 보유 시간을 데이터량에 따라 증폭시키는 경로를 우선한 평가다. 운영 장애나 특정 응답시간 초과를 확인했다는 의미는 아니다.

## 2. 기존 측정의 범위와 실제 결과

### 2.1 먼저 조사한 benchmark

| 기존 도구 | 데이터·실행 경계 | 검사하는 것 | 검사하지 않는 것 |
| --- | --- | --- | --- |
| [WorkOperationBenchmarkTest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkOperationBenchmarkTest.java) | PostgreSQL, 실제 HTTP; 작업 100건 × 대상 20개; API별 3회 warmup·20회 측정 | 목록 ≤7, 상세 ≤4, 난 묶음 이력 ≤5 SQL; 응답 의미; median/p95 기록 | 대상 Entity 적재 수, 긴 이력, target 규모 변화, 쓰기·취소, 병렬 요청, 실행계획·buffer·응답 bytes |
| [SearchScalabilityBenchmarkTest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SearchScalabilityBenchmarkTest.java) | PostgreSQL, service 직접 호출; 일치 거래처 501·5,001개; 마지막 페이지 1행 | 전체 건수·마지막 페이지 보존, SQL 증가식, 호출 스레드 할당량·시간 | 반복 시간 분포, 전체 프로세스 메모리/peak heap, 100행의 복잡한 이력, 긴 검색어, 더 큰 IN, DB plan |

[build.gradle.kts](../backend/build.gradle.kts)의 기본 `test`는 `work-e2e`·`work-benchmark`를 제외한다. `workBenchmark -PworkBenchmarkEnforce=true`를 지정해야 Work benchmark의 SQL 상한이 실패 조건이 된다. 검색 benchmark의 SQL 증가식 검사는 옵션과 관계없이 실행된다. CI도 PostgreSQL job에서 강제 옵션을 사용한다.

Work fixture의 첫 난 묶음 이력은 1건이다. 2,000개의 대상이 있다고 해서 한 묶음의 누적 이력 2,000건을 측정하는 실험은 아니다. 검색 fixture의 전표에는 품목·배분이 없고 lot에는 시도·결과·상태 이력이 없다.

이번 실행: `cd backend && ./gradlew workBenchmark -PworkBenchmarkEnforce=true` — **2개 테스트 성공**.

| Work API | SQL | 상한 | median ms | p95 ms |
| --- | ---: | ---: | ---: | ---: |
| 작업 목록 100건 | 7 | 7 | 79.73 | 152.16 |
| 작업 상세 대상 20개 | 4 | 4 | 16.14 | 23.75 |
| 난 묶음 이력 1건 | 3 | 5 | 13.92 | 17.54 |

| 일치 거래처 | 판매 SQL | 경매 SQL | 판매 할당 bytes | 경매 할당 bytes | 판매 ms | 경매 ms |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 501 | 5 | 8 | 1,480,448 | 1,638,608 | 33.56 | 59.73 |
| 5,001 | 14 | 17 | 11,891,800 | 10,799,984 | 245.44 | 210.62 |

보고서 원본: `backend/build/work-benchmark/results.json`, `partner-search.json`. 단일 로컬 컨테이너 결과이며 서비스 SLA나 운영 처리량으로 해석하지 않는다. 검색 시간은 각 규모에서 1회 측정값이다. 할당량은 `ThreadMXBean`의 호출 스레드 allocation이며 최대 상주 메모리가 아니다.

### 2.2 기존 query-count 회귀 테스트

| 테스트 | 실제 보호 범위 | 남는 경계 |
| --- | --- | --- |
| [CoreQueryRegressionTest](../backend/src/test/java/com/greenhouse/backend/CoreQueryRegressionTest.java) | H2; viewport ≤3; Farm state 0·1·500·501개 배치; lot 페이지 ≤5; 일반 판매 상세 배분 1·10·50개에서 5; 판매 검색 4·경매 문구 검색 7; 사용 중 출하 제외 후 옵션 200개 채우기 | 판매 쓰기/경매 변경 mapper, 큰 하위 이력, 운영 PostgreSQL plan |
| [OrchidGroupCollectionQueryTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCollectionQueryTest.java) | H2; 그룹 1·10·50개 목록 ≤3, 묶음별 소속 ≤5 | 전체 소속 수/목록 크기 상한·메모리 |
| [SettlementPartnerQueryTest](../backend/src/test/java/com/greenhouse/backend/SettlementPartnerQueryTest.java) | H2; 정산 페이지 3, legacy 상세 4; 페이지에서 정산행·Auction 결과 적재 0; 입금 페이지 3·parent 불필요 적재 방지; 이미 연결된 결과 502개 재구성 4 | 다수 미연결 결과를 처음 재구성하는 비용·전체 범위 잠금 |
| [BusinessPartnerIntegrationTests](../backend/src/test/java/com/greenhouse/backend/BusinessPartnerIntegrationTests.java) | H2; 거래처 옵션 page+count 2, page size만큼 Entity 적재 | 대규모 문자열 검색·source filter에 전달하는 전체 ID 집합 |
| [FarmQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/FarmQueryPostgresE2ETest.java) | 다이 1·10·50개·복수 구역; 전체 구조·맵 3, 다이·구역 목록 2; 맵의 난 묶음/품종 Entity 적재 0 | 다이 하나의 대량 묶음, profile graph, 자동 그룹 |
| [WorkDetailQueryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkDetailQueryPostgresE2ETest.java) | 보정 0·1·10·50건에서도 정형 상세 4 | 전체 target 적재량, Work/Mutation graph, 긴 effect JSON |
| [SalesAuctionBoundaryPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAuctionBoundaryPostgresE2ETest.java) | 검색 문구·literal wildcard·거래처 batch 경계·print 응답; dashboard 집계 5, Entity 적재 0 | Auction mutation response의 resultLines lazy traversal |
| [AnalyticsMetricsPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AnalyticsMetricsPostgresE2ETest.java), [SalesAnalyticsPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAnalyticsPostgresE2ETest.java) | Work 집계 2, 재고 집계 1; Sales 분석 `12+ceil(P/500)`, Partner 분석 `2+ceil(P/500)`; Entity 적재 방지·동일 snapshot·전체 집합에서 순위 보존 | distinct partner 규모의 Java 정렬/메모리, 넓은 기간의 DB 실행계획 |
| [WorkCompletedRecordBatchPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCompletedRecordBatchPostgresE2ETest.java) | 일반 즉시 완료 기록 대상 120개 ≤32; `applyNew`로 대상별 replay 조회 방지 | 구조 변경·Inbound 포트 실행·배치 취소·Sales reserve/outbound/cancel |
| [SequenceBatchInsertPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/SequenceBatchInsertPostgresE2ETest.java), [SequenceBatchInsertIntegrationTest](../backend/src/test/java/com/greenhouse/backend/SequenceBatchInsertIntegrationTest.java) | pooled sequence와 JDBC batch insert | AUTO flush로 중간에 끊기는 실제 업무 배치, SQL 실행/전송 횟수 |

`Statistics.getPrepareStatementCount()`는 Hibernate의 statement 준비 횟수다. 반환 행 수·transfer bytes·DB CPU·query plan·배치 실행 횟수와 동일하지 않다. `JdbcTemplate`의 전표 번호 생성 같은 SQL은 이 수치에 포함되지 않는다. H2 테스트는 flush/clear 후 측정해 1차 cache 은폐를 줄이지만 운영 index/통계/lock 대기는 검증하지 못한다. HTTP 시험의 Hibernate 통계는 SessionFactory 전역 값이므로 병렬 실험에서는 요청별 attribution이 추가로 필요하다.

## 3. 추가 재현과 확정한 적재 문제

PostgreSQL 18 Testcontainers와 실제 Flyway를 사용하는 외부 임시 probe를 작성했다. 1·10·50 규모, seed 후 `EntityManager.flush/clear`, Hibernate statistics reset, 실제 application/mapper 호출로 비교했다. probe는 `/tmp`에 두고 Gradle init script로 test source에만 추가했으며 저장소 production/test 코드는 수정하지 않았다.

| 추가 실험 | SQL 1개 규모 | SQL 10개 규모 | SQL 50개 규모 | Entity 확인 |
| --- | ---: | ---: | ---: | --- |
| `AuctionTrackingService.adjust`, 시도마다 결과 1행 | 5 | 14 | 54 | 전체 적재 6·24·104개 |
| 기존 직접 계보, 결과마다 다른 동/다이/구역 | 11 | 38 | 158 | 전체 적재 9·54·254개 |
| `BedPlacementProfileService.getProfile`, 같은 구역의 난 묶음 | 1 | 1 | 1 | 불필요한 OrchidGroup 1·10·50개 |

Work 목록도 기존 benchmark와 같은 작업 100건·대상 2,000건으로 재현했다. SQL은 7회지만 `WorkOperationTarget` 2,000개가 적재된다. **대상을 응답에서 생략한 것과 DB에서 대상 Entity를 생략한 것은 다르다.**

### PERF-01 — 경매 변경 응답 N+1

경로: [AuctionTrackingService](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)의 `adjust/confirmReturn/addResult/changeStatus` → `findForUpdateById` → [AuctionLotResponse.from](../backend/src/main/java/com/greenhouse/backend/auction/dto/AuctionLotResponse.java).

GET 경로는 `assembleLots`에서 partner·attempt+resultLines·statusHistory를 일괄 조회한다. 쓰기는 이 경로를 사용하지 않고 `lot.getAttempts()/getStatusHistory()`를 직접 넘긴다. root graph는 `shipment`만 fetch한다. mapper가 각 attempt의 `getResultLines()`를 펼치면서 시도 수 A에 비례하는 조회가 발생한다. 별도의 collection batch-fetch 설정도 없다.

재현 fixture의 SQL은 `A+4`다. 측정 지점은 service 반환 직후로, commit 시 발생하는 UPDATE/INSERT 비용은 포함하지 않았다. GET 목록의 ≤5회 회귀가 이 경로를 보호하지 않는다. 필요한 이력을 writer의 검증과 응답이 공유하도록 명시적으로 로딩하고, 시도 1·10·50개에서 증가하지 않는 쿼리 검사를 추가할 가치가 높다.

### PERF-02 — 기존 계보 mapper N+1

경로: [OrchidGroupLineageService.getLineage](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java) → [OrchidGroupLineageRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/transformation/OrchidGroupLineageRepository.java) → [OrchidGroupLineageItemResponse.from](../backend/src/main/java/com/greenhouse/backend/farm/dto/transformation/OrchidGroupLineageItemResponse.java) → `OrchidGroupResponse.from`.

Work effect 기반 `transformations`는 그룹 ID를 모아 `findDetailsByIds`로 읽는다. 반면 호환 `sources/results`는 graph에 source/result 그룹만 있고 위치 tree·variety·inbound는 빠져 있다. 위치를 mapper에서 순회하고 현재 년생 계산에서 입고일을 참조한다. 새 Work 효과가 없는 직접 계보 fixture에서 SQL `3L+8`을 재현했다. 동/다이/구역을 공유하면 1차 cache 때문에 증가폭이 작아진다.

Work-covered 계보는 Java에서 제외하므로 모든 신규 계보가 이 N+1을 겪는다고 판단하지 않는다. 과거/직접 계보도 지원하는 실제 경로다. 대상 위치/참조를 일괄 조회하거나 완전한 조회 graph를 사용하고, 위치를 공유하지 않는 fixture로 검사해야 한다.

### PERF-03 — profile 한 번의 SQL에 과다 적재

[BedPlacementProfileService.findZone](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/BedPlacementProfileService.java)은 [BedZoneRepository.findWithDetailsById](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneRepository.java)의 `physicalBed.house + orchidGroups + capacities` graph를 사용한다. profile DTO와 audit snapshot은 구역/다이/용량 규칙만 필요하다.

난 묶음 G개를 전부 적재한다는 것은 재현했다. 같은 SELECT에서 두 to-many를 join하므로 용량 규칙 C개가 있으면 DB 결과 행은 대략 `max(G,1)×max(C,1)`로 증폭될 수 있다. 이는 graph에 따른 구조적 위험이며 이번 probe에서 실제 JDBC 반환 행 수는 측정하지 않았다. `List+Set`이므로 multiple-bag 오류를 기대해 방어할 수도 없다. profile 전용 graph에서 orchidGroups를 제외하는 국소 개선이 적절하다.

### PERF-04 — Work 요약 목록의 target Entity 적재

[WorkOperationSummaryAssembler](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSummaryAssembler.java)는 progress를 DB projection으로 읽지만, [WorkOperationRelationSummaryAssembler.assemble](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationRelationSummaryAssembler.java)이 root IDs로 **모든 target Entity**를 읽어 inbound IDs를 만든다. 스냅샷 JSON을 포함한 target가 작업당 수천 개이면 페이지 100개만으로도 대량 적재된다. 관련 child operation도 전체 Entity로 읽어 개수를 센다.

query-count는 고정이며 전형적인 N+1은 아니다. summary에 필요한 distinct inbound ID와 relation count를 소유 모듈의 projection/집계로 읽는 개선이 가능하다. 현재 benchmark 자체의 2,000 target 적재를 통과 조건에 넣지 않아 이 비용을 놓친다.

## 4. 규모에 따른 비용 증가

### PERF-05 — 품종 요약·자동 그룹의 Java 집계

[VarietyResponseAssembler.assemble(Page)](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java)는 페이지의 품종 IDs로 연결된 모든 활성 OrchidGroup과 위치 tree를 읽는다. 실제 목록에는 count/total/saleable quantity/최근 날짜만 필요하지만 전부 Entity로 적재하고 Java에서 합산한다. 이어 `WorkOperationMetricsReader.getLatestWorkDates`에 전체 난 묶음 ID를 한 번의 IN으로 전달한다. 품종 1개에 난 묶음 10,000개가 있으면 품종 페이지 크기 1도 이 비용을 줄이지 못한다. 최근 작업일 map을 품종마다 반복 순회하는 추가 CPU 비용도 있다.

[DerivedOrchidGroupService.getGroups/getMembers](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DerivedOrchidGroupService.java)는 전체 candidates를 fetch하고 상세 DTO를 만든 다음 현재 년생 조건을 적용한다. `getGroups`는 최종 summary만 반환해도 member DTO 목록 전체를 보관·합산·정렬한다. 년생은 입고일/생성일과 업무일에 따라 계산되므로 단순히 저장된 `age_year` 조건으로 옮기면 의미가 달라진다.

DB에 표현 가능한 품종 집계는 projection으로 이동할 후보다. 자동 그룹은 현재 년생 의미를 보존하는 DB 표현이나 최소값 projection·누적 집계부터 검토할 수 있다. `VarietyQueryIntegrationTests`는 집계 결과를 검증하지만 난 묶음 규모별 Entity 적재 상한을 검사하지 않는다.

### PERF-06 — 거래처 module API 검색과 큰 IN

`SalesQueryService.getSalesSlipPage` → [BusinessPartnerReader.findMatchingIds](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java) → Partner keyset 조회 500개씩 → [SalesSlipRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepositoryImpl.java)의 `partnerId.in(allMatches)` → content/count → page partner batch.

Partner 조회는 쿼리당 500개지만 **전체 ID 목록은 제한 없이 누적**하며 content와 count의 IN에는 전부 넣는다. 일치 수 M의 조회 수는 `floor(M/500)+1`이다. 500의 배수에서는 종료 확인을 위한 빈 batch 조회도 필요하다. 기존 검색 benchmark는 이 증가를 성공 조건으로 인정하며, 무제한 SQL 인자/heap 위험을 제거하는 검사가 아니다.

[AuctionTrackingService.getLots](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)는 검색어의 공백 위치마다 `NAME_PREFIX` Partner 조회를 한다. 추가로 전체 검색어의 `NAME_CONTAINS`, market이 있으면 `NAME_EXACT`를 실행한다. 따라서 비용은 페이지 행 수뿐 아니라 공백 수·각 suffix의 거래처 일치 수에 비례한다. `AuctionShipmentLotRepositoryImpl`의 content/count 조건에도 boundary별 OR·IN이 복제된다. benchmark는 공백 1개 문구만 측정한다.

ID 조회를 500개씩 나누는 현재 구현은 전체 검색 의미와 pagination을 지키기 위한 것이다. 일부 ID만 남기는 방식은 잘못된 최적화다. 검색어 길이/형태별 실험, dedupe 가능한 module API, 실제 IN/OR 계획 검증부터 필요하다. `getIdentities`와 `OrchidGroupReader.getStates`는 500개 배치지만 `BusinessPartnerReader.getAllInfo`, lineage/work-history/collection의 여러 IN은 같은 배치 보장이 없다. PostgreSQL/JDBC 인자 수·SQL 길이 한계에 도달하는 임계값은 이번 평가에서 측정하지 않았다.

### PERF-07 — Mutation 배치에서 구역별 Repository 반복 호출

[OrchidGroupMutationEngine](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java)의 `transform`은 결과마다 `validatePlacementExcluding`, inbound create는 결과마다 `resolveInboundPlacement`, batch move의 `validateBatchMovePlacements`는 item마다 placement validation을 호출한다.

[OrchidPlacementPolicy.validateNoOverlap/findFirstAvailableSingleSlot](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java)는 매번 같은 구역의 살아 있는 OrchidGroup 목록을 Entity로 읽고 순회한다. 결과 R개·기존 G개이면 대략 R회의 구역 조회와 O(R×G) 비교가 필요하다. 자동 배치는 매번 정렬하고, 생성 결과가 늘어나는 동안 반복하면 추가로 O(R²) 형태가 생긴다. batch move의 item 간 충돌 비교도 이중 loop다. 복구 검증은 구역 IDs를 일괄 조회하지만 placement마다 전체 remaining 및 이전 placements를 순회한다.

Engine은 MANDATORY 쓰기 트랜잭션에서 root/zone 잠금을 유지하므로 이 비용은 공유 구역의 다음 작업 대기로 이어질 수 있다. 조회 사이 새 Entity가 저장되는 경로는 AUTO flush로 JDBC insert batch도 잘게 끊길 수 있다. 실제 flush/round-trip·잠금 대기는 미측정이다.

구역 잠금 후 필요한 interval/sort 값만 구역별로 한 번 읽고 메모리 상태에 새 결과를 반영하는 방식이 후보다. 불변식·lock ordering·중간 결과 검증을 유지해야 한다. 단순히 검증이나 잠금을 제거할 수 없다. 기존 테스트의 기능·동시성 보장은 [03-domain-consistency.md](03-domain-consistency.md)를 참조한다.

### PERF-08 — 쓰기 orchestration의 반복 조회

| 경로 | 비용 발생 지점 | 기존 보호와 차이 |
| --- | --- | --- |
| `StructureChangeRecordService` | record마다 plan 생성 응답 → start 응답 → execution 응답 → 최종 get/getAll. 반환 DTO가 필요한 정보보다 넓고 targets/executions/corrections를 반복 읽음 | 일반 PESTICIDE 즉시 완료 120개 검사는 이 orchestration을 호출하지 않음 |
| `InboundPottingOperationService.executeRecord` | replay effect는 일괄 조회됐지만 request별 `executeActivePlan`이 start/resume/get 및 completeTarget의 전체 응답을 받음. 마지막 getAll 이후 caller도 getAll | batch 메서드명만으로 일괄 SQL이라고 판단할 수 없음; effect matching도 request마다 effects 전체 순회 |
| Sales 생성·즉시 출고 | allocation factory의 `lockStates` → reserve Engine의 다시 root lock/replay → outbound의 다시 `lockStates` → consume Engine의 다시 lock/replay → response assembler의 현재 state/partner 조회 | 일반 Sales 상세 5회는 쓰기 비용을 제한하지 않음 |
| Sales 수정·취소 | old allocations/reservations 조회·mutation·new state/snapshot·잔액 합계·응답 조립 | 이전/현재 snapshot과 최신 불변식 확인 목적이 섞여 있음 |
| 정산 rebuild | `getSoldResultLines`로 snapshot 생성 후 응답 assembler가 결과 IDs로 `getResults` 재조회 | snapshot 값과 현재 display 참조는 다른 계약 |

이는 대부분 **module API/중간 응답 단위의 query amplification**이며 모든 loop가 row별 repository call인 것은 아니다. 동일 트랜잭션의 1차 cache도 JPQL·projection·일괄 조회 자체를 없애주지는 않는다. 중간 단계를 ID/상태 반환으로 분리하고 최종 조립을 한 번 수행하는 개선을 검토하되, 검증 시점·잠금 재확인·스냅샷 시점을 훼손하지 않아야 한다. rollback/atomicity를 성능 목적으로 축소하지 않는다.

### PERF-09 — root pagination 밖의 데이터량과 후처리

| 경로 | 제한되는 것 | 실제로 커질 수 있는 것 |
| --- | --- | --- |
| Farm 전체 구조/맵 | 전체 농장 계약, 맵은 projection 사용 | 살아 있는 전체 묶음·구역·다이; 3 SQL이어도 전체 결과 전송/JSON 크기 증가 |
| `/api/orchid-groups`, 판매 가능 묶음, derived groups/members | 선택 filter만 있음 | filter 생략 시 전체 상세 Entity/DTO. 판매 선택 조회도 무제한 |
| 사용자 그룹 목록 | batch 조립으로 N+1 제거 | 전체 collection/member IDs/그룹 상세, IN·heap |
| 구형 난 묶음 work-history·lineage | 범위는 난 묶음 단위 | 누적 전체 target/effect/계보. 새 work-history 페이지 API와 공존 |
| Work calendar | from/to 필수 | 기간 폭 상한·전체 개수 상한 없음, target/relation 조립도 증가 |
| Work 상세, Inbound 목록·상세 | root 1개 또는 페이지 ≤100 | root별 전체 targets/생성 결과 묶음 |
| Auction lot 페이지 | lot root ≤100 | 각 lot의 전체 attempts/resultLines/statusHistory; GET은 SQL 고정이어도 이력 크기 무제한 |
| Mutation 페이지 | mutation root ≤100 | 각 mutation의 전체 entries·connected relations·전후 상태 JSON |
| Sales/정산 legacy 목록 | root 최신 500개 | 품목·배분·snapshot·정산 lines와 외부 result 조회는 root별로 증가 |

`SalesSlipRepositoryImpl`, `AuctionShipmentLotRepositoryImpl`, Work search, Inbound search, 정산 page의 root pagination 뒤 **Java filter로 page 행을 버리는 잘못된 구현은 확인하지 않았다.** Sales legacy는 ID에 limit 후 child fetch를 적용한다. Inbound search의 graph는 variety만 포함해 collection pagination도 피한다.

예외적인 후처리 비용은 [SalesQueryService.getAuctionShipmentOptions](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java)다. Auction 후보 200개 page → Sales에서 used IDs 제외 → 미사용 200개가 될 때까지 반복한다. 결과 의미는 기존 테스트로 보호되지만 최근 후보가 모두 연결되어 있으면 많은 page를 훑는다. 출력 200개 상한이 입력 스캔 상한은 아니다. 높은 page OFFSET도 누적된다.

Work/Mutation graph의 depth ≤3·nodes ≤300 제한은 유효한 보호다. Mutation 탐색 slice도 hop마다 제한된다. 다만 Work graph는 관련 operations/effects/targets를 읽은 뒤 nodes를 자르고, Mutation graph의 relation 조회도 출력 노드 상한과 별개의 연결 fan-out이 있다. 모든 내부 적재가 300행 이하라고 해석하면 안 된다.

전체 농장 map 자체를 바로 pagination으로 바꾸기보다 필요한 화면의 viewport를 사용하고, 누적 이력과 선택지에 상한/분리 상세 계약을 먼저 검토하는 것이 합리적이다.

### PERF-10 — 실제 쿼리와 Flyway의 index 위험

Flyway의 PK·UNIQUE·명시적 INDEX를 확인했다. FK 선언이 참조하는 쪽의 index를 자동 생성한다고 가정하지 않았다. 아래는 **실행계획으로 확정한 병목이 아닌 우선 확인 후보**다.

| 실제 경로 | 현재 index와 남는 위험 | 확인할 후보 |
| --- | --- | --- |
| OrchidGroup 구역 배치·overlap·sort | `idx_orchid_groups_derived_group(variety_id, age_year, pot_size_code, status)`는 있으나 `bed_zone_id` 선두 index는 migration에서 찾지 못함 | `(bed_zone_id, sort_order)` 또는 `quantity>0` partial; interval query 계획과 함께 검토 |
| Inbound 생성 결과·목록 | `orchid_groups.inbound_record_id` 및 inbound date/id 정렬 전용 index를 찾지 못함. derived index는 variety 선두 조회에 도움 가능 | inbound FK, `(inbound_date DESC,id DESC)` 및 상태/유형 필터의 선택도 |
| Sales 품목/배분/이동 | item→slip, allocation→item/group, movement→slip FK에 대응하는 선두 index를 찾지 못함. snapshot allocation/type UNIQUE는 별개 | 상세·예약 reconciliation·취소 실제 join 조건별 FK index |
| Sales page·미수 합계 | `sales_slips(partner_id)`·번호/출하 UNIQUE는 있음. `(sale_date,id)` 정렬 및 완료상태·미수 합계 계획은 별도 | 범용/partner별 날짜 index, 상태 partial의 선택도 |
| 정산 목록/lines·설정 계산 | `(auction_house_id,auction_date)` UNIQUE는 있음. `settlement_lines(settlement_id)`·lot FK 선두 index를 찾지 못함 | line parent/lot 조회; 모든 경매장 날짜 정렬과 UNIQUE 선두 차이 |
| 입금 이벤트 페이지 | `(partner_id,event_date)`, `(target_type,target_id)`는 있음 | 전체 거래처 `event_date DESC,id DESC`, event type 필터, parent 이벤트 연결 계획 |
| Auction page·집계 | V16에 shipment/lot/attempt/history FK index 보강; shipment date/house index도 있음 | status+id·검수 EXISTS·전체 summary 집계가 실제 행 수에 따라 커짐 |
| Work 이력·Mutation 검색 | target group/operation·effect operation/key·correction 원본·mutation entry group/revision·source UNIQUE·relation 양방향 index는 있음 | 넓은 기간/전체 status 정렬, Mutation type/domain+id 및 EXISTS/count. 인덱스 존재만으로 최적 plan 단정 불가 |
| Partner·Sales·Auction 문자열 검색 | lower/contains·OR·concat 조건; migration에서 `pg_trgm`/문자열 검색용 expression index를 찾지 못함 | `lower(...) LIKE '%...%'`에 일반 B-tree를 추가하는 것으로 해결되지 않음; 실제 검색 분포의 계획부터 확인 |

근거: [V1](../backend/src/main/resources/db/migration/V1__initial_schema.sql), [V7](../backend/src/main/resources/db/migration/V7__add_work_operation_v2_schema.sql), [V16](../backend/src/main/resources/db/migration/V16__enforce_auction_and_balance_concurrency.sql), [V18](../backend/src/main/resources/db/migration/V18__add_sales_orchid_group_snapshots.sql), [V21](../backend/src/main/resources/db/migration/V21__add_orchid_group_mutation_engine.sql) 및 이후 migration 전체 검색. 작은 테스트 데이터에서는 sequential scan이 적절할 수 있다. 이번에는 `EXPLAIN (ANALYZE, BUFFERS)`·운영 `pg_stat_statements`를 실행하지 않았고 index 변경도 하지 않았다.

### PERF-11 — 배치 조회와 전체 메모리/트랜잭션의 차이

[AuctionSettlementService.rebuildExistingResults](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java)는 낙찰 결과 IDs를 500개씩 읽고 이미 연결된 결과를 제외한다. 그러나 미연결 상세 전체를 `newResults`에 모아 정렬/그룹화한 뒤 partner 전체 잠금·연결 재확인·기존 정산 상세 적재·라인 병합·saveAll을 **한 트랜잭션**에서 수행한다. `loadExistingSettlements`는 신규 key의 최소/최대 날짜 사이 전체 상세를 읽으므로 key가 듬성듬성하면 불필요한 기존 정산도 포함한다.

[AuctionSettlementInitializer](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementInitializer.java)는 기본 활성인 startup runner다. 이미 연결된 결과만 있으면 aggregate를 읽지 않는 fast path는 테스트가 보호한다. 처음 처리할 결과가 많은 경우의 peak heap·startup 시간·partner lock 대기는 보호하지 않는다. 500개 조회마다 commit/clear되는 스트리밍 처리도 아니다. 개선 시 정산별 원자성·다중 인스턴스 재확인·입금 snapshot 보존을 유지하는 처리 단위를 먼저 정해야 한다.

[OrchidGroupLedgerReconciliationService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerReconciliationService.java)도 그룹 ID 500개 batch 뒤 전체 그룹 값을 누적하고, 모든 state-chain Entry를 map에 모은다. 그룹 수가 작아도 그룹별 Entry 이력이 길면 크다. 장기 read-only persistence context에 Entity도 남는다. 운영 CLI/전환 검증 비용이며 일반 페이지 요청과 구분해야 한다. 전체 원장 규모의 rehearsal·heap 측정이 필요하다.

## 5. 잘 적용된 persistence 경계

- `open-in-view=false`, 조회 service의 readOnly 트랜잭션, JDBC batch 50·pooled sequence·insert/update ordering·PostgreSQL `reWriteBatchedInserts`가 적용돼 있다. 단, 모든 쓰기에서 batch가 끝까지 유지된다는 보장은 아니다.
- Farm map은 scalar projection으로 전체 상세 Entity/variety graph를 만들지 않는다. viewport는 2~4개 다이의 묶음만 읽는다. 반복문에서 조회하지 않고 모은 ID로 읽는 구조다.
- Sales 일반 상세의 배분/snapshot/current Farm/partner/actions, Auction GET의 attempts/resultLines/history, Inbound 페이지의 result groups/potting dates/undo 가능 여부는 일괄 조회한다. 모든 mapper를 lazy-loading 문제로 분류하지 않았다.
- 정산/입금 page는 이름만 module API로 batch 조회하고 정산행/원본 parent Entity를 불필요하게 읽지 않는다. DB summary도 page/legacy 상한과 별개로 전체 조건을 집계한다.
- Analytics는 거래 행 전체 Entity를 적재하지 않고 소유 module의 집계/scalar 값을 사용한다. Java에서 이름 중복 통합·전체 후보 순위를 계산하는 부분은 기존 의미 보존에 필요하며, 거래 수 N보다 distinct partner 수 P에 비례하는 비용이다.
- module 경계를 지키기 위해 DTO API를 쓰는 설계 자체가 결함은 아니다. 단건 API 반복·전체 ID 계약·중간 응답 재조립의 구체적인 호출 비용을 평가해야 한다.

## 6. 기존 benchmark가 놓치는 영역과 검증 우선순위

| 우선순위 | 추가할 실험 | 실패 조건/관측값 |
| --- | --- | --- |
| 1 | Auction 변경 응답: 시도 1·10·50, 서로 다른 resultLines | 요청 SQL이 A에 선형 증가하지 않음; commit SQL과 mapper SQL 구분 |
| 1 | 직접 계보: 결과 1·10·50, 위치/품종/입고를 서로 다르게 생성 | 공유 1차 cache 없이 query 수 제한; mapper detached 사용/조회 graph 확인 |
| 1 | profile: 난 묶음 G·capacity C 교차 증가 | OrchidGroup 적재 0; 반환 행/할당량도 확인 |
| 1 | Work summary: roots 고정·target 20/200/2,000 및 child operation 증가 | target Entity 적재 0을 목표로 검증; JSON allocation·응답 bytes·DB rows |
| 1 | 동일 구역 구조 변경/inbound 자동배치: 결과 R·기존 그룹 G를 독립 증가 | 구역 조회가 결과 수와 선형 증가하지 않음; flush/batch 실행·lock 보유/대기 |
| 2 | Sales reserve→outbound→cancel, Work 구조 변경·포트 배치·취소 end-to-end | module별 SQL breakdown; 중간 get 반복·JDBC 직접 SQL 포함; 수량/원장/atomicity 회귀 유지 |
| 2 | 검색어 공백 0/1/5/20·일치 수 500/5,000/50,000·공통 이름/빈 결과 | Partner scan 횟수·IN args·plan time·할당량; 마지막 page/total 정확성 유지 |
| 2 | 품종 root 1개에 그룹 100/1,000/10,000·자동 그룹 필터 선택도 변화 | Entity 적재·heap·group별 집계 비용; 현재 년생 의미 유지 |
| 2 | 미사용 Auction option이 최근 목록에 거의 없는 상태 | 후보 page 수·OFFSET 비용, 출력 200개 정확성 |
| 2 | root 100개에서 하위 이력/Entry/target fan-out 증가 | 쿼리 수 외 response bytes·DB 반환 rows·peak heap |
| 2 | 실제 PG schema+대규모 fixture의 실행계획 | `EXPLAIN (ANALYZE, BUFFERS)`의 scan/rows/sort spill/estimate 오차; 선택도별 비교 |
| 3 | 미연결 정산 결과/원장 Entry 장기 누적 및 재실행 | 최초/fast path 분리, peak heap/GC/startup 시간; rollback·lock 대기 |
| 3 | pool 10에서 병렬 read/write·warm/cold 비교 | p95/p99, connection wait, transaction/lock duration; Hibernate 전역 통계를 요청별 계측으로 보완 |

시간을 CI 절대 실패 조건으로 추가하기 전에 fixture·환경 변동을 통제해야 한다. 확정된 N+1은 row 규모에 따른 query-count 회귀로, 과다 적재는 Entity/row 상한으로 먼저 보호할 수 있다. 더 큰 pagination/검색/정산 구조 변경은 실측 후 범위를 결정한다.

## 7. 실행 검증과 한계

- `./gradlew workBenchmark -PworkBenchmarkEnforce=true`: 성공, 기존 benchmark 2개.
- 기존 PostgreSQL query/performance E2E 7개 클래스 선택 실행: `FarmQueryPostgresE2ETest`, `WorkDetailQueryPostgresE2ETest`, `SalesAuctionBoundaryPostgresE2ETest`, `AnalyticsMetricsPostgresE2ETest`, `SalesAnalyticsPostgresE2ETest`, `WorkCompletedRecordBatchPostgresE2ETest`, `SequenceBatchInsertPostgresE2ETest` — 성공.
- `./gradlew test --rerun-tasks`: **113개 클래스·525개 테스트 성공**, 실패/오류/skip 0. H2 및 기존 module/unit/architecture/query 회귀 포함.
- PostgreSQL 외부 probe: profile/legacy lineage/Auction write 각 1·10·50개, Work summary 100×20 — 총 10개 성공. 첫 probe의 lineage seed가 필수 Work ID/실제 FK를 충족하지 못해 실패한 것은 fixture를 수정한 뒤 재검증했다. 이는 application 성능 실패와 구분한다.
- 외부 probe는 관측된 현상을 assert하는 진단 테스트다. 성공이 N+1 해결을 의미하지 않는다. 소스 `/tmp/green-house-performance-audit-src`, init script `/tmp/green-house-performance-probe.init.gradle`; 최종 XML/로그도 `/tmp/green-house-performance-probe-results`, `/tmp/green-house-performance-probe-final.log`에 보관했다. 임시 자료는 영구 회귀 테스트가 아니다.
- 이번 변경은 문서뿐이다. 프론트 검사는 같은 HEAD의 직전 domain audit에서 성공한 `npm run check`를 재사용했고 다시 실행하지 않았다. 전체 `workE2eTest` 재실행 대신 이번 성능 범위의 7개 기존 클래스를 선택했다.
- 미실행: 운영 데이터 실행계획/통계, 부하·동시성 throughput, DB 반환 행 계측, heap dump/GC/JFR, cold-cache benchmark, 매우 큰 IN 한계 실험. 정적 위험을 운영 병목으로 확정하지 않았다.

기존 구현 문서의 목록 SQL 상한 설명은 코드/테스트와 대체로 일치한다. 다만 이를 전체 backend 성능 보장으로 넓혀 읽을 수 없으며, 누적 이력·쓰기·하위 적재의 별도 gate가 필요하다. 이 평가에서 API contract나 도메인 정책을 변경하지 않았으므로 OpenAPI/기준 구현 문서는 재생성하지 않았다.
