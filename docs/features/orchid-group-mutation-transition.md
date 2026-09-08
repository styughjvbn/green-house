# 난 묶음 Mutation Engine 전환과 복구

2026-09-08부터 신규 배포 코드는 Engine 단일 writer다. `LEGACY|ENGINE` 라우팅과
직접 변경 분기, 예약 라우팅 래퍼는 제거했다. 과거 백업에서 다시 전환하는 복구 도구와
사실 데이터는 유지한다. 절차는 `../07-deployment.md`를 따른다.

## 유지 범위

| 분류 | 범위와 목적 |
|---|---|
| `TARGET` | Mutation Engine, typed command/result, 원인 identity와 지문, 연속 revision·snapshot, Work·Sales 연결, coverage·DB write fence·reconciliation |
| `RECOVERY` | complete state-chain manifest 검증·적재, Work source 조회·연결, coverage 준비·VERIFY·ACTIVE CLI. V20 백업을 복원할 때 사용하며 HTTP 업무 API에서 호출하지 않는다. |
| `DATA_RETAIN` | 기존 Flyway 이력, Mutation/Entry와 삭제 tombstone, coverage와 manifest provenance, Work·Sales·Lineage 사실 데이터 |

Entity 상태 메서드는 Engine이 불변 조건을 적용하는 데 필요하다. 직접 상태 변경자는
Engine과 복구 importer만 허용하며 생성자·Repository 쓰기는 Engine만 허용한다.
`OrchidGroupWriterArchitectureTest`가 이 경계를 검사한다. 허용 목록을 늘려 신규 업무
writer를 우회시키지 않는다.

복구 도구는 대체 복구 절차가 마련되기 전까지 유지한다. 별도 런타임 모듈이나
fallback writer를 추가하지 않는다. 단일 원본 Lineage와 Work 결과 snapshot도 현재
조회 소비자가 있으므로 보존한다. `LegacyStructureChangeRequestMapper`와 과거
source 표시는 기존 HTTP 계약·이력 해석이며 제거한 직접 writer와 구분한다.

## 제거 완료 범위

Farm·Inbound의 직접 생성·수정·이동·품종 전파, Work 폐기·구조 변경·보정·다중 생성과
취소, Sales 예약·해제·출고·복구의 Legacy 분기를 제거했다.
`OrchidGroupLedgerWriterMode`, `OrchidGroupMutationRoutingPolicy`,
`OrchidGroupReservationService`, `createEntity`와 미사용 직접 이동 메서드도 제거했다.

모드별 parity 테스트는 Engine 결과 순서·속성·계보·롤백 검증으로 유지한다. 과거 Sales
복구 테스트는 명시적인 전환 전 데이터 fixture를 사용하며 Legacy writer를 되살리지 않는다.

## 전환 판단과 배포 경계

사용자 승인에 따라 추가 대기 기간 대신 최신 저장 백업의 복원·전체 이력 적재·재실행·
VERIFY·ACTIVE·회귀 검증을 진행한다. 검증한 백업은
`temp/green-house_20260826_030001.dump.gz`이다. 상세 결과는
`orchid-engine-cutover-20260908.md`에 기록한다. 격리 복원 DB의 성공과 운영 Kubernetes
배포 완료를 같은 상태로 보고하지 않는다.

새 런타임은 기본 writer version `2.0.0`을 사용한다. 원장 없는 난 묶음 또는
`PREPARING` coverage가 있으면 기동을 거부한다. ACTIVE에서는 coverage 최소 버전도
검사한다. 빈 DB는 바로 Engine으로 생성할 수 있다. 복구 CLI만 startup guard를 끈다.

롤백은 백업 복원 또는 동일 manifest의 검증된 재실행이다. Legacy flag 복귀, 원장
checksum repair, 과거 요청 원문 추정, 운영 이력 삭제는 복구 방법으로 사용하지 않는다.
