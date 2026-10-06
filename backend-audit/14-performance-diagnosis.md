# 정산·원장 지연 원인 조사

2026-10-06. [standard 전후 측정](13-domain-performance-results.md)의 501정산 초기화, 오류 5,000건 대사, Work 참조 대사를 실제 application 호출·PostgreSQL 계획·JFR로 조사했다. 운영 코드·index·transaction·보류 중인 RECONCILIATION 정책은 변경하지 않았다.

## 판정

| 대상 | 확인한 원인 / 비용 | 아직 확정하지 않은 사항 |
| --- | --- | --- |
| 501정산 초기화 약 25초 | 통계가 맞지 않는 상태의 원본 조회가 잘못된 join 순서를 선택해 20건 대신 10,020건을 경유한다. 501번 반복되어 초 단위 지연이 누적된다. 정산 line 조회도 빈 테이블에서 선택된 generic 전체 스캔 계획이 테이블 증가 후 재사용될 수 있다. | 사용자 standard 실행 당시의 통계·준비된 statement 계획은 수집하지 않았다. 두 원인이 당시 각각 얼마를 차지했는지는 소급 확정하지 않는다. |
| 오류 대사 241→377ms | 동일 데이터의 이전/현재 코드 비교에서는 큰 증가가 재현되지 않았다. 조회량·할당량은 거의 같고, 정상/오류 공통으로 chain 소비·snapshot 비교·fingerprint에 비용이 든다. | 당시 약 136ms 증가의 정확한 원인. GC·batch 변경 하나로 설명하거나 회귀가 없다고 확정하지 않는다. |
| Work 참조 대사 | 별도 비교에서도 할당량 약 20% 감소와 500건 batch 조회 9회 증가를 확인했다. 이번 비교의 JDBC/전체 시간 증가는 재현되지 않았다. | 과거 standard에서 약 46ms 증가한 정확한 원인. 할당량 감소를 heap peak 감소 또는 항상 빠른 실행으로 동일시하지 않는다. |

## 조사 방법과 재현 범위

`DomainPerformanceDiagnosisTest`와 수동 `domainDiagnosis` task를 추가했다. 독립 PostgreSQL 18 컨테이너에만 `pg_stat_statements`를 preload하고 자체 JDBC URL을 확인한 후 합성 fixture를 초기화한다. production 설정·migration에는 extension이나 plan 강제를 추가하지 않았다. SQL 로그를 측정 구간에서 끄고 test JVM heap 2GiB, JDBC 계측·heap sampler를 사용한다.

- 정산: 실제 `AuctionSettlementRebuildService`를 호출한다. 원본 SQL은 `AuctionDataReader` 호출에서 포착하고 custom/generic `EXPLAIN ANALYZE, BUFFERS`를 비교한다. writer 종료 후 실제 connection의 `pg_prepared_statements`와 해당 statement의 계획도 확인한다.
- 원장: fixture를 한 번 준비하고 같은 행을 정상/오류 상태로 번갈아 변경한다. warmup 4회 뒤 정상/오류 각 4회 측정한다. Work는 500그룹×10revision·보정 5,000건에서 warmup 4회 뒤 8회 측정한다. 이전 `5c664962`와 현재 `82444a86`를 격리 checkout·새 컨테이너에서 순서대로 실행했다. 두 revision 사이 production 변경은 63차 Work 참조 개선이다.
- JFR ExecutionSample 주기는 2ms다. 시작·dump·fixture 변경은 elapsed 측정 밖이다. fingerprint의 실제 호출 시간은 spy로 별도 계측한다. 프로파일링·spy·서버 통계의 추가 비용이 있으므로 standard 절대 시간과 직접 비교하지 않는다.
- custom/generic 비교용 사전 조회·EXPLAIN은 버퍼와 준비된 statement 이력을 바꾼다. 이후 elapsed는 순수 cold benchmark가 아니다. 사후 계획은 마지막 시점의 계획이며, 앞선 501회 모두가 같은 계획이었다고 가정하지 않는다.

증적은 [진단 JSON](measurements/20261006-performance-diagnosis.json)에 핵심 SQL 통계·계획·원시 sample을 보존한다. 원본 경로와 SHA-256, 조사 단계별 계측 차이도 함께 기록한다. 실패한 탐색 실행은 완료 증적으로 사용하지 않았다.

## 정산: index 누락보다 통계와 계획 선택

### 원본 조회의 잘못된 join 순서

원본은 `AuctionResultLineRepository.findSoldReadRowsUpTo`의 4-table join이다. 경매일·양수 금액·최대 결과 ID는 결과 테이블에, 경매장 조건은 출하 테이블에 있다. 동일 경매장이라도 501개의 날짜 key마다 이 SQL을 다시 실행한다.

기존 benchmark fixture는 `TRUNCATE ... CASCADE` 후 출하/lot/attempt/result를 채우지만 **`ANALYZE auction_result_lines`만 실행**한다. 나머지 관계의 cardinality·이전 경매장 ID 통계와 prepared plan 이력은 일관된 시작 조건이 아니다. 이전 분포를 분석한 뒤 다음 fixture로 교체하는 조건을 만들면 다음 계획이 재현된다.

| 동일 SQL, 첫 날짜 20결과 | 통계 미갱신 custom | 통계 미갱신 generic | 관련 테이블 갱신 custom |
| --- | ---: | ---: | ---: |
| EXPLAIN 실행 ms | 57.664 | 0.408 | 0.439 |
| 시작 테이블 | shipments | result_lines | result_lines |
| 출하 시작 추정 / 실제 행 | 1 / 10,020 | 출하 PK 20회 | 출하 PK 20회 |
| lot·attempt·result 후속 반복 | 각각 10,020회 | 각각 20회 | 각각 20회 |
| 전체 계획 shared hit blocks | 90,286 | 183 | 183 |
| 실제 반환 행 | 20 | 20 | 20 |

미갱신 custom에서는 `idx_auction_shipments_date_house`에서 시작한다. 결과의 경매일 조건은 마지막 result 접근에서 적용되므로 다른 500일의 출하까지 먼저 경유한다. 관계 `reltuples=-1 / relpages=0` 상태가 포착됐고, 별도로 남은 분포 통계와 조건 selectivity가 실제와 다르다. 통계 갱신 후에는 기존 V42의 `idx_auction_results_sold_date_id`에서 해당 날짜 20행부터 읽는다. 필요한 index는 이미 존재한다.

전체 writer 재현에서도 미갱신 첫 sample은 **15.970초**, 원본 SQL 501회 서버 실행 합계 **9.903초**였다. 갱신 조건은 **3.409초**였고 원본 SQL 합계는 **70ms**였다. 같은 미갱신 조건의 두 번째 sample은 3.831초로 빨라졌다. 실행 중 auto/custom/generic 선택과 통계 상태가 변할 수 있어, 이 수치를 반복 가능한 일정 배율로 해석하지 않는다.

별도 `force_custom_plan` 조사에서는 전체 19.500/18.154초 중 원본 SQL이 15.056/13.627초였다. **custom 강제는 해결책이 아니다.** generic이 빠른 원본 조회와 generic이 느린 연결 조회가 공존하므로 전역 `plan_cache_mode` 변경을 권고하지 않는다.

### 연결 조회의 cached generic 전체 스캔

`AuctionSettlementRepository.findLinkedResultIds`는 기존 UNIQUE index가 있는 `auction_result_line_id IN (...)` 조회다. 정산 line이 비어 있을 때 선택한 generic 계획의 cost·추정 행이 작게 남고, 정산 생성으로 테이블이 커진 뒤 같은 statement가 재사용되는 경우를 관측했다.

- 결과 ID 20개: 10,020행 전체 스캔, 반환 20·필터 제외 10,000행, 4.828ms. 관측 당시 generic 사용 996회·custom 5회였다.
- 결과 ID 500개: 같은 전체 스캔, 반환 500·필터 제외 9,520행, 79.999ms. generic 31회·custom 6회였다.
- 해당 조사 sample에서 연결 SQL 522회 서버 실행 합계는 **2.157초**였다. 같은 갱신 조건의 다른 sample은 6.49ms였다. SQL 수가 같아도 scan/조건 평가 비용은 다르다.

마지막 재현에서도 미갱신 첫 sample 16.295초 중 원본 조회가 10.202초였다. 통계 갱신 첫 sample은 3.519초였고, 다음 sample은 연결 조회 2.153초 때문에 5.328초였다. 후자의 실제 cached 전체 스캔과 같은 bind의 custom 계획을 비교하면 20개 입력은 **4.816→0.034ms**, 500개 입력은 **104.712→0.152ms**였으며 custom은 기존 UNIQUE index의 Index Only Scan을 사용했다. 이 비교는 writer 이후 같은 connection·같은 인자에서만 강제한 진단이다. 운영 설정 변경이나 전체 application 개선 효과 측정으로 해석하지 않는다.

이는 실제 cached plan에서 확인한 비용이다. fixture의 반복 TRUNCATE·빈 테이블에서의 준비·급격한 증가가 일반 운영과 동일하다고 주장하지 않는다. 운영에서도 bulk import나 초기화처럼 분포가 급변할 때 실제 계획을 확인할 필요가 있다. 같은 column의 index를 하나 더 만드는 것으로 해결할 근거는 없다.

### 줄이면 안 되는 경계

정산별 transaction과 경매장 잠금·원본 재조회는 실패 격리, 재시작, 중복 연결 방어를 위한 경계다. 사용자 standard의 commit 호출 합계 약 0.15초와 SQL 실행 약 16~28초를 구분한다. commit 개수를 줄이려고 전체 초기화를 한 transaction으로 합치면 원래 해결한 장기 잠금·rollback 범위를 되돌린다.

조회 횟수 회귀만으로 이 문제를 잡기 어렵다. 반환 20행·SQL 1회여도 DB가 10,020행을 경유할 수 있다. 계획 회귀에는 실제 scan/loop/buffer, 분석된 분포와 미갱신 분포, prepared statement 재사용 조건을 함께 남겨야 한다.

## 원장: 큰 오류 지연은 재현되지 않음

| 동일 5,000그룹×1revision | 이전 코드 | 현재 코드 |
| --- | ---: | ---: |
| 정상 중앙값 ms | 280.85 | 284.07 |
| 오류 중앙값 ms | 273.05 | 280.29 |
| 오류 최소~최대 ms | 258.33~368.40 | 242.64~333.26 |
| 오류 측정 스레드 할당량 중앙값 MiB | 118.80 | 118.88 |
| JDBC 실행 / 소비 행 | 37 / 15,006 | 37 / 15,006 |

오류 조건의 증가율은 약 2.7%였고 분포가 겹친다. 정상보다 오류가 항상 느린 것도 아니다. 이는 과거 standard에서 관측한 56.4% 증가를 재현하지 못했다는 결과이지, 당시 증가를 무시하거나 회귀 부재를 통계적으로 증명한 결과가 아니다. 조회량/메모리량의 큰 구조 변화나 Work Mutation batch의 추가 호출로 당시 지연을 설명할 근거는 없다.

JFR의 첫 application frame은 정상·오류 모두 `inspectLedger`와 JDBC wrapper에 집중됐다. `inspectLedger`는 iterator 진행에 따른 JSON snapshot cursor 소비, canonical 상태 비교, baseline 조립을 포함한다. fingerprint 실제 두 호출 합계는 대부분 약 25~50ms, 일부 sample에서는 약 69~73ms였다. 문자열/JSON 해석과 전체 baseline/current payload 직렬화는 여전히 데이터에 비례한다. JFR frame 빈도를 정확한 CPU 시간 비율로 해석하거나 비용 전부를 특정 한 메서드에 귀속하지 않는다.

기존 fixture는 Work 참조가 없어 분할 Mutation 조회 9회 증가 경로를 실행하지 않는다. 오류 추가는 기본 chain 소비를 생략하지 않으며 issue 객체 5,000건은 보존해야 한다. 현재 snapshot·fingerprint 의미나 오류 목록을 바꾸는 성급한 최적화보다, 과거 증가를 재현할 때 JDBC execute와 cursor 소비/JIT/GC/호스트 부하를 함께 기록하는 것이 우선이다.

## Work 참조: 할당량 감소 재확인, 시간 증가는 재현되지 않음

| 동일 500그룹×10revision·보정 5,000건, sample 각 8회 | 이전 코드 | 현재 코드 |
| --- | ---: | ---: |
| 중앙값 ms | 250.57 | 222.87 |
| 최소~최대 ms | 212.48~325.61 | 174.91~271.96 |
| 측정 스레드 할당량 중앙값 MiB | 102.74 | 82.02 |
| JDBC execute 합계 중앙값 ms | 63.99 | 62.35 |
| JDBC 실행 / 소비 행 | 30 / 16,506 | 39 / 16,506 |

추가 9회 조회는 500개 입력 상한에 따른 확정적인 비용이다. 그러나 이번 순차 비교에서는 JDBC 시간 증가와 전체 시간 증가가 재현되지 않았다. 적재/변환 비용 감소가 추가 조회 비용과 함께 작용하므로 조회 횟수 증가만으로 표준 측정의 약 46ms 증가를 전부 설명할 수 없다. 두 조건의 JDBC 누적 시간 범위도 겹친다.

할당량은 약 20.2% 감소해 기존 standard의 감소 방향을 재확인했다. JFR에서 이전의 `WorkCorrectionDetailResponse.from`과 관련 참조 처리, 현재의 공통 `WorkCorrectionResultDetails.from`과 scalar 참조 처리가 관측된다. 8회·별도 프로세스 순차 실행은 정밀한 paired 통계 실험이 아니며, 현재가 항상 11% 빠르다는 결론을 내리지 않는다. 안전한 batch 상한을 없애거나 Entity 전체 적재로 되돌릴 근거는 없다.

## 다음 변경의 범위

후속 결정: 일괄 정산을 별도 기능으로 구현하고 시스템 시작 시 자동 정산을 제거한다. 현재는 실행기·일괄 서비스·관련 Repository에 TODO만 추가했으며 자동 실행을 끄거나 새 기능을 구현하지 않았다. **시작 경로 자체의 성능 최적화는 보류**하고 아래 정산 관련 1·2번 검증을 별도 일괄 정산 구현 시 반영한다. 500개는 조회 분할 상한이며 지연의 발생 임계값이 아니다. 시작 실행기를 제거해도 같은 조회를 재사용하면 계획 문제가 남을 수 있다. 원장 후속은 이 결정과 별개다.

1. **benchmark 시작 조건 보강:** 관련 관계 통계·prepared plan 시작 조건을 명시한다. 분석된 안정 분포와 bulk 교체/증가 조건을 구분해서 측정한다. 기존 standard의 25초를 정상 통계 상태의 고유 처리 비용으로 사용하지 않는다.
2. **실제 Repository 계획 보호:** 원본 조회의 날짜 선택도와 출하 경매장 분포, 연결 조회의 초기 공백→성장·20/500 bind 조건을 검증한다. 단순 SQL 수 상한에 scan/loop 증적을 보완한다. 필요하면 해당 모듈 안의 query 형태를 개선하며 전역 plan 강제·중복 index는 피한다.
3. **원장 후속:** Work 참조의 batch 비용과 Entity 제거 효과를 별도로 판단한다. 일반 snapshot/canonical/fingerprint 비용 최적화는 저장 지문·오류 순서·repeatable snapshot 회귀를 유지하는 범위에서 측정 근거를 추가한 뒤 선택한다.

이번 작업은 원인 조사와 재현 도구/증적 보존이다. production 성능 수정 완료·large 측정·동시 부하·운영 데이터 검증으로 집계하지 않는다. 일반 수정의 RECONCILIATION 분류와 Work 생성·실사/보정 gate 보류도 유지한다.

검증: 실제 PostgreSQL 진단 실행 성공, 일반 backend 144개 클래스·748건 및 spotless·frontend check 성공. production DB 경계 변경이 없어 전체 `workE2eTest`와 standard/large는 재실행하지 않았다. 진단 증적의 성공 상태·sample identity·원시 핵심 지표·원본 hash를 대조했다. 일반 검증 이후 변경은 TODO 주석·문서와 증적 정리뿐이다.
