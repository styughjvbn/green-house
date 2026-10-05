# BE-035 — PostgreSQL 조회 계획과 index 검증

검증일: 2026-10-05. [08의 BE-035](08-findings.md#be-035--실제-조회fk검색에-대한-index-검증-부족)의 개선 근거다. 원래 감사 결과는 수정 전 기록으로 보존한다.

## 측정 범위

[PersistenceIndexPlanPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/PersistenceIndexPlanPostgresE2ETest.java)는 실제 Flyway를 적용한 Testcontainers PostgreSQL 18에서 root 1,000/50,000개를 각각 적재하고 `ANALYZE`한다. Hibernate가 해당 Repository 호출에서 생성한 첫 SELECT를 캡처하고 fixture의 조건을 바인딩하여 `EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT JSON)`을 실행한다. planner 옵션으로 index 사용을 강제하지 않는다.

동일 DB에서 V41의 index만 transaction 안에서 제거하여 이전 계획을 측정하고, catalog의 실제 정의로 다시 생성하여 새 계획을 측정한다. 측정 transaction은 rollback한다. 운영 DB에 접속하거나 운영 index를 제거하는 도구가 아니다. 양쪽 반환 행 수와 완료 후 index 복원을 확인한다. page의 count 쿼리·application 응답 조립·전체 HTTP latency를 포함하는 benchmark는 아니다.

fixture는 [PersistencePlanFixtures](../backend/src/test/java/com/greenhouse/backend/work/e2e/PersistencePlanFixtures.java)의 native SQL로 만든 cardinality·분포 실험이다. 각 root 테이블에 N개, 품목·배분·재고 이동·lot·시도·결과·정산 line에 각각 2N개를 만든다. 난 묶음 절반은 한 구역, 나머지는 19개 구역에 분산하며 양수 수량은 20%다. 전표의 10%가 작성중이고 거래처 참조는 100개로 분산한다. 날짜 동률도 포함한다. 계보는 100개의 다른 Mutation/결과 쌍과 같은 결과의 source 최대 5,000개를 포함한다. 표시 Entry는 1개/101개다.

배치 구간은 의도적으로 겹치며 모든 쓰기 원장·감사·snapshot을 생성하지 않는다. 실제 농장 배치 또는 유효한 application 쓰기 처리량을 재현하는 fixture로 사용하지 않는다. 네이티브 수량 변경 표본도 `ACTIVE` 원장 검증을 거치는 Mutation 경로가 아니다.

Partner contains, Sales contains OR, Auction의 넓은 상태 조건, Work의 좁은 날짜 범위는 별도로 명시한 **구성 조건 SQL**이다. 관련 API의 모든 join·EXISTS·필터를 설명했다고 취급하지 않는다. 이전 graph 쿼리만 [captured legacy SQL](../backend/src/test/resources/performance/legacy-graph-lineage.sql)로 별도 비교한다.

## 50,000 root 행 관측

아래 값은 root plan의 `Shared Hit Blocks + Shared Read Blocks`다. 상위 node가 하위 접근량을 포함하므로 전체 tree의 buffer를 다시 더하지 않는다. 고유 페이지 수나 물리 디스크 I/O 횟수도 아니다. 동일 fixture의 로컬 비교이며 운영 SLA 또는 p95 근거가 아니다.

| 실제 Repository 경로 | V41 이전 | V41 이후 |
| --- | ---: | ---: |
| 활성 배치, 선택적인 구역 / 큰 구역 | 1,725 / 1,725 | 268 / 1,282 |
| 구역의 최대 표시 순서 | 1,725 | 4 |
| 입고 결과 | 1,733 | 12 |
| 판매 품목+배분 / 배분+snapshot | 1,870 / 1,877 | 13 / 20 |
| 그룹의 작성중 예약 합계 | 949 | 18 |
| 전표의 재고 이동 / 그룹의 이동 count | 1,334 / 1,334 | 5 / 4 |
| 정산 상세 / lot의 정산 여부 | 1,337 / 1,334 | 8 / 3 |
| 전체 / 특정 거래처 판매 최신 20행 | 1,554 / 502 | 22 / 23 |
| 정산 / 입고 / 입금 최신 20행 | 910 / 619 / 715 | 4 / 24 / 22 |
| 경매 출하 최신 20개 ID | 139 | 22 |
| Entry에서 시작하는 계보 라벨 1개 / 101개 | 78 / 11,074 | 8 / 732 |
| 이전 계보 라벨 쿼리 1개 | 365,075 | 15,091 |

큰 구역은 실제로 5,000행을 반환하므로 작은 구역과 같은 고정 buffer 상한을 요구하지 않는다. 작은 N=1,000에서는 순차 scan이 더 싸거나 index로 buffer가 늘기도 했다. 예를 들어 첫 lot의 정산 여부는 2→3이다. 모든 조회에서 index가 선택되거나 빨라야 한다는 회귀 조건은 두지 않는다.

## index와 쿼리를 함께 수정한 이유

[V41](../backend/src/main/resources/db/migration/V41__index_operational_reference_and_date_queries.sql)은 확인된 조회를 지원하는 B-tree 17개를 추가한다.

| 묶음 | 목적·선택 이유 |
| --- | --- |
| group zone/sort, 활성 zone partial, inbound/id | 수량 0도 포함하는 전체 표시 순서/FK와 활성 배치 조회는 조건이 다르다. partial만으로 전체 구역 참조를 대체하지 않는다. |
| item slip/id, allocation item/id·group | 소유 aggregate와 group 예약 합계를 참조 테이블 전체 scan 없이 읽는다. snapshot의 기존 allocation/type UNIQUE는 유지한다. |
| movement slip/type·group, settlement line parent/id·lot | 이력 조회·정산 상세·정산 여부를 실제 선두 조건으로 지원한다. |
| sales 날짜/id·partner/날짜/id, settlement·inbound·payment 날짜/id | 전체 및 거래처별 최신 page의 동률 ID 정렬을 지원한다. 기존 partner·정산 UNIQUE index는 제거하지 않는다. |
| shipment 날짜/id | 기존 날짜/경매장 index의 incremental sort와 추가 스캔을 줄인다. |
| lineage mutation/result/id | 표시 Entry의 Mutation/결과 쌍에서 최초 계보 ID를 찾는다. 100개의 다른 쌍이 있는 분포에서도 사용을 확인했다. |

BE-034의 계보 projection은 Entity 적재를 없앴지만, 기존 쿼리는 결과에 연결된 source마다 같은 `MIN(id)`를 실행했다. V41 이후에도 source 5,000개에서 subplan을 5,000번 호출했다. [Repository](../backend/src/main/java/com/greenhouse/backend/farm/repository/transformation/OrchidGroupLineageRepository.java)는 표시 Entry에서 MIN을 찾고 선택된 lineage PK만 읽도록 바꿨다. 라벨 1개의 MIN 호출은 1회이고 동일 index에서도 buffer가 15,091→8로 줄었다. 101 Entry에서는 hash join이 MIN 식을 Entry당 두 번 평가해 202회였다. 이는 source fan-out 대신 표시 Entry 수에 비례하는 반복이다. 회귀도 Entry 수의 두 배 이내를 허용하며 join 방식을 강제하지 않는다. SQL 횟수·Entity 적재 상한만 확인한 기존 검증으로는 이 반복을 발견할 수 없었다.

최초 ID의 relation type, 출력 순서, 누락된 계보의 라벨 없음과 graph JSON 계약을 유지한다. 외부 모듈의 테이블을 production SQL로 읽는 경계를 추가하지 않았다. 쓰기 transaction·잠금 순서·접수·감사·snapshot은 유지하며 응답 schema는 바뀌지 않는다.

## 추가하지 않은 index와 남는 위험

- Partner의 `lower(name) LIKE '%partner 99%'`는 선택적인 111행을 반환해도 50,000행을 읽었고 buffer 715다. 넓은 검색도 715였다. B-tree로 leading wildcard 검색을 해결했다고 주장하지 않는다. trigram extension·검색 의미 변경은 실제 검색 분포와 운영 설치 정책을 확인할 후속 작업이다.
- Sales contains OR의 1,554→22 개선은 넓게 일치하는 최신 20개를 날짜 index로 읽은 효과다. 희귀어·미일치·concat·긴 공백/복수어 검색·count의 비용을 보장하지 않는다.
- Auction의 대부분이 SOLD인 조건은 기존 PK 역순 scan의 buffer 23을 유지했다. 넓은 상태에 새 index를 추가하지 않았다. 드문 상태·검수 EXISTS·전체 summary 집계는 미측정이다.
- Work의 2일 범위는 기존 status/start-date index로 273행·buffer 277을 읽었다. 새 Work index를 추가하지 않았다. 넓은 기간·다른 상태·운영 PostgreSQL 버전의 같은 계획은 보장하지 않는다.
- payment parent 연결, movement item, inbound variety 등 이번 조회에서 비교하지 않은 FK·join도 남는다. 모든 FK의 index 또는 참조 변경 비용을 검증한 결과가 아니다. 날짜 범위+여러 필터, 깊은 offset, generic prepared plan, 대형 IN, 초기 정산 재구성·원장 대사도 별도다.

## 쓰기·저장 비용과 배포

17개 index의 관측 크기 합계는 1,000 root에서 약 1.26MiB, 50,000 root에서 약 41.47MiB다. index 생성 직후 fixture 크기이며 실제 bloat·일일 증가량·peak index-build 공간을 추정한 값은 아니다.

양수 수량 200개를 `quantity + 1, version + 1`로 변경하고 savepoint로 되돌리는 native 표본에서 50,000 root의 WAL이 **129,072→177,429 bytes**, shared buffer 접근은 **2,968→4,653**이었다. 각각 한 번 관측한 값이며 생성/rollback 뒤의 dead tuple·캐시 상태도 다르다. index별 비용을 분리하거나 실제 Mutation 처리량 감소율을 입증한 실험은 아니다. 활성 partial의 predicate가 quantity에 의존하므로 양수→양수 변경도 HOT update 자격을 잃을 수 있다. 조회 개선을 쓰기 비용 감소로 설명하지 않는다. 운영에서는 조회 빈도·수량 갱신 부하와 WAL·HOT 비율·index 크기를 함께 확인한다.

V41은 일반 `CREATE INDEX`를 한 Flyway transaction으로 적용한다. 쓰기 중지 시간을 확보해야 하며 온라인 무중단 생성으로 취급하지 않는다. `lock_timeout=5s`는 **잠금 획득 대기**, `statement_timeout=5min`은 **각 statement**에 적용된다. 전체 migration 시간이나 획득한 잠금의 유지 시간을 5초/5분으로 제한하지 않는다. 실패하면 transaction 전체가 rollback된다. [배포 절차](../docs/07-deployment.md#조회-index-점검과-v41-적용)를 따른다.

## 재현과 회귀 방어

```bash
cd backend
./gradlew workE2eTest --tests '*PersistenceIndexPlanPostgresE2ETest'
```

결과는 ignored `backend/build/work-query-plans/persistence-{1000,50000}.json`이다. 실제 SQL·index 정의/크기·estimate/actual rows·loops·sort·buffer·WAL을 남긴다. 큰 fixture의 선택적 참조/최신 page는 buffer 150 미만 및 이전 계획의 절반 미만을 요구한다. 작은 fixture는 같은 계획을 강제하지 않는다. 계보는 표시 Entry 수 기준 MIN 호출·buffer 상한, 기존 쿼리와의 큰 fixture 차이를 검사한다. 특정 index 이름·node 종류·밀리초를 성능 정답으로 고정하지 않는다.

기존 query-count/Entity 적재 회귀 및 Work/Search benchmark는 별도의 목적을 유지한다. 이번 계획 검증은 SQL 횟수 gate를 대체하지 않는다. 운영 버전·행 분포·통계·설정·경쟁 쓰기·cold cache·반복 latency/peak heap 측정은 남는다. 운영 점검용 [read-only inventory](../scripts/performance/inspect-backend-indexes.sql)는 테이블 추정 행 수/ANALYZE 시점·실제 FK 선두 index·유효성·사용량·크기를 제공하며 실제 PostgreSQL에서 실행을 검증한다.
