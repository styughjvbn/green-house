# ADR-001: OrchidGroup 상태 변경을 Farm Mutation Engine으로 단일화한다

- 상태: 승인
- 결정일: 2026-08-20
- 범위: Backend `farm`, `work`, `sales`, `auction`, `settlement` 모듈과 입고 기능

## 배경

`OrchidGroup`은 `farm` 모듈이 소유하지만 현재 상태 변경은 여러 업무 흐름에서
발생한다.

- Farm 관리: 생성, 상세 수정, 이동, 생성 취소와 삭제
- Work: 폐기, 자리 이동, 분갈이, 분주, 합식, 다중 생성, 보정
- Inbound: 즉시 배치 입고와 포트 작업 결과 생성
- Sales: 예약, 예약 해제, 출고, 출고 취소

각 업무는 고유한 lifecycle과 이력을 가지고 있으며 현재도 다음 기록을 별도로
보존한다.

- `WorkAppliedEffect`: 작업 실행 회차와 멱등성
- `WorkEffectOrchidGroup`: 작업 효과의 원본·결과 연결
- `OrchidGroupLineage`: 호환 가능한 직접 파생 관계
- `SalesInventoryMovement`: 판매 예약·출고의 업무 이력
- `AuditEvent`: 행위자와 요청 맥락을 포함한 운영 변경 감사

이 기록들은 각 업무 사실을 설명하지만 모든 `OrchidGroup` 상태 변경을 동일한
규칙으로 적용하거나, 하나의 원자적 변경 전후 상태로 연결하지는 않는다. 이
상태에서 감사 테이블만 추가하면 직접 변경 경로가 계속 남아 ledger의 완전성을
보장할 수 없다.

궁극적인 목표는 `OrchidGroup`의 모든 운영 상태 변경을 하나의 엔진으로
전환하고, 적용 기준 시점 이후의 모든 상태 revision을 불변 mutation ledger로
설명할 수 있게 하는 것이다.

## 결정

### 0. Revision

`stateRevision`은 하나의 OrchidGroup aggregate가 Mutation Engine을 통해 확정한 불변 상태 버전의 순번이다. 하나의 `revision`은 해당 시점의 수량뿐 아니라 품종, 예약 수량, lifecycle 상태, 위치, 배치 범위, 화분·년생 등 Mutation 관리 대상 전체 상태를 나타낸다.

예를 들어:
```text
MutationEntry
beforeRevision = N
afterRevision  = N+1
```
의 뜻은
"N번째 전체 상태가 하나의 Mutation에 의해 N+1번째 전체 상태로 전환" 을 의미한다.

### 1. Farm이 상태 변경 엔진을 소유한다

현재 `OrchidGroup`과 `orchid_groups` 테이블의 소유 Bounded Context는 `farm`이다.
상태 변경 엔진도 독립적인 업무 모델이 아닌 `OrchidGroup` 변경 메커니즘이므로
별도 기술적 top-level `mutation` 모듈을 만들지 않고 `farm`의 `orchid` 기능
경계 안에 둔다.

이 결정은 `OrchidGroup` 도메인의 영구적인 top-level 분리 금지를 의미하지
않는다. 향후 독립된 ubiquitous language와 consistency boundary가 형성되면 엔진만
분리하지 않고 `OrchidGroup` Aggregate, Repository, Reservation, Lineage와 ledger
전체를 `OrchidInventory` Bounded Context로 추출하는 결정을 다시 검토한다.

개념적인 구성은 다음과 같다.

```text
Work / Sales / Inbound / Farm command
        │ 업무 의도, lifecycle, 최상위 transaction
        ▼
Typed Farm application contract
        ▼
OrchidGroupMutationEngine
 ├─ 대상과 목적지 잠금
 ├─ 현재 상태 조회
 ├─ 공통 invariant 검증
 ├─ OrchidGroup 상태 적용
 ├─ state revision 증가
 └─ Mutation과 MutationEntry 저장
        │
        ├─ orchid_groups: 현재 상태
        └─ mutation ledger: 불변 변경 이력
```

다른 모듈은 `OrchidGroupRepository`를 참조하거나 관리 상태의 `OrchidGroup`
엔티티를 받아 직접 수정하지 않는다. 모듈 간 계약에는 난 묶음 ID, 값 객체,
command와 result DTO를 사용한다. DB FK가 필요한 경우에도 다른 모듈의 JPA
엔티티 연관 대신 식별자 컬럼을 우선한다.

### 2. 완전 전환은 Event Sourcing을 의미하지 않는다

`orchid_groups`는 계속 현재 상태의 source of truth다. 일반 조회는 ledger를
replay하지 않는다.

```text
Current materialized state + Immutable mutation ledger
```

Mutation ledger는 변경 이력, 정합성 검증, Timeline과 향후 과거 시점 조회의
기반이다. 이 결정만으로 Event Sourcing, checkpoint snapshot 또는 모든 조회의
replay 전환을 도입하지 않는다.

### 3. 상위 업무와 엔진의 책임을 분리한다

Work, Sales, Inbound와 Farm command service는 다음을 계속 결정한다.

- 요청한 업무 상태 전이가 가능한가
- 어떤 대상과 수량을 변경해야 하는가
- 보정 또는 보상을 허용할 수 있는가
- 어떤 업무 이력과 상태를 함께 저장해야 하는가

Mutation Engine은 다음을 담당한다.

- 대상 난 묶음과 목적지 구조 잠금
- 현재 상태를 기준으로 한 공통 수량·예약·배치 invariant 검증
- 생성, 속성 수정, 수량·예약·상태·위치 변경과 N:M transform 적용
- 변경 전후 상태와 원인을 mutation ledger에 저장
- 동일한 정규화 Mutation command의 중복 실행 방지

엔진은 Work, Sales, Inbound의 workflow를 대신 판단하지 않는다.
WorkOperation 생성이나 Work 효과 실행 자체의 멱등성도 대신 소유하지 않는다.
상위 application 계층은 자신의 요청·효과 멱등성을 먼저 보장하고, 엔진은 전달된
물리 상태 변경 command의 멱등성을 독립적으로 보장한다.

의존 방향은 호출 업무 모듈에서 Farm의 공개 Mutation application contract로만
향한다. Farm Engine과 domain policy는 `WorkOperation`, `SalesSlip`, 각 모듈의 DTO나
handler interface를 받지 않는다. 업무별 adapter는 호출 모듈에 두고 난 묶음 ID,
값 객체와 source identity를 Farm command로 번역한다. 현재 Farm에 있으면서 Work
타입을 직접 구현·참조하는 효과 handler와 구조 변경 facade는 전환용 구조로 보고
최종적으로 제거한다.

### 4. 외부 계약은 의도를 표현하는 typed command로 제한한다

상위 모듈이 임의의 필드 변경 목록을 전달하는 범용
`ExecuteOrchidGroupMutationCommand`를 공개하지 않는다. 공개 application
contract는 최소한 다음 의도를 구분한다.

```text
CreateOrchidGroup
UpdateOrchidGroupDetails
MoveOrchidGroup
ReserveQuantity
ReleaseReservation
ConsumeReservation
RestoreOutboundQuantity
DiscardQuantity
TransformOrchidGroups
CorrectOrchidGroups
CompensateMutation
CancelCreation
```

typed command는 엔진 내부에서 공통 execution plan으로 변환할 수 있다. 공통
plan과 저수준 필드 변경 API는 엔진 구현 밖으로 노출하지 않는다.

단일화 대상은 상태 변경 경로이지 모든 상태 축이 아니다. 최소한 다음 축은
직교하는 값과 invariant로 유지한다.

- lifecycle과 종료 사유
- 총수량·예약수량·가용수량
- 논리 구역·정렬 순서·배치 범위
- 품종·년생·화분 크기 등 재배 속성
- 분주 예정·별도 보관 같은 관리 의도
- 입고·변환과 원본·결과 provenance

이 축을 하나의 거대한 status enum으로 합치지 않는다. 하나의 command가 여러 축을
원자적으로 바꿀 수는 있지만 command별 변경 가능 축을 명시한다. 특히 일반 상세
수정 command로 수량·예약·lifecycle·위치를 임의 설정하지 않고 각각의 전용
command와 policy를 통과시킨다.

HTTP API는 기존 Work, Sales, Inbound, OrchidGroup API를 유지한다. Mutation을
직접 생성·수정·삭제하는 외부 CRUD API는 만들지 않는다.

### 5. 엔진은 모든 OrchidGroup 운영 상태 변경의 유일한 경로다

완전 전환 후 다음 변경은 모두 엔진을 통과한다.

- 난 묶음 생성과 생성 취소
- 품종, 년생, 화분 크기와 배치 속성 수정
- 수량, 예약 수량과 상태 변경
- 논리 구역, 정렬 순서와 숫자 배치 구간 변경
- 폐기, 출고와 출고 취소
- 분갈이, 분주, 합식, 자리 이동과 다중 생성
- 완료 작업 결과의 보정과 업무 취소에 따른 보상

하나의 요청에서 여러 필드를 변경하면 하나의 원자적 Mutation으로 기록한다.
같은 난 묶음을 같은 Mutation에서 여러 번 수정하지 않고 최종 전후 상태를 한
Entry로 표현한다.

적용 기준 시점 이후 `OrchidGroup`을 물리 삭제하지 않는다. 삭제가 허용되던
사용자 흐름은 생성 취소 또는 명시적인 비활성 상태 전이로 바꾸고 Mutation을
남긴다. 운영 이력과 연결된 상태는 기존과 같이 보정·취소 기록을 우선한다.

### 6. Reservation 변경도 엔진과 ledger를 통과한다

`reserved_quantity`는 `OrchidGroup` 현재 상태와 가용 수량 계산의 일부다. 따라서
판매 예약, 해제, 소비와 복구도 Farm의 typed reservation command를 사용하고
MutationEntry에 전후 값을 기록한다.

`SalesInventoryMovement`는 판매 도메인의 예약·출고 이력으로 유지한다. 향후
별도 Reservation aggregate가 필요하면 source별 예약 lifecycle을 표현하기 위해
추가하되, `reserved_quantity`의 materialized 값과 실제 변경은 계속 엔진이
관리한다.

### 7. 최상위 업무 서비스가 트랜잭션을 소유한다

Mutation Engine은 호출한 최상위 application transaction에 참여한다. 엔진
내부에서 별도의 `REQUIRES_NEW` transaction을 만들지 않는다. 엔진을 독립적으로
호출할 수 없게 `MANDATORY` 전파 수준 또는 동등한 구조적 제약을 사용한다.

다음은 각각 하나의 transaction으로 처리한다.

```text
Work validation
→ Mutation
→ WorkAppliedEffect와 SOURCE/RESULT 연결
→ 필요한 Lineage
→ 대상 및 WorkOperation 상태 변경
```

```text
Sales validation
→ Mutation
→ SalesInventoryMovement
→ Sales 상태 변경
→ 경매 출하인 경우 Shipment/Lot 생성
```

```text
Inbound validation
→ Mutation
→ Inbound와 Work 이력 연결
→ Inbound 상태 변경
```

Mutation 또는 연계 이력 저장이 실패하면 현재 상태 변경도 함께 rollback한다.

하나의 최상위 요청이 여러 WorkOperation, WorkAppliedEffect 또는 Mutation을 만들
수 있다. 이들은 같은 transaction에 참여할 수 있지만 하나의 멱등 키나 하나의
이력 행으로 합치지 않고 공통 `correlationId`로 연결한다.

### 8. 잠금 순서와 DB 보호를 고정한다

수정할 기존 `OrchidGroup`은 ID 오름차순으로 잠근다. 생성·이동·transform 결과
배치처럼 목적지가 있는 변경은 관련 `BedZone`도 고정된 ID 순서로 잠근 뒤 배치
겹침과 `sortOrder`를 검증한다.

Application 검증만으로 동시성 안전을 가정하지 않는다. 다음 수단을 함께
사용한다.

- `OrchidGroup` version 또는 명시적 비관적 잠금
- Engine이 직접 증가시키는 `state_revision`
- `quantity >= 0`
- `0 <= reserved_quantity <= quantity`
- source operation의 DB UNIQUE 제약
- 필요한 배치 범위와 순서 제약

PostgreSQL lock과 constraint는 실제 PostgreSQL 또는 Testcontainers 테스트로
검증한다.

운영 전환이 `ACTIVE`가 된 뒤에는 애플리케이션 구조 검사만으로 단일 writer를
보장하지 않는다. `orchid_groups` INSERT·UPDATE는 Engine이 설정한 transaction-local
Mutation context가 있을 때만 허용하고 DELETE는 거부하는 PostgreSQL write fence를
둔다. Engine은 같은 transaction에서 Mutation identity를 먼저 확보하고 context를
설정한 뒤 상태를 flush한다. 기존 버전 애플리케이션, 남은 직접 저장 코드와 일반
운영 SQL은 이 context가 없으므로 커밋할 수 없다.

complete state-chain 이관은 `ACTIVE` 이전의 제한된 migration context로만 수행한다. write
fence의 활성화 순서와 context 누락·위조·rollback 동작은 실제 PostgreSQL에서
검증한다. 이 fence는 권한 체계를 대신하는 보안 장치가 아니라 잘못된 writer와
혼합 버전 배포를 막는 정합성 장치다.

### 9. 요청·효과·Mutation 멱등성의 축을 분리한다

멱등성은 다음 인과관계의 서로 다른 단계에서 보장한다.

```text
WorkCommandReceipt
  request scope + idempotency key + request fingerprint
        │
        ├─ WorkOperation A
        └─ WorkOperation B
                │
                ├─ WorkAppliedEffect TARGET
                ├─ WorkAppliedEffect EXECUTION-1
                └─ WorkAppliedEffect EXECUTION-2
                              │
                              ▼
                    OrchidGroupMutation
                              │
                 ┌────────────┼────────────┐
                 ▼            ▼            ▼
              Entry A      Entry B      Entry C
```

각 단계의 책임은 다음과 같다.

| 단계 | 멱등 기준 | 보호 대상 |
|---|---|---|
| 외부 Work 요청 | `WorkCommandReceipt` | 같은 사용자 명령의 작업 중복 생성 |
| Work 효과 | `WorkAppliedEffect` | 같은 대상 효과나 실행 회차의 재적용 |
| 물리 상태 변경 | `OrchidGroupMutation` | 같은 난 묶음 상태 변경 command의 재적용 |
| 대상 진행 상태 | `WorkTargetExecution` | 누적 처리 수량과 대상 lifecycle |

`WorkTargetExecution` 행 잠금, 상태 전이와 `processedQuantity` 상한은 멱등 키가
아니다. 잘못된 새 키나 동시 요청으로 인한 초과 실행을 막는 별도의 concurrency와
domain invariant다.

#### Work 요청 멱등성

2026-09-08 적용 범위: 현재 키가 있는 즉시 실행·구조 변경 기록·포트 기록에 receipt를
도입했다. 아래의 키 없는 일반 계획·기록까지 확장하는 목표는 요청 계약을 정한 뒤
적용한다. 현재 receipt는 범위를 포함한 키·지문·결과 ID를 저장하며 request correlation
전파는 추가하지 않았다. 효과 scope는 기존 key prefix에 유지하고 DB UNIQUE는
`(work_operation_id, effect_key)`로 맞췄다. `effect_kind`는 identity에서 제외했다.


외부 요청의 멱등성은 Work application 계층이 소유한다. 일반 작업 계획과 batch
생성을 포함해 하나의 요청이 여러 WorkOperation을 만들 수 있으므로 최종적으로
`requestKey`를 WorkOperation 한 행에만 저장하는 방식에 의존하지 않는다.

개념적인 receipt는 다음 의미를 가진다.

```text
WorkCommandReceipt
- command_scope
- idempotency_key
- request_fingerprint
- correlation_id
- status
- result WorkOperation IDs
```

동일 scope와 key에 동일 fingerprint가 들어오면 기존 결과를 반환한다. 다른
fingerprint이면 안정적인 `IDEMPOTENCY_KEY_REUSED` 충돌로 거부한다. receipt의
저장 방식과 결과 연결 테이블은 Work 구현 단계에서 구체화한다.

#### Work 효과 멱등성

`TARGET:123`, `EXECUTION:key`, `POTTING:key` 같은 문자열 prefix에 업무 의미를
숨기지 않고 구조화된 효과 identity를 사용한다.

```text
WorkEffectIdentity
- scope: OPERATION | TARGET | EXECUTION
- key
```

WorkAppliedEffect의 유일성 기준은 다음 의미로 통일한다.

```text
(work_operation_id, effect_scope, effect_key)
```

`effect_kind`는 효과 속성이지 identity의 일부가 아니다. Repository의 조회
기준과 DB UNIQUE 기준은 같아야 한다. WorkAppliedEffect에도 정규화된 effect
command의 fingerprint를 저장하고 같은 identity를 다른 payload에 재사용하면
거부한다.

부분 실행의 `processedQuantity`, 완료 상태와 마지막 효과 적용 시점은 효과
identity와 분리한다. 부분 실행이 한 번 있었다는 이유만으로 대상 전체가 완료된
것으로 판단하지 않는다.

#### 단계별 fingerprint와 correlation

다음 fingerprint는 목적이 다르므로 같은 값으로 강제하지 않는다.

- request fingerprint: 정규화된 외부 Work 요청
- effect fingerprint: 실제 Work 효과 command
- mutation fingerprint: Farm Engine에 전달된 물리 상태 변경 command

각 fingerprint는 해당 단계에서 validation과 normalization을 마친 semantic
command를 canonical serialization한 뒤 계산한다. JSON object key 순서 같은 표현
차이는 제거하고, 의미 있는 배열 순서는 보존한다.

하나의 사용자 요청에서 파생된 WorkOperation, WorkAppliedEffect와 Mutation에는
같은 `correlationId`를 전파한다. 직접 원인은 별도의 source identity로 표현한다.

### 10. Mutation은 원자적 변경과 재실행 식별자를 가진다

Mutation 본체는 최소한 다음 의미를 가진다.

```text
mutation_type
source_domain
source_type
source_reference_id
source_operation_key
correlation_id
command_fingerprint
recorded_at
effective_business_date
reason
schema_version
```

- `recorded_at`: 실제 DB 반영 시각이며 UTC `Instant` 기준이다.
- `effective_business_date`: 업무상 적용일이며 농장 시간대 기준이다.
- `source_operation_key`: 같은 상위 객체에서 여러 효과나 실행 회차를 구분한다.
  Work가 원인이면 구조화된 WorkEffectIdentity를 안정적으로 직렬화한 값이다.
- `correlation_id`: 같은 최상위 요청에서 파생된 여러 작업·효과·Mutation을 묶는다.
- `command_fingerprint`: 같은 source operation key에 다른 물리 변경 command를
  재사용하는 것을 거부한다.

다음 조합은 유일해야 한다.

```text
(source_domain, source_type, source_reference_id, source_operation_key)
```

같은 키와 같은 command를 재요청하면 기존 결과를 반환하고, 같은 키에 다른
command를 사용하면 충돌로 거부한다.

Work가 원인인 Mutation은 `WorkOperation.requestKey`가 아니라 Work 효과 identity를
직접 원인으로 사용한다.

```text
source_domain        = WORK
source_type          = WORK_EFFECT
source_reference_id  = workOperationId
source_operation_key = effectScope + ":" + effectKey
correlation_id       = 최상위 Work command correlationId
```

일반 작업 계획 생성처럼 `OrchidGroup` 상태를 바꾸지 않는 요청에는 Mutation을
만들지 않는다.

### 11. MutationEntry는 존재 여부와 revision을 구분한다

하나의 Mutation은 여러 `OrchidGroup`을 변경할 수 있으므로 Entry를 분리한다.
각 Entry는 다음 의미를 가진다.

```text
mutation_id
orchid_group_id
entry_kind: BASELINE | CREATE | CHANGE
role: SOURCE | RESULT | AFFECTED
state_revision_before
state_revision_after
before_state nullable
after_state nullable
```

기존 운영 행을 ledger에 편입하는 BASELINE은 `before_state = null`,
`state_revision_before = null`, `state_revision_after = 0`으로 저장한다. 적용 기준
시점 이후 CREATE는 `before_state = null`, `state_revision_after = 1`로 시작한다.
일반 CHANGE는 현재 revision `n`을 `n + 1`로 변경한다. 논리적으로 존재하는 수량
0 그룹은 수량 0인 `after_state` snapshot으로 구분한다. 적용 기준 시점 이후에는
물리 삭제를 사용하지 않으므로 일반 Mutation의 `after_state`는 존재한다.

replay와 정합성 검사에 필요한 상태는 versioned snapshot schema로 저장한다.
최소 범위는 다음과 같다.

- quantity, reservedQuantity, status
- bedZoneId, sortOrder, startPosition, endPosition
- varietyId, genus, varietyName
- ageYear, potSizeCode
- placementType, trayCount, splitPlacementAllowed
- 필요 시 inboundRecordId와 기타 운영 속성

자유 입력 메모처럼 별도 보존 정책이 필요한 값은 schema 확정 시 감사·보안
정책을 함께 검토한다. 엔진 경유 자체는 예외 없이 유지한다.

같은 난 묶음의 연속된 Entry는 다음을 만족해야 한다.

```text
previous.state_revision_after = next.state_revision_before
```

BASELINE은 기존 난 묶음의 생성 사건을 의미하지 않는다. CREATE와 BASELINE을
`before_state = null`만으로 구분하거나 baseline을 과거 Work·Sales 행의 결과로
추정하지 않는다.

### 12. Correction과 Compensation은 N:M 관계로 표현한다

Correction과 Compensation은 서로 다른 업무 의미를 가진다.

- Correction: 과거 기록 또는 결과가 잘못되어 현재 상태를 보정한다.
- Compensation: 과거 행위는 유효했으나 이후 업무 취소로 반대 효과를 적용한다.

하나의 보정 작업이 여러 실행 회차의 결과를 변경할 수 있으므로 Mutation 본체의
단일 `correction_of` 또는 `compensation_of` 컬럼을 사용하지 않는다. 별도 관계를
사용한다.

```text
orchid_group_mutation_relations
- mutation_id
- related_mutation_id
- relation_type: CORRECTS | COMPENSATES | SUPERSEDES
```

자동 역연산이나 자동 연쇄 rollback은 제공하지 않는다. 각 상위 도메인이 후속
사용 여부와 보정·보상 가능성을 판단한 후 명시적인 typed command를 요청한다.

### 13. 기존 이력 모델을 유지하고 Mutation과 연결한다

기존 이력의 책임은 유지한다.

| 모델 | 유지하는 책임 | Mutation 연결 |
|---|---|---|
| `WorkCommandReceipt` | 외부 Work 요청 멱등성과 1:N 결과 작업 | `correlationId` |
| `WorkAppliedEffect` | 작업 실행 회차와 효과 멱등성 | nullable scalar `mutationId`, `correlationId` |
| `WorkEffectOrchidGroup` | 실행 회차의 SOURCE/RESULT | AppliedEffect를 통해 연결 |
| `OrchidGroupLineage` | 단일 원본 등 직접 파생 호환 관계 | 해당 행이 있을 때 선택적 `mutationId` |
| `SalesInventoryMovement` | 판매 예약·출고 업무 이력 | scalar `mutationId` |
| `AuditEvent` | 행위자·세션·요청·변경 감사 | request/source correlation |

다만 유지한다는 것은 같은 물리 상태 사실을 모든 모델에 계속 복제한다는 의미가
아니다. 완전 전환 후 사실의 기준 소유자는 다음과 같이 한 곳으로 정리한다.

- `MutationEntry`: 난 묶음의 before/after 상태와 revision
- `WorkAppliedEffect`: 작업 효과 identity, 업무 입력·결과 식별자와 진행 의미
- `SalesInventoryMovement`: 판매 예약·출고의 업무 의미
- `AuditEvent`: 행위자, 요청과 세션 context

새 Work·Sales·Audit 행에 Mutation과 동일한 전체 snapshot을 중복 저장하지 않는다.
기존 `resultDetails`와 감사 데이터에 들어 있는 난 묶음 before/after snapshot은
적용 기준 이전 호환 데이터로 읽되, 업무 입력·결과 identity는 계속 Work가
보존한다. 새 조회는 `mutationId`를 통해 물리 변경 snapshot을 조립한다.
`WorkEffectOrchidGroup`과 `OrchidGroupLineage`는 기존 API 전환 중 호환 모델로
유지하고, ledger 기반 조회가 동등한 정보를 제공한 뒤 canonical write인지 파생
read model인지 결정해 중복 write를 제거한다.

N:M 구조 변경의 기준 계보 노드는 계속 `WorkAppliedEffect`다. 모든 N:M 실행에서
직접 `OrchidGroupLineage` 행을 만들거나 결과별 원본 수량을 임의 배분하지 않는다.

`work`가 `farm` 엔티티에 의존하지 않도록 `WorkAppliedEffect.mutationId`는 scalar
식별자로 저장한다. Mutation 조회가 필요한 기능은 application contract를 통해
접근한다. 기록 전용 효과나 작업일만 바꾸는 보정처럼 `OrchidGroup` 상태를
변경하지 않은 효과의 `mutationId`는 `null`을 유지한다.

WorkAppliedEffect와 Mutation을 무조건 1:1로 제한하지 않는다. 하나의 최상위
요청이 여러 효과와 여러 Mutation을 만들 수 있고, 여러 업무 효과가 하나의 물리
상태 변경을 설명할 수도 있다. 예를 들어 자리 이동 중 손실은 Work 이력에서 이동
효과와 파생 폐기 효과로 구분하되, Engine은 원본 총 차감·이동 결과·손실을 하나의
원자적 transform Mutation으로 적용할 수 있다. 이 경우 두 WorkAppliedEffect가
같은 `mutationId`를 참조한다.

반대로 품종별 batch처럼 하나의 요청이 여러 독립 Mutation을 만들면 합치지 않고
같은 `correlationId`로 연결한다.

### 14. Timeline은 별도 read model에서 조립한다

Mutation Engine은 쓰기와 ledger 조회를 제공하지만 Work·Sales·Inbound 상세를
조립하지 않는다. 통합 Timeline을 구현할 때는 읽기 전용 `history` 모듈을 추가해
`farm`, `work`, `sales`의 application query를 조합한다. 이를 통해
`farm → sales → farm` 순환 의존을 만들지 않는다.

Timeline은 cursor 또는 page 기반 제한 조회를 사용하고 source ID를 도메인별로
모아 일괄 조회한다. 적용일 이전 데이터를 완전한 Mutation 이력처럼 표시하지
않고 `historyAvailableFrom` 또는 동등한 coverage 정보를 제공한다.

### 15. 기존 운영 데이터는 complete state-chain으로 편입한다

전환 시 기존 `orchid_groups` 행을 새로 생성하거나 ID를 바꾸지 않는다. 현재 PK와
Work·Sales·Inbound·Collection·Lineage의 FK 및 scalar 참조를 유지하면서, 운영 백업과
업무 근거에서 복원한 전체 revision chain을 적재한다. 최초 관측 상태는 `BASELINE`,
생성 근거가 있는 그룹은 `CREATE`, 이후 변경과 삭제는 `CHANGE/DELETE`로 이어진다.
현재 행이 없는 그룹도 terminal `DELETE` Entry로 보존한다.

이관 전에 운영 데이터 profiling과 정합성 검사를 실행한다.

- `quantity >= 0`, `0 <= reserved_quantity <= quantity`
- 현재 status, pot size와 기타 코드의 canonical mapping 가능 여부
- 활성 난 묶음의 품종·논리 구역과 배치 범위 유효성
- 동일 구역 배치 겹침과 정렬 순서 충돌
- 진행 중 판매 allocation 합계와 `reserved_quantity`의 일치
- 진행 중 Work 대상의 `processedQuantity`, 효과와 SOURCE/RESULT 참조
- Inbound 생성 결과, Collection membership와 Lineage의 dangling reference

결정적으로 변환 가능한 legacy 값만 승인된 mapping으로 정규화한다. 원본 값,
변환 값, 사유와 대상 ID는 migration report에 남긴다. 수량·예약·배치처럼 업무
판단이 필요한 불일치는 현재 값을 조용히 덮어쓰거나 관련 업무 행에서 임의로
재계산하지 않는다. 운영자가 원인을 확인해 전환 전 보정하거나 전환을 중단한다.
Engine의 핵심 invariant를 위반한 행이 하나라도 남으면 `ACTIVE`로 전환하지 않는다.

DDL과 제약의 expand 단계는 Flyway로 수행한다. cross-domain 판정과 snapshot chain
생성은 애플리케이션 시작 시 자동 실행되는 Flyway에 넣지 않는다. profiler가 만든
승인 manifest를 정규화한 뒤 operator-run importer가 원자적으로 적재한다. importer는
과거 사실을 다시 추론하지 않으며 dry-run, fingerprint 검증, 재실행 결과와 종료 코드를
제공한다.

운영 cutover는 기본적으로 짧은 쓰기 중단 창에서 다음 순서로 수행한다.

1. 복원한 운영 DB 사본에서 migration rehearsal과 소요 시간·저장량을 확인한다.
2. 전체 DB backup과 restore 가능성을 확인한다.
3. 모든 난 묶음 write API를 차단하고 실행 중 transaction과 구버전 인스턴스를
   종료한다.
4. 최종 백업으로 profiler manifest를 다시 만들고 승인 artifact와 현재 DB가 일치하는지 확인한다.
5. `BASELINE/CREATE/CHANGE/DELETE` Mutation·Entry와 업무 연결을 적재한다.
6. revision 연속성, 마지막 snapshot, fingerprint, FK와 reservation 정합성을 검증한다.
7. coverage를 `ACTIVE`로 바꾸고 DB write fence와 신버전 writer를 활성화한다.
8. 핵심 Work·Sales·Inbound 시나리오를 smoke test한 뒤 쓰기를 재개한다.

현재 규모의 import는 하나의 transaction으로 처리한다. manifest 전체 검증, Mutation과
Entry, Work·Lineage 연결, 현존 그룹의 최종 `state_revision`, coverage fingerprint와
reconciliation 중 하나라도 실패하면 모두 rollback한다. 같은 manifest 재실행은 기존
Mutation을 replay하고 신규 행을 만들지 않는다. 다른 payload나 fingerprint는 충돌로
중단한다. 실패한 PREPARING import 위에서 기존 writer를 재개하지 않고 검증된 전체 DB
restore 또는 동일 manifest 재실행만 허용한다.

### 16. 진행 중인 업무를 complete chain 위에서 계속 수행한다

과거 revision을 이관하되 진행 중 Work·Sales·Inbound를 초기화하지 않는다. 원본 업무
객체는 해당 모듈의 사실로 보존하고, 난 묶음 상태 변화만 공통 Mutation chain으로
연결한다.

Work는 다음 원칙으로 이어받는다.

- 기존 `WorkOperation`, target, execution, `processedQuantity`와 완료 상태를 보존한다.
- 상태를 변경한 기존 `WorkAppliedEffect`는 manifest가 지정한 Mutation ID와
  `correlationId`에 연결한다. 기록 전용 효과는 연결하지 않는다.
- 같은 effect 재시도는 기존 결과와 연결된 Mutation을 반환하고 새 Mutation을 만들지 않는다.
- 전환 후 새 실행 회차나 미적용 대상 효과는 Engine command로 새 Mutation을 만든다.

기존 `requestKey`가 있는 즉시 작업은 결과를 복원할 수 있을 때
`WorkCommandReceipt`로 backfill한다. 일반 계획처럼 과거 요청 identity를 알 수
없는 작업에는 receipt를 발명하지 않는다. 전환 후 새 생성 요청부터 receipt를
필수로 적용한다.

Sales는 활성 전표 allocation과 최종 imported snapshot의 `reserved_quantity`를 전환 전에
대조한다. 전환 전에 생성된 예약도 이후 해제·출고·출고 취소는 Engine command로
처리하고 그 시점의 Mutation을 새 `SalesInventoryMovement`와 연결한다. 시스템 운영 전
자료를 토대로 등록해 난 묶음 귀속이 원래 없는 기존 전표에는 Mutation을 추정하지 않는다.

기존 업무 객체를 취소·보정할 때는 실제 연결된 과거 Mutation이 있을 때만
`COMPENSATES` 관계를 만든다. 연결 근거가 없다면 해당 업무 객체를 source로 하는
명시적인 correction 또는 release Mutation을 현재 chain 끝에 추가한다. Inbound와
Collection의 기존 연결은 ID를 유지하고, Lineage는 manifest가 지정한 Mutation에 연결한다.

### 17. Ledger 적용 범위와 전환 상태를 영속적으로 관리한다

ledger coverage는 운영 DB에 최소한 다음 의미로 보존한다.

```text
cutover_id
status: PREPARING | ACTIVE | FAILED
engine_schema_version
snapshot_schema_version
baseline_started_at
baseline_completed_at
effective_business_date
baseline_group_count
baseline_fingerprint
import_fingerprint
minimum_writer_version
```

`ACTIVE` 시각 이후 커밋된 모든 상태 revision은 ledger가 완전해야 한다. 적용 기준
이전의 상세 이력은 [ADR-002](ADR-002-orchid-group-historical-migration.md)에 따라
기존 Work, Inbound와 Audit source를 공통 Mutation identity와 연속
`BASELINE/CREATE/CHANGE/DELETE` Entry로 이관한다. coverage의 `import_fingerprint`는
승인된 complete state-chain manifest를 고정하며, cutover는 이 적재가 끝난 뒤에만
허용한다. 기존 참고 판매 전표처럼 난 묶음 귀속이 원래 없는 자료에는 Mutation을
추정해 만들지 않는다.

`ACTIVE` 이후에는 Engine을 비활성화하거나 기존 직접 경로로 fallback할 수 없다.
일반 장애는 roll-forward로 복구한다. 전체 DB restore는 cutover 이후 다른 업무
transaction까지 함께 되돌려도 되는 재해 복구 상황에서만 사용하고
`orchid_groups`나 ledger만 부분 복원하지 않는다.

### 18. 구현은 write path별로 하고 운영 활성화는 한 번에 수행한다

개발과 검증을 한 PR에 몰아넣지 않는다. 각 write path를 이관하는 PR에서 다음을
수행한다.

1. 기존 유스케이스 입력을 typed Farm command로 변환한다.
2. command별 policy와 공통 Mutation pipeline을 구현한다.
3. 기존 결과 보존, rollback, 재시도와 동시성 테스트를 추가한다.
4. 운영 백업을 복원한 격리 DB에서 기존 결과와 Engine 결과를 시나리오별로 검증한다.
5. 삭제할 기존 mutator·handler·중복 snapshot 목록을 retirement inventory에 남긴다.

하지만 동일한 `OrchidGroup`을 일부 경로는 Engine이, 다른 경로는 기존 코드가
수정하도록 운영 활성화를 write path별로 나누지 않는다. 직접 경로의 변경은
revision을 증가시키지 않아 ledger 공백을 만들기 때문이다. 구현은 점진적으로
완료하되 모든 운영 writer가 Engine 경로를 지원하고 complete state-chain 검증이 끝난 뒤
cutover 창에서 aggregate 전체의 write authority를 한 번에 전환한다.

권장 순서는 다음과 같다.

```text
ADR, write-path·retirement inventory와 운영 데이터 profiling
→ Work command receipt·effect identity·fingerprint 정리
→ Mutation schema, core와 operator-run state-chain importer
→ Farm 생성·수정·생성 취소·이동 command 준비
→ 폐기·다중 생성과 Work N:M 구조 변경 command 준비
→ Inbound 생성·포트 command 준비
→ Sales 예약·해제·출고·취소 command 준비
→ Correction·Compensation과 legacy source 처리 준비
→ complete state-chain manifest 적재와 복원 DB ENGINE 시나리오 검증
→ 복원 운영 DB rehearsal과 전체 회귀 테스트
→ 쓰기 중단, 최종 manifest 적재, coverage ACTIVE와 write fence 활성화
→ smoke test와 쓰기 재개
→ 기존 직접 writer·호환 handler·feature flag 제거
→ ledger 연속성 상시 검증
→ Timeline
```

Feature flag는 적용 기준 시점 전의 Legacy/Engine routing에만 사용할 수 있다. 하나의
유스케이스에서 기존 경로와 Engine 경로가 동시에 상태를 변경하는 dual write는
허용하지 않는다. cutover 이후 구버전 인스턴스가 다시 붙지 않도록 coverage의
`minimum_writer_version`, 배포 절차와 DB write fence를 함께 사용한다.

운영 권한 전환과 전환 코드 정리는 서로 다른 gate로 관리한다.

- `ACTIVE` 전에는 write-path inventory를 닫는다. 이는 운영에서 호출될 수 있는 모든
  writer가 식별되어 Engine을 지원하고, 스위치 밖의 미확인 직접 writer가 없다는
  뜻이다. 전환 실패 시점이 `ACTIVE` 전이라도 PREPARING import 위에서 기존
  writer를 재개하지 않고 검증된 전체 restore 또는 동일 manifest 재실행만 허용한다.
- `ACTIVE` 전환 릴리스에는 짧은 안정화 기간을 위해 식별된 `LEGACY` 호환 분기가
  남아 있을 수 있다. 그러나 모든 실행 인스턴스는 `ENGINE`으로 고정하며 DB fence가
  legacy 실행을 최종 차단한다. `ACTIVE` 이후 flag 변경이나 fallback은 금지한다.
- smoke test와 상시 대사가 안정적으로 통과하면 다음 릴리스에서 routing flag와
  legacy 직접 writer를 물리적으로 제거한다. 이때 retirement inventory가 비면
  완전 전환이 완료된다.

2026-09-08 구현은 라우팅 모드와 Legacy 직접 writer를 제거했다. 다음 운영 경로는
typed command로 Engine만 호출한다. 위의 단계별 전환 절차는 결정 이력이며, 현재
배포·복구 기준은 `../features/orchid-group-mutation-transition.md`를 따른다.

- Farm 단건·일괄 생성/수정, 생성 취소, 이동과 품종명 전파
- Inbound 즉시 배치와 Work 기반 포트 결과 생성
- Work 폐기, 이동, 분갈이, 분주, 합식, 다중 생성·취소와 보정
- Sales 예약, 수정 예약 해제·재예약, 출고, 예약 취소와 출고 복구

각 요청은 Engine만 실행한다. Work 효과는 기존 `TARGET:{id}`,
`EXECUTION:{key}`, `POTTING:{key}`, `OPERATION` identity를 Mutation source operation
key로 전달하고 `WorkAppliedEffect`와 호환 `OrchidGroupLineage`에 Mutation ID를
연결한다. Sales는 전표 ID와 전표 version을 포함한 동작별 operation key를 사용하고
각 `SalesInventoryMovement`에 Mutation ID와 correlation ID를 연결한다.

운영 트래픽을 복제해 비교하는 모드는 사용하지 않는다. 사용자 수와 변경 이벤트가 적어
관찰 기간을 늘려도 의미 있는 시나리오 coverage를 얻기 어렵기 때문이다. 대신 최신 운영
백업을 복원한 격리 PostgreSQL에서 생성·수정·이동·입고·구조 변경·보정·폐기와 판매
예약·출고·취소 시나리오를 의도적으로 실행하고 state-chain import, ENGINE 회귀와 실제 `ACTIVE`
전환 rehearsal을 통과해야 한다.

이 검증 완료 상태는 운영 cutover 완료를 뜻하지 않는다. 다음 retirement inventory는
`ACTIVE` 전까지 모두 식별·라우팅하고, 안정화 후 별도 릴리스에서 물리적으로 제거한다.

구현 파일별 수명과 제거 gate는
`docs/features/orchid-group-mutation-transition.md`를 기준 원장으로 관리한다.

- 각 adapter에 남아 있는 `LEGACY` 직접 mutator 분기와 routing flag
- `OrchidGroupCommandService.createEntity` 등 전환 호환용 내부 writer
- Farm 패키지에서 Work handler interface를 직접 구현하는 전환 adapter
- ledger 조회가 대체할 수 있는 Work 결과 snapshot과 단일 원본 Lineage 중복 write

복원 PostgreSQL에서 state-chain import, ENGINE 전체 회귀, 진행 중 업무 호환 검증과 실제
`ACTIVE` 전환 rehearsal을 마치기 전에는 운영 coverage를 `ACTIVE`로 바꾸지 않는다.

### 19. 구조와 데이터 검증을 자동화한다

기존 타 모듈 Repository 직접 접근 금지 규칙을 유지하고 다음 검사를 추가한다.

- Engine 이외 코드의 `OrchidGroup` 상태 mutator 호출 금지
- Sales와 Work가 관리 상태의 `OrchidGroup` 엔티티를 받아 수정하는 코드 금지
- Mutation 없는 `OrchidGroup` revision 증가 탐지
- Entry 없는 Mutation과 Mutation 없는 Entry 탐지
- source key 중복과 다른 command fingerprint 재사용 탐지
- 같은 request/effect key의 다른 fingerprint 재사용 거부
- 동시 최초 요청의 기존 결과 반환
- 하나의 command receipt가 만든 1:N WorkOperation 결과 재조회
- correlationId를 통한 요청·효과·Mutation 추적
- 그룹별 revision 연속성 검사
- `WorkAppliedEffect`와 `SalesInventoryMovement`의 Mutation 연결 정합성 검사
- PREPARING·ACTIVE coverage 상태와 실행 writer version의 호환성 검사
- 기존 난 묶음 수와 BASELINE Entry 수 및 fingerprint 일치
- 현재 `orchid_groups` snapshot과 각 그룹의 마지막 Entry `after_state` 일치
- 기존 effect 재시도가 새 Mutation을 만들지 않는지 검증
- 전환 전 예약의 전환 후 해제·출고와 진행 중 Work 후속 회차 검증
- 구버전 또는 Engine context 없는 writer의 DB write fence 차단

수량·예약·배치 동시성은 다음 실제 PostgreSQL 시나리오를 포함한다.

- 판매 예약과 폐기
- 판매 출고와 구조 변경
- 동일 Work 실행 재시도
- 겹치는 원본을 사용하는 N:M transform
- 같은 목적지의 동시 생성과 이동
- 보정 대상의 후속 사용과 동시 변경

운영에서도 read-only reconciliation command를 주기적으로 실행해 latest snapshot,
revision, source 연결과 coverage를 검사한다. 불일치는 자동 수정하지 않고 대상 ID,
예상 값과 실제 값을 운영 경보 및 repair report로 남긴다.

## 결과

### 장점

- `OrchidGroup` 상태 invariant와 잠금 정책을 한 경계에서 일관되게 적용한다.
- 적용 기준 시점 이후 모든 상태 revision의 원인과 전후 값을 추적할 수 있다.
- Work, Sales, Inbound의 업무 의미와 lifecycle을 통합하지 않고 유지한다.
- Timeline, 정합성 검사와 제한적인 과거 시점 조회를 위한 안정적인 기반을
  제공한다.
- 신규 기능이 직접 `OrchidGroup`을 수정해 이력에서 누락되는 회귀를 구조적으로
  차단한다.
- 기존 PK와 업무 참조를 유지하므로 운영 중인 Work·Sales·Inbound를 중단하거나
  난 묶음을 재생성하지 않고 새 revision chain을 시작할 수 있다.
- 물리 상태 snapshot의 기준을 MutationEntry로 모아 업무 이력의 중복 책임을
  단계적으로 줄일 수 있다.

### 비용과 위험

- 여러 모듈의 기존 쓰기 경로와 cross-module JPA 연관을 단계적으로 변경해야 한다.
- Mutation과 기존 업무 이력을 같은 transaction에서 저장하므로 쓰기 비용과
  장애 지점이 증가한다.
- 다중 대상 snapshot으로 저장량이 증가한다.
- 최초 신뢰 가능한 백업 이전 상태는 복원하지 않으므로 그 이전 시점 replay는 보장하지 않는다.
- state-chain profiling, backup·restore rehearsal과 짧은 운영 쓰기 중단이 필요하다.
- 진행 중 업무와 과거 effect 연결 규칙을 cutover 기간에 유지해야 한다.
- PostgreSQL write fence와 operator-run migration command의 운영·테스트 비용이
  추가된다.
- 엔진이 지나치게 범용화되면 도메인 규칙을 우회하는 거대한 service가 될 수
  있으므로 typed command와 책임 분리가 계속 필요하다.

## 검토한 대안

### 기존 직접 변경을 유지하고 AuditEvent만 확대

N:M 원자 변경, 그룹별 revision과 공통 멱등성을 표현하기 어렵고 직접 변경 누락을
막지 못하므로 채택하지 않는다.

### Work를 모든 상태 변경의 상위 도메인으로 사용

판매 예약·출고와 입고 lifecycle을 작업 도메인으로 왜곡하고 모듈 책임을
혼합하므로 채택하지 않는다.

### Mutation Engine만 별도 top-level 모듈로 분리

Mutation은 독립된 업무 언어보다 `OrchidGroup` 상태 변경의 기술적 메커니즘에
가깝다. 엔진만 분리하면 Aggregate와 invariant의 소유권이 모호해지고 Farm 배치
검증을 위해 다시 `farm`에 의존하므로 채택하지 않는다.

### OrchidInventory Bounded Context로 전체 추출

현재는 `OrchidGroup`이 `BedZone`, 품종, 입고와 Farm 배치 정책에 강하게 결합되어
있어 즉시 추출하지 않는다. 다음 조건이 확인되면 별도 ADR로 다시 검토한다.

- OrchidGroup, Reservation과 Lineage가 Farm 구조와 구분되는 고유 업무 언어를
  가진다.
- `BedZone`과 품종을 직접 Entity 연관이 아닌 ID와 application port로 참조할 수
  있다.
- Farm 현황을 `FarmStructure`와 `OrchidInventory`의 별도 read model로 조립할 수
  있다.
- Context 간 ACID transaction 축소와 eventual consistency를 수용할 운영 요구가
  있다.
- 독립적인 팀 소유권, 배포, 저장소 또는 확장 요구가 생긴다.

추출할 때는 Mutation Engine만 이동하지 않는다. `OrchidGroup` Aggregate,
Repository와 테이블 소유권, 수량·예약·상태 정책, Mutation ledger, transform과
Lineage를 하나의 Bounded Context로 함께 이동한다.

### 완전한 Event Sourcing으로 즉시 전환

기존 데이터의 완전한 backfill이 어렵고 현재 조회·운영 요구보다 위험과 범위가
크므로 채택하지 않는다.

### 범용 Mutation 외부 API 제공

상위 업무 lifecycle과 권한을 우회하고 잘못된 상태 조합을 만들 수 있으므로
채택하지 않는다.

### 하나의 전역 멱등 키로 요청·효과·Mutation을 통합

요청 생성, 대상 효과, 부분 실행 회차와 물리 상태 변경은 lifecycle과 cardinality가
서로 다르다. 하나의 WorkOperation이 여러 효과와 Mutation을 가질 수 있고, 하나의
요청이 여러 WorkOperation을 만들 수도 있으므로 단일 전역 키로 합치지 않는다.
각 단계의 identity와 fingerprint를 유지하고 `correlationId`로 연결한다.

### 구현과 과거 데이터 전량 backfill을 한 번에 수행

모든 command와 adapter를 한 PR·배포에서 구현하고 과거 전후 상태까지 복원하는
방식은 운영 rollback과 데이터 검증이 어렵고 복원 불가능한 값이 있으므로 채택하지
않는다. 이는 write path별 점진 구현이 끝난 뒤 aggregate write authority를 한 번에
활성화하는 결정과 구분한다.

### 기존 데이터의 lazy baseline

난 묶음이 처음 변경될 때 현재 상태를 baseline으로 만드는 방식은 그룹마다
coverage 시점이 달라지고, 아직 접근되지 않은 행의 직접 변경과 진행 중 예약을
일관되게 검증하기 어렵다. 승인된 manifest로 모든 기존 chain을 한 번에 확정한다.

### 기존 writer와 Engine의 dual write

비교를 위해 두 경로가 같은 상태를 각각 저장하면 중복 차감, flush 순서와 ledger
불일치가 발생할 수 있다. 비교는 운영 백업을 복원한 격리 DB에서 수행하고
운영 transaction의 상태 write는 항상 한 경로만 담당한다.

## 완료 기준

운영 write authority 전환은 다음 조건을 만족하면 완료한다.

- write-path inventory의 모든 writer가 식별되어 Engine으로 라우팅되고 미확인 직접
  writer가 없다.
- 복원 운영 DB에서 state-chain import·ENGINE 회귀와 `ACTIVE` 전환 rehearsal이 통과한다.
- 운영 DB coverage가 `ACTIVE`이고 모든 실행 인스턴스가 최소 writer version 이상의
  `ENGINE`이며 DB fence가 legacy write를 차단한다.
- 전환 직후 smoke test와 read-only reconciliation이 통과한다.

완전한 코드 전환은 안정화 후 legacy writer와 routing flag까지 제거되어 다음 조건을
모두 만족할 때 완료한다.

- 적용 기준 시점 이후 커밋된 모든 `OrchidGroup` 상태 revision에 정확히 하나의
  MutationEntry가 존재한다.
- 전환 대상 모든 그룹이 동일한 `cutoverId` coverage 아래 `BASELINE` 또는 `CREATE`로
  시작하는 연속 chain을 가지며, 삭제 그룹은 `DELETE`로 끝난다.
- 현존 그룹의 마지막 revision·snapshot이 전환 직전 current state와 일치하고,
  manifest와 baseline fingerprint가 승인값과 같다.
- 각 Entry의 before/after revision이 그룹별로 연속된다.
- Farm의 Engine 밖에서 수량·예약·상태·위치·속성·구조를 직접 변경할 수 없다.
- Work, Sales, Inbound가 `OrchidGroupRepository`나 관리 상태 엔티티를 직접
  사용하지 않는다.
- Farm Engine과 domain policy가 Work·Sales 업무 타입에 의존하지 않는다.
- Work 효과, 판매 재고 이동과 Mutation의 연결을 추적할 수 있다.
- 외부 요청, Work 효과와 Mutation의 멱등 identity가 구분되고 correlationId로
  연결된다.
- 같은 request/effect/mutation key의 동일 command는 기존 결과를 반환하고 다른
  command는 안정적인 충돌 코드로 거부한다.
- 동일 source operation 재시도에서 상태와 Mutation이 중복 생성되지 않는다.
- 경매 출하에서 재고 차감은 한 번만 발생한다.
- 기존 effect 재시도는 새 Mutation을 만들지 않고, 진행 중 Work의 후속 실행과
  전환 전 판매 예약의 해제·출고는 imported chain의 마지막 revision에서 이어진다.
- 수량·예약·N:M transform·목적지 배치 동시성 테스트가 실제 PostgreSQL에서
  통과한다.
- 운영 DB의 coverage가 `ACTIVE`이고 적용 기준 시점, baseline fingerprint,
  ledger schema version과 minimum writer version이 기록되어 있다.
- Engine context가 없는 INSERT·UPDATE와 모든 물리 DELETE가 DB write fence에서
  차단된다.
- 복원한 운영 DB 사본에서 state-chain import rehearsal과 핵심 진행 업무 회귀 테스트가
  통과한다.
- ADR-002의 manifest fingerprint와 import 결과가 일치하고, 모든 그룹의 revision·snapshot
  chain과 삭제 tombstone이 연속이며 통합 Timeline이 legacy source별 조립에 의존하지 않는다.
- retirement inventory가 비어 있고 전환용 feature flag, 기존 직접 쓰기 경로와
  중복 상태 snapshot write가 제거되어 있다.

Timeline과 과거 시점 replay는 이 완료 조건을 검증한 이후 ledger를 소비하는
후속 기능이며, 핵심 전환 완료의 선행 조건은 아니다.
