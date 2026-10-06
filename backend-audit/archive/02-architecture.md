# Backend 아키텍처 평가

> 보관 문서: 당시 코드·감사·검증 이력이며 현재 구현의 기준이 아니다. 미해결/운영 검증/보류 상태는 [현재 작업 목록](../10-remediation-progress.md)을 따른다.

- 평가일: 2026-10-03
- 기준 revision: `08b50dc7bc4be84aa4fec2688a47a027f8938e80`
- 대상: `backend/src/main/java`, 모듈 경계 테스트, [01-system-map.md](01-system-map.md)의 의존·호출 구조
- 원칙: 문서의 의도와 실제 구현을 구분한다. 코드와 필요한 OpenAPI slice를 대조했으며, 이 보고서 작성 중 코드는 변경하지 않았다.

## 1. 평가 결과

현재 구조는 패키지 기반 모듈러 모놀리스다. Entity·Repository 소유권과 소스 의존 방향은 아키텍처 테스트로 보호된다. 반면, 다른 모듈에 제공하는 application 계약과 모듈 내부 조립 기능의 경계는 충분히 구분되지 않았다. 주요 개선 대상은 모듈을 더 나누는 일이 아니라 공개 계약의 범위와 유스케이스 조율 책임이다.

이번 조사에서 확인한 문제는 **Medium 4건, Low 1건**이다. Critical·High에 해당하는 아키텍처 문제는 확인하지 않았다. 이는 업무 로직·동시성·보안·성능 전체에 대한 판정이 아니다.

### Severity 기준

| Severity | 이 평가에서의 의미 |
| --- | --- |
| Critical | 광범위한 데이터 손상 또는 핵심 업무 수행 불가가 구조상 확인됨 |
| High | 소유권 우회나 순환 등으로 중요한 정합성 경계가 실제로 무너짐 |
| Medium | 현재 동작은 가능하지만 변경 책임·계약·검증 경계가 흐려져 변경 비용이나 우회 위험이 큼 |
| Low | 영향이 한정된 내부 구현 결합 또는 계약 정리 문제 |

| ID | Severity | 문제 | 주된 평가 대상 |
| --- | --- | --- | --- |
| ARC-001 | Medium | 외부 application 계약과 Entity 기반 내부 API가 같은 공개 서비스에 혼재 | public/internal, application API |
| ARC-002 | Medium | Work 보정의 조율과 감사 저장 시점이 Farm adapter에 분산 | ownership, port/adapter, 호출 의존 |
| ARC-003 | Medium | Work Receipt의 처리 방식이 Farm 유스케이스에 노출 | application API, abstraction |
| ARC-004 | Low | Sales 전표 감사가 Settlement 내부 감사 helper에 결합 | ownership, 모듈 의존 |
| ARC-005 | Medium | 효과 경계의 Object·Map 계약이 실행 타입과 저장 표현을 함께 전달 | abstraction, port/adapter, 호환 복잡성 |

### 평가 항목별 판단

| 평가 항목 | 판단과 상세 위치 |
| --- | --- |
| module ownership | Entity·저장소 소유권은 유지된다. 보정 조율과 전표 감사의 책임 배치는 ARC-002·004에서 평가한다. |
| 모듈 간 dependency | 실제 참조 방향은 2.1에 기록했다. 필요한 업무 API 의존과 내부 helper 결합을 ARC-001·003·004에서 구분한다. |
| Repository/Entity 직접 참조 | 아키텍처 검사 범위에서 타 모듈 직접 의존 0건. 공개 Reader의 Entity 반환 API 노출은 실제 외부 Entity 사용과 구분해 ARC-001에 기록한다. |
| application API | 값 기반 조회·잠금·업무 API는 유지할 근거가 있다. 내부 조립 기능과 Receipt 메커니즘의 공개 범위는 ARC-001·003의 개선 대상이다. |
| port/adapter | Work 소유 port와 Farm 구현의 의존 역전은 타당하다. 보정 adapter의 조율 책임과 효과 계약의 표현 수준은 ARC-002·005에서 평가한다. |
| circular dependency | 소스 모듈 그래프는 비순환이다. 런타임 왕복 호출과 Spring Bean 순환은 4절에서 별도로 구분한다. |
| public/internal application 경계 | 동일 public 서비스에 외부 값 계약과 내부 Entity API가 혼재한다. ARC-001의 주요 문제다. |
| abstraction 수준 | 필요한 업무 값 계약은 존재한다. 저장 callback·Receipt 처리 선택·Object/Map 효과 계약의 추상화 수준은 ARC-002·003·005에서 평가한다. |
| 경계에 필요한 복잡성과 불필요한 복잡성 | port, 원장, 실행 Strategy, 복구·호환 경로의 필요성과 조율·표현 경계에서 축소 가능한 복잡성을 5절에서 구분한다. |

## 2. 모듈 소유권·의존 방향 평가

### 2.1 실제 소스 의존 그래프

아래는 main Java 소스의 프로젝트 클래스 참조를 대조한 방향이다. 런타임 호출 방향이나 DB FK 방향과는 구분한다. `ModularArchitectureTests.ALLOWED_DEPENDENCIES`는 허용 방향이며, 실제 사용 방향과 동일한 목록이라는 뜻은 아니다. 예를 들어 Audit에는 현재 다른 프로젝트 모듈을 참조하는 코드가 없지만 Common 의존은 허용되어 있다.

| 모듈 | 실제 참조하는 다른 모듈 |
| --- | --- |
| common | 없음 |
| audit | 없음 |
| demo | common |
| auth | common, demo |
| work | common |
| farm | common, audit, work |
| partner | common, audit |
| auction | common, partner |
| settlement | common, audit, auction, partner |
| sales | common, audit, farm, partner, auction, settlement |
| dashboard | common, farm |
| analytics | common, farm, work, sales, partner, settlement |
| print | common, sales |

근거: [ModularArchitectureTests.java](../../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java)의 `ALLOWED_DEPENDENCIES`, `modulesUseOnlyDeclaredDependencies()`, `declaredModuleDependenciesAreAcyclic()`와 [ModuleBoundaryInventoryTest.java](../../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java)의 `compiledDependenciesFollowTheDeclaredModuleGraph()`.

이 방향에서는 **모듈 간 소스 의존 순환이 없다**. Farm 구현을 Work port에 연결하므로 Work가 Farm 클래스를 import하지 않고도 농장 기능을 호출한다. 이 역전 자체는 의도에 맞는다.

### 2.2 소유권이 유지되는 부분

| 영역 | 실제 코드 근거 | 평가 |
| --- | --- | --- |
| 난 묶음 현재 상태와 원장 | [OrchidGroupMutationEngine](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) `create()`, `transform()`, `reserve()`, `consumeReservation()`, `correct()` | Farm이 상태와 revision 변경을 소유한다. Work·Sales의 업무 이력을 함께 Farm으로 옮길 이유는 확인되지 않았다. |
| 작업 aggregate와 실행 효과 | [WorkEffectProcessor](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java) `executeAndPersist()`, [WorkEffectStore](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java) `save()` | Work가 작업·효과 저장을 소유하고 Farm handler는 결과 값과 Mutation 식별자를 돌려준다. |
| 작업 대상의 농장 정보 | [WorkTargetResolver](../../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkTargetResolver.java), [FarmWorkTargetResolver](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java) `resolve()`, `lockAndValidateActive()` | Work 소유 port와 Farm 저장소 사용 adapter로 분리된다. Work의 타 모듈 Repository 참조를 요구하지 않는다. |
| 취소·보정의 참조 검사 | [OrchidGroupUsageInspector](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupUsageInspector.java), [WorkOrchidGroupUsageAdapter](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/WorkOrchidGroupUsageAdapter.java) `inspect()`, [SalesOrchidGroupUsageInspector](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupUsageInspector.java) `inspect()` | Farm이 필요한 차단 계약을 소유하고 각 소유 모듈이 자기 데이터만 검사한다. Work 결과를 Farm blocker로 바꾸는 adapter는 단순 위임 이상의 역할이 있다. |
| 출하·lot 생성 | [SalesSlipOutboundService](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java) `complete()`, [AuctionShipmentCreator](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java) `create()` | Sales가 완료 업무를 조율하고 Auction이 자기 Entity를 만든다. 원본 품목 ID별 lot ID 값 계약을 사용한다. |
| 거래처 잠금 | [BusinessPartnerLock](../../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerLock.java) `lockAll()` | Partner가 잠금을 소유하며 외부에는 `BusinessPartnerInfo` 값을 반환한다. |
| 출력·분석 조회 | [PrintQueryService](../../backend/src/main/java/com/greenhouse/backend/print/application/PrintQueryService.java), [AnalyticsQueryService](../../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java) `getSalesAnalytics()`, `getPartnerAnalytics()`, `getWorkAnalytics()` | 소유 모듈 application 값 계약을 조합한다. 분석 모듈의 여러 모듈 의존은 조회 책임에서 발생하며 그 자체를 결함으로 보지 않는다. |
| 감사 저장 | [AuditRecorder](../../backend/src/main/java/com/greenhouse/backend/audit/application/AuditRecorder.java), [JpaAuditRecorder](../../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java) `record()` | 독립 이벤트 계약과 JPA 구현이 분리되고 저장은 호출자의 transaction을 요구한다. Audit이 업무 모듈 Entity를 가져오지 않는다. |

### 2.3 Repository·Entity 직접 참조

`ModuleBoundaryInventoryTest.crossModuleImplementationDependenciesDoNotGrow()` 실행 결과에서 타 모듈 Entity, Repository, QueryDSL Q 타입, Controller, HTTP DTO 의존은 **0건**이었다. `annotatedQueriesDoNotReadForeignEntitiesOrTables()` 결과도 **0건**이었다. 기준 TSV는 설명 주석만 있으며 예외 항목이 없다.

이는 검사 범위의 결과다. 쿼리 검사는 `@Query`의 명시적 Entity/table 이름을 정규식으로 확인한다. 동적 SQL, 별칭을 통한 association join, 모든 런타임 동작을 검증하는 SQL parser는 아니다. [architecture/README.md](../../backend/src/test/resources/architecture/README.md)도 이 한계를 명시한다.

타 모듈의 `PartnerType`, `PaymentTargetType`, `WorkEffectKind`, `WorkTypeDefinition` 같은 enum 참조는 존재한다. Entity·Repository 직접 사용과 구분한다. 코드상 필요한 업무 값·작업 식별 계약을 공유하는 사례이며, domain 패키지에 있다는 이유만으로 모두 위반으로 분류하지 않았다.

### 2.4 application API·트랜잭션의 구조

[backend/settings.gradle.kts](../../backend/settings.gradle.kts)와 [backend/build.gradle.kts](../../backend/build.gradle.kts)는 하나의 Java 프로젝트를 구성한다. 모듈 경계는 Gradle 하위 프로젝트나 Java module export로 강제되지 않으며 패키지·테스트가 보호한다. 현재 규모에서 이것만으로 별도 빌드 모듈 분리가 필요하다는 근거는 없다.

`SalesSlipCreationService.create()`, `InboundRecordService.create()/voidPotting()`, `WorkOperationCorrectionService.create()` 등 최상위 application service가 쓰기 transaction을 연다. Engine, Partner lock, Auction creator, Receipt, Audit recorder는 `MANDATORY` 계약으로 그 transaction에 참여한다. 이 구성은 모듈 경계를 유지하면서 업무 변경과 관련 기록을 원자적으로 처리하기 위한 구조다. 상세 잠금·정합성 평가 전체를 이번 평가의 결론으로 확대하지 않는다.

구체 서비스 클래스를 application API로 호출하는 것도 허용 가능한 방식이다. 모든 서비스에 interface를 붙일 필요는 없다. 문제는 외부 사용을 승인한 계약과 내부 조립 API의 범위가 명확한지에 있다(ARC-001).

## 3. 문제별 평가

### ARC-001 — 외부 값 계약과 내부 Entity API가 같은 공개 서비스에 혼재

- **ID:** ARC-001
- **Severity:** Medium
- **근거 코드:**
  - [OrchidGroupReader.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java) 19~39행: public 클래스에 `findById()/findDetailById(): Optional<OrchidGroup>`와 `lockStates()/getStates(): Map<Long, OrchidGroupState>`가 함께 있다.
  - [SalesSlipAllocationFactory.java](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationFactory.java) `createItems()` 35행, [SalesSlipDocumentAssembler.java](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipDocumentAssembler.java) `assemble()` 55행: 외부 소비자인 Sales가 같은 Reader의 값 반환 메서드를 사용한다.
  - [OrchidGroupReconciliationService.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReconciliationService.java) `reconcile()` 26행: Entity 반환 메서드는 현재 Farm 내부에서 사용한다. 23·31행에서는 Work의 내부 조립 helper 성격인 `WorkOperationSupport.varietyHistoryTitle()`에도 의존한다.
  - [ModuleBoundaryInventoryTest.java](../../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java) `implementationKind()`: application 내부 타입 자체는 금지 대상으로 분류하지 않는다. [OrchidGroupMutationRecorder.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationRecorder.java) 35행에는 package-private 내부 구현을 사용하는 선례가 있다.
- **현재 구조:** 소유 모듈 내부 API와 다른 모듈용 API가 public application 패키지에 함께 배치된다. Reader를 참조할 수 있는 외부 코드에 Entity 반환 메서드도 Java 접근 수준상 열려 있다.
- **문제점:** 외부에 제공하는 계약의 범위를 클래스에서 식별하기 어렵다. 현재 Sales가 Entity 반환 메서드를 호출하는 위반은 확인하지 않았다. 그러나 경계 보호는 접근 자체보다 이후 타 모듈 Entity 의존을 검사하는 방식에 의존한다. application helper 직접 참조는 현재 테스트를 통과한다.
- **실제 변경 시 영향:** Reader 외부 계약을 좁히면 Sales의 allocation 생성·출고·상세·판매 가능 조회와 Farm 현장 보정의 주입 타입에 영향이 있다. Work helper를 내부화하려면 Farm의 작업 생성 입력 조립도 함께 정리해야 한다. 반환 값과 HTTP 응답을 유지하면 API schema 변경은 필수적이지 않다. JPA Entity를 값으로 무조건 교체하면 내부 변경 감지·연관 접근 동작이 달라질 수 있다.
- **개선 방향:** 실제 외부 소비자가 필요한 값 조회·잠금 메서드만 공개 계약으로 식별하고 Entity 조회는 Farm 내부 경로로 제한한다. Work의 내부 helper 대신 작업 생성 API가 필요한 조립 책임을 맡도록 한다. 외부에서 참조 가능한 타입·메서드의 허용 범위를 아키텍처 테스트로 보호한다. 모든 application 클래스의 일괄 이동이나 interface 추가는 필요하지 않다.

### ARC-002 — Work 보정 조율과 감사 저장 시점이 Farm adapter에 분산

- **ID:** ARC-002
- **Severity:** Medium
- **근거 코드:**
  - [WorkOperationCorrectionService.java](../../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java) `create()` 37~58행: Work가 접수·원본 잠금·감사 Entity를 준비한 뒤 `() -> correctionRepository.save(correction).getId()`를 port에 넘긴다.
  - [WorkCorrectionPort.java](../../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionPort.java) 7행: 계약이 단순 감사 ID 대신 `Supplier<Long>` 저장 callback을 받는다.
  - [FarmWorkCorrectionAdapter.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java) `correct()` 46~124행: Work 참조 조회, 난 묶음 잠금·차단 검사, Work 날짜 비교, 98행 callback 호출, Farm Mutation, 120행 Work 날짜 변경, 보정 결과 JSON 조립을 담당한다.
  - [WorkOperationDateCorrectionService.java](../../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationDateCorrectionService.java) `correct()` 24~32행: Work Entity의 작업일을 바꾼다.
- **현재 구조:** Work가 최상위 transaction과 저장소를 소유하지만 보정의 실제 순서 일부는 Farm adapter가 결정한다. 호출은 `Work correction service → Farm correction adapter → Work reference/date service`이고, 감사 저장은 adapter의 callback 호출 시점에 실행된다.
- **문제점:** Farm 상태 변경을 위한 adapter가 Work 날짜 변경과 Work 감사 생성의 실행 시점까지 조율한다. 날짜만 바꾸는 보정도 Farm port를 통과한다. adapter 교체나 보정 규칙 변경 시 Work 저장 callback과 날짜 유스케이스를 함께 이해해야 한다. 이는 **런타임 호출의 재진입과 책임 분산** 문제이며, 소스 모듈 순환 또는 Spring Bean 생성 순환이 확인됐다는 뜻은 아니다.
- **실제 변경 시 영향:** 보정 생성 service, port, Farm adapter, 날짜 service, 감사 결과 조립 및 보정 테스트가 함께 영향을 받는다. 접수 지문·재요청 결과, 결과 사용 검사, 원본→대상 잠금, no-op 거부, 날짜만 변경할 때 Mutation 없음, 생성 취소와 날짜 변경 조합 거부를 유지해야 한다. 감사 ID는 Mutation 출처이므로 Farm 변경 전에 확정되어야 하고 실패 시 감사·접수·Mutation 전체 rollback을 보존해야 한다.
- **개선 방향:** Work가 날짜 판단·변경, 감사 ID 확보, 최종 결과 조립을 조율한다. Farm port는 대상의 농장 규칙 검증과 상태 변경을 수행하고 typed 변경 결과를 반환한다. 저장 callback 대신 Work가 확보한 감사 ID와 필요한 값 context를 전달한다. 검증을 별도 transaction으로 떼거나 외부 메시징으로 바꾸지 않는다. Farm과 Work 양쪽 데이터가 필요한 조합 규칙은 상위 유스케이스에서 명시적으로 조율한다.

### ARC-003 — Work Receipt의 처리 방식이 Farm 유스케이스에 노출

- **ID:** ARC-003
- **Severity:** Medium
- **근거 코드:**
  - [InboundRecordService.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java) `voidPotting()` 146~164행: `WorkCommandReceipts.executeExisting("INBOUND_POTTING_VOID:" + inboundRecordId, key, new PottingVoidIdentity(...), Supplier<List<Long>>)`로 Farm의 잠금·검증·감사까지 감싼다.
  - [WorkCommandReceipts.java](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java) 28~57행: 문자열 scope, Object 지문 입력, 실행 callback을 받는다. `execute()`와 `executeExisting()` 선택이 creation membership 저장 여부를 결정한다.
  - [InboundWorkOperationLifecycleService.java](../../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundWorkOperationLifecycleService.java) `voidPottingForInboundRecord()` 51행: 입고와 연관된 Work 취소라는 업무 API도 별도로 존재한다.
- **현재 구조:** 저장소 직접 접근은 Work 내부에 남아 있다. 하지만 Farm이 Work Receipt의 namespace, 지문 입력 표현, 생성/기존 작업 분류, 저장할 Work ID 목록을 직접 결정한다.
- **문제점:** 외부 API가 업무 명령보다 Receipt 저장 메커니즘 수준으로 공개되어 있다. Farm은 자기 업무와 함께 Work 접수 모델의 규칙을 알아야 한다. 새로운 소비자가 잘못된 scope나 creation 방식을 선택해도 타입 계약으로 막기 어렵다. 현재 멱등 처리가 실패했다는 판정은 아니다.
- **실제 변경 시 영향:** Receipt identity·지문이 이미 저장된 요청의 재시도 기준이다. namespace 또는 `PottingVoidIdentity` 표현을 바꾸면 기존 동일 요청을 신규 요청이나 충돌로 처리할 수 있다. 생성 Receipt membership을 기존 작업 취소에 추가하면 관계 조회 의미도 바뀐다. 입고 상태·Work 보상·감사 기록의 동일 transaction을 유지해야 한다.
- **개선 방향:** 외부에는 포트 실행 취소의 typed 업무 계약을 제공하고 scope·지문·membership 선택은 Work 내부에 둔다. Farm 전체 유스케이스의 접수까지 소유해야 한다면 그 경계를 명시한 전용 계약으로 좁힌다. 기존 identity와 지문 호환을 유지하며 저장 엔진 재사용과 외부 업무 API를 구분한다. 이를 위해 곧바로 범용 common 멱등 프레임워크를 추가할 필요는 없다.

### ARC-004 — Sales 전표 감사가 Settlement 내부 감사 helper에 결합

- **ID:** ARC-004
- **Severity:** Low
- **근거 코드:**
  - [SalesPaymentService.java](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java) 27행과 `confirmPayment()` 43~56행: `SettlementAuditSupport.paymentSnapshot()/recordTargetPayment()`를 호출해 Sales 전표 변경을 기록한다.
  - [SettlementAuditSupport.java](../../backend/src/main/java/com/greenhouse/backend/settlement/application/SettlementAuditSupport.java) 21~75행: 정산 설정·경매 정산·입금 이벤트 Entity를 받는 내부 메서드와 외부 Sales용 값·Map 메서드가 함께 존재한다.
  - [SalesSlipAuditSupport.java](../../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAuditSupport.java): Sales가 자기 전표 감사를 조립하는 기존 위치다.
- **현재 구조:** Sales의 입금 transaction은 Sales가 조율하고, 입금 원장·잔액 처리는 Settlement의 업무 API를 사용한다. 전표의 전후 snapshot과 감사 저장 호출도 Settlement helper를 사용한다.
- **문제점:** 입금 원장 호출은 필요한 모듈 의존이다. 반면 Sales Entity 변경의 snapshot 표현은 Settlement의 다른 내부 감사 기능과 같은 helper에 묶여 있다. Settlement 감사 내부 변경이 Sales 소비자 계약에도 영향을 줄 수 있다. Entity를 외부로 넘기는 직접 참조 위반은 없다.
- **실제 변경 시 영향:** Sales 전표 입금 감사의 필드명·변경 순서·`SETTLEMENT_MANAGEMENT` source·대상 유형을 유지해야 한다. Sales 전표 감사와 Settlement 입금 이벤트 감사는 서로 다른 사실이므로 하나를 없애거나 중복 기록하면 안 된다. 입금·잔액·감사 실패 시 전체 rollback도 유지한다.
- **개선 방향:** 전표 변경 snapshot은 Sales가 소유하고 Audit의 값 계약을 사용한다. 공유가 필요한 입금 감사 의미가 있다면 Settlement의 전용 값 API로 좁혀 내부 Entity snapshot helper와 구분한다. 입금 원장·잔액 application API를 제거할 이유는 없다.

### ARC-005 — 효과 경계가 실행 타입과 저장 표현을 함께 전달

- **ID:** ARC-005
- **Severity:** Medium
- **근거 코드:**
  - [WorkEffectCommand.java](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectCommand.java) 7~8·36행: `Map<String, Object> resultDetails`, `Object payload`, `payloadAs()`의 런타임 타입 검사를 사용한다.
  - [WorkExecutionResult.java](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkExecutionResult.java) 6~7행: 효과 결과 경계도 `Map<String, Object>`다.
  - [RepotWorkHandler.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotWorkHandler.java) `execute()` 33~47행과 [MergeWorkHandler.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MergeWorkHandler.java) `execute()` 44~56행: typed `StructureChangeCommand`와 호환 요청 경로를 런타임에 선택한다.
  - [LegacyStructureChangeRequestMapper.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/LegacyStructureChangeRequestMapper.java) `read()` 18~21행: Map 또는 Object를 Farm HTTP DTO `RepotWorkOperationRequest`로 변환한다.
  - [BatchStructureTransformationExecutor.java](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java) 123~130·170~179행: Farm에서 `WorkEffectResults.Transformation.toMap()`을 호출한 결과를 반환한다.
  - [WorkEffectStore.java](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java) `save()` 51~55행: command Map과 result Map을 Work Entity의 저장 표현으로 사용한다. [WorkEffectResults.java](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectResults.java) `Transformation.toMap()`은 단일 원본 호환 필드까지 포함한다.
- **현재 구조:** 새 구조 변경은 typed command를 사용하고 typed 결과 builder도 존재한다. 그러나 공통 handler 경계는 Object·Map으로 열려 있고, Farm adapter가 호환 HTTP DTO 해석 및 Work 저장 JSON 변환까지 수행한다.
- **문제점:** handler code와 payload 타입의 조합을 컴파일 시점에 제한하지 못한다. 입력 채널과 persistence JSON 표현이 실행 경계까지 확장되어 타입 변경을 payload 검사·변환·fingerprint·조회와 함께 추적해야 한다. typed builder 덕분에 문자열 필드 중복은 줄었으나 결과의 타입 보장은 모듈 경계를 통과하기 전에 사라진다.
- **실제 변경 시 영향:** Work 실행 service·processor·store, Farm handler, 호환 Controller, 결과 조회·계보·보정, 효과 지문 테스트가 영향을 받는다. 기존 JSON 필드의 존재 여부·null·값 타입은 저장 계약이며 재실행 지문에도 쓰인다. 공개 분갈이·합식 호환 endpoint는 실제 Controller와 OpenAPI에 남아 있으므로 경로를 단순 삭제할 수 없다.
- **개선 방향:** 새 실행 경로는 handler가 받는 명령과 결과를 typed 계약으로 끝까지 유지하고 JSON 변환은 Work 저장·조회 경계에서 수행한다. 공개 호환 입력은 진입부에서 application command로 변환한다. 기존 저장 JSON 해석은 별도 호환 변환 경계로 남긴다. 이미 존재하는 command/result 타입을 활용하며 범용 플러그인 시스템이나 전 handler의 대규모 generic 계층을 먼저 도입하지 않는다.

## 4. 순환 의존 평가

| 구분 | 결과 | 근거·한계 |
| --- | --- | --- |
| 모듈 소스 의존 순환 | 발견되지 않음 | 선언 DAG 검사와 compiled dependency 검사 모두 통과 |
| 런타임 모듈 왕복 호출 | 존재 | 보정의 `Work → Farm adapter → Work date/reference service`, 입고 취소의 `Farm → Work lifecycle → Farm compensation adapter` |
| Spring Bean 생성 순환 | 이번 평가에서 확인하지 않음 | 위 호출 왕복만으로 빈 순환을 판정하지 않는다. 이번 실행은 아키텍처 테스트이며 전체 application context 기동 검증은 수행하지 않았다. |

입고 취소는 Farm 상태와 Work lifecycle을 한 transaction에 조율해야 하므로 모듈 왕복만으로 결함이 되지 않는다. ARC-002는 왕복 유무가 아니라 Farm adapter가 Work 날짜 변경과 감사 저장 실행 시점까지 맡는 구체적 책임 배치를 문제로 삼는다. 해결을 위해 Work→Farm 소스 의존을 추가하면 현재의 의존 역전을 훼손할 수 있다.

## 5. 경계에 필요한 복잡성과 축소 가능한 복잡성

| 구조 | 구분 | 판단 근거 |
| --- | --- | --- |
| Work 소유 target/reference/void/graph port와 Farm adapter | 필요한 복잡성 | Work가 농장 저장소에 의존하지 않고 값·식별자로 필요한 정보를 얻는다. 조회·실행·보상은 서로 다른 계약이다. |
| Farm 사용 여부 port, Work 변환 adapter, Sales inspector | 필요한 복잡성 | 소유 모듈의 참조 검사를 Farm의 차단 계약으로 변환하며 의존 순환을 피한다. `@Order`는 차단 사유 우선순위 의미도 가진다. |
| typed Mutation command·결과·recorder·replay·write fence | 필요한 복잡성 | 단일 writer, revision 이력, 멱등 재실행, DB 우회 차단의 서로 다른 책임이다. 클래스 수만으로 불필요하다고 볼 수 없다. |
| Work 효과 기록과 Farm Mutation 원장의 분리 | 필요한 복잡성 | 작업의 실행 의미와 난 묶음 상태 revision은 다른 사실이다. Mutation ID·correlation ID로 연결하는 현재 구성을 유지할 근거가 있다. |
| 구조 변경 공통 실행기와 작업별 Strategy | 필요한 복잡성 | [StructureChangeStrategy](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategy.java)의 품종·원본 속성·수량 정책 차이를 공유 N:M 실행과 분리한다. [StructureChangeStrategyRegistry.validateDefinitions()](../../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java)는 등록 누락을 확인한다. |
| 원장 importer·대사 CLI·제한된 Work 이관 API | 필요한 복잡성 | ADR-002와 실제 `OrchidGroupStateChainMigrationService`·`WorkOrchidGroupStateChainMigrationService`가 복구·기존 이력 연결을 담당한다. 운영 복구 조건 확인 없이 과거 코드로 간주해 제거할 대상이 아니다. |
| 공개 분갈이·합식 호환 API | 현재 필요한 복잡성 | [RepotWorkOperationController.execute()](../../backend/src/main/java/com/greenhouse/backend/farm/controller/transformation/RepotWorkOperationController.java), [WorkOperationController.completeMerge()](../../backend/src/main/java/com/greenhouse/backend/work/controller/WorkOperationController.java), orchid-command/work-operation slice에 실제 노출된다. |
| public Reader의 Entity API와 외부 값 API 혼재 | 축소 가능한 경계 복잡성 | ARC-001. 내부 기능을 별도로 제한하면 외부 계약 이해 범위를 줄일 수 있다. |
| Farm 보정 adapter의 Work 저장 callback·날짜 변경 | 축소 가능한 조율 복잡성 | ARC-002. Work 소유 조율을 모으면 왕복과 숨은 실행 시점을 줄일 수 있다. |
| 외부에 노출된 Receipt scope·membership 선택 | 축소 가능한 계약 복잡성 | ARC-003. 저장 엔진 재사용을 유지하면서 업무 API를 좁힐 수 있다. |
| 실행 port 안의 호환 DTO 해석·JSON 저장 표현 | 축소 가능한 표현 복잡성 | ARC-005. 호환성은 유지하되 변환 위치와 typed 실행 계약을 분리할 수 있다. |

별도 Mutation top-level 모듈, 모든 service의 interface화, Print의 범용 renderer/provider, 외부 메시징·분산 transaction을 추가해야 한다는 근거는 확인되지 않았다. 개선은 기존 소유 모듈 안에서 공개 계약과 조율 책임을 좁히는 범위부터 검토한다.

## 6. 실제 변경 시 검증할 계약

아래 테스트는 해당 개선을 실제 구현할 때의 회귀 근거다. **이번에 실행한 테스트와 구분한다.**

| 대상 | 확인할 테스트·메서드 |
| --- | --- |
| 공개 경계 제한 | `ModularArchitectureTests`, `ModuleBoundaryInventoryTest` 및 외부 API 허용 범위 검사 |
| 보정 조율 이동 | [WorkCorrectionAuditPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java) `concurrentDuplicateRequestsCreateOneAuditAndOneMutation()`, `dateOnlyCorrectionHasNoMutationAndKeyReuseIsRejected()`, `failedBulkCorrectionRollsBackReceiptAuditAndAllQuantityChanges()` |
| Receipt 계약 축소 | [WorkIdempotencyPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkIdempotencyPostgresE2ETest.java) `concurrentImmediateRequestsCreateOneOperationAndOneEffect()`, `aFailureInTheSecondBatchRecordRollsBackAllResultsAndTheReceipt()` 및 입고 포트 취소의 동일 키 재시도·다른 내용 충돌 검사 |
| Sales 감사 책임 정리 | [PaymentTests](../../backend/src/test/java/com/greenhouse/backend/PaymentTests.java) `confirmsPartialAndFullSalesSlipPayments()`, [PartnerSettlementPostgresE2ETest](../../backend/src/test/java/com/greenhouse/backend/work/e2e/PartnerSettlementPostgresE2ETest.java) `aLaterFailureRollsBackTheSalesPaymentAndAllLedgerEffects()` 및 전표·입금 감사의 필드/source 계약 |
| 효과 typed 경계 | [WorkEffectResultsTest](../../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectResultsTest.java) `transformationKeepsSingleSourceCompatibilityAndResultOrder()`, `movementKeepsRawSnapshotAndNullPositions()`, [WorkEffectProcessorTest](../../backend/src/test/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessorTest.java) `replayReturnsStoredResultWithoutResolvingHandlerOrCreatingContext()` |

HTTP 계약이 변경될 경우 Controller·DTO·validation과 테스트를 기준으로 OpenAPI 및 프론트 생성 타입을 갱신해야 한다. 내부 경계 정리만으로 공개 schema까지 변경할 필요는 없다. 이번에는 OpenAPI를 생성하거나 수정하지 않았다.

## 7. 이번 평가의 검증 결과·제한

실행 명령(`backend` 디렉터리):

```powershell
.\gradlew.bat test --tests 'com.greenhouse.backend.ModularArchitectureTests' --tests 'com.greenhouse.backend.ModuleBoundaryInventoryTest' --tests 'com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupWriterArchitectureTest'
```

- 결과: **BUILD SUCCESSFUL**. XML 결과 기준 9 + 4 + 3 = **16 tests**, failures/errors/skipped 모두 0.
- 재확인: 기준 revision과 구현 코드가 그대로인 상태에서 문제별 근거와 실제 소스 의존 그래프를 다시 대조했다. 이후 변경은 이 문서의 평가 항목별 판단 표 추가이며, 테스트를 재실행하지 않고 기존 성공 결과를 확인했다.
- 결과 파일: `backend/build/test-results/test/TEST-*.xml`, `backend/build/reports/architecture/*.tsv`.
- Repository·Entity·HTTP DTO·Q 타입 등 외부 구현 의존 inventory와 명시적 외부 쿼리 inventory는 빈 결과였다.
- Engine writer 검사도 통과했다. 내부 기능 노출이나 조율 책임의 적절성은 이 검사들의 성공만으로 보장되지 않으며 ARC-001~005는 별도 코드 검토 결과다.
- 미실행: 전체 backend test, frontend check, PostgreSQL E2E, 운영 DB/manifest 검증, 전체 application context 기동 검증. 이번 문서 작성에서는 코드·DB 경계를 변경하지 않았다.
- [01-system-map.md](01-system-map.md)는 탐색 기준으로 사용했고 문제별 근거는 실제 소스를 다시 확인했다. 수정 전 문서나 이관 설명을 현재 구현 위반의 증거로 사용하지 않았다.
