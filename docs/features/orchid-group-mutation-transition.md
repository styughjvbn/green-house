# 난 묶음 Mutation Engine 유지 범위

2026-09-08부터 신규 배포 코드는 Engine 단일 writer다. 2026-10-06 사용자 요청으로 과거 state-chain 이관·coverage 준비·활성화 도구도 제거했다. 현재 복원·검증 절차는 [배포 가이드](../07-deployment.md)를 따른다.

## 유지 범위

| 분류 | 범위와 목적 |
|---|---|
| `TARGET` | Mutation Engine, typed command/result, 원인 identity와 지문, 연속 revision·snapshot, Work·Sales 연결, coverage·DB write fence·reconciliation·startup guard |
| `DATA_RETAIN` | 기존 Flyway 이력, Mutation/Entry와 삭제 tombstone, coverage와 manifest provenance, Work·Sales·Lineage 사실 데이터 |

Entity 직접 상태 변경·생성자·Repository 쓰기는 Engine만 허용하고 `OrchidGroupWriterArchitectureTest`가 이 경계를 검사한다. 테스트 초기 원장은 test source set의 fixture로 구성하며 운영 writer를 추가하지 않는다. 기존 Lineage와 Work snapshot, HTTP 호환 mapper와 과거 source 해석은 현재 조회·계약에 필요하므로 유지한다.

`/mutation-lab`은 `APP_ENV=dev`에서만 Mutation 헤더, Entry의 전후 snapshot과 revision,
보정·보상 관계를 읽기 전용으로 확인하는 진단 화면이다. 조회 API는 root Mutation을 먼저
페이지로 읽고 해당 페이지의 Entry와 연결 관계를 일괄 조회한다. `prod`에서는 메뉴와
페이지, `/api/orchid-group-mutations` API를 모두 비활성화한다. 이 화면은 원장을 수정하거나
replay하는 운영 도구가 아니며, write fence를 우회하지 않는다. 난 묶음 ID를
선택하면 `GET /api/orchid-group-mutations/graph/{orchidGroupId}`가 제한된 깊이와 노드 수로
Mutation과 계보를 결합한 read model을 반환한다. 난 묶음 revision snapshot과 Mutation을
교대로 배치하고, `TRANSFORM`의 `SOURCE`·`RESULT` Entry로 분기·합류를 구성한다. 연결된
Lineage의 `mutationId`로 분주·합식·분갈이 의미를 결과 edge에 붙이고 보정·보상·대체 관계도
별도 edge로 표시한다. 구조 변경 결과가 여러 개이면 원본의 revision 흐름은 직선으로 유지하고,
결과 edge는 공통 분기점까지 묶은 뒤 신규 난 묶음별로 갈라 표시한다. 제한을 넘는 이력은 응답과
화면에서 잘림을 명시한다. 상태 node는 snapshot의 품종과 반개구간 배치 좌표를 보존해 보여주며,
좌표는 화면에서 양끝을 포함하는 칸 범위로 변환한다. 동·다이·측 명칭은 snapshot의 `bedZoneId`를
현재 농장 구조에 일괄 매핑하므로 구조 명칭 자체가 바뀐 경우에는 현재 명칭으로 표시한다.

## 제거 완료 범위

Farm·Inbound의 직접 생성·수정·이동·품종 전파, Work 폐기·구조 변경·보정,
Sales 예약·해제·출고·복구의 Legacy 분기를 제거했다.
`OrchidGroupLedgerWriterMode`, `OrchidGroupMutationRoutingPolicy`,
`OrchidGroupReservationService`, `createEntity`와 미사용 직접 이동 메서드도 제거했다.

모드별 parity 테스트는 Engine 결과 순서·속성·계보·롤백 검증으로 유지한다. 과거 Sales
복구 테스트는 명시적인 전환 전 데이터 fixture를 사용하며 Legacy writer를 되살리지 않는다.

## 복원과 배포 경계

현재 release는 ACTIVE 원장을 포함한 백업 복원 후 read-only 대사와 schema·기동 검증을 수행한다. 최신 제공 백업의 대사·제약 검증은 [현재 감사 결과](../../backend-audit/10-remediation-progress.md)에 기록한다. 과거 이관 결과는 [보관 기록](../archive/plans/orchid-engine-cutover-20260908.md)이며 현재 실행 절차가 아니다.

원장 없는 기존 난 묶음 또는 PREPARING coverage의 업무 서버 기동은 계속 거부하고 ACTIVE의 최소 writer version을 검사한다. 빈 DB는 Engine으로 신규 생성할 수 있다. startup guard·DB fence를 끄거나 현재 Entity에서 과거 원장을 다시 만들어 복구하지 않는다. 운영 데이터의 삭제·자동 보정·기존 Flyway 파일 변경은 이번 제거 범위에 포함하지 않는다.
