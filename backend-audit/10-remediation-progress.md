# Backend 감사 — 현재 작업 목록

기준: 2026-10-06, 66차 조회 경계 조사·최신 운영 백업 대사·V35 조정 반영. 브랜치: `fix/backend-sales-reservation-consistency`.

완료된 1~66차 변경·커밋·검증 기록은 [보관된 개선 이력](archive/10-remediation-progress.md)에 있다. 초기 findings는 당시 판정이며 현재 미완료 목록으로 읽지 않는다. 현재 구현 정책은 `docs/`·코드·OpenAPI를 따른다.

**현재 요청 범위: 과거 운영 데이터·DB 제약 검증, V35 조정과 완료된 원장 이관 도구 제거.** 제공된 최신 백업의 격리 복원본에서 점검을 완료했다. 나머지 개선 구현은 사용자의 직접 코드 검토까지 보류한다. 판매 선택지 등 아래 기술 목록은 이후 재개 후보다.

아래는 기술 작업 5묶음·운영 검증 4묶음·정책 판단 3건이다. 미해결 버그 수나 필요 커밋 수가 아니다. 조건부 확장은 현재 필수 구현량에 포함하지 않는다.

## 사용자 코드 검토 후 재개할 기술 작업

| 작업 | 관련 finding | 남은 범위와 완료 기준 |
| --- | --- | --- |
| 정산 초기화·원장 대사의 대량 처리 비용 개선·측정 | BE-015/033/036/042 | 61차 도구·62/64차 standard 수집/전후 비교, 63차 Work Entity 누적·큰 ID 조회 개선, 65차 SQL/계획·코드 비교 조사는 완료했다. 별도 일괄 정산 구현 후 시작 자동 정산을 제거하기로 결정해 관련 TODO를 남겼다. 시작 경로 성능 수정은 보류하고, 재현한 통계 미갱신 join·cached 전체 스캔과 benchmark/plan 회귀는 별도 기능에서 조회 재사용 시 검증한다. 원장은 과거 오류/Work 시간 증가가 비교에서 재현되지 않았으며 필요한 후속 측정은 별개다. 정산별 commit·Entry cursor를 유지하며 필요한 large/운영 증적을 남긴다. 상세는 [측정 결과](archive/13-domain-performance-results.md)·[원인 조사](archive/14-performance-diagnosis.md) 참조. |
| 남은 목록·하위 이력·선택지의 조회 경계 | BE-032/034 | 66차 endpoint/실제 소비자/기존 회귀 조사는 완료했다. 다음은 판매 선택지의 페이지 이전 필터·전체 동별 집계·선택 ID 복원 계약과 구현이다. 이후 collection summary/member·경매 목록/선택 상세/이력을 분리한다. 일반/자동 그룹·resolver 단일 IN·Work 상세/중복 보정 load·계보·사전 scope/검색 ID·출하 후보 scan 등은 완전성/비용을 확인한 목적별 개선으로 남는다. Inbound 입력 상한·dev Mutation을 고려해 후순위로 두며 전체 farm map·작업 snapshot·수지 계산에 임의 cut하지 않는다. 상세와 회귀 완료 기준은 [조회 경계 조사](15-read-boundary-review.md) 참조. |
| 미측정 쿼리와 index 비용 검증 | BE-032/035 | contains/OR/concat·희귀 상태·count/집계·복합 조건·deep offset·generic prepared plan·미측정 FK 등의 실제 Repository 계획을 확인한다. 48·49차에서 검증한 참조/날짜/계보 index를 다시 미완료로 세지 않는다. 결과·행/loop/buffer·쓰기 비용을 비교해 필요한 개선만 채택한다. |
| 기존 접수·응답의 저장 계약 보호 범위 확대 | BE-013 | Mutation 및 Sales/일반 Work 생성의 v1 고정은 완료했다. 그 밖의 Work 실행·구조 기록·포트·취소·보정, Farm 입고·Auction 접수와 저장 응답에서 추가 보호가 필요한 shape/hash를 식별한다. 기존 golden·필드 변경 guard·구형 replay로 보호하며 과거 hash나 응답을 재작성하지 않는다. 신규 version 전환 자체는 아래 조건부 작업이다. |
| 핵심 경로의 선택적 회귀·해석 경계 보강 | BE-012/037/038/039 | 독립 transaction의 후행 실패·실제 잠금 충돌 관측이 빠진 중요한 writer와 보정 이벤트 수량 수지 해석을 선별한다. 필요한 PG/호환 회귀와 해당 경로의 fixture 정리를 추가한다. 모든 H2 시험의 wrapper 전환·모든 lifecycle 분해·자유 JSON의 일괄 타입화를 완료 목표로 삼지 않는다. |

기술 작업을 재개한다면 판매 묶음 선택지의 조회 경계 개선이 후보다. 목록 조사는 완료했고 API/소비자 전환 구현은 아직 하지 않았다. 시작 경로 성능 수정은 사용자 결정에 따라 보류했으며 필요한 정산 계획 회귀는 별도 일괄 기능의 조회 재사용 시 반영한다. Work 참조 적재 개선과 standard 전후 비교는 완료했지만 속도 개선이나 전체 시간 회귀 부재로 판정하지 않는다. large 한계와 실제 운영 부하는 별도 검증이다. 미측정 영역 전체에 성능 결함이 확인됐다는 의미는 아니다.

## 최신 운영 백업 점검 결과

대상: `temp/green-house_20261006_030001.dump.gz`. 백업 원본과 같은 PostgreSQL 14.24에서 V34 원본 복원본을 읽기 전용 대사하고, 두 번째 복원본에 V35~V42 SQL·제약별 validation을 리허설했다. 실제 운영 DB에는 적용하지 않았다. [검증 증적](archive/measurements/20261006-operational-data-audit.json)에 백업 SHA256·42개 점검 결과·실행 SQL·제약 상태·원장 결과를 보관한다.

| 영역 | 결과와 해석 |
| --- | --- |
| 예약·판매 계산·경매/정산 수지·원장/Work 연결 | 점검한 현재 snapshot의 invariant 위반 0건. 원장 CLI `ACTIVE`, `ready=true`, `issues=[]`: 묶음 320개·Mutation 377개·Entry 474개. 모든 과거 요청·삭제 이력의 완전성까지 입증하는 결과는 아니다. |
| 기존 CHECK 4개 | 원본은 모두 `NOT VALID`, 위반 0건. 별도 복원본에서 4개 모두 validation 성공. 운영 primary의 validation은 미적용. |
| 조정 전 V35 금액 CHECK 2개 | SQL 설치는 성공했지만 validation은 SQLSTATE `23514`로 실패·rollback, `NOT VALID` 유지. 품목 777/778/809/823, 음수 전표 134/137이 대상. V34 원본에서 미설치는 아직 V35 미적용인 정상 상태다. |
| 과거 반품 | 음수 품목 4개 모두 메모에 반품 표시, 수량×단가와 금액 일치. 영향 전표는 127/134/137의 3개. 계산 오류보다 과거 반품 표현과 신규 수량·금액 부호 제약의 충돌이다. 부호 변경·삭제·0원 보정 없이 보존했다. 조정 전 제약은 해당 행의 후속 수정도 막지만, 아래 V35 조정과 재검증으로 이 충돌을 해소했다. |
| 입금 추적 | 판매 148개에 저장 입금액이 있으나 입금 이벤트는 0개. 과거 입금 출처의 검증 한계이며 148건의 금액 오류로 확정하지 않는다. 은행/원전표 없이 이벤트를 합성하지 않는다. |
| Work 시간·경매 동일 결과 후보 | Work 40/79개는 종료일이 시작일보다 빠르다. 등록 시점 시작과 소급 완료일의 의미를 검토할 대상이다. 경매 동일 값 32묶음은 모두 같은 attempt의 복수 행으로 수지가 일치해 중복 요청으로 확정하지 않는다. 자동 시간 보정·중복 제거는 하지 않았다. |

V35~V42 SQL 8개는 복원본에서 성공했지만 Flyway 이력은 변경하지 않았다. 전체 Flyway 배포·애플리케이션 업무 회귀 완료를 의미하지 않는다. 최종 원장 CLI 실행 후에도 원본 47개 테이블의 기존 컬럼 전체 행 해시·건수가 동일하고 백업 SHA256도 보존됐다. 실행 로그는 무시되는 `temp/operational-audit/`에 남기고 임시 DB 컨테이너는 제거했다. 위 증적은 조정 전 V35의 결과이며 현재 제약 정의와 구분한다.

사용자 확인으로 운영 미적용 V35를 직접 수정했다. 수량/금액 부호와 전표 총액 비음수 조건을 제거하고, 품목의 0이 아닌 수량·비음수 단가·BIGINT 곱셈 일치를 유지한다. 현재 대사 inventory는 5개 CHECK다. 같은 운영 백업의 PostgreSQL 14.24 복원본에서 수정 V35~V42 SQL 적용과 5개 CHECK validation이 모두 통과했다. 기존 47개 테이블의 행 해시·건수와 백업 SHA256은 동일하며, 새 결과는 위 증적의 `afterUnappliedV35Revision`에 추가했다. Java 정확 연산과 일반 판매 입력 규칙은 유지한다. **거래 타입·음수 수량·비음수 금액 모델은 [전환 계획](../docs/features/sales-auction-settlement.md#반품-표현-전환-계획--미구현)만 기록하며 구현·과거 금액 변경은 하지 않는다.**

## 완료된 원장 이관 도구 제거

사용자 요청으로 `orchidStateChainMigrate`·`orchidLedgerCutover`와 전용 CLI·적재/활성화 service·Work 이관 API·profiling/정규화 스크립트·전용 시험을 제거했다. Engine·대사·기동 guard·Mutation/Entry/coverage와 Work/Sales/Lineage의 기존 데이터, Flyway는 보존한다. 이관 manifest와 당시 검증 기록은 `docs/archive/plans/`에 보관한다. 업무 회귀·benchmark의 초기 원장 구성은 운영 importer 대신 test source set의 fixture로 전환했고, 기존 schema 보존 시험도 별도 유지했다.

같은 운영 백업의 PostgreSQL 14.24 복원본에서 남긴 대사·기동 검증 CLI가 모두 성공했다. `ACTIVE`, `ready=true`, `issues=[]`, 묶음 320개·Mutation 377개·Entry 474개이며 원본 47개 테이블과 백업 SHA256은 보존됐다. 이 결과는 기존 JSON 증적의 `afterOperatorToolsRemoval`에 추가했다. 현재 release는 ACTIVE 백업 복원·검증을 사용하며 과거 전환 도구의 재실행은 제공하지 않는다.

## 운영 자료·환경이 필요한 검증

| 작업 | 관련 finding | 필요한 입력과 완료 기준 |
| --- | --- | --- |
| 과거 데이터 대사·조건부 복구 | BE-001~005/007/008/010/014 | 제공된 최신 백업의 42개 점검·원장 대사 완료. 반품 정책 충돌과 입금 출처/시간/중복 후보의 해석 한계는 위 결과 참조. 데이터 복구는 하지 않았다. 과거 삭제 사실·입금 사실·중복 요청은 원자료가 있어야 추가 검증할 수 있다. |
| 기존 CHECK의 실제 validation | BE-003/011 | 백업의 설치 정의·위반·convalidated 확인 및 복원본의 기존 4개 validation 성공. 조정 전 V35 금액 2개는 반품 기록으로 실패했다. 수정 V35를 적용한 복원본에서는 현재 5개 CHECK 전부 validation 성공. 실제 운영 primary validation은 별도 적용이다. Flyway 성공을 과거 행 검증 완료로 취급하지 않는다. |
| 운영 과거 요청·저장 JSON의 호환 대사 | BE-013 | 실제 과거 요청 자료와 receipt/Mutation/응답 자료를 확보해 현재 reader·fingerprint·replay와 비교한다. 합성 golden은 실제 운영 corpus를 대체하지 않는다. 원문을 추정하거나 현재 Entity로 과거 snapshot을 만들지 않는다. |
| 운영 부하·index 배포 비용 확인 | BE-015/033/035/036/042 | 운영 규모·분포·PostgreSQL 통계·동시 요청 조건에서 지연/처리량·heap/GC·잠금 대기/보유·긴 snapshot의 vacuum 영향·index 쓰기/WAL/HOT/build 공간·배포 시간을 확인한다. 로컬 측정과 실제 운영 적용 결과를 구분해 증적을 남긴다. |

확인된 금액 CHECK 위반은 과거 반품 표현과의 충돌이다. 입금 이력 부재·시간 순서·경매 동일 값 후보를 운영 수량/금액 오류나 중복 요청으로 일괄 확정하지 않는다. 운영 DB 수정·validation·배포는 별도 적용 단위로 수행한다.

## 정책 보류와 적용 범위 판단

| 상태 | 관련 finding | 남은 판단 |
| --- | --- | --- |
| 사용자 요청으로 보류 | BE-009 | 일반 수정의 허용 범위와 `RECONCILIATION` 분류·Work 생성 여부. 기존 일반 수정의 Audit/Mutation과 실사/보정 gate를 유지한다. 관련 코드·API·기능 활성화는 변경하지 않는다. |
| 추가 적용 범위 판단 | BE-008 | 일반 생성에 포함하지 않은 입고 포트 계획·폐기 기록 등과 키 없는 연동에 재시도 방어가 필요한지, 같은 입력의 새 업무와 replay를 어떻게 구별할지 정한다. 현재 키 기반 Sales·입고·일반 Work 생성은 구현 완료다. |
| 추가 감사 범위 판단 | BE-010 | 입고·inline 신규 품종 등에 일반 AuditEvent가 추가로 필요한지 판단한다. 기존 WorkEffect·Mutation·Auction 자체 이력을 먼저 확인하며 업무 원장이 있는 경로를 일반 감사 부재만으로 무기록으로 취급하지 않는다. 전표 생성·묶음 metadata 감사는 구현 완료다. |

뒤의 두 항목은 범위 판단이 필요한 사항이며 사용자가 보류한 BE-009와 구분한다. 모든 생성 경로에 멱등 키나 일반 감사 이벤트를 일괄 추가하지 않는다.

## 해당 변경이 생길 때 수행할 조건부 작업

- **새 영속 필드/형식 도입 — BE-013:** 실제 version dispatcher·upgrade, absent/null/default 의미, reader-first 배포, 구버전 writer 병행·rollback 시험. 현재 형식을 바꾸지 않는 동안 신규 형식 migration을 만들 필요는 없다.
- **새 Work 유형·비 HTTP 채널 도입 — BE-014/019:** 새 저장 이름·strategy·계보·취소/replay의 전체 흐름 회귀와 채널별 검증·주체·인가·감사 context. 미래 유형·채널을 미리 구현하지 않는다.
- **새 보상·정산 기능 도입 — BE-007/044:** 경매 결과/반환 이후 정정·보상 이벤트, 월간 정산·자동화·입금 분배·예치금. 별도 기능 범위와 업무 계약을 정한 후 진행한다.
- **테스트/architecture gate 밖의 실제 문제가 확인될 때 — BE-039/040/041/042:** 나머지 fixture 중복·긴 lifecycle 정리, context/JDBC 강제 종료·executor 종료 정책, 동적/alias SQL·reflection·새 원자 writer 검사, driver 내부 round trip 계측을 필요 범위에서 보강한다. 완전한 SQL parser나 모든 writer의 무교착 증명을 독립적인 필수 구현 목표로 두지 않는다.

## 완료·검증 기준

- 원래 BE-001~044의 구현 결과는 보관 이력에서 확인한다. 해당 기록의 과거 “남은 범위”를 현재 미완료로 중복 집계하지 않는다.
- 최신 전체 검증: 원장 이관 도구 제거 후 backend 142개 클래스·741건, PostgreSQL `workE2eTest` 77개 클래스·788건, frontend `npm run check` 성공. 실패·오류·skip은 0건이다. backend 시험은 임시 init script의 test heap 2GiB로 실행했으며 repository의 heap 설정은 변경하지 않았다. `bootJar`와 task 목록도 확인해 제거된 운영 도구·test fixture가 배포 JAR에 포함되지 않음을 검증했다.
- 66차 조회 조사는 정적 코드/소비자/시험 대조다. 조회 개선 구현·새 부하 측정 완료를 의미하지 않는다.
- 최초 백업 점검은 실제 PostgreSQL 14.24 대사·원장 CLI·DDL 리허설로, 당시에는 제품 코드 변경 없이 전체 시험을 반복하지 않았다. 이후 V35 조정은 PostgreSQL 회귀 3개 클래스·41건과 운영 백업의 CHECK 5개 validation으로 별도 검증했다. 이관 도구 제거 후 전체 PostgreSQL suite와 남긴 대사·기동 CLI를 다시 검증했고 benchmark는 재실행하지 않았다. HTTP 계약 변경이 없어 OpenAPI·생성 타입은 변경하지 않았다.
- 문서 보관을 운영 자료 대사·제약 validation·보류 정책의 완료로 취급하지 않는다.
