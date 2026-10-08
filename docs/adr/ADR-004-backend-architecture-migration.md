# ADR-004: 공개 API·SPI 경계와 기능 우선 패키지로 백엔드를 점진적으로 전환한다

- 상태: 승인 — 기존 소유권·의존 방향 유지, 단계적 전환 진행 중
- 작성일: 2026-10-08, Asia/Seoul
- 범위: 백엔드 패키지 배치, 공개 계약, 아키텍처 테스트 및 문서 전환
- 관련 문서: [목표 설계](../green-house-backend-architecture-final.md), [현행 아키텍처](../04-architecture.md), [Sales 소유권 결정](ADR-003-sales-document-information-architecture.md)

실제 소스와 아키텍처 테스트를 기준으로 전환 방향과 이유를 기록한다. 구현 완료를 의미하지 않는다.

## 1. 배경과 결정

목표 설계의 공개 계약·기능 우선 배치·단일 Writer 원칙을 적용한다. 현재 구현에 이미 반영된 Sales 통합과 이관 도구 제거는 반복하지 않는다. 기존 소유권과 컴파일 의존 방향을 유지하며 계약 정리 → 기능별 이동 → Mutation 내부 분리 순으로 진행한다.

목표 문서의 예시 트리를 그대로 복제하지 않는다. HTTP·DB·업무 정책 변경과 구조 리팩터링은 분리한다. 기존 클래스명도 1차 이동에서 유지한다.

### 확인된 차이

| 항목 | 현재 코드·테스트 | 계획에 반영할 결정 |
|---|---|---|
| Sales 최상위 통합 | `partner`, `auction`, `settlement` 최상위 패키지가 없고 Sales 안으로 통합됨 | 재통합 작업 제외 |
| Sales 내부 소유권 | `document`, `direct`, `auction`, `payment`, `partner` | 이름과 소유권 유지. `slip`으로 일괄 치환하거나 `settlement` 기능을 다시 만들지 않음 |
| Farm–Work 컴파일 의존 | `Farm → Work`, Work는 Farm 타입을 직접 참조하지 않는 허용표 | 1차 전환에서 유지. Work가 소유한 확장 계약은 `work/spi`, Farm 구현은 `integration`에 배치 |
| 계층 배치 | `module/application/feature`, `domain/feature`, `repository/feature`, `controller`, `dto` | `module/feature/{application,domain,repository,web}`로 단계적 이동 |
| Mutation | 단일 Engine 쓰기를 테스트로 강제하지만 계약·엔진·대사·설정 등이 `farm/application/orchid/mutation`에 혼재 | 외부 쓰기 계약 추출 후 `farm/mutation` 내부 정리 |
| 이관 도구 | Cutover·StateChainMigration runtime 클래스 제거를 테스트로 강제 | 제거 작업 제외. 남은 대사·기동 검증 CLI는 운영 도구로 보존 |
| 아키텍처 검사 | 기존 계층 경로와 application 공개 계약을 전제로 검사 | 새 경로·API·SPI를 인식하도록 먼저 보강 |

근거: `ModularArchitectureTests`, `SalesArchitectureTest`, `ModuleBoundaryInventoryTest`, `PublicApplicationContractArchitectureTest`, `OrchidGroupWriterArchitectureTest`. 테스트 소스와 정적 참조를 확인한 결과다. 착수 시 기존 아키텍처·공개 계약·단일 Writer 테스트도 통과했으며 후속 검증은 아래 실행 기록에 남긴다.

## 2. 유지할 의존 방향

### 최상위 모듈

`ModularArchitectureTests.ALLOWED_DEPENDENCIES`의 현행 허용표를 초기 기준으로 사용한다.

| 호출·구현 모듈 | 허용 대상 |
|---|---|
| common | 없음 |
| audit | common |
| farm | common, work, audit |
| work | common |
| sales | common, audit, farm |
| dashboard | common, farm |
| print | common, sales |
| analytics | common, farm, sales, work |
| auth | common, demo |
| demo | common |

Work 실행이 Farm의 효과 구현을 호출하는 런타임 경로와 `Farm → Work` 컴파일 의존을 구분한다. 현재 Farm의 입고·구조 변경·조회가 Work application을 호출하므로 `Work → Farm` 직접 의존만 추가하면 순환이 생긴다. 이 방향 전환은 이번 구조 정리에서 제외하고, 필요하면 해당 호출을 모두 조사한 별도 설계로 다룬다.

### Sales 내부

`SalesArchitectureTest.GRAPH` 기준의 허용표:

| 기능 | 허용 대상 |
|---|---|
| document | partner, payment |
| direct | document, payment, partner |
| auction | document, payment, partner |
| payment | partner |
| partner | 없음 |

Document의 Payment 참조는 기존 인터페이스 제한도 유지한다. Document 소유 `AuctionDocumentPort`, `DirectDocumentAccountingPort`와 Payment 소유 대상 Port를 Auction·Direct가 구현하는 의존 역전도 보존한다. 목표 문서의 예시 `slip → auction`으로 대체하지 않는다.

## 3. 단계별 실행 단위

각 단계는 독립적으로 검증·병합 가능한 PR로 나눈다. 많은 기능을 포함하는 단계는 기능마다 PR을 분리한다.

| 단계 | 작업 | 완료 조건 |
|---|---|---|
| P0 기준 확정 | 전체 공개 타입·메서드·SPI 구현·호출 경로의 이동표 작성. 목표 문서의 현황·Sales 허용표·Farm–Work 방향 보완. 기존 검사 baseline 실행 | 현행 의존표, 예외, 타입의 소유권·이동 위치와 검증 결과 기록 |
| P1 검사 보강 | 새 API/SPI/기능 우선 경로를 인식하는 소유권 판별 추가. 기존과 새 경로를 이행 중 함께 검사 | 두 배치 모두에서 위반 검출. 경로 이동으로 검사 대상이 빠지지 않음 |
| P2 공개 계약 정리 | 외부 사용 타입을 `farm/api`, `work/api`, `sales/api`와 필요한 공개 SPI로 이동. 소비자 import 전환 | 타 모듈 application 구현 직접 참조 제거, 공개 값의 Entity·HTTP DTO·projection 누출 없음 |
| P3 Sales 기능별 이동 | partner → payment → document → direct → auction 순으로 기능 우선 배치. 기능 간 계약은 각 기능 `api`, 공급자 확장 계약은 필요한 기능 `spi` | 기존 기능 그래프·Port 제한·동일 트랜잭션 참여 유지 |
| P4 Farm·Work 이동 | Farm의 variety/material/structure 등부터 이동. Work operation/target/effect/correction 이동 후 Farm inbound/transformation 및 관련 adapter 정리 | 업무별 완료 책임·실행 순서 유지, Work 구현에 Farm 직접 의존 없음 |
| P5 Mutation 분리 | engine/ledger/query/verification/config 및 Farm API 계약으로 재배치 | 단일 Writer, 기존 지문·snapshot·replay·revision·write fence 보존 |
| P6 마무리 | 불명확한 이름과 불필요한 위임 계층을 개별 검토. 이행용 예외·기존 경로 지원 제거. 문서·CI 동기화 | 새 구조만 허용하는 검사 통과, 전체 CI와 PostgreSQL 핵심 회귀 통과 |

### P1에서 놓치기 쉬운 검사

- `ModularArchitectureTests`의 고정 계층 목록과 경로 탐색을 기능 우선 구조에 맞춘다. 기존 검사를 통째로 제거하지 않는다.
- `SalesArchitectureTest.feature()`는 현재 경로의 두 번째 조각을 기능으로 해석한다. 새 경로의 첫 번째 기능과 API/SPI 소유권을 인식하도록 수정한다.
- `CrossModuleApplicationApiInspection` 및 공개 계약 검사에 `api`·`spi`를 포함한다. 외부가 구현한 SPI도 값 계약 검사 대상이다.
- 공개 API의 모든 메서드·중첩 값·generic을 검사하고, 내부 application/web/domain/repository 접근을 차단한다.
- architecture TSV의 FQCN은 실제 이동표에 따라 갱신한다. 새 위반을 일괄 승인하거나 패키지 전체를 예외로 허용하지 않는다.
- Farm Mutation의 OrchidGroup 접근은 명시적 내부 예외다. 기능 간 저장소 금지를 적용하면서 단일 Writer의 정상 접근까지 막지 않는다. 다른 Farm 기능 간 저장소 접근은 조사 후 제공 기능의 계약으로 전환한다.

### P2의 계약 선택

- Farm의 `OrchidGroupReader`는 저장소를 가진 구현이다. 공개 읽기 계약과 구현을 분리하고 기존 구현은 `farm/orchid/application`에 둔다.
- `OrchidGroupMutationWriter`는 기존 Engine의 실제 소비 메서드만 제공한다. Engine은 이 계약을 구현하며 `MANDATORY` 트랜잭션 참여를 유지한다.
- Mutation 명령·결과와 함께 공개 시그니처에 필요한 enum·중첩 값의 소유 위치도 정리한다. Entity에서 공개 값으로 변환하는 factory는 내부 assembler로 옮긴다.
- Farm이 구현하는 `WorkEffectHandler`, `WorkCorrectionPort`, 조회·잠금·취소 확장 계약은 Work 소유 공개 SPI 후보다. Farm이 호출하는 Work 유스케이스는 Work API 후보다.
- 기존의 안전한 호출 계약은 최소 범위로 옮긴다. 내부 구현을 감추는 데 필요한 경계는 만들되, 단순 전달용 Service·Adapter를 일괄 추가하지 않는다.
- 현재 여러 기능의 계약을 조합하는 HTTP Controller는 책임을 확인해 기능 `web` 또는 모듈 `web`에 배치한다. Controller의 기존 조합 동작을 새 application 트랜잭션으로 옮기는 변경은 별도 검증한다.

### P5의 이동 기준

| 현재 책임 | 목표 위치 |
|---|---|
| Engine, normalizer, fingerprint, replay | `farm/mutation/engine` |
| Mutation·Entry·snapshot Entity 및 저장소, Recorder | `farm/mutation/ledger/{domain,repository}` 및 ledger |
| 원장·그래프 조회 | `farm/mutation/query` |
| 대사·rehearsal·운영 검증 CLI | `farm/mutation/verification` |
| writer properties/configuration/startup guard | `farm/mutation/config` |
| 외부 공개 Writer·command·result | `farm/api/orchid` |
| Work SPI 구현 | 책임을 가진 Farm 기능의 `integration` |

`backend/build.gradle.kts`의 `orchidLedgerReconcile`, `orchidLedgerStartupVerify` mainClass, 테스트 FQCN, reflection·Bean 조건·QueryDSL 생성 참조도 함께 갱신한다. 저장 JSON의 필드·enum·지문·멱등 namespace는 유지하며 FQCN이 저장되는 경로가 있는지 P0에서 확인한다.

## 4. 검증과 문서 갱신

1. P0: 기존 architecture/공개 계약/Writer 테스트를 집중 실행해 baseline을 남긴다. 기존 실패와 이번 변경의 회귀를 구분한다.
2. 반복 중: `compileJava`, `compileTestJava`, 영향받는 architecture·기능 테스트만 실행한다. 이동 후 clean compile로 오래된 FQCN·QueryDSL 생성물의 잔존을 확인한다.
3. 기능 단위 완료: 백엔드 `./gradlew test`, `./gradlew spotlessCheck`, 프론트 `npm run check`를 각 한 번 실행한다.
4. API 표현에 영향 가능한 이동: `python3 scripts/generate_openapi.py`로 재생성해 경로·schema·validation drift를 비교한다. 생성 명세 직접 수정 금지. 프론트 계약 변경이 있으면 `npm run api:types` 실행.
5. DB 경계를 변경한 체크포인트: `workE2eTest`로 rollback·잠금·동시성·멱등 재전송·수량/금액·원장 연결을 검증한다. 단순 파일 이동마다 반복하지 않는다. 최종 병합 전 기존 CI의 PostgreSQL 핵심 회귀도 통과한다.
6. CLI 이동: 대사·기동 검증 task의 진입점과 복원본에서의 검증 결과를 확인한다. 운영 DB 쓰기는 이 계획의 실행 범위가 아니다.

구현 단계마다 `docs/04-architecture.md`를 현재 구조에 맞춰 갱신한다. 목표 문서는 확정된 의존 방향을 반영하고, 운영 CLI 경로가 바뀌면 `docs/07-deployment.md`도 갱신한다. 기존 `backend-refactoring-plan.md`의 완료 작업을 재개하지 않고 이 계획을 후속 구조 정리로 연결한다. 업무 정책이 바뀌지 않으면 도메인 문서와 API 필드 목록을 중복 작성하지 않는다.

### 문서의 최종 위치

- 전환 중 `docs/green-house-backend-architecture-final.md`는 목표 설계, `docs/04-architecture.md`는 현재 구현의 기준으로 사용한다.
- 구현 완료 시 목표 설계의 확정된 모듈 소유권·패키지 규칙·API/SPI 경계·허용 의존 방향·단일 Writer 원칙을 `docs/04-architecture.md`의 백엔드 구조와 구현 기준에 통합한다. 실제 구현에 맞춰 예시와 미확정 항목을 정리하고, 프론트엔드 등 기존 아키텍처 내용은 유지한다.
- 통합 완료 후 목표 설계 파일은 `docs/archive/plans/green-house-backend-architecture-final.md`로 이동한다. 이 ADR은 결정 근거와 전환 기록으로 `docs/adr/`에 유지하고, 구현 진행에 따라 상태와 검증 결과를 갱신한다.
- `docs/00-index.md`, `docs/features/README.md`, 보관 문서의 상대 링크와 기타 참조를 갱신한다. 보관 문서에는 현행 기준이 `docs/04-architecture.md`임을 명시한다.
- 이후 신규 개발과 유지보수의 아키텍처 기준은 `docs/04-architecture.md`다. 보관된 설계는 변경 경위를 확인하는 참고 자료로만 사용하며, 이 ADR은 결정 근거를 보존한다.

## 5. 최초 착수 범위와 최종 완료 기준

첫 PR은 P0·P1의 기준 확정과 검사 보강으로 제한한다. 이후 P2는 Work API/SPI, Farm Reader/Writer, Sales 외부 계약으로 나누어 진행한다. 전체 패키지 이동과 계약·클래스명·업무 로직 변경을 한 PR에 섞지 않는다.

최종 완료 조건:

- 외부 모듈 접근이 허용된 `api`·`spi`로 제한되고 모듈·Sales 기능 그래프에 순환이 없다.
- 기능 우선 구조 및 Mutation 내부 구조가 실제 소유권과 일치한다.
- Engine만 OrchidGroup을 생성·변경·저장한다. 업무 기록과 원장 기록은 동일 트랜잭션 및 기존 잠금 순서를 유지한다.
- HTTP/OpenAPI, 저장 스냅샷·지문·Receipt 재전송 결과, 예약·출고·입금의 의미가 보존된다.
- 이행용 예외가 제거되고 코드·아키텍처 테스트·현재 문서가 일치한다.
- 확정된 목표 설계가 `docs/04-architecture.md`에 통합되고, 목표 설계 파일은 archive로 이동하며 문서 링크가 정리된다. 이 ADR은 `docs/adr/`에 유지한다.

## 6. 검토한 대안과 영향

| 대안 | 채택 여부와 이유 |
|---|---|
| 목표 트리를 그대로 일괄 적용 | 미채택. 이미 완료된 Sales 통합을 반복하고 현재 소유권·의존 방향과 충돌함 |
| 계층 우선 패키지를 계속 유지 | 미채택. 공개 경계와 기능 소유권을 패키지 및 검사에 명시하려는 목표를 충족하지 못함 |
| 패키지 이동과 함께 Farm–Work 의존 방향 전환 | 미채택. 역방향 호출 전체와 트랜잭션 경계를 별도로 검토해야 함 |
| 기존 소유권·의존 방향을 유지하며 계약부터 점진적으로 전환 | 채택. 각 단계의 회귀 범위를 제한하면서 목표 구조로 진행 가능 |

기능 소유권과 외부 공개 범위가 명확해지고 내부 구현 접근을 자동 검사할 수 있다. 대신 FQCN·생성 코드·운영 CLI·검사 inventory의 동시 갱신이 필요하며, 전환 중 두 패키지 배치를 지원하는 검사도 관리해야 한다. DB·HTTP·저장 형식 변경과 의존 방향 전환은 별도 결정으로 다룬다.

## 7. 실행 기록

### 2026-10-08: P0 기준 검증·P1 검사 보강

- 사용자 승인에 따라 기존 소유권·의존 방향을 유지하는 전환으로 착수했다. 현재 작업 단위는 첫 PR 범위인 기준 검증과 검사 보강이다.
- 착수 baseline: 기존 ModularArchitecture/ModuleBoundary/PublicApplicationContract/SalesArchitecture/OrchidGroupWriter 검증 통과.
- 기존 계층 우선 배치와 새 기능 우선 배치의 소유권·역할 판별을 통합했다. Sales 기능 그래프, 저장소·계층 검사와 계약 inventory가 새 경로에서도 동작하도록 보강했다.
- 새 Farm/Work/Sales API·SPI는 사용되지 않은 public 멤버도 값 계약을 검사한다. 내부 기능 API·SPI를 다른 최상위 모듈에서 사용하는 접근은 차단한다.
- API 호출·생성자·메서드 참조·SPI 구현의 bytecode fixture 및 중첩 HTTP DTO 누출 회귀 검사를 추가했다. 기존 reviewed contract inventory와 허용 의존표는 변경하지 않았다.
- P0 잔여: 개별 공개 타입의 최종 이동표·runtime 호출 및 저장 FQCN 조사. P2 이전에 확정한다.
- P2 조사 항목: 기존 Payment Port가 사용하는 `PaymentAllocationTargetOption`, `AuctionProceedsResponse` 등 HTTP 값 반환 경로를 application 값으로 분리한다. 기존 Port의 Entity·projection 검사는 유지하며 새 API·SPI에서는 HTTP 타입도 금지한다. 공통 HTTP·pagination 기술 계약은 업무 API·SPI inventory 확대 대상에서 제외한다.
- 변경 전 검토된 계약 baseline은 TYPE 134개, METHOD 328개, CONSTRUCTOR 50개다. 신규 업무 API·SPI도 동일한 정확한 멤버 검토 대상으로 추적한다.
- 프로덕션 코드·HTTP·DB·트랜잭션·저장 형식 변경 없음. 운영 DB 경계 변경이 없어 `workE2eTest`는 이번 단계에서 실행하지 않는다.


검증 결과:

- 백엔드 전체 `test` 및 `spotlessCheck` 통과. 기본 테스트 JVM heap에서는 OOM으로 중단되어, 저장소 설정 변경 없이 임시 Gradle init script로 테스트 heap을 2 GiB로 늘려 재검증했다.
- 프론트엔드 `npm run check` 통과.
- 전체 검증 이후 변경은 이 ADR의 실행·검증 기록뿐이다.

### 2026-10-08: P2 조회용 Work SPI 1차 전환

작업 상세 참조와 Mutation 그래프의 계약·구현·소비자를 하나의 변경 단위로 전환한다. 아래 이동 대상에 대한 소유권·runtime 경로·FQCN 사용을 확인했다. 나머지 공개 계약의 이동표 확정은 후속 작업으로 남는다.

| 기존 위치 | 확정 위치 | 이유 |
|---|---|---|
| `work/application/target/WorkExecutionReferenceGateway` | `work/spi/target` | Farm이 공급하는 품종명·위치 참조 조회를 Work가 정의 |
| `work/application/operation/WorkExecutionLocation` | `work/spi/target` | 해당 SPI가 반환하는 불변 값. 기존 HTTP schema 이름 유지 |
| `work/application/operation/WorkOperationMutationGraphPort` | `work/spi/operation` | Farm이 공급하는 Mutation 그래프를 Work가 정의. 중첩 값도 함께 이동 |
| Farm의 위 두 계약 구현 | `farm/orchid/integration` | Work의 공개 SPI를 구현하는 Farm 소유 조회 adapter |

- runtime: Work 상세/그래프 조회 → Work SPI → Farm 구현 → Farm 소유 저장소. 기존 `readOnly` 트랜잭션·쿼리·그래프 상한·현재 위치 해석은 유지한다.
- 이동한 타입명의 문자열 참조를 main/resources/scripts에서 확인했다. 공개 위치 값의 `@Schema(name = "WorkExecutionLocationResponse")` 외에 reflection·설정·저장 JSON용 FQCN 문자열은 발견되지 않았다. Bean 이름과 클래스명은 유지한다.
- Work와 Farm 소비자 및 graph adapter 테스트의 import·위치를 전환하고, 검토된 계약 inventory는 해당 FQCN만 치환했다. 계약의 메서드·생성자를 새로 승인하지 않았다.
- 클래스·메서드명 변경, 쓰기 유스케이스, DB·원장·지문·잠금 순서 변경은 이번 단계에 포함하지 않는다.

검증 결과:

- 관련 architecture·상세 계약·그래프·Farm adapter 테스트 통과.
- `clean test spotlessCheck` 통과: 774개, 실패·오류·skip 0개. 앞 단계와 동일하게 임시 Gradle init script로 테스트 heap 2 GiB를 적용했으며 저장소 설정은 변경하지 않았다. 이전 FQCN의 `.class` 파일은 clean 빌드 후 남아 있지 않다.
- `python3 scripts/generate_openapi.py` 재생성 후 전체 명세·slice diff 없음. 공개 schema가 동일하므로 TypeScript 타입 재생성은 필요하지 않았다.
- 프론트엔드 `npm run check` 통과.
- DB·트랜잭션·잠금·멱등 처리 변경이 없는 구조 이동이므로 `workE2eTest` 미실행. 전체 검증 이후 변경은 이 ADR의 검증 기록뿐이다.

### 2026-10-08: P2 입고·취소·선잠금 Work SPI 전환

| 계약·구현 | 확정 위치 | 책임 |
|---|---|---|
| `InboundPottingPlanGateway`, `InboundPottingPlanTarget` | `work/spi/target` | Work가 입고 후보·현재 값·계획 상태·실행 잠금 계약 소유 |
| `InboundPottingVoidPort`, `PottingVoidPort`, `StructureChangeVoidPort` 및 중첩 값 | `work/spi/operation` | Work가 취소 검증·Farm 보상·입고 감사 협력 계약 소유 |
| `StructureChangeRecordLockPort` | `work/spi/operation` | Work가 구조 변경 기록의 Farm 선잠금 계약 소유 |
| 입고 관련 Farm 구현 3개 | `farm/inbound/integration` | 입고 소유 저장소·유스케이스로 Work SPI 구현 |
| 구조 변경 취소·선잠금 Farm 구현 2개 | `farm/transformation/integration` | 구조 변경의 Farm 보상·잠금 실행 |

- Work의 계획·실행·취소·진행·응답 조립, Farm 구현과 기존 테스트의 참조를 함께 전환했다. 검토된 계약 inventory는 기존 FQCN만 치환하고 정렬했으며 승인 멤버를 추가하지 않았다.
- 이동한 11개 프로덕션 선언의 본문과 annotation은 이전 커밋과 동일하다. 기존 클래스명·Bean 이름·트랜잭션 참여 방식·잠금 순서·보상·Receipt·snapshot 형식을 유지한다.
- 구조 변경 선잠금 adapter의 기존 테스트도 새 integration 패키지로 이동했다. 입고 계획 integration, 취소 유스케이스, 잠금 adapter, 공개 계약·모듈 경계·단일 Writer 집중 검증 통과.
- 보정·효과 실행 계약은 관련 공개 값과 내부 factory·codec의 책임 분리가 필요하므로 별도 후속으로 남긴다. 단순히 SPI를 옮기면서 내부 application 값을 공개 계약에 남기지 않는다.

검증 결과:

- `clean test spotlessCheck` 통과: 774개, 실패·오류·skip 0개. 임시 Gradle init script의 테스트 heap 2 GiB 사용. 이전 FQCN의 `.class` 파일 11개는 제거됐고 새 경로에만 선언이 존재한다.
- `python3 scripts/generate_openapi.py` 재생성 후 전체 명세·slice diff 없음. 프론트엔드 `npm run check` 통과. TypeScript schema 계약 변경 없음.
- main/resources/scripts에서 이동한 타입의 이전 FQCN·설정 또는 저장용 클래스명 문자열 참조 없음.
- DB·트랜잭션·잠금·멱등 처리 로직은 변경하지 않아 `workE2eTest` 미실행. 기존 PostgreSQL E2E의 참조는 전환하고 컴파일했다. 전체 검증 후에는 문서의 검증 기록만 추가했다.
