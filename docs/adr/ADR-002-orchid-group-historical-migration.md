# ADR-002: 기존 난 묶음 이력을 complete state-chain으로 이관한다

- 상태: 승인
- 결정일: 2026-08-21
- 최종 수정일: 2026-08-28
- 범위: cutover 이전 난 묶음 상태 이력과 운영 전환
- 선행 결정: [ADR-001](ADR-001-orchid-group-mutation-engine.md)

## 배경

cutover 시점의 현재 상태만 `BASELINE revision 0`으로 만들면 운영 전환은 쉽지만,
과거 조회가 계속 Work·Audit·Lineage를 조합해야 한다. 반대로 revision과 snapshot이 없는
별도 과거 Entry를 추가하면 한 테이블 안에 두 종류의 이력 모델이 생겨 장기 복잡도가
줄지 않는다.

운영 백업을 분석해 과거 상태를 full snapshot chain으로 고정한 manifest가 준비되었으므로,
cutover 전후를 하나의 `OrchidGroupMutationEntry` revision 규칙으로 통합한다. importer는
manifest를 적재할 뿐 과거 상태나 사건 원인을 다시 추론하지 않는다.

## 운영 백업 분석 결과

2026-08-26 운영 백업을 PostgreSQL에 복원하고 Work·Audit·Lineage와 난 묶음 snapshot을
대사했다.

| 항목 | 결과 |
|---|---:|
| 최종 현존 난 묶음 | 269 |
| 전체 chain 대상 난 묶음 | 275 |
| 삭제 terminal 난 묶음 | 6 |
| 정규화된 Mutation | 314 |
| 정규화된 Entry | 348 |
| 기존 상태 변경 Work 효과 연결 | 42 |
| Lineage 연결 | 16 |
| 운영자가 확인한 수량 보정 | 그룹 234, `+12` |
| 미분류 gap | 0 |

기존 판매 전표 148건은 시스템 운영 전 자료를 토대로 등록되어 난 묶음 귀속 정보가
원래 없다. 품목명이나 규격으로 귀속을 추정하지 않으며 Orchid Mutation 이관 대상에서도
제외한다. 판매 원본 사실은 그대로 보존한다.

## 결정

### 1. 모든 상태 이력은 하나의 revision 규칙을 사용한다

```text
최초 관측 그룹  BASELINE  null → 0
이후 생성 그룹  CREATE    null → 1
상태 변경        CHANGE       N → N+1
물리 삭제        DELETE       N → N+1, afterState=null
```

- 모든 Entry는 `revision_after`를 가진다.
- `BASELINE`과 `CREATE`는 `beforeState=null`, `afterState=full snapshot`이다.
- `CHANGE`는 full `beforeState`와 `afterState`를 모두 가진다.
- `DELETE`는 full `beforeState`를 가지며 terminal Entry여야 한다.
- 그룹별로 `previous.afterState == next.beforeState`가 성립해야 한다.
- N:M 변화는 하나의 Mutation 안에 `SOURCE`와 `RESULT` Entry로 표현한다.
- 현재 행이 삭제된 그룹도 tombstone chain을 보존할 수 있도록 Entry의
  `orchid_group_id`는 current `orchid_groups` FK를 갖지 않는다.

revision과 snapshot이 없는 `HISTORICAL` kind, 별도 과거 evidence 테이블, 부분 fragment,
신뢰도 필드는 사용하지 않는다.

### 2. 승인된 manifest를 유일한 이관 입력으로 사용한다

이관 artifact는 두 단계로 고정한다.

```text
profiler manifest schema 1
→ normalize_orchid_state_chain_manifest.py
→ importer manifest schema 2
→ orchidStateChainMigrate
```

정규화기는 다음 기계적 변환만 수행한다.

- 상태 의미를 바꾸지 않는 Flyway 표현 변경 제거
- 현재 snapshot 필드명과 canonical 화분 코드로 정규화
- profiler 순서에 따라 `BASELINE/CREATE/CHANGE/DELETE` revision 재부여
- 그룹별 연속성, terminal DELETE와 최종 현존 그룹 수 검증

importer는 `generated_from=orchid_state_chain_manifest_normalizer`, `migration_ready=true`,
blocking issue 0인 schema 2만 받는다. importer가 원본 테이블을 다시 분석하거나 누락된
상태를 보완하지 않는다. 최종 백업과 manifest의 난 묶음 ID 집합 또는 마지막 snapshot이
다르면 전체 적재를 거부한다.

### 3. revision 순서와 업무 시각을 분리한다

그룹 이력의 인과 순서는 revision이 기준이다. `occurredAt`은 원본이 증명하거나 profiler가
고정한 업무 발생 시각이고, `recordedAt`은 importer 적재 시각이다. 과거 사건의 시각이
현재 적재 시각보다 늦거나 같은 시각인 경우에도 revision chain을 재정렬하지 않는다.

### 4. 기존 업무 사실은 연결만 하고 삭제하지 않는다

- Work 효과는 현재 Engine과 같은 `(workOperationId, effectKey)` source identity를 쓴다.
- manifest의 `work_effect_ids`를 해당 Mutation과 correlation ID에 연결한다.
- Lineage는 새 Mutation을 만들지 않고 manifest가 지정한 Mutation ID를 연결한다.
- Work, Audit, Inbound, Sales와 Lineage 원본 행은 계속 각 소유 모듈의 사실로 보존한다.
- 기존 참고 판매 전표에는 난 묶음 Mutation을 만들지 않는다.

### 5. 적재는 원자적이고 재실행 가능해야 한다

하나의 transaction에서 다음을 수행한다.

```text
manifest 전체 검증
→ Mutation/Entry 적재
→ Work/Lineage 연결
→ 현존 난 묶음 최종 stateRevision 반영
→ coverage에 manifest SHA-256 기록
→ reconciliation
```

같은 source identity와 같은 payload는 기존 Mutation을 반환한다. payload, Entry 구성 또는
manifest SHA-256이 달라지면 충돌로 중단한다. 중간 실패는 모두 rollback한다.

### 6. cutover 절차를 단순화한다

```text
이전: 과거 관계 이관 → 현재 상태 baseline → ENGINE
변경: complete state-chain 적재 → ENGINE
```

`orchidLedgerCutover`는 더 이상 baseline을 생성하지 않는다. 동일 cutover key의 complete
state-chain 적재와 `ready=true`를 확인한 뒤 `ACTIVE`로만 전환한다.

`migration` 별도 모듈과 run 테이블은 제거한다. 실행 감사에 필요한 입력 근거는 버전
관리되는 manifest provenance, 파일 SHA-256, coverage의 `import_fingerprint`, Mutation의
source identity와 command fingerprint로 충분하다.

### 7. 전환 전용 코드의 수명을 명시한다

`OrchidGroupStateChainMigration*`, Work 연결 adapter, cutover CLI는 `TRANSITION_ONLY`다.
운영 cutover와 복구 정책 확정 후 제거할 수 있다. Mutation, Entry, coverage,
reconciliation과 Work/Lineage의 Mutation 연결은 전환 후에도 유지하는 `TARGET` 구조다.

## 검증 gate

- manifest mutation key와 source identity가 유일하다.
- 그룹별 revision이 최초 Entry부터 연속이다.
- 이전 after snapshot과 다음 before snapshot이 같다.
- DELETE는 마지막 Entry이며 after snapshot이 없다.
- manifest 최종 현존 ID 집합과 `orchid_groups` ID 집합이 같다.
- 각 마지막 snapshot과 현재 난 묶음 snapshot이 같다.
- Work 효과 42건과 Lineage 16건이 지정 Mutation에 연결된다.
- 모든 현존 그룹의 `state_revision`이 마지막 revision과 같다.
- 동일 manifest 재실행 시 신규 Mutation과 Entry가 0건이다.
- reconciliation이 `ready=true`, `issues=[]`다.

## 2026-08-28 복원 DB rehearsal

통합 전 실험 migration의 최종 스키마를 적용한 `greenhouse_rehearsal`에서 실제 schema 2
manifest를 실행했다. 운영 배포본은 같은 최종 스키마를 V20에서 통합 V21~V23으로 바로
구성한다.

| 단계 | 결과 |
|---|---|
| dry-run | 314 Mutation, 348 Entry, 현존 269, 삭제 6 검증 통과 |
| 최초 import | 신규 Mutation 314, Entry 348 |
| 재실행 | 신규 0, replay 314 |
| Work/Lineage 연결 | 42 / 16 |
| 최종 revision 그룹 | 269 / 269 |
| import 후 reconciliation | `BASELINE_PREPARING`, `ready=true`, `issues=[]` |
| VERIFY | `BASELINE_PREPARING`, `ready=true`, `issues=[]` |
| ACTIVE 전환 후 reconciliation | `ACTIVE`, `ready=true`, `issues=[]` |

## 기각한 대안

### revision 없는 과거 Entry를 같은 테이블에 유지

조회와 대사에서 계속 두 종류의 chain 규칙이 필요하므로 기각한다.

### 과거 evidence를 별도 테이블로 분리

Mutation/Entry와 별도 evidence의 동기화·조회 분기가 추가되어 현재 규모에 비해 복잡하다.

### importer가 운영 DB에서 과거를 다시 추론

실행 시점과 구현 버전에 따라 결과가 달라질 수 있다. 검토된 manifest를 그대로 적재한다.

### cutover 현재 상태만 baseline으로 생성

과거 Timeline이 원본별 조합 로직을 영구히 유지해야 하므로 기각한다.
