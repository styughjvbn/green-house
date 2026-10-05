# 정산·원장 standard 측정 결과

2026-10-05 사용자 제공 결과를 실제 구현과 대조했다. **10개 scenario·45개 sample 모두 성공했지만, 다수 정산의 초기화 비용과 Work 보정 참조의 Entity 누적은 후속 개선 대상으로 남는다.** 이번 변경은 분석과 증적 보존이며 제품 코드는 수정하지 않았다.

## 증적과 검증

- 실행: `20261005T122534Z-e84e1210`, revision `5c664962cb50b5cade50a40aa7ebcfd2834b7af9`, 작업 트리 변경 없음.
- 조건: standard/all, scenario별 warmup 1회·sample 3회, heap 2GiB. 전체 실행 281.754초에는 fixture 준비·warmup·빌드가 포함된다.
- 환경: Linux amd64, JDK 21.0.12.1 Ubuntu, JVM/Docker 사용 가능 CPU 8개, 물리 메모리 약 15.49GiB, Docker 29.8.0, PostgreSQL 18.6(`postgres:18-alpine`). PostgreSQL 컨테이너의 별도 CPU/메모리 상한은 없음. `shared_buffers=128MiB`, `work_mem=4MiB`, `effective_cache_size=4GiB`, `max_connections=100`, `max_parallel_workers_per_gather=2`.
- 원본: `backend/build/domain-benchmark/20261005T122534Z-e84e1210/result.json`. SHA-256: `b6ebcc264c7b8577c0d13088d5cbd5be83eb0a7bb7ba8447e8ac0286d5a9f294`. build 산출물은 git에 포함하지 않는다. 핵심 원시 sample 45행은 [CSV](measurements/20261005T122534Z-e84e1210-standard.csv)에 보존했다. CSV는 결과 JSON의 발췌이며 원본 전체와 동일한 형식은 아니다.
- runner의 완료 guard를 다시 적용했고 summary를 원시 sample로 재계산해 일치함을 확인했다. Gradle 종료 0, sample의 JDBC 실패·rollback·미종료/관측 없이 닫힌 transaction은 모두 0이다.
- 정산 생성 수·line 수·금액·재실행 무변경이 모두 예상과 일치했다. 원장 정상 fixture는 ready=true, 오류 fixture는 예상한 `SNAPSHOT_CHAIN_MISMATCH` 5,000건과 ready=false였다. 실패한 실행을 빠른 성공으로 계산하지 않았다.

## 처리 시간과 조회량

시간은 application 호출·commit을 포함한 ms다. JDBC 실행 수는 batch 호출을 포함하고 batch 내부 행별 round trip과 같지 않다. 반환 행은 소비한 행이며 DB scan 행 수가 아니다. Entity 수는 Hibernate 적재 수로 새로 생성한 Entity 수와 구분한다.

| scenario | phase | 중앙값 ms | 최소~최대 ms | JDBC 실행 | 반환 행 중앙값 | Entity 적재 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| settlement-1x100 | initialize | 115.53 | 109.40~120.01 | 14~15 | 304 | 1 |
| settlement-1x100 | replay | 8.29 | 8.07~10.43 | 3 | 201 | 0 |
| settlement-1x1000 | initialize | 280.82 | 273.69~310.99 | 53 | 3,022 | 1 |
| settlement-1x1000 | replay | 18.85 | 17.63~19.99 | 5 | 2,001 | 0 |
| settlement-1x10000 | initialize | 1,676.51 | 1,639.13~1,745.82 | 467 | 30,202 | 1 |
| settlement-1x10000 | replay | 136.08 | 97.63~146.49 | 41 | 20,001 | 0 |
| settlement-50x20 | initialize | 934.69 | 915.55~935.65 | 378 | 3,072 | 50 |
| settlement-50x20 | replay | 10.57 | 9.02~17.91 | 5 | 2,001 | 0 |
| settlement-501x20 | initialize | 24,634.56 | 16,994.02~30,141.33 | 3,781~3,782 | 30,772 | 501 |
| settlement-501x20 | replay | 76.76 | 73.30~131.70 | 43 | 20,041 | 0 |
| ledger-500x1 | reconcile | 79.09 | 65.85~80.08 | 19 | 1,506 | 1 |
| ledger-5000x10 | reconcile | 1,677.49 | 1,603.96~1,742.63 | 37 | 60,006 | 1 |
| ledger-1x50001 | reconcile | 1,430.82 | 1,342.65~1,510.66 | 18 | 50,009 | 1 |
| ledger-500x10-work | reconcile | 257.76 | 239.36~305.69 | 30 | 16,506 | 10,001 |
| ledger-5000x1-errors | reconcile | 241.07 | 237.86~244.86 | 37 | 15,006 | 1 |

## 메모리와 transaction

메모리는 MiB다. 할당량은 sample별 측정 스레드의 누적 할당량 중앙값, heap은 세 sample 중 가장 큰 JVM 전체 관측값이다. 할당량이 heap보다 클 수 있으며 같은 객체가 모두 동시에 살아 있다는 뜻이 아니다. 아래는 initialize/reconcile 기준이고 replay 원시값은 CSV에 보존했다.

| scenario | 할당량 중앙값 MiB | sampled heap 최대 MiB | JDBC transaction 최대 ms | row-lock 관측 구간 최대 ms |
| --- | ---: | ---: | ---: | ---: |
| settlement-1x100 | 1.74 | 312.30 | 93.66 | 92.91 |
| settlement-1x1000 | 12.84 | 331.30 | 269.86 | 269.14 |
| settlement-1x10000 | 106.66 | 363.91 | 1,585.91 | 1,585.40 |
| settlement-50x20 | 20.82 | 346.75 | 25.89 | 25.44 |
| settlement-501x20 | 203.37 | 375.41 | 96.16 | 95.82 |
| ledger-500x1 | 11.80 | 275.56 | 79.07 | 0 |
| ledger-5000x10 | 467.95 | 360.52 | 1,741.28 | 0 |
| ledger-1x50001 | 388.79 | 303.09 | 1,509.20 | 0 |
| ledger-500x10-work | 104.30 | 292.49 | 302.34 | 0 |
| ledger-5000x1-errors | 118.37 | 284.65 | 243.37 | 0 |

원장 5,000그룹×10revision의 sample마다 young GC가 2~3회 발생했고 해당 collector의 누적 collection 시간은 9~13ms였다. old GC는 모든 sample에서 0이다. 이 결과에는 OOM이나 긴 GC 중단 증거가 없지만, 2GiB 상한 전체나 대형 fixture의 안전성을 증명하지 않는다. heap 시작점·fixture 잔여 객체·GC 시점이 다르므로 표의 heap 최대값만으로 scenario의 고유 메모리 비용을 순위화하지 않는다.

## 코드와 대조한 판단

### 정산: 한 정산의 크기와 정산 개수의 비용은 다르다

`AuctionSettlementRebuildService.readCandidates`는 최대 ID를 고정하고 500개씩 ID/연결을 대조한다. replay는 정산 aggregate를 적재하지 않으며 10,000개 결과에서도 Entity 적재 0이었다. replay 시간·반환 행·실행 수는 기존 결과 전체의 batch scan에 비례한다. 이는 no-op의 비용이 상수가 아니라는 뜻이며 후보 조회의 행별 N+1과는 구분한다.

`AuctionSettlementService.appendUnlinkedResults`는 거래처를 잠근 다음 해당 경매장/날짜의 후보·연결·정산·설정을 확인하고 commit한다. 50정산→501정산에서 transaction 수는 53→523, JDBC 실행은 378→약 3,781로 늘었다. **정산별 commit과 재확인은 중복 기동/부분 실패를 방어하는 필요한 경계**다. 전체를 한 transaction으로 합쳐 비용을 줄이지 않는다.

하지만 같은 경매장 fixture에서 개별 결과 수 20개를 유지한 채 정산 수가 약 10배 늘 때 전체 시간은 약 26배 증가했다. 501정산의 JDBC execute 누적 시간은 15.18~28.65초이며 commit 호출 누적 시간은 0.149~0.162초였다. 이 계측에서는 commit 자체나 GC만으로 지연을 설명할 수 없다. 반복하는 원본/연결/설정 조회와 쓰기 실행의 계획·실행 비용을 우선 조사한다. `AuctionResultLineRepository.findSoldReadRowsUpTo`는 날짜·경매장 조건의 다중 join이다. **특정 쿼리의 full scan·누락 index가 원인이라는 판정은 아직 없다.** statement별 시간과 같은 분포의 실행 계획/통계를 추가로 확인해야 한다. fixture는 결과 테이블을 ANALYZE하지만 모든 join 대상의 통계 상태를 고정하지 않으며, 실행 간 autovacuum/analyze·캐시·외부 부하도 관측하지 않았다.

하나의 정산에 결과 10,000개를 넣으면 거래처 locking SQL 이후 관측 구간이 최대 1.585초였다. 501개의 작은 정산은 전체로 오래 걸려도 한 transaction/잠금 관측 최대는 약 96ms였다. 전체 처리 시간과 한 번의 거래처 잠금 유지 위험을 나눠 판단해야 한다. 경합 시 영향을 측정한 결과는 아니며, 한 정산 snapshot·금액·line atomicity를 임의 분할하지 않는다.

### 원장: chain cursor는 작동하지만 업무 참조는 Entity를 누적한다

그룹이 1개인 50,001revision fixture는 18개 JDBC 실행·Entity 1개로 완료했다. 5,000그룹×10revision도 Entity 1개였다. 전체 Entry를 JPA Entity로 적재하는 이전 경로가 반복되지 않는 증거다. 다만 scalar snapshot decoding·chain 비교·fingerprint 등의 임시 할당은 각각 약 389/468MiB로 남는다. 처리 시간이 JDBC execute 시간보다 길어도 남은 시간을 전부 CPU로 단정할 수 없다. 이 계측의 `executionNanos`는 ResultSet 소비·후속 cursor fetch를 포함한 모든 DB/네트워크 시간을 분리하지 않으므로 allocation profile과 cursor 소비를 함께 확인해야 한다.

반면 **Work 보정 5,000건 fixture는 Entity 10,001개를 적재했다.** `WorkOrchidGroupLedgerRehearsalInspector.corrections`가 보정을 500개씩 조회하더라도 `WorkOperationCorrectionRepository.findAfterId`는 Entity를 반환한다. 같은 대사 transaction의 persistence context에 적재가 누적되고, Farm의 `inspectWorkReferences`가 correction Mutation ID 전체를 `mutationRepository.findAllById` 한 번으로 받아 추가 Entity를 적재한다. 코드와 측정이 보정/Mutation 각 5,000건과 coverage 1건의 적재에 부합한다. 그룹 Entity/Entry cursor 개선과 별도로 남아 있는 **업무 참조 경계의 누적 및 큰 IN 조회**다. 5,000건에서는 성공했으므로 대형 입력의 실패·OOM은 재현한 것으로 기록하지 않는다.

후속 변경은 Work 소유 scalar projection을 application 값으로 변환하고, Farm 소유 Mutation의 검증에 필요한 값만 bounded batch/projection으로 읽는 범위가 우선이다. source/correlation/그룹 링크 판정·오류 순서·fingerprint·같은 read snapshot을 보존해야 한다. 모듈 경계를 건너 Repository projection을 노출하거나 persistence context 전체를 clear해 다른 호출의 미확정 변경을 잃지 않게 한다. 실제 보정 writer나 보류 기능을 활성화할 필요는 없다.

오류 5,000건도 정상 검증 결과로 완료했다. 오류 목록·그룹 값·baseline/current fingerprint 입력은 기존 계약상 건수에 비례해 남는다. 정상 5,000그룹×1revision의 대응 측정이 없으므로 118MiB 할당량을 오류 목록만의 비용으로 계산하지 않는다. 오류를 잘라 ready 판정을 변경하거나 저장 fingerprint 형식을 성능 목적만으로 바꾸지 않는다.

## 다음 작업과 종료 기준

1. **우선 개선:** Work correction/Mutation 참조의 scalar 조회·입력 분할. 실제 PostgreSQL에서 correction 수를 늘려 Entity 적재가 건수에 비례하지 않는지, query count가 명시적 batch 수에만 따라가는지 검증한다. 기존 링크 오류·snapshot·fingerprint 회귀를 유지한다.
2. **우선 조사:** 다수 정산의 statement별 비용과 실제 Repository 계획. 현재 transaction/잠금 순서를 보존한 상태에서 SQL 실행·버퍼/loop·통계를 확인한 뒤 필요한 조회 또는 index 변경만 선택한다. 501정산 3회 결과의 변동이 커서 정산 개수만으로 안정적인 증가율을 단정하지 않는다.
3. **추가 측정:** 위 변경 후 같은 standard 조건으로 전후 비교한다. large는 표준보다 큰 입력의 한계 확인이 필요할 때 사용자 환경에서 실행한다. standard 성공만으로 large가 필수 오류 검증까지 완료됐다고 처리하지 않는다.
4. **별도 운영 검증:** 실제 데이터 분포·동시 요청·ACTIVE 배치·긴 snapshot/vacuum 영향·index 배포 비용. 현재 합성 `BASELINE_PREPARING`·수량 0 fixture와 3회 sample은 운영 대사를 대체하지 않는다.

standard 결과 수집·분석은 완료했다. 확인된 Work 참조 적재와 다수 정산 비용의 개선/검증은 남아 있다. BE-009 일반 수정의 RECONCILIATION 분류·Work 생성 및 실사/보정 gate는 계속 보류한다.
