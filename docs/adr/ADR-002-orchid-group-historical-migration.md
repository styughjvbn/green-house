# ADR-002: 기존 난 묶음 상태 이력을 공통 Mutation 모델로 완전 이관한다

- 상태: 승인
- 결정일: 2026-08-21
- 범위: Backend `farm`, `work`, `sales`, `inbound`의 cutover 이전 난 묶음 상태 이력
- 선행 결정: [ADR-001](ADR-001-orchid-group-mutation-engine.md)

## 배경

ADR-001은 cutover 시점의 `OrchidGroup` 현재 상태를 revision 0의 BASELINE으로 만들고,
이후 상태 변경부터 완전한 revision chain을 기록한다. 이 방식은 운영 전환에는
안전하지만 Timeline이 cutover 이전 Work·Inbound·Sales·Audit를 다시 조합해야 하므로
장기적으로 조회와 호환 코드의 복잡도가 남는다.

기존 데이터를 Engine 모델로 이관하되, 원본에 없는 과거 상태를 사실처럼 생성하면
안 된다. 따라서 이 ADR에서 말하는 **완전 이관**은 다음 의미다.

- 난 묶음 상태 변경을 증명하는 모든 legacy source를 Mutation identity로 변환한다.
- 변환할 수 없는 자료도 누락시키지 않고 명시적인 origin, 운영자 확인 또는 gap으로
  분류한다.
- Timeline은 cutover 전후 모두 공통 Mutation 조회 모델을 사용한다.
- 원본에 없는 필드와 사건 순서는 추측하지 않는다.

완전 이관은 모든 과거 시점의 완전한 aggregate snapshot을 복원한다는 뜻이 아니다.
관측되지 않은 사실을 정확하게 복원하는 것은 기술적으로 불가능하다.

## 운영 백업 profiling 결과

2026-08-21 운영 백업 `green-house_20260821_030001.dump.gz`를
`greenhouse_rehearsal`에 복원하고 Flyway V24까지 적용했다. PRE_BASELINE
reconciliation은 `ready=true`, `issues=[]`였다.

| 항목 | 결과 | 판단 |
|---|---:|---|
| 현재 난 묶음 | 269 | 모두 baseline 가능 |
| Work 효과 | 55 | Engine source identity 55개 모두 유일 |
| 상태 변경 Work 효과 | 42 | command, result, 시점, group link 모두 존재 |
| Work source/result link | 76 | 기대 집합과 누락·초과 0 |
| Lineage | 16 | 16개 모두 하나의 Work 효과에 결정적으로 연결 |
| 난 묶음 Audit | 4 | 생성 1, 보정 3; Work 효과와 중복 0 |
| 생성 근거가 있는 난 묶음 | 29 | Audit·구조 변경·포트 결과로 식별 |
| 합성 origin이 필요한 난 묶음 | 240 | 생성 당시 상세 상태는 알 수 없음 |
| 상태 변경 근거가 하나 이상 있는 난 묶음 | 71 | 나머지 198개는 origin과 cutover baseline만 존재 |
| 합성 origin 수량 역산 | 240/240 유효 | 모든 역산 수량이 0 이상 |
| 생성 근거 그룹의 수량 replay | 28/29 직접 일치 | 그룹 234의 차이는 운영자 확인 보정으로 설명됨 |
| 과거 수량 보정 | 그룹 234, `+12` | `ATTESTED CORRECTION`으로 이관 |
| 시스템 운영 전 참고 판매 전표/품목 | 148/868 | 난 묶음 귀속이 원래 없는 자료 |
| Sales allocation/movement/snapshot | 0/0/0 | `LEGACY_REFERENCE_ONLY`로 분류하고 Orchid Mutation에서 제외 |
| source dangling reference | 0 | Work·Lineage·Inbound·Audit 대상이 모두 존재 |

상태 변경 Work 효과 42건의 구성은 폐기 16, 단일 이동 12, 분주 3, 구조 이동 2,
포트 4, 분갈이 5다. Work의 기록 전용 효과 13건과 농약·잎 정리 작업은
`OrchidGroup` 상태 변경이 아니므로 Mutation으로 만들지 않는다.

기존 판매 전표 148건은 시스템 운영 전 자료를 바탕으로 등록한 참고 정보다. 당시
난 묶음 귀속 자체가 존재하지 않았으므로 allocation·movement·snapshot 부재는 이관
누락이 아니다. 이 자료는 `LEGACY_REFERENCE_ONLY`로 분류해 Sales 이력으로만 보존하고
Orchid Mutation 이관 대상에서는 제외한다. 품목명이나 규격을 이용한 추정 매칭도
하지 않는다.

그룹 234는 생성 수량 1,362와 현재 수량 1,374 사이의 `+12`가 실제 난 묶음 수량
보정이었음을 운영자가 확인했다. 원본 시스템 이벤트는 없으므로 직접 검증된 사실과
구분하되, 미확인 gap으로도 취급하지 않는다.

profiling은 다음 read-only SQL로 반복한다.

```bash
PGPASSWORD=greenhouse_rehearsal_test psql \
  -h 127.0.0.1 -p 5432 \
  -U greenhouse_rehearsal_test -d greenhouse_rehearsal \
  -f scripts/data-audit/orchid-history-migration-profile.sql
```

## 결정

### 1. 모든 난 묶음 참여 관계를 하나의 Entry 테이블에 저장한다

cutover 이전 자료도 `orchid_group_mutations`의 공통 identity를 사용한다. 하지만
불완전한 자료를 `OrchidGroupMutationEntry`의 완전한 before/after snapshot으로
위조하지 않는다.

```text
OrchidGroupMutation
└─ OrchidGroupMutationEntry
     ├─ HISTORICAL              원본 사건과 난 묶음의 관계
     └─ BASELINE/CREATE/CHANGE  완전 snapshot + 연속 revision
```

- 물리적인 난 묶음 참여 테이블은 `orchid_group_mutation_entries` 하나만 사용한다.
- `HISTORICAL` Entry는 `mutation_id`, `orchid_group_id`, `role`, `migration_run_id`만
  의미 있게 사용하고 revision과 before/after snapshot은 `NULL`이다.
- `BASELINE`, `CREATE`, `CHANGE` Entry는 기존처럼 완전한 상태와 연속 revision을
  요구한다. DB 제약과 write fence도 이 세 kind만 상태 chain으로 인정한다.
- 모든 Mutation은 kind와 관계없이 Entry를 하나 이상 가져야 한다.
- `orchid_group_mutations` 헤더와 migration run control-plane 테이블은 사건 identity와
  실행 감사를 위한 별도 책임이므로 유지한다. 여기서 하나의 테이블은 과거 관계와
  실시간 관계를 별도 자식 테이블로 나누지 않는다는 의미다.

이 구조는 과거와 현재를 하나의 Engine 언어로 제공하면서, 불완전한 과거 자료가
운영 상태 revision의 신뢰성을 낮추지 않게 한다.

### 2. 과거 Entry에는 추정 상태를 저장하지 않는다

과거 Entry에 별도 snapshot, 부분 fragment, known fields, 신뢰도 필드를 두지 않는다.
과거 사건의 의미와 근거는 다음 기존 정보로 판단한다.

```text
Mutation.sourceDomain/sourceType/sourceReferenceId/sourceOperationKey
Mutation.mutationType/occurredAt/reason/commandFingerprint
MutationEntry.orchidGroupId/role
원본 Work·Audit·Lineage 데이터
버전 관리되는 migration manifest와 run fingerprint
```

Work와 Audit의 상세는 원본 테이블이 계속 소유한다. 합성 origin과 운영자 확인 보정은
`sourceType`으로 구분하고 입력 내용은 manifest와 command fingerprint로 고정한다.
planner가 수량 replay에 사용하는 생성 수량과 변화량은 이관 검증용 일시 데이터이며
Entry에 영속화하지 않는다. 원본에 없는 상태는 어떤 테이블에도 만들어 넣지 않는다.

### 3. 발생 시각과 적재 시각을 구분한다

과거 이력 정렬을 위해 Mutation에 `occurred_at`을 추가하고 현재 `recorded_at`과
구분한다.

- `occurred_at`: legacy source가 증명하는 업무 발생 시각
- `recorded_at`: historical migrator가 Mutation을 적재한 시각
- `effective_business_date`: 농장 업무일

동일 시각이 있으면 `sourceDomain`, source PK, Mutation ID의 고정 순서로 정렬한다.
현재 백업의 상태 변경 증거에는 동일 그룹·동일 시각 충돌이 없지만 migrator는 이를
항상 검사한다.

### 4. 원본별 변환 규칙을 고정한다

| 원본 | Mutation 유형 | 처리 |
|---|---|---|
| Audit 생성 | `CREATE` | Audit identity와 결과 난 묶음 Entry를 연결 |
| Audit 보정 | `CORRECTION` 또는 `UPDATE_DETAILS` | Audit identity와 대상 Entry를 연결 |
| Work `DISCARD` | `DISCARD` | Work 효과와 대상 Entry를 연결 |
| Work `MOVE` | `MOVE` | Work 효과와 대상 Entry를 연결 |
| Work `DIVIDE`, `MOVEMENT`, `REPOT` | `TRANSFORM` | N:M source/result Entry를 하나의 Mutation으로 연결 |
| Work `POTTING` | `CREATE` | Inbound ID와 생성 결과 Entry를 하나의 Mutation으로 연결 |
| Lineage | 새 Mutation 없음 | 대응하는 Work Mutation에 `mutation_id` 연결 |
| SalesInventoryMovement | 동작별 예약·출고 Mutation | 난 묶음 ID가 있는 행만 이관 |
| 시스템 운영 전 참고 판매 전표 | Orchid Mutation 제외 | `LEGACY_REFERENCE_ONLY`로 Sales 원본 사실만 보존 |
| 기록 전용 Work 효과 | Orchid Mutation 제외 | Work 원본 사실로만 보존 |

기존 Work 효과의 identity는 현재 Engine과 동일하게 다음 키를 사용한다.

```text
sourceDomain       = WORK
sourceType         = WORK_EFFECT
sourceReferenceId  = workOperationId
sourceOperationKey = effectKey
```

correlation ID도 `WORK_OPERATION:{workOperationId}`에서 결정적으로 생성한다. 따라서
이관 후 기존 효과 재조회와 새 Engine Mutation의 추적 규칙이 같다.

### 5. origin과 운영자 확인 보정도 같은 Entry로 이관한다

생성 근거가 없는 240개 그룹에는 `MIGRATION/LEGACY_ORIGIN/{groupId}` identity의
Mutation과 `HISTORICAL` Entry를 만든다. `created_at`은 존재 시점으로 사용할 수 있지만
당시의 전체 상태라고 주장하거나 Entry snapshot으로 저장하지 않는다. 역산 수량은
replay 검증에만 사용한다.

그룹 234의 `+12`는 운영자가 실제 수량 보정으로 확인했으므로
`MIGRATION/OPERATOR_ATTESTATION/234` identity의 `CORRECTION` Mutation과 `HISTORICAL`
Entry로 이관한다. before 1,362, after 1,374와 확인자·확인 시각·사유는 버전 관리되는
manifest 입력과 fingerprint로 고정하고 Entry에는 복제하지 않는다.

운영자 확인이 없는 replay 불일치는 별도 GAP 행을 만들지 않고
`UNCLASSIFIED_GAPS`로 보고해 cutover를 중단한다. 원인이 확인되면 원본 변환 규칙이나
승인된 attestation을 manifest에 추가한 뒤 새 plan을 만든다.

### 6. 원본 업무 사실은 삭제하지 않는다

Mutation은 난 묶음 상태 변화의 통합 색인이지 Work·Sales·Inbound 업무 사실의
대체물이 아니다.

- `WorkAppliedEffect`, `SalesInventoryMovement`, `InboundRecord`, `AuditEvent`는 유지한다.
- Lineage는 Mutation과 연결하되 소비자 전환이 끝나기 전에 삭제하지 않는다.
- Timeline은 더 이상 각 원본 테이블을 직접 조합하지 않고 Mutation identity를 통해
  원본 상세로 이동한다.

따라서 완전 이관 후 제거 대상은 원본 데이터가 아니라 legacy 전용 상태 변경 코드와
중복 Timeline 조립 코드다.

### 7. operator-run migrator로 실행한다

대량 historical 이관은 Flyway에서 자동 실행하지 않는다. 별도의 operator command는
다음 단계와 상태를 가진다.

```text
DRY_RUN
→ PREPARING
→ IMPORTED
→ VERIFIED
→ cutover BASELINE
→ ACTIVE
```

`orchid_group_history_migration_runs`에는 run key, source cutoff, 백업 fingerprint,
source별 행 수·fingerprint, 변환 수, origin·attested correction·gap 수,
`LEGACY_REFERENCE_ONLY` 제외 수와 검증 결과를 저장한다.
이 테이블과 상태 전이는 Farm 핵심 도메인이 아닌 `migration` 지원 모듈이 소유한다.
Farm의 `HISTORICAL` Entry는 run Entity 연관을 갖지 않고 감사 추적용 scalar run ID만
보존하며, Farm application은 Migration application API를 통해 실행 상태를 제어한다.

실행 절차는 다음과 같다.

1. 운영 백업을 격리 DB에 복원하고 profiling SQL을 실행한다.
2. stable source identity와 payload fingerprint로 dry-run plan을 만든다.
3. Historical Mutation과 `HISTORICAL` Entry를 batch로 적재한다.
4. Work 효과와 Lineage를 대응 Mutation에 연결한다.
5. source count, link 집합, 수량 delta와 현재 상태를 다시 검증한다.
6. 최초 이관 이후 운영 중 추가된 legacy source를 cutoff watermark 이후부터 반복 적재한다.
7. 실제 cutover에서 쓰기를 중단하고 마지막 catch-up을 실행한다.
8. 최신 `orchid_groups` 전체를 StateChain BASELINE revision 0으로 기록한다.
9. historical 검증과 baseline reconciliation이 모두 통과한 경우에만 ACTIVE로 바꾼다.

Historical import는 `orchid_groups`의 현재 상태나 `state_revision`을 변경하지 않는다.
같은 run 또는 source를 재실행하면 fingerprint가 같을 때 기존 결과를 반환하고,
다르면 충돌로 중단한다.

현재 구현은 Flyway V25, `orchidHistoryMigrate` operator command와 버전 관리되는
manifest로 이 규칙을 적용한다. dry-run도 run과 현재 상태 fingerprint를 기록하며,
실제 적재는 Work·Audit·ORIGIN·ATTESTED source를 batch 처리한 뒤 Work 효과와 Lineage를
연결한다. Sales 모듈이 제공하는 검사 port로 전표·품목 수가 manifest와 같은지,
allocation·inventory movement·난 묶음 snapshot이 모두 0인지 실제 DB에서 확인한 뒤에만
기존 판매 자료를 `LEGACY_REFERENCE_ONLY`로 제외한다. VERIFIED run이 존재하면 같은 현재
상태 fingerprint에서만 baseline을 시작할 수 있다.

### 8. 운영 중 historical catch-up을 반복한다

최초 historical import 이후에도 운영 Legacy writer가 만든 Work·Audit source를
cutoff watermark 기준으로 반복 적재한다. 최종 write-stop 이후 마지막 catch-up과
현재 상태 baseline을 순서대로 수행한다. ACTIVE coverage에서는 계속 ENGINE만 허용한다.

## 검증 gate

완전 이관은 다음 조건을 모두 만족해야 한다.

- profiling source count와 import run의 source count·fingerprint가 같다.
- 상태 변경 Work 효과가 정확히 하나의 Mutation으로 변환된다.
- expected Work source/result link의 누락과 초과가 0이다.
- 모든 Lineage가 정확히 하나의 Mutation에 연결되고 ambiguous link가 0이다.
- Audit와 Work의 중복 변환이 없다.
- 생성 근거 그룹의 replay 결과가 현재 상태와 일치하거나 승인된 보정으로 설명된다.
- synthetic ORIGIN, 운영자 확인 보정, 미분류 GAP과 `LEGACY_REFERENCE_ONLY` Sales 건수가
  보고서에 명시된다.
- 미분류 gap과 dangling source reference가 0이다.
- historical import가 `orchid_groups`와 state revision을 변경하지 않는다.
- baseline 이후 기존 reconciliation의 `ready=true`, `issues=[]`가 유지된다.
- 같은 import를 재실행해 Mutation, Entry와 원본 link가 증가하지 않는다.

## 기각한 대안

### 과거 기록을 완전 snapshot으로 추정해 live revision 앞에 삽입

과거 target에는 sort order, 예약 수량, 상태, 세부 배치와 일부 속성이 없다. 추정
snapshot은 조회 편의 때문에 사실성을 희생하고 live chain 제약도 약화하므로 기각한다.

### baseline만 만들고 Timeline에서 원본 테이블을 계속 조합

운영 전환은 단순하지만 legacy 조회 분기와 source별 해석이 영구히 남으므로 기각한다.

### 같은 운영 DB에 Legacy와 Engine을 동시에 write

중복 수량 변경과 서로 다른 revision을 만들 수 있으므로 기각한다. 비교는 운영 백업을
복원한 격리 DB에서만 수행한다.

## 구현 순서

```text
profiling SQL과 attestation·gap 승인 기준
→ historical schema와 migration run
→ Work 효과·Lineage importer
→ Audit·Inbound·Sales movement importer
→ ORIGIN·ATTESTED·GAP 분류와 quantity replay verifier
→ 통합 Timeline query
→ 복원 DB 전체 historical migration rehearsal
→ 복원 DB ENGINE 시나리오 회귀
→ write-stop, final catch-up, BASELINE, ACTIVE
→ legacy writer와 legacy Timeline 조립 제거
```

## 2026-08-21 복원 DB 구현 검증

운영 백업을 V25까지 올린 뒤 동일 run key로 plan, import와 재실행을 검증했다.

| 항목 | 결과 |
|---|---:|
| Historical Mutation | 287 |
| HISTORICAL Entry | 321 |
| Work Mutation / 연결 | 42 / 42 |
| Audit Mutation | 4 |
| 합성 ORIGIN | 240 |
| ATTESTED CORRECTION | 1 |
| Lineage 연결 | 16 |
| 미분류 GAP | 0 |
| Entry 없는 Mutation | 0 |
| 재실행 신규 Mutation | 0 |
| 재실행 replay Mutation | 287 |

이관 전후 현재 상태 fingerprint는
`fcebd8f4313cf32d009e5f14f0f4a3c6e7314268c94663409df7a894978cb69c`로 같았고,
이관 후에도 `PRE_BASELINE`, `ready=true`, `issues=[]`였다.
