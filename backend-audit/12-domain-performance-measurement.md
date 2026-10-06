# 정산·원장 성능 측정 실행 가이드

사용자 환경에서 측정을 실행하고 `result.json` 하나를 전달하면 결과를 분석할 수 있다. 실제 application service를 호출하며 Testcontainers의 PostgreSQL 18 격리 DB에 합성 자료를 만든다. 실행마다 새 컨테이너와 새 결과 디렉터리를 사용한다.

## 실행

프로젝트 루트에서 실행한다. JDK 21, Python 3, 실행 중인 Docker가 필요하다. 첫 실행에는 Gradle 의존성과 PostgreSQL 이미지 다운로드 시간이 추가될 수 있다. Python 실행 파일이 `python`인 환경에서는 아래 `python3`를 `python`으로 바꾼다. 검증 환경은 Linux이며 native Windows 실행은 검증하지 않았다.

먼저 실행 환경과 결과 파일 생성을 확인한다.

```bash
python3 scripts/performance/run-domain-benchmark.py --profile smoke --warmup 0 --samples 1 --heap 1g
```

일반 측정은 다음 명령이다. scenario별 warmup 1회, 실제 sample 3회, test JVM 최대 heap 2GiB를 사용한다.

```bash
python3 scripts/performance/run-domain-benchmark.py --profile standard
```

정산과 원장을 나눠 실행하거나 더 큰 fixture를 선택할 수 있다. 각 실행의 조건은 결과 파일에 저장되므로 비교 시 같은 조건을 사용한다.

```bash
python3 scripts/performance/run-domain-benchmark.py --profile standard --scenario settlement
python3 scripts/performance/run-domain-benchmark.py --profile standard --scenario ledger
python3 scripts/performance/run-domain-benchmark.py --profile large --samples 3 --warmup 1 --heap 2g
```

`--samples`는 1~30, `--warmup`은 0~20이다. `--heap`으로 test JVM 상한을 바꿀 수 있다. 전체 실행 시간은 컴파일·fixture 준비·warmup을 포함하며, sample의 처리 시간과 메모리/GC 측정은 fixture 준비를 제외한다. 측정 service 호출을 외부 테스트 transaction으로 감싸지 않는다. 정산은 sample마다 미연결 결과부터 다시 시작하고 이어서 재실행 비용을 별도 기록한다.

## 전달할 결과

실행 마지막 줄에 다음 형태의 경로가 표시된다.

```text
backend/build/domain-benchmark/<UTC 실행 ID>/result.json
```

**이 `result.json`을 전달한다.** revision·작업 트리 변경 여부·설정·JVM/OS/CPU/메모리·Docker engine/컨테이너 자원·PostgreSQL 버전·scenario별 원시 sample과 요약이 들어 있다. CPU는 JVM/engine의 사용 가능 개수이며 CPU 모델·운영 workload를 자동 추정하지 않는다.

처리는 오래 걸리는 동안 `console.log`에 진행 상황을 기록한다. 실패·OOM·중단 시에도 runner가 반환할 수 있으면 `FAILED` 결과와 완료된 sample을 보존한다. 실패할 때는 같은 디렉터리의 `console.log`도 함께 확인한다. 외부 강제 종료·전원 종료로 runner의 마지막 저장이 실행되지 않으면 결과가 `RUNNING`일 수 있으며 완료로 취급하지 않는다.

runner는 Gradle 종료 코드와 report의 설정·scenario/sample 수·개별 성공 상태를 함께 확인한다. 매번 고유 디렉터리를 사용해 이전 성공 파일이 실패를 가리지 않게 한다. 결과 디렉터리는 `backend/build` 아래의 로컬 산출물로 git에 포함하지 않는다.

## fixture 범위

| profile | 정산 | 원장 |
| --- | --- | --- |
| smoke | 1정산 × 8결과, 3정산 × 3결과 | 3그룹 × 2revision: 정상, Work 참조/보정 연결 6건, 오류 3건 |
| standard | 1정산 × 100/1,000/10,000결과, 50/501정산 × 20결과 | 500그룹 × 1revision, 5,000그룹 × 10revision, 1그룹 × 50,001revision, Work 참조/보정 연결 5,000건, 오류 5,000건 |
| large | 1정산 × 100,000결과, 501정산 × 100결과 | 20,000그룹 × 20revision, 1그룹 × 500,001revision, Work 참조/보정 연결 100,000건, 오류 20,000건 |

정산은 생성된 정산/line 수·금액과 두 번째 실행의 무변경을 확인한다. 원장은 그룹/Entry 수·fingerprint 생성·ready와 예상 오류 수/종류를 확인한다. 오류 fixture의 `ready=false`는 예상된 정상 측정 결과다. 단순히 빨리 실패한 실행을 정상 처리 시간으로 기록하지 않는다.

원장 fixture는 `BASELINE_PREPARING` 상태의 수량 0 그룹과 합성 revision chain을 사용한다. Work 참조 fixture는 각 변경 Entry에 보정의 Mutation 출처·correlation·그룹 연결을 맞춘다. 이는 원장 reader의 누적 비용을 측정하는 자료이며 실제 Work 보정 writer 실행이나 ACTIVE cutover·활성 배치 분포·운영 데이터 대사를 측정한 결과는 아니다. 현재 보류 중인 일반 수정/실사/보정 기능을 활성화하지 않는다.

## 수치 해석

- **처리 시간:** application proxy 호출부터 반환까지이며 commit을 포함한다. HTTP 처리·응답 직렬화·fixture 준비·검증용 조회는 제외한다. 요약의 median/min/max와 원시 sample을 함께 본다. 기본 3회 sample을 안정적인 운영 p95나 SLA로 해석하지 않는다.
- **메모리:** heap 사용량을 10ms 간격과 시작/끝에 샘플링한 최대값이다. 짧은 peak는 놓칠 수 있다. 시작 heap과 종료 heap도 기록하며 fixture 잔여 객체·idle Spring context를 포함한 JVM 전체 수치다. 측정 스레드의 할당량은 살아 있는 객체의 크기·process RSS와 다르다. GC는 JVM 전체 collector의 count/time 차이다. 지원하지 않는 할당량은 null, GC counter는 -1로 표시한다. 측정을 위해 강제 GC를 호출하지 않는다.
- **transaction:** auto-commit이 꺼진 connection의 첫 관측 SQL부터 성공한 commit/rollback 또는 auto-commit 복원까지의 JDBC 시간이다. 최상위 application transaction 진입·connection 취득부터의 시간과 구분한다. sample별 개수·합계·최대와 미종료/관측되지 않은 종료 수를 기록한다.
- **잠금:** `FOR UPDATE`/`FOR SHARE` 계열 SQL의 성공 반환부터 transaction 종료까지의 관측 구간이다. 서버의 정확한 lock 취득/해제 시각은 아니다. locking statement 시간에는 SQL 실행·네트워크·잠금 대기가 함께 들어가며 순수 wait만 분리하지 않는다. lock 구문이 없는 원장 read-only transaction은 row-lock 관측 0이 정상이다. 경합 workload·vacuum 영향은 별도 운영 검증 대상이다.
- **JDBC/Entity:** 실행/batch·소비한 반환 행·commit/rollback과 Hibernate statement/load/flush 수를 함께 기록한다. JDBC 호출 수는 driver 내부 round trip이 아니고 반환 행은 DB scan 행 수가 아니다. 계측 wrapper와 heap sampler의 비용도 포함하므로 같은 도구/설정으로 비교한다.

시간·heap의 절대값으로 테스트를 실패시키지 않는다. 별도 `domainBenchmark` task/tag를 사용하며 일반 `test`, 기존 `workE2eTest`/`workBenchmark` 및 기본 CI에서는 대량 측정을 자동 실행하지 않는다. 이 도구 준비와 smoke 성공만으로 BE-036/042의 대량 측정·병목 개선을 완료 처리하지 않는다.

## 분석 기록

2026-10-05 사용자 제공 standard 결과의 수치·코드 대조·후속 우선순위는 [측정 결과 분석](13-domain-performance-results.md)에 기록했다. 10개 scenario·45개 sample 성공과 확인된 잔여 비용을 구분하며, 이후 전후 비교에는 동일한 profile/설정을 사용한다.

## 원인 조사용 별도 task

2026-10-06 [지연 원인 조사](14-performance-diagnosis.md)에 사용한 수동 도구다. 일반 benchmark 결과와 분리한다. 프로젝트 루트에서 다음처럼 실행한다. 출력 디렉터리는 실행마다 새 경로를 지정하고 `diagnosis.revision`에는 조사할 production revision을 기록한다. 도구 자체의 수정 여부는 이 값으로 보증하지 않는다.

```bash
cd backend
./gradlew domainDiagnosis -Pdiagnosis.scope=plans -Pdiagnosis.revision=82444a86 -Pdiagnosis.outputDir=build/domain-diagnosis/my-plans-run
```

| scope | 조사 범위 |
| --- | --- |
| plans | 통계 미갱신/갱신 조건의 원본 SQL custom/generic 계획, 각 1회 |
| stats | 위 두 조건의 실제 501정산 초기화, 각 2회·사전/사후 계획 |
| ledger | 동일 5,000그룹×1revision 정상/오류 교대, warmup 4회·측정 8회 |
| work | 500그룹×10revision·Work 참조 5,000건, warmup 4회·측정 8회 |
| all (기본) | stats·앞선 standard 정산 fixture 순서·전체 ANALYZE·custom 강제 조건 및 ledger. Work는 별도 scope로 실행 |

최대 heap은 2GiB다. 실제 application을 호출하고 outcome을 검증하며 결과는 `diagnosis.json`, 원장 JFR은 같은 디렉터리에 남긴다. scope별로 원시 sample 구조가 다르다. `plans`에는 writer의 elapsed/outcome 측정이 없으며, 초기 실패·외부 강제 종료·파일 미완성은 `PASSED`로 취급하지 않는다. 기존 standard runner의 sample 수 guard를 이 별도 도구에 적용하지 않는다.

새 Testcontainers PostgreSQL에만 extension preload·ALTER DATABASE/ANALYZE/plan 설정을 사용하며 운영 DB에 적용할 명령이 아니다. 일반 test/E2E/CI에서는 이 tag를 실행하지 않는다. Docker·JDK 21이 필요하고 15분 test timeout을 적용한다.

계획 비교·pg_stat_statements·JFR·fingerprint spy는 캐시/실행 비용에 영향을 준다. 사전 조회·EXPLAIN은 버퍼와 statement 사용 이력을 바꾸므로 benchmark 전후의 절대 시간과 직접 비교하지 않는다. 서버 통계 `track=all`의 nested FK 시간은 상위 insert 시간과 겹쳐 단순 합산하지 않는다. JFR frame 빈도는 정확한 CPU 시간 비율이 아니다. 원인 재현 후 효과 측정에는 동일 조건의 standard를 사용한다.
