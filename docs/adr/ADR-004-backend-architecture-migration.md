# ADR-004: 공개 API·SPI 경계와 기능 우선 패키지로 백엔드를 점진적으로 전환한다

- 상태: 승인 — P0~P6 구현·검증·현행 문서 통합 완료
- 작성일: 2026-10-08, Asia/Seoul
- 범위: 백엔드 패키지 배치, 공개 계약, 아키텍처 테스트 및 문서 전환
- 진행: P0~P6 완료. 공개 API·SPI, 기능 우선 배치, Mutation 내부 분리와 최종 경계·문서 통합을 적용했다.
- 관련 문서: [보관된 최초 목표 설계](../archive/plans/green-house-backend-architecture-final.md), [현행 아키텍처](../04-architecture.md), [Sales 소유권 결정](ADR-003-sales-document-information-architecture.md)

실제 소스와 아키텍처 테스트를 기준으로 결정 이유와 완료된 전환·검증을 기록한다. 현행 구현과 신규 작업의 기준은 `docs/04-architecture.md`다.

## 1. 배경과 결정

목표 설계의 공개 계약·기능 우선 배치·단일 Writer 원칙을 적용한다. 현재 구현에 이미 반영된 Sales 통합과 이관 도구 제거는 반복하지 않는다. 기존 소유권과 컴파일 의존 방향을 유지하며 계약 정리 → 기능별 이동 → Mutation 내부 분리 순으로 진행한다.

목표 문서의 예시 트리를 그대로 복제하지 않는다. HTTP·DB·업무 정책 변경과 구조 리팩터링은 분리한다. 기존 클래스명도 1차 이동에서 유지한다.

`integration`은 실제 Outbound Port·공개 SPI 구현 또는 외부 기술 격리가 필요한 경우에만 생성하는 선택적 패키지다. 허용된 내부 기능·최상위 모듈 API는 직접 호출하며, 패키지를 채우기 위한 Port·Interface·Gateway·전달 Adapter는 추가하지 않는다. 기존 구현이 SPI를 직접 제공할 수 있으면 별도 위임 계층을 만들지 않는다.

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

### 2026-10-08: integration 생성 기준과 새 패키지 전수 재검토

조사 범위는 main source의 모든 `integration` 경로다. 현재 3개 패키지에 구현 7개가 있으며 모두 이전 리팩터링에서 기존 클래스·계약을 옮긴 것이다. 이번 감사에서 새 프로덕션 Port·Interface·Gateway·Adapter는 추가하지 않았다.

| 패키지 | 구현 | 계약 소유자·실제 소비자 | 격리 대상과 유지 이유 |
|---|---|---|---|
| `farm/orchid/integration` | `FarmWorkExecutionReferenceGateway` | Work `WorkExecutionReferenceGateway`; `WorkOperationDetailService` | Farm 묶음·구역 저장소 projection을 Work 위치·품종명 값으로 변환. Work의 Farm 저장소 직접 참조 방지 |
| `farm/orchid/integration` | `FarmWorkOperationMutationGraphAdapter` | Work `WorkOperationMutationGraphPort`; `WorkOperationGraphQueryService` | Farm 원장 Entry·관계·snapshot의 읽기 및 제한된 그래프 탐색을 Work 값으로 변환. Work에 원장 Entity·저장소 노출 방지 |
| `farm/inbound/integration` | `FarmInboundPottingPlanGateway` | Work `InboundPottingPlanGateway`; 계획·진행·잠금·취소·응답 유스케이스 | Farm 입고 조회·자격 검증·상태 변경·잠금을 Work의 대상 계약으로 제공. 단순 전달이 아니라 입고 모델과 저장소 접근 소유 |
| `farm/inbound/integration` | `FarmInboundPottingVoidAdapter` | Work `InboundPottingVoidPort`; `InboundPottingOperationService` | Work 호출 전후 입고 root 잠금·취소 가능 검증·변경 전후 감사 수행. Work lifecycle 직접 호출도 포함하지만 이를 전달만 하는 래퍼로 제거하면 Farm 책임이 Work에 유출됨 |
| `farm/inbound/integration` | `FarmPottingVoidAdapter` | Work `PottingVoidPort`; `WorkOperationVoidService` | 입고·포트 생성 원장·현재 유효 head·후속 참조의 검증과 잠금, Mutation 생성 보상 및 필요 시 입고 재개를 Farm에서 실행 |
| `farm/transformation/integration` | `FarmStructureChangeRecordLockAdapter` | Work `StructureChangeRecordLockPort`; `StructureChangeRecordService` | Farm 묶음·구역 저장소 잠금과 존재 검증. 묶음 ID 순 → 잠금 후 위치를 포함한 구역 ID 순의 전체 잠금 순서를 제공 |
| `farm/transformation/integration` | `FarmStructureChangeVoidAdapter` | Work `StructureChangeVoidPort`; `WorkOperationVoidService` | Farm 원장·유효 head·예약·후속 참조를 검증하고 단건/일괄 Mutation 보상과 일괄 감사 조율. Work의 재고 Entity·원장 직접 접근 방지 |

판정: **유지 7개, 제거 0개**. 동일 최상위 모듈의 허용 API를 전달만 하는 구현이나, 허용된 최상위 API 호출을 장식하는 구현은 이 범위에서 발견되지 않았다. 모두 Work 소유 공개 SPI의 공급자이며 외부 네트워크·기술 Adapter는 아니다. 외부 기술 연동이라는 이유로 유지한다고 설명하지 않는다.

변경 전후 관계:

```text
컴파일: Farm 구현 → Work 공개 SPI ← Work application
런타임: Work 유스케이스 → Work SPI를 구현한 Farm 구현 → Farm 소유 저장소/정책/Mutation
```

이 관계는 감사 전후 동일하다. Work의 Farm 직접 의존을 허용하지 않는 현재 모듈 그래프에서 위 구현을 제거하고 Work가 Farm API를 직접 호출하면 새 역방향 의존과 순환이 생긴다. 기존 허용표를 바꾸어 제거하지 않는다. Farm 구현 내부의 허용된 application·정책·Mutation 호출에는 새 위임 Adapter를 추가하지 않는다.

`FarmInboundPottingVoidAdapter`의 runtime 재진입은 별도로 확인했다: Work 포트 취소 접수 → Farm 입고 잠금·검증 → Work lifecycle/취소 → Farm 포트 보상 → Farm 입고 감사. 기존 `MANDATORY`, 접수 replay 순서와 동일 트랜잭션 처리를 유지한다. 구조 변경 선잠금도 `MANDATORY`를 유지하며, 두 조회 구현의 `readOnly`와 취소 구현의 기존 트랜잭션 annotation은 변경하지 않는다. OrchidGroup 변경은 계속 Mutation Engine을 거친다.

변경은 선택적 생성 기준 명시와 architecture 검사 보강이다. 검사는 공개 SPI·호출 측 Outbound Port 또는 클래스별 검토된 기술 격리 근거를 요구하며, 허용 API를 전달만 하는 음성 fixture를 거절한다. 이 검사가 메서드 본문만으로 불필요한 전달을 모두 판정하는 것은 아니므로 유지 이유와 실제 사용처는 코드 리뷰에서 함께 확인한다.

검증 결과:

- 관련 integration 생성 기준·모듈 경계·공개 값 계약·단일 Writer·상세/그래프·입고 계획·작업 취소·잠금 adapter 집중 테스트 통과.
- 전체 `test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과.
- 프로덕션 코드·HTTP/OpenAPI·DB·트랜잭션·잠금·Mutation 쓰기 경로 변경 없음. OpenAPI 재생성 및 `workE2eTest`는 이번 검토에서 실행하지 않았다. 전체 검증 이후에는 문서의 검증 기록만 추가했다.


### 2026-10-08: P2 효과 실행·보정 공개 계약 전환

| 기존 위치·책임 | 확정 위치 | 판단 |
|---|---|---|
| Work application의 효과 handler 계약 | `work/spi/effect` | Work가 호출하고 Farm이 구현하는 기존 의존 역전 보존 |
| Work application의 보정 Port·준비 결과 | `work/spi/correction` | 동일 Work 트랜잭션에서 Farm 잠금을 유지하며 prepare/apply 실행 |
| 효과·보정 명령, 입력, 결과, Mutation 연결 값 | `work/api/{effect,correction}` | 공유 값만 공개. sealed payload와 허용 구현, typed 결과는 함께 이동 |
| 효과 종류·결과 목적·대상 참조 종류 enum | `work/api/{effect,target}` | 공개 시그니처가 내부 domain enum에 의존하지 않도록 이동. 문자열 값 유지 |
| Farm 보정 adapter | `farm/transformation/integration` | 실제 Work SPI 구현. 대상 검증·잠금·재고 실사/사용 여부 검사·Mutation 적용 책임 보유 |

- 변경 전: Work processor/correction service → Work application 확장 계약 → Farm handler/보정 adapter. 변경 후: 같은 소비자 → Work 공개 SPI·API 값 → 동일 Farm 구현. 컴파일 의존은 계속 `Farm → Work`다.
- 보정 adapter는 기존 클래스를 이동했으며 신규 위임 계층은 없다. 기존 Farm 효과 handler도 공개 SPI를 직접 구현한다. 허용된 Work application 호출을 전달하는 Adapter나 새 Port는 추가하지 않았다.
- Entity → effect context 변환은 공개 값에서 내부 processor의 private factory로 옮겼다. 동일 필드·null 처리·과거 location snapshot의 불변 복사 규칙을 유지한다. processor/store/codec 및 보정 계산·참조 조회 구현은 내부에 남기며, 외부의 기존 직접 참조는 후속 API 정리 대상이다.
- 기존 MANDATORY/쓰기 트랜잭션, 잠금·검증·Mutation 호출 순서, 단일 Writer, 저장 JSON 필드 존재 여부·지문·Receipt 재전송 의미를 유지한다. 클래스명·Bean 이름·HTTP schema 이름과 validation도 유지한다. 이동 대상의 저장/설정용 FQCN 문자열이나 다형 JSON 타입 식별자는 확인되지 않았다.
- 검토된 inventory는 이동 FQCN만 치환했다. domain에서 API로 이동한 기존 enum 참조 3개 TYPE와 결과 목적 enum의 `values()`·`ordinal()` 2개 METHOD는 공개 계약 검사에 새로 포함되므로 개별 검토해 추가했다. 신규 업무 호출은 없다.

검증 결과:

- architecture·공개 값 계약·integration·효과 processor/store·저장 JSON·보정·지문·입고/구조 변경 mapper 집중 검증 통과. enum 이동으로 검사 대상이 된 기존 참조는 개별 확인 후 inventory 재검증했다.
- `clean test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 이전 FQCN 클래스 잔존 없음. 이동한 26개 선언의 본문은 동일하며, context의 Entity factory만 동일 필드 변환으로 내부 processor에 옮겼다.
- 프론트엔드 `npm run check` 통과. OpenAPI 재생성 성공: 157 operations, 132 paths, 292 schemas; 생성 명세 차이 없음. 프론트 타입 재생성은 계약 차이가 없어 실행하지 않았다.
- SQL·Flyway·트랜잭션 경계·잠금·수량 계산·Mutation 로직 변경이 없어 `workE2eTest`는 실행하지 않았다. E2E 소스 import도 변경했으며 `compileTestJava`로 컴파일했다.
- 전체 검증 이후 변경은 이 ADR의 검증 기록뿐이다. 후속 작업은 남은 Work 내부 구현의 외부 참조를 공개 API로 정리하며, 허용 API 호출은 직접 유지한다.


### 2026-10-08: P2 Farm의 Work 보정 구현 직접 참조 제거

| 소비자 | 변경 전 | 변경 후 | 경계가 필요한 이유 |
|---|---|---|---|
| Farm 병합 handler·보정 adapter | `work/application/correction/StructureChangeReferenceReader` | `work/api/correction/StructureChangeReferenceApi` | Work 소유 대상·효과·작업 저장소를 숨기고 ID·Mutation 참조 값만 공개 |
| Farm 보정 adapter | `work/application/correction/WorkCorrectionQuantityService` | `work/api/correction/WorkCorrectionQuantityApi` | 저장된 Work 스냅샷·보정 이력의 수량 수지와 검증만 제공하고 저장소·codec·feature flag 구현은 내부 유지 |

- 기존 Work 서비스가 공개 API를 직접 구현한다. 새 Service·Port·전달 Adapter나 integration 패키지는 추가하지 않았다. Farm은 허용된 `Farm → Work` 공개 API를 직접 호출한다.
- 기존 외부 사용 메서드 6개만 계약으로 추출했다. `isEnabled()`는 Work 내부 전용으로 유지하고 Farm이 이미 사용하던 `requireEnabled()` 검증만 공개한다. 기존 Work 내부 호출은 그대로 유지한다.
- 기존 서비스의 `readOnly = true`, Farm 보정의 `MANDATORY`, 기존 Bean 이름, 호출·잠금 순서·수량 계산·Mutation Writer·저장 스냅샷/JSON·지문을 유지한다. 조회와 검증의 본문은 변경하지 않았다.
- reviewed inventory는 두 구현 TYPE·메서드 참조 8개를 대응하는 API FQCN으로 치환한다. 신규 업무 메서드나 모듈 의존은 추가하지 않는다.
- Work의 metrics/metadata·입고 lifecycle·즉시 실행·대상 해석·계보·운영 대사 관련 외부 참조는 후속 작업으로 남는다. 공개 API를 추가하기 전에 각 반환 값의 domain·HTTP·Entity 의존을 따로 확인한다.

검증 결과:

- 공개 값 계약·모듈 inventory·integration·단일 Writer·작업 보정·수량 수지·지문·병합·생성 취소 집중 테스트 통과. Spring 통합 테스트로 API 주입 후 기존 구현 연결도 확인했다.
- 전체 `test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과.
- HTTP Controller·DTO·validation·schema 변경이 없어 OpenAPI·프론트 타입은 재생성하지 않았다. DB·트랜잭션·잠금·수량 계산 로직 변경이 없어 `workE2eTest`는 실행하지 않았다.
- 전체 검증 이후에는 이 ADR의 검증 기록만 추가했다.


### 2026-10-08: P2 작업 집계·메타데이터 조회 API 전환

| 소비자·책임 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| Farm 품종 응답·Analytics 작업 집계 | Work application의 `WorkOperationMetricsReader`와 중첩 값 | `work/api/operation/WorkOperationMetricsApi`와 중첩 값 | QueryDSL 구현을 숨기고 집계·최근 작업·일괄 최신 작업일 계약 공개 |
| Farm Mutation의 원본/보정 작업 표시 | Work application의 `WorkOperationMetadataReader`와 중첩 값 | `work/api/operation/WorkOperationMetadataApi`와 중첩 값 | Work 저장소·Entity 변환을 숨기고 식별자·이름·제목만 공개 |
| 조회 결과의 작업 상태·범위·유형 enum | `work/domain/operation` | `work/api/operation` | 공개 결과가 내부 domain 타입을 참조하지 않도록 기존 값 계약 이동 |

- 기존 조회 서비스가 API를 직접 구현하며 Farm·Analytics는 허용된 API를 직접 호출한다. 새 전달 Service·Adapter·Port·integration 패키지는 추가하지 않는다. 컴파일 의존과 Bean 이름은 유지한다.
- 기존 공개 조회 메서드 4개와 중첩 결과 record 4개를 전환한다. 결과 값의 필드·생성자·불변 목록 복사, enum의 문자열·handler/effect/custom-type 규칙은 동일하다.
- QueryDSL projection은 Work API의 값 record를 직접 생성하며 Repository projection을 외부 계약으로 노출하지 않는다. 기존 쿼리 본문·완료 상태 조건·최근 10개 제한·500개 단위 ID 일괄 조회·정렬·제외 대상 처리·`readOnly = true`를 유지한다.
- Work 저장 JSON·JPA enum 문자열·Mutation 단일 Writer·트랜잭션 및 잠금 순서는 변경하지 않는다. 기존 Work 내부 조회 구현 테스트는 구체 서비스를 검증하며, 외부 Analytics 테스트는 공개 API를 사용한다.
- reviewed inventory는 조회 구현/중첩 값 FQCN만 대응 계약으로 치환한다. domain에서 공개 API로 이동한 enum의 기존 외부 참조는 별도 확인해 추가하며, 신규 업무 호출을 승인하지 않는다.

검증 결과:

- 집중 검증의 Analytics·품종·Mutation 조회 및 공개 값 계약·integration·단일 Writer 검사 통과. enum 이동으로 새로 검사된 기존 TYPE 3개와 범위 enum의 `values()`·`ordinal()` METHOD 2개는 개별 확인 후 반영했으며 전체 검증에서 inventory 검사도 통과했다.
- `clean test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. QueryDSL 생성 코드가 새 enum 경로를 사용하며 이전 enum·중첩 결과 FQCN 클래스는 남아 있지 않다.
- 프론트엔드 `npm run check` 통과. OpenAPI 재생성 성공: 157 operations, 132 paths, 292 schemas; 명세 차이 없음. 프론트 타입 재생성은 계약 차이가 없어 실행하지 않았다.
- 두 조회 구현의 쿼리 본문, 결과 record 4개, enum 3개의 값·동작이 동일함을 확인했다. 설정·저장용 FQCN 참조는 main/resources와 scripts에서 발견되지 않았다.
- SQL·조회 조건·트랜잭션·잠금·DB 제약·업무 계산 변경이 없어 `workE2eTest`는 실행하지 않았다. E2E 소스의 import도 전환했으며 `compileTestJava`로 컴파일했다.
- 전체 검증 이후 변경은 이 ADR의 검증 기록뿐이다. 후속 Work 공개 계약 전환은 대상 해석·입고 lifecycle·즉시 실행·계보·운영 대사 참조를 각각 검토한다.


### 2026-10-08: 두 단계 진행 — 1단계 대상 해석·사용 여부 계약

| 소비자·책임 | 변경 전 | 변경 후 | 판단 |
|---|---|---|---|
| Work 계획·대상 조회/잠금 → Farm 대상 해석 | `work/application/target/WorkTargetResolver`, `ResolvedWorkTarget` | `work/spi/target` | 실제 Farm 데이터 조회·값 변환·잠금 검증을 Work 소유 SPI로 제공 |
| 대상 선택·입력 값·포함 출처 enum | Work application/domain target | `work/api/target` | 기존 validation·조건 snapshot·출처 의미 그대로 공개 |
| Farm 대상 해석 구현 | `farm/application/orchid/FarmWorkTargetResolver` | `farm/orchid/integration` | 기존 실제 SPI 구현 이동. 새 위임 Adapter 없음 |
| Farm 난 묶음 명령·사용 blocker 조합 | `work/application/target/WorkOrchidGroupUsageInspector` | `work/api/target/WorkOrchidGroupUsageApi` | Work 소유 대상/효과 저장소를 숨기고 기존 사용 여부·건수 검사만 공개 |

- Work 사용 여부 조회 구현은 기존 component가 API를 직접 구현한다. Farm의 허용 API 호출에 새 Adapter·Gateway를 추가하지 않는다. 기존 Farm `WorkOrchidGroupUsageAdapter`는 Work 집계 결과를 Farm의 ordered blocker 값으로 조합하는 역할을 유지하며 integration으로 옮기지 않는다.
- Work에서 Farm을 실행하는 의존 역전과 `Farm → Work` 컴파일 방향을 유지한다. Farm 대상 해석의 그룹/위치/사용자 그룹/자동 그룹 조회·snapshot 변환·업무일 년생 계산은 동일하다.
- 선택 ID의 null 제거·중복 제거·조건 검증, ID 정렬·500개 단위 선잠금·활성 대상 검증·MANDATORY, 사용 여부의 취소/무효 제외 조건·readOnly와 단일 Writer를 유지한다.
- 기존 선언 6개는 package/import만 변경했으며 본문은 동일하다. 사용 여부 구현도 interface/Override 선언만 추가한다. Bean 이름·HTTP 요청·validation·JSON·지문·DB 변경 없음. reviewed inventory는 대응 FQCN만 치환한다.

1단계 검증 결과:

- 대상 선택·scope·Farm 선잠금·난 묶음 명령 잠금·사용 여부 blocker·모듈/API/SPI/integration/Writer 집중 검사 통과. reviewed inventory는 이동에 따른 치환 외 차이가 없다.
- `clean test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 이전 FQCN 클래스 잔존 없음. 프론트엔드 `npm run check` 통과.
- OpenAPI 재생성 성공: 157 operations, 132 paths, 292 schemas; 생성 명세 차이 없음. 프론트 타입은 계약 차이가 없어 재생성하지 않았다. 설정·저장용 FQCN 문자열은 main/resources와 scripts에서 발견되지 않았다.
- SQL·트랜잭션·잠금·DB 제약·업무 계산이 동일해 `workE2eTest`는 실행하지 않았다. E2E 소스의 import 전환은 `compileTestJava`로 검증했다. 전체 검증 이후에는 이 ADR의 검증 기록만 추가했다.


### 2026-10-08: 두 단계 진행 — 2단계 입고 작업 lifecycle API

| 소비자 | 변경 전 | 변경 후 | 필요한 경계 |
|---|---|---|---|
| Farm 입고 변경/취소 유스케이스 | Work application lifecycle service 직접 참조 | `work/api/operation/InboundWorkOperationLifecycleApi` | Work의 연결 작업·대상 실행·효과 저장소와 취소 구현을 감춤 |
| Farm 입고 응답 조립 | 같은 구현의 취소 가능 포트 조회 | 같은 API 직접 조회 | 입고 ID 집합만 공개, Work 저장소 접근 없음 |
| Farm 포트 취소 SPI 구현 | 같은 구현의 선잠금·포트 되돌리기 | 같은 API 직접 호출 | 기존 Work → Farm → Work 재진입과 트랜잭션 순서 보존 |

- 외부에서 이미 사용하는 메서드 5개를 공개 API로 추출했다. 기존 Work lifecycle service가 API를 직접 구현하며 별도 facade·전달 Adapter·Port·integration 패키지는 추가하지 않는다.
- Work의 연결 작업 잠금 → 대상 실행 잠금 → 취소/효과 상태 변경 순서와 Farm 입고 검증·보상 Mutation·감사 흐름은 동일하다. 기존 class `@Transactional`, 조회 메서드 `readOnly = true`, Farm SPI 구현 `MANDATORY`와 Bean 이름을 유지한다.
- runtime 재진입의 책임이나 트랜잭션 경계를 재배치하지 않는다. 기존 private Entity 처리·업무일 시점·단일 Writer·멱등 접수·취소 가능한 상태 조건·잠금/SQL 본문은 그대로 유지한다.
- reviewed inventory는 기존 service TYPE 1개와 METHOD 5개의 FQCN을 API로 치환한다. 새 업무 메서드·외부 의존·HTTP 계약 변경 없음.

2단계 검증 결과:

- 입고 감사·다중 입고 포트 계획·입고 취소/되돌리기·Mutation·작업 취소·API/SPI/integration/Writer 집중 테스트 통과. Spring 통합 테스트로 공개 API 주입 후 기존 lifecycle 구현 연결도 확인했다.
- 전체 `test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과.
- 기존 메서드 본문과 트랜잭션 annotation이 동일하며, reviewed inventory도 기존 lifecycle 참조의 API 치환 외 차이가 없다. HTTP Controller·DTO·validation·schema가 동일하여 1단계에서 확인한 OpenAPI를 다시 생성하지 않았다.
- SQL·DB 제약·트랜잭션 경계·잠금·수량/상태 처리 변경이 없어 `workE2eTest`는 실행하지 않았다. 전체 검증 이후에는 이 ADR의 검증 기록만 추가했다.
- 이번 요청의 두 단계 완료. 남은 Work 외부 참조는 입고 실행/기록·즉시 실행·계보·대사·codec/계산 계약을 각각 확인하며 후속 두 단계 작업으로 나눈다.


### 2026-10-08: 두 단계 진행 — 입고 기록·즉시 실행의 1단계

| 소비자·책임 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| Farm 입고 생성 → Work 기록 | `work/application/operation/InboundWorkOperationRecorder` | `work/api/operation/InboundWorkOperationRecordingApi` | Work의 작업·대상·효과 저장소와 생성 흐름은 내부 유지 |
| Farm 입고 snapshot 명령 생성 | `work/application/operation/RecordInboundWorkCommand` | `work/api/operation` | Farm Entity 대신 생성 시점의 값과 생성된 난 묶음 ID만 전달 |

- 실제 외부 호출인 `record(command, mutationLink)`만 API로 공개했다. 기존 단일 인자 overload는 Work 내부에서 유지하며 공개 범위를 늘리지 않는다. 기존 service가 API를 직접 구현하고 추가 전달 계층은 없다.
- Farm의 snapshot factory와 Mutation 생성 → Work 기록 → 입고 감사 순서, Work의 작업/대상/실행/효과 생성·완료 처리와 기존 트랜잭션 annotation은 동일하다. snapshot 시점·JSON 필드·Mutation 연결·원장 단일 Writer·Bean 이름을 유지한다.
- 명령 record는 package만 이동하며 필드·생성자·map/list 의미는 그대로다. reviewed inventory의 명령 TYPE/CONSTRUCTOR와 기록 TYPE/METHOD만 대응 FQCN으로 치환한다.

입고 기록 단계 검증 결과:

- 입고 감사·입고 Mutation·포트 계획과 공개 값·inventory·integration·단일 Writer 집중 테스트 통과. API 주입 후 기존 Work 기록 service 연결을 Spring 통합 테스트로 확인했다.
- 전체 `test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과.
- 명령 record와 기록 서비스 본문·트랜잭션 annotation이 동일함을 확인했다. Controller·DTO·validation·schema 변경이 없어 OpenAPI·프론트 타입은 재생성하지 않았다. SQL·트랜잭션 경계·잠금·수량 계산 변경이 없어 `workE2eTest`는 실행하지 않았다.
- 전체 검증 이후 변경은 이 ADR의 검증 기록뿐이다.


### 2026-10-08: 두 단계 진행 — 입고 기록·즉시 실행의 2단계

| 소비자·책임 | 변경 전 | 변경 후 | 판단 |
|---|---|---|---|
| Farm 분갈이·난 묶음 실사 이력 | `work/application/operation/ImmediateWorkExecutionService` | `work/api/operation/ImmediateWorkExecutionApi` | 외부 사용 메서드 2개만 공개. 기존 즉시 실행 service가 직접 구현 |
| Farm 분갈이 작업 단건 표시 | `work/application/operation/WorkOperationQueryService.get` | `work/api/operation/WorkOperationQueryApi` | 외부 사용 단건 조회만 공개. 목록·preview 등 내부 조합은 기존 구현 유지 |
| 공개 작업·진행·대상 값 | Work application operation/target | `work/api/{operation,target}` | 중첩 값까지 공개 경계에 두고 Entity를 시그니처에서 제거 |
| 값 계약의 operation action/relation/workflow, target action/execution status enum | Work domain operation/target | `work/api/{operation,target}` | enum literal·schema·업무 의미 그대로 이동 |
| Entity/입고/저장 JSON → 작업·대상 값 변환 | 공개 값의 `from(Entity, ...)` factory | 내부 `WorkOperationViewFactory` | 공개 API가 Entity·codec에 의존하지 않도록 실제 변환 책임만 내부 추출 |

- 기존 Work service가 API를 직접 구현하며 Farm은 허용 API를 직접 호출한다. 새 위임 Service·Adapter·Port·integration 패키지는 없다. 내부 factory는 위임 계층이 아니라 기존 필드·snapshot 변환 본문을 소유한다.
- 기존 내부 `execute(...)` overload와 query의 다른 메서드는 공개 API에 추가하지 않는다. Bean 이름·트랜잭션 annotation·최상위 모듈 방향은 그대로다.
- 작업/대상 factory의 UTC → 농장 시간 변환, 활성 포트의 현재 입고 값 조합, 저장 result ID 해석·unknown JSON 보존, progress 계산·capability/action 판단은 동일하다. 순수 값 preview도 그대로 유지한다.
- 즉시 작업의 IMMEDIATE namespace·private receipt command 구조·key 정규화·지문·replay → 단건 응답 흐름·효과 실행/저장 순서·Mutation 단일 Writer와 SQL/잠금 순서는 변경하지 않는다.
- 기존 DTO schema 이름과 record 필드/생성자·enum을 보존한다. 검토된 inventory는 실제 이동 및 구현 → API의 FQCN만 대응 치환한다.

즉시 실행 단계 검증 결과:

- 공개 값·모듈 inventory·integration·단일 Writer·즉시 실행·분갈이·실사 payload·작업 상세·저장 JSON·포트 계획 집중 검증 통과. Entity factory를 내부로 옮긴 후 기존 값/JSON 테스트의 진입점도 함께 전환했다.
- `clean test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과. 이전 값·enum FQCN 클래스 잔존 없음.
- 이동한 값·enum·progress/preview 본문, 추출한 factory 본문, 즉시 실행·단건 조회 service 본문과 트랜잭션 annotation이 동일함을 확인했다. Work 공개 API의 application/domain/DTO/Repository import 없음. 설정·저장용 FQCN 문자열은 main/resources와 scripts에서 발견되지 않았다.
- OpenAPI 재생성 성공: 157 operations, 132 paths, 292 schemas; 생성 명세 차이 없음. 프론트 타입은 계약 차이가 없어 재생성하지 않았다.
- SQL·DB 제약·트랜잭션 경계·잠금·수량/상태 처리 변경이 없어 `workE2eTest`는 실행하지 않았다. E2E 소스의 import 전환은 `compileTestJava`로 검증했다. 전체 검증 이후에는 이 ADR의 검증 기록만 추가했다.
- 이번 두 단계 완료. 남은 Work 외부 참조는 입고 포트 실행·계보·운영 대사·codec/계산 계약이다. 각 반환 값과 책임을 확인해 후속 두 단계로 진행한다.


### 2026-10-08: 두 단계 진행 — 포트 실행·계보 조회의 1단계

| 소비자 | 변경 전 | 변경 후 | 경계 목적 |
|---|---|---|---|
| Farm 입고 Controller의 포트 실행 | `work/application/operation/InboundPottingOperationService` | `work/api/operation/InboundPottingOperationApi` | 공개 명령·작업 값만 사용하며 Work 계획/잠금/효과 구현을 숨김 |
| Farm 입고 service의 포트 되돌리기 | 같은 구현 직접 참조 | 같은 API 직접 호출 | 기존 MANDATORY 트랜잭션 참여·접수/보상 흐름 유지 |

- 외부 사용 메서드 `executeNow(command)`·`voidForInbound(id, key, reason)`만 공개했다. 기존 Work service가 API를 직접 구현하며 별도 facade·Adapter·Port·integration 패키지는 추가하지 않는다.
- package-private `executeRecord(plan, executions)`와 HTTP plan DTO 조합은 Work 내부에 유지한다. 공개 API는 이미 전환한 Work 명령·작업 값과 scalar만 노출한다.
- 기존 트랜잭션 annotation, key/reason 정규화·Receipt namespace/지문·replay, 입고 계획 선잠금·활성 계획 재사용·효과 조회/검증·Work 시작/완료·Mutation 단일 Writer·Bean 이름은 동일하다.
- reviewed inventory의 기존 TYPE 1개·METHOD 2개만 대응 API FQCN으로 치환한다. Controller의 요청·응답·validation·HTTP status나 SQL/잠금/수량 처리 변경은 없다.

포트 실행 단계 검증 결과:

- 포트 계획·입고 감사·Mutation 라우팅과 API/SPI/integration/Writer 집중 검증 통과. Farm Controller/service의 API 주입 후 기존 구현 연결을 Spring 통합 테스트로 확인했다.
- 전체 `test spotlessCheck` 통과: 776개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과.
- 서비스 메서드 본문·트랜잭션 annotation은 동일하다. Controller 요청/응답·validation·schema 변경이 없어 OpenAPI·프론트 타입은 재생성하지 않았다.
- SQL·DB 제약·트랜잭션 경계·잠금·수량/상태 처리 변경이 없어 `workE2eTest`는 실행하지 않았다. 전체 검증 이후 변경은 이 ADR의 검증 기록뿐이다.


### 2026-10-08: 두 단계 진행 — 포트 실행·계보 조회의 2단계

| 소비자·책임 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| Farm 난 묶음 계보 조합 | `work/application/effect/StructureChangeLineageQueryService` | `work/api/effect/StructureChangeLineageQueryApi` | Work의 효과/연결 저장소·JSON codec을 숨기고 저장된 사실 값만 제공 |
| 효과·원본/결과 계보 값 | Work application effect | `work/api/effect` | 두 record를 공개 값 계약으로 이동 |
| 저장 handler → 구조 변경 유형 | 공개 값이 내부 `WorkTypeDefinition`을 반환 | Work 내부 조회 구현이 해석한 유형 코드 값 | 정책 enum 누출 제거. 기존 authoritative decoder와 별칭 해석 그대로 사용 |

- 기존 Work readOnly service가 조회 API를 직접 구현하고 Farm이 직접 호출한다. 새 전달 Service·Adapter·Port·integration 패키지는 없다. Work의 Farm 직접 의존도 추가하지 않는다.
- 계보 효과 값에 JSON/schema에서 숨긴 유형 코드를 전달한다. 기존 `structureType()` 정책 enum 반환 메서드를 제거하고 Farm의 `structureType().name()` 사용을 해당 코드 접근으로 치환했다. 저장 handler 문자열과 기존 JSON 필드·schema 이름은 유지한다. 공개 값에 내부 domain·Repository·codec 의존이 없다.
- 기존 `WorkTypeDefinition.forStoredStructureHandler()`가 코드 해석을 계속 소유한다. MOVE/MOVEMENT 등 저장 별칭이나 조건을 Farm에 복제하지 않는다. 현재 WorkType Entity를 읽어 과거 유형을 복원하지 않는다.
- 효과 조회 → 효과 ID 집합의 연결 일괄 조회, source/result 수량의 저장 JSON 보완·순서·중복 제거·분류/제외 조건, Farm 난 묶음 일괄 hydration·기존 계보 제외·표시 시간대 변환은 동일하다. 타입 코드 계산 외 SQL·조회/잠금·트랜잭션 annotation·Mutation Writer 변경 없음.
- reviewed inventory는 API/값의 FQCN 치환과 실제 외부 접근 `structureType()` → `structureTypeCode()` 변경만 개별 반영한다. 새로운 업무 호출을 일괄 승인하지 않는다.
- 기존 별칭 분류·무관 효과 제외·1개/20개 모두 두 번의 조회 회귀 검사를 유지한다. 새 JSON 회귀 검사는 canonical 코드가 저장 handler를 덮어쓰지 않고 JSON에 새 필드를 추가하지 않음을 확인한다.

계보 조회 단계 검증 결과:

- 계보 별칭/저장 source rows 분류·무관 효과 제외·1개/20개 모두 두 번의 일괄 조회, 새 JSON 회귀 검사, 계보·분갈이·분리/병합·공개 값·inventory·integration·단일 Writer 집중 검증 통과.
- `clean test spotlessCheck` 통과: 777개, 실패·오류·skip 0개. 기존 임시 Gradle init script로 테스트 heap 2 GiB를 적용했다. 프론트엔드 `npm run check` 통과. 이전 계보 값 FQCN 클래스 잔존 없음.
- OpenAPI 재생성 성공: 157 operations, 132 paths, 292 schemas; 생성 명세 차이 없음. 프론트 타입은 계약 차이가 없어 재생성하지 않았다. 이동한 값의 설정·저장용 FQCN 문자열은 main/resources와 scripts에서 발견되지 않았다.
- Work 공개 API의 application/domain/DTO/Repository import 없음. 계보의 SQL·일괄 조회 방식·저장 사실 해석·트랜잭션 설정은 유지했고, 추가 코드는 기존 decoder의 canonical 유형 값 전달과 JSON 비노출 검증이다.
- DB·트랜잭션 경계·잠금·수량/상태 처리 변경이 없어 `workE2eTest`는 실행하지 않았다. 전체 검증 이후에는 이 ADR의 검증 기록만 추가했다.
- 이번 두 단계 완료. 남은 Work application의 외부 참조는 포트 command codec·이동 수량 allocator·운영 대사 inspector/report다. 구현 책임과 공개 값 의존을 확인해 후속 두 단계로 정리한다.


### 2026-10-08: 두 단계 진행 — 공유 효과·운영 대사의 1단계

| 소비자·책임 | 변경 전 | 변경 후 | 판단 |
|---|---|---|---|
| Farm 포트 효과의 과거 JSON fallback | `work/application/effect/InboundPottingCommandCodec.decode` | `work/api/effect/InboundPottingCommandDecodingApi` | 실제 Jackson/저장 JSON 호환 구현은 Work 내부에 유지하고 기존 decode만 공개 |
| Farm 이동 전략과 Work의 이동/폐기 배분 | `work/application/effect/MovementQuantityAllocator` | `work/api/effect/MovementQuantityAllocator` | 이미 공유하는 순수 계산을 공개 위치로 이동. 새 interface·전달 계층 없음 |

- 기존 codec component가 decode API를 직접 구현한다. 내부 `encode`, ObjectMapper와 StoredDetails는 그대로 내부에 둔다. Farm은 허용 API를 직접 호출하며 typed payload 우선·없을 때 JSON fallback·입고 ID 일치 검증 순서를 유지한다.
- 기존 이동 수량 계산의 원본 ID 정렬·중복 검증·비례 폐기·나머지/ID tie-break·수량 합계·예외는 동일하다. allocator 본문은 package만 이동하며 수량 정책을 여러 모듈에 복제하지 않는다.
- 공개 계약은 typed Work 명령·scalar·Map만 사용한다. codec의 encode/decode 본문·Bean 이름·JSON metadata 제외·입고 실행/Mutation 흐름·트랜잭션·잠금·Receipt 지문을 변경하지 않는다.
- reviewed inventory는 기존 codec/allocator TYPE와 실제 METHOD 참조의 대응 FQCN만 치환한다. 공개 API를 호출하는 Wrapper/Adapter는 추가하지 않는다.

공유 효과 단계 검증 결과:

- codec의 저장 JSON 호환·metadata 제외, Farm typed payload/legacy fallback·입고 ID 검증, 이동 배분과 이동/포트 실행·공개 값·inventory·integration 집중 검증 통과. allocator class 본문은 이동 전과 동일하다.
- 전체 `test spotlessCheck` 통과: 777개, 실패·오류·skip 0개. 임시 init script로 테스트 heap 2 GiB 적용. 프론트 `npm run check` 통과.
- HTTP Controller·DTO·schema와 DB/트랜잭션·수량 계산 본문 변경 없음. OpenAPI/프론트 타입 재생성과 `workE2eTest`는 실행하지 않았다. 전체 검증 이후에는 이 ADR의 결과만 추가했다.

### 2026-10-08: 두 단계 진행 — 공유 효과·운영 대사의 2단계

| 소비자·책임 | 변경 전 | 변경 후 | 판단 |
|---|---|---|---|
| Farm 원장 대사의 Work 참조 검사 | `work/application/effect/WorkOrchidGroupLedgerRehearsalInspector` | `work/api/effect/WorkOrchidGroupLedgerRehearsalApi` | Work 소유 저장소와 진행/효과 연결 검증 구현을 숨기고 실제 사용 `inspect()`만 공개 |
| 대사 결과·보정 참조 | Work application effect report·중첩 record | `work/api/effect`의 동일 report·중첩 record | 식별자·UUID·boolean·목록만 제공하는 기존 값 계약 이동 |

- 기존 inspector component가 API를 직접 구현하고 Farm 대사가 직접 호출한다. 저장소가 없는 전달 Service·Adapter·Port·integration 패키지는 추가하지 않는다. Entity·Repository projection·HTTP DTO와 보정 결과 JSON 해석은 공개 계약에 노출하지 않는다.
- 기존 report의 collection 복사·생성자·중첩 보정 참조와 결과 내용은 동일하다. 조회 구현의 target/effect ID 조회, 실행 수량/상태 검증, 미완성 Mutation 연결 조회, 500개 keyset 보정 참조 조회·저장 JSON 해석은 그대로 유지한다.
- Farm 대사의 `readOnly = true`, `REPEATABLE_READ`와 Work의 기존 호출자 트랜잭션 참여는 유지한다. SQL·저장소·상태/수량 정책·Mutation Writer·잠금·Receipt 지문·운영 CLI 진입점은 변경하지 않는다.
- reviewed inventory는 기존 inspector/report/중첩 record의 TYPE와 실제 METHOD 대응 FQCN만 치환한다. 테스트의 실제 구현 spy/autowired 참조는 유지한다.

운영 대사 단계 검증 결과:

- 대사·원장 우회 변경 탐지·롤백/보상·Writer guard·공개 값·inventory·integration 집중 검증 통과. inspector 실행 본문과 report는 이동 전과 동일하다.
- 전체 `clean test spotlessCheck` 통과: 777개, 실패·오류·skip 0개. 임시 init script로 테스트 heap 2 GiB 적용. 프론트 `npm run check` 통과. 이동 전 allocator/report/중첩 record의 클래스 잔존 없음.
- 프로덕션 다른 최상위 모듈의 Work application 참조와 inventory의 Work application 항목은 0개다. Work 내부 구현 및 구현을 검증하는 테스트 참조는 유지한다.
- HTTP Controller·DTO·schema와 SQL·DB/트랜잭션 경계·상태/수량 판단 변경 없음. OpenAPI/프론트 타입 재생성과 `workE2eTest`는 실행하지 않았다. 운영 CLI 진입점은 동일하고 운영 DB 명령은 실행하지 않았다. inventory의 설명 주석 순서는 유지했다. 전체 검증 이후에는 이 ADR의 결과 기록만 추가했다.
- 이번 두 단계 완료. 전체 이행은 진행 중이며 후속 P2 대상은 Farm Reader/Writer·Mutation 값과 Sales 공개 계약이다.

### 2026-10-08: 두 단계 진행 — Farm 조회·사용 여부의 1단계

| 책임 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| Sales 난 묶음 상태·판매 선택·선잠금 | Farm application Reader 직접 참조 | `farm/api/orchid/OrchidGroupQueryApi` | 저장소·배치 로딩 구현은 기존 Reader에 유지, 외부 사용 네 메서드만 공개 |
| 현재 상태 값 | Farm application `OrchidGroupState` | `farm/api/orchid/OrchidGroupState` | 순수 값만 공개하고 Entity 변환은 내부 factory로 이동 |

- Sales가 허용된 Farm API를 직접 호출하며 Reader가 직접 구현한다. 전달 Service·Adapter·Port·integration을 추가하지 않는다. 조회 500개 배치·ID 정렬/중복 제거·누락 예외·판매 가능 정책과 동일 시점의 상태 값은 유지한다.
- Reader의 readOnly와 잠금 메서드의 `MANDATORY`, root/배분/난 묶음 선잠금 순서·예약·출고 snapshot·Mutation Writer는 동일하다. Entity 변환 본문은 그대로 내부 factory로 옮긴다. SQL·수량 정책·HTTP DTO·Receipt 지문 변경 없음.
- reviewed inventory는 기존 Reader와 상태 값의 TYPE/METHOD FQCN만 치환한다. 실제 Reader를 검증하는 기존 테스트·spy는 유지한다.
- Writer는 sealed 명령·정규화·결과/원장 snapshot의 공개 값 분리가 함께 필요하므로 후속 독립 작업으로 유지한다. 이번 두 번째 단계는 이미 Sales가 구현하는 Farm 사용 여부 SPI와 값 계약을 정리한다.

Farm 조회 단계 검증 결과:

- Reader 잠금·배치 건수 회귀, Sales allocation·출고·snapshot·Mutation 계약·공개 값·inventory·integration 집중 검증 통과. 상태 변환 본문은 이동 전과 동일하다.
- 전체 `test spotlessCheck` 통과: 777개, 실패·오류·skip 0개. 임시 init script로 heap 2 GiB 적용. 프론트 `npm run check` 통과.
- Controller·요청/응답 DTO 필드·schema와 SQL·트랜잭션/잠금·수량 처리 변경 없음. OpenAPI/프론트 타입 재생성과 `workE2eTest`는 실행하지 않았다. 전체 검증 이후 변경은 이 ADR의 결과 기록뿐이다.

### 2026-10-08: 두 단계 진행 — Farm 조회·사용 여부의 2단계

| 책임 | 변경 전 | 변경 후 | 이유 |
|---|---|---|---|
| 취소·보정의 외부 참조 검사 확장 | Farm application `OrchidGroupUsageInspector` | `farm/spi/orchid/OrchidGroupUsageInspector` | Farm이 검사 흐름을 소유하고 Sales가 자신의 저장소를 조회하는 실제 확장 계약 |
| 사용 여부 차단 값 | Farm application `OrchidGroupUsage` | `farm/api/orchid/OrchidGroupUsage` | code·message·count의 기존 순수 값 공개 |

- 기존 Sales inspector가 공개 Farm SPI를 직접 구현한다. Farm 내부 입고 검사와 Work 참조를 Farm 차단 값으로 해석하는 기존 구현도 동일 SPI를 사용한다. 허용 API의 단순 전달을 위한 새 Service·Adapter·Port·integration은 추가하지 않는다. 기존 Work usage adapter는 Work 참조 수/범위 밖 존재 여부를 Farm 차단 값으로 변환하는 책임을 유지한다.
- SPI의 세 메서드와 default fallback 본문·원본 작업/일괄 작업/허용 입고 제외 의미는 동일하다. `@Order(100/200/300)`, Bean 이름, 차단 값 내용·순서·첫 사유 선택을 유지한다. Sales 저장소는 Sales 구현 내부에 남긴다.
- reviewed inventory는 사용 여부 SPI/값의 대응 FQCN만 치환한다. SQL·트랜잭션·잠금·수량·취소/보정 유스케이스·Receipt 지문·단일 Writer는 변경하지 않는다.

Farm 사용 여부 단계 검증 결과:

- 첫 실행은 import 치환 오류로 컴파일 실패했으며 올바른 SPI import로 수정 후 집중 검증을 다시 통과했다. 사용 참조 수·원본 작업 제외·취소/void 제외·Sales→Work 검사 순서, command 잠금·보정·보상 Mutation·포트 계획·공개 값·inventory·integration 검증 통과. 계약과 구현의 실행 본문은 이동 전과 동일하다.
- 전체 `clean test spotlessCheck` 통과: 777개, 실패·오류·skip 0개. 임시 init script로 heap 2 GiB 적용. 프론트 `npm run check` 통과. 이전 상태/사용 여부 값·SPI 클래스 잔존 없음.
- Controller·HTTP DTO·schema와 SQL·DB 경계·트랜잭션/잠금·수량·업무 판단 변경 없음. OpenAPI/프론트 타입 재생성과 `workE2eTest`는 실행하지 않았다. 전체 검증 이후 변경은 이 ADR의 결과 기록뿐이다.
- 이번 두 단계 완료. 후속 P2에서 Mutation Writer의 실제 소비 메서드, sealed 명령·정규화, 결과의 Entity factory와 원장 snapshot을 함께 검토·분리한다. Farm 운영 대사 SPI/값과 집계, Sales 공개 계약도 후속 대상이다. 전체 전환 완료 전 목표 문서 archive 이동은 수행하지 않는다.

### 2026-10-08: 다섯 단계 진행 — 1. Mutation 출처·결과·snapshot 값

- Mutation source/source domain, 결과/Entry, type/kind/role enum과 상태 snapshot을 `farm/api/orchid`로 이동한다. 결과·Entry Entity 변환과 replay flag 조합은 application factory, snapshot Entity 변환은 내부 domain factory로 분리한다. 공개 값은 Entity·projection·HTTP 타입을 받지 않는다.
- 기존 enum 명칭·필드·null·JSON 순서·canonical 위치 정밀도·source UUID 생성·source 정규화·Entry 순서·replay 값은 동일하다. Engine·Recorder·ReplayResolver는 기존 변환 본문을 호출하며 Entity 캡처 시점과 SQL·트랜잭션·잠금·Writer를 바꾸지 않는다. 새 전달 Adapter·Port·integration 없음.
- 저장 schema 회귀 fixture는 snapshot의 Java FQCN 키만 치환하며 필드/JSON/기대 지문은 그대로 유지한다. 설정·scripts/main resources의 저장 FQCN 참조 없음. reviewed inventory는 대응 FQCN과 새로 추적되는 공개 값의 기존 외부 접근만 개별 검토한다.

- 이번에 새로 추적되는 snapshot TYPE와 Sales 대사의 기존 `reservedQuantity()` 접근 두 항목만 inventory에 추가했다. 설정된 전체 계약을 자동 승인하지 않았다. 초기 컴파일에서 factory import/메서드 참조를 수정했으며, 집중 테스트의 업무·JSON·지문 검증은 통과하고 inventory 차이를 검토해 반영했다.

1단계 검증 결과:

- Mutation 저장 JSON·snapshot canonical·기존 fingerprint·실행/조회/판매 재고·공개 값·단일 Writer 집중 검증 통과. inventory 검토 반영 후 재검증 통과. 초기 factory import/메서드 참조 오류는 수정 완료.
- 전체 `clean test spotlessCheck` 777개 통과, 실패·오류·skip 0개. 임시 테스트 heap 2 GiB 사용. 프론트 `npm run check` 통과. OpenAPI 재생성 157 operations/132 paths/292 schemas, diff 없음. HTTP 타입 재생성 불필요.
- DB schema·저장 JSON·트랜잭션/잠금·수량 계산 변경이 없어 `workE2eTest` 미실행. 전체 검증 후 변경은 이 ADR 결과 기록뿐이다.

### 2026-10-08: 다섯 단계 진행 — 2. sealed Mutation 명령·정규화

- sealed 명령과 모든 permits 하위 record, 항목/상세/관련 Mutation 값·출처 생성 helper를 `farm/api/orchid`로 이동한다. Java unnamed module의 sealed same-package 조건을 지키고, 두 명령 전용 normalizer는 같은 패키지의 package-private 구현으로 유지한다. 디렉터리용 Port/Interface/전달 Adapter를 추가하지 않는다.
- 명령 생성자의 필수값·중복·수량 검증, ID 정렬·결과 순서·text/position 정규화·source UUID/operation key 생성 본문은 동일하다. 상세의 화분/생성 취소 정책은 Farm 기존 domain 정책을 호출하며 외부 공개 시그니처에 정책 타입을 노출하거나 규칙을 복제하지 않는다.
- fingerprint projection·Normalizer 결과·영구 JSON 필드·Receipt/replay·잠금·트랜잭션·Writer 동작은 동일하다. 저장 schema fixture는 record Java FQCN 키만 치환하고 기대 필드/JSON/지문을 유지한다. reviewed inventory는 대응 FQCN만 치환하며 새 업무 호출을 일괄 승인하지 않는다.

2단계 검증 결과:

- 29개 명령/명령 전용 helper/출처 생성 class 본문은 package/import를 제외하면 동일하다. 출처 helper를 포함한 최종 상태에서 `clean` 집중 검증 통과: 지문·영구 필드/JSON·Mutation·입고·판매 재고·보상·공개 값·inventory·Writer.
- 전체 `test spotlessCheck` 777개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- 초기 검증 중 source helper의 이동을 추가해 발생한 import 순서/inventory 불일치는 최종 clean build로 해결했다. Controller·HTTP DTO·schema 변경 없음. DB/트랜잭션·수량 정책 변경이 없어 OpenAPI/타입 재생성 및 `workE2eTest` 미실행. 전체 검증 후에는 이 ADR 결과만 추가했다.

### 2026-10-08: 다섯 단계 진행 — 3. 외부 Mutation Writer

- Sales의 기존 Engine 직접 참조를 `farm/api/orchid/OrchidGroupMutationWriter`로 전환한다. 외부에서 실제 사용한 create/reserve/releaseReservation/consumeReservation/restoreOutbound/compensateCreations 여섯 메서드만 공개하며 Engine이 직접 구현한다. Farm 내부의 나머지 메서드는 기존 Engine에 유지한다. 별도 위임 계층·Adapter 없음.
- Engine의 `MANDATORY`, 유스케이스 트랜잭션·정렬된 잠금·원본 snapshot 캡처·지문/Receipt/replay·수량 처리·판매 이동 기록·경매 도착 보상은 동일하다. 본문 변경은 없고 선언/소비자 타입만 바꾼다. 기존 Engine 테스트/spy는 유지한다.
- reviewed inventory의 Engine TYPE와 외부 여섯 METHOD만 Writer로 치환한다. architecture 회귀 검사에서 Writer의 유일 구현이 Engine이고 외부 메서드가 readOnly가 아닌 `MANDATORY`임을 확인한다. 기존 Entity/constructor/Repository 단일 Writer 검사는 그대로 유지한다.

3단계 검증 결과:

- 신규 Writer 구현/트랜잭션 회귀와 기존 단일 Writer·공개 값·inventory·integration, 판매 재고·Mutation 라우팅·경매 반환 집중 검증 통과.
- 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- Engine 실행 본문·SQL·DB/트랜잭션 경계·잠금·수량 정책·HTTP 계약 변경 없음. OpenAPI/타입 재생성과 `workE2eTest` 미실행. 전체 검증 이후에는 이 ADR 결과만 추가했다.

### 2026-10-08: 다섯 단계 진행 — 4. Farm 원장 대사 SPI

- 기존 `OrchidGroupLedgerRehearsalInspector`는 `farm/spi/orchid`, 대상 group/issue record는 `farm/api/orchid`로 이동한다. snapshot은 1단계의 공개 순수 값을 그대로 사용한다. Farm이 대사 흐름을 소유하고 Sales가 자신의 allocation/movement 저장소로 정합성을 검사하는 실제 확장 계약이다.
- Sales inspector가 기존 구현으로 SPI를 직접 구현하고 Farm 대사가 호출한다. 새 위임 Service/Adapter/Port/integration 없음. SPI/값의 필드·검증·issue 코드·메시지, 구현과 조회/대사 순서·readOnly/REPEATABLE_READ 트랜잭션은 동일하다. 원장을 자동 보정하지 않는다.
- reviewed inventory는 대응 FQCN만 치환한다. 운영 CLI 진입점·mainClass·기동 검증·SQL·snapshot·단일 Writer 변경 없음. 공개 SPI/값에 Entity·Repository projection·HTTP 타입을 노출하지 않는다.

4단계 검증 결과:

- SPI와 group/issue class 본문은 package/import 외 동일하다. clean 집중 검증 통과: 대사·원장 우회 변경 탐지·보상/rollback·writer guard·판매 재고·공개 값·inventory·integration·단일 Writer.
- 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- HTTP·SQL·트랜잭션 경계·DB·업무 판단 변경 없음. OpenAPI/타입 재생성과 `workE2eTest` 미실행. 운영 CLI 변경/실행 없음. 전체 검증 이후에는 이 ADR 결과만 추가했다.

### 2026-10-08: 다섯 단계 진행 — 5. Farm 집계 API

- Dashboard·Analytics의 `FarmMetricsReader` 직접 참조를 `farm/api/status/FarmMetricsApi`로 전환한다. 실제 외부 조회 getInventorySummary/getSnapshot과 기존 중첩 Snapshot/InventorySummary/VarietyInventory 값을 공개한다. 기존 Reader가 직접 구현하며 SQL·상태 정책·projection 변환은 내부에 유지한다. 별도 전달 Adapter·Service·Port·integration 없음.
- readOnly 트랜잭션, 판매 가능/주의 상태 해석·집계/합계·조회 순서·collection 복사·Dashboard/Analytics 응답 조합은 동일하다. 현재 Entity에서 과거 snapshot을 복원하는 로직을 추가하지 않는다.
- reviewed inventory는 Reader와 중첩 record의 대응 FQCN만 치환한다. 실제 Reader 테스트/spy는 유지하고 API 값의 FQCN import만 갱신한다. HTTP DTO·필드/enum·DB·수량 정책 변경 없음.

5단계 검증 결과:

- 집계 구현 본문은 Override/import/중첩 값 선언 이동 외 동일하다. clean 집중 검증 통과: Analytics 집계·응답 조합·조회 건수·공개 값·inventory·integration·단일 Writer.
- 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과. OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 파일 차이 없음. 프론트 타입은 변경 없어 재생성하지 않았다.
- 프로덕션 코드의 다른 최상위 모듈에서 Farm/Work application 직접 참조는 각각 0개다. 이동 전 명령/결과/원장 대사/domain 값/중첩 metrics 클래스의 잔존 없음. 기존 Engine과 실제 구현 테스트/spy는 유지한다.
- HTTP·SQL·DB/트랜잭션 경계·잠금·수량·상태 정책·CLI 변경 없음. `workE2eTest` 미실행. 전체 검증 이후에는 이 ADR 결과만 추가했다.
- 이번 다섯 단계 완료. 전체 전환은 진행 중이며 Sales 공개 계약, 기능 우선 패키지 이동, Mutation 내부 분리와 최종 CI/문서 통합은 후속 단계다. 목표 문서의 archive 이동은 최종 통합 시 수행한다.

### 2026-10-08: Sales 다섯 단계 — 1. Partner 조회·잠금·예정일

- 외부 Analytics와 Sales 내부에서 사용하는 거래처 조회/Identity/Info/검색 값/enum은 `sales/api/partner`로 공개한다. Entity 변환은 내부 factory에 두고 기존 Reader가 직접 구현한다. 잠금·예정일 API는 실제 소비가 Sales 내부뿐이므로 `sales/partner/api`로 제한한다. 새 전달 Adapter·Port·integration 없음.
- 기존 검색 keyset/500 ID·32 검색 조건 배치·정렬/중복/누락·비활성 조회·잠금 순서·예정일 계산·Info JSON 필드/Schema 이름/순서·readOnly/MANDATORY·호출자 트랜잭션 참여를 유지한다. Entity·projection·HTTP DTO를 API 시그니처에 노출하지 않는다.
- Sales top-level API/SPI의 기능 소유권도 식별한다. 외부 공개 여부는 module contract 경로로 판단하고 기능 의존 그래프/정확한 계약 inventory는 partner/document 등 실제 소유권으로 판단한다. 기존 그래프·Document의 concrete service 금지는 유지하며 parser 회귀 검사에 공개 API/SPI 소유권을 추가한다.
- reviewed inventory는 대응 FQCN을 치환하고 기존 domain 값이 공개 계약으로 이동하면서 새로 추적되는 접근만 개별 검토한다. HTTP·업무 정책·DB/잠금/트랜잭션·Receipt 지문 변경 없음.

Sales 1단계 검증 결과:

- 초기 잠금 API와 wildcard enum import 누락은 수정했다. 거래처/예정일·검색/일괄 조회 건수·소유권 parser·기능 그래프·값 계약 집중 검증 통과. 새로 추적되는 기존 검색 생성자·검색/enum TYPE 네 항목만 reviewed inventory에 추가했다.
- 전체 `clean test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과. OpenAPI 재생성 157 operations/132 paths/292 schemas, diff 없음. 생성 타입 변경 불필요.
- HTTP·SQL·DB/트랜잭션 경계·잠금·정책·Receipt 변경이 없어 `workE2eTest` 미실행. 전체 검증 이후에는 이 ADR 결과만 추가했다.

### 2026-10-08: Sales 다섯 단계 — 2. 판매 집계 API

- Analytics의 SalesMetricsReader 직접 참조를 `sales/api/document/SalesMetricsApi`로 전환한다. 기존 외부 아홉 조회와 세 중첩 record·저장 결제 상태 분류 enum을 같은 공개 값 패키지에 두며 기존 Reader가 직접 구현한다. QueryDSL/집계 구현은 내부에 유지한다. 전달 Service/Adapter/Port/integration 없음.
- 완료 전표 조건·기간·합계/coalesce·상태 문자열 호환 분류·정렬/10개·5개 제한·projection 구성·readOnly는 동일하다. enum 분류 본문도 그대로 이동하고 규칙을 Analytics에 복제하지 않는다. reviewed inventory는 대응 FQCN과 기존 domain enum 접근만 검토한다.

Sales 2단계 검증 결과:

- clean 집중 검증의 집계·응답/조회 건수·공개 값·기능 그래프·단일 Writer 통과. 기존 분류 enum TYPE 한 항목만 reviewed inventory에 추가 후 전체 검사 통과.
- 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과. HTTP DTO/schema 변경 없음.
- SQL·DB/트랜잭션 경계·정책 변경 없음. OpenAPI는 출력 값 이동 후 다시 확인하며 이번 단계에는 타입 재생성/`workE2eTest` 미실행. 전체 검증 후에는 이 ADR 결과만 추가했다.

### 2026-10-08: Sales 다섯 단계 — 3. Print 전표 조회·공개 값

- Print의 concrete SalesQueryService 참조를 `sales/api/document/SalesDocumentQueryApi`로 바꾼다. 실제 사용하는 page/단건 두 메서드만 공개하고 기존 readOnly service가 직접 구현한다. 호환 전체 목록과 출하 선택 HTTP 값은 내부 기존 service에 유지한다. 전달 Adapter 없음.
- 전표·목록·품목·배분·과거 snapshot record와 필요한 enum을 `sales/api/document`로 이동한다. Entity factory 다섯 개는 내부 application으로 옮기며 기존 변환 본문·금융 검토/가격 누락 조건·현재 값과 과거 snapshot 조합·시간대 변환·capability/JSON Schema 이름/필드/순서·Receipt 지문/replay는 유지한다. 공개 멤버는 Entity·Repository projection·HTTP DTO를 받지 않는다.
- 유스케이스·일괄 조회·잠금/트랜잭션·SQL·수량/금액 정책·A5 출력 흐름 변경 없음. reviewed inventory는 대응 FQCN과 기존 domain enum 접근만 검토한다. DB 저장 enum 이름·JSON은 동일하며 Enum/값 Java FQCN이 저장되는 경로도 확인한다.

Sales 3단계 검증 결과:

- clean 집중 검증과 전체 백엔드 테스트 778개 통과, 실패·오류·skip 0개. 프론트 `npm run check` 통과. 초기 Spotless 검사 실패 후 `spotlessApply spotlessCheck --rerun-tasks` 통과; 이후 변경은 포맷과 검증 기록뿐이다.
- OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 파일 diff 없음. 타입 재생성 불필요. HTTP·SQL·DB/트랜잭션 경계·정책 변경이 없어 `workE2eTest` 미실행.

### 2026-10-08: Sales 다섯 단계 — 4. Document 회계 SPI

- Document 소유 기존 `DirectDocumentAccountingPort`와 중첩 순수 값을 `sales/document/spi`로 이동한다. Document → 소유 SPI 호출, Direct → Document SPI 구현의 의존 방향을 유지한다. Sales 내부 기능 계약이므로 다른 최상위 모듈에는 공개하지 않는다.
- 기존 DirectDocumentAccountingAdapter는 Direct 가격/조건/검토와 Payment 잔액/입금·Partner 예정일을 Document 계약에 연결하는 실제 역전 구현이므로 유지한다. 구현·호출 순서·잠금·트랜잭션·값 변환 변경이나 새 전달 Adapter/Port/integration은 없다. reviewed inventory는 대응 FQCN만 치환한다.

Sales 4단계 검증 결과:

- 초기 집중 검증의 import 정렬 오류 수정 후 전체 `clean test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 프론트 `npm run check` 통과. 임시 heap 2 GiB 사용. SPI 본문과 Adapter 실행 본문은 동일하며 다른 Java 수정은 import뿐이다.
- reviewed inventory는 대응 FQCN만 치환하고 신규 항목은 없다. HTTP·SQL·DB/트랜잭션 경계·정책 변경이 없어 타입 재생성/`workE2eTest` 미실행. OpenAPI는 5단계 후 다시 확인한다. 전체 검증 이후에는 이 ADR 결과만 추가했다.

### 2026-10-08: Sales 다섯 단계 — 5. Document 경매 SPI

- 기존 `AuctionDocumentPort`와 출하/lot/생성 결과 값을 Document 소유 `sales/document/spi`로 이동한다. Document → 자신의 SPI 호출, Auction → Document SPI 구현을 유지한다. Sales 내부 계약이며 외부 최상위 모듈에 공개하지 않는다.
- AuctionDocumentAdapter는 출하 생성·초안 삭제·결과 참조와 취소 불가 조건 결합·표시/일괄 조회를 Document에 연결하는 실제 역전 구현이므로 유지한다. Auction Entity나 저장소는 노출하지 않는다. 새 전달 Adapter/Port/integration은 없다.
- SPI/record 검증과 collection 복사·실행 본문·잠금/트랜잭션·과거 출하 값·Mutation 단일 Writer·Receipt 지문/replay는 동일하다. reviewed inventory는 대응 FQCN만 치환한다.

Sales 5단계 검증 결과:

- clean 집중 검증 통과: 경매 추적·판매/재고·생성 지문·공개 값·기능 그래프·단일 Writer·정확한 inventory. 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 파일 diff 없음. 타입 재생성 불필요. SPI 본문은 package 외 동일하고 다른 Java 변경은 import뿐이다. 두 SPI의 이전 FQCN 참조는 코드·리소스·스크립트에 남아 있지 않다.
- SQL·DB/트랜잭션 경계·잠금·수량/금액 정책·HTTP 변경 없음. `workE2eTest` 미실행. 전체 검증 이후에는 이 ADR 결과만 추가했다.
- 이번 다섯 단계 완료. 다른 최상위 모듈에서 Sales application을 직접 사용하는 프로덕션 코드는 Analytics의 PartnerBalanceService/Balance 참조 두 import가 남는다. 다음 단계는 Payment 잔액 공개 조회 및 내부 대상/배분 계약 정리다. 전체 전환·최종 문서 통합은 진행 중이므로 목표 문서의 archive 이동은 아직 하지 않는다.

### 2026-10-08: P2 마무리 — Payment 계약과 HTTP 값 분리

- Analytics 잔액 조회는 `sales/api/payment`로 공개한다. 내부 잔액 변경·입금 이력·유효 배분·단건 입금 조율은 Payment 내부 API를 기존 서비스가 직접 구현한다. Direct 단건 입금과 Document 입금 대상 계약도 실제 소비 범위만 기능 API로 제공한다. 새 전달 Adapter 없음.
- Payment 대상 SPI와 입력/옵션 값을 `sales/payment/{spi,api}`로 이동한다. 기존 Direct/Auction 대상 구현은 실제 의존성 역전이므로 유지한다. Auction 대금과 결과 참조 값을 `sales/auction/api`로 옮기고 Repository row 변환은 Reader 내부에 유지한다. JSON·Schema 이름/필드·validation은 동일하다.
- 잠금/트랜잭션·원장·멱등 처리·입금/배분/정정·잔액 갱신·오류/capability·Mutation 단일 Writer 변경 없음. reviewed inventory는 실제 호출별 API 소유권을 재지정하며 새로 추적되는 기존 값 접근만 개별 검토한다.

P2 마무리 검증 결과:

- 집중 검증의 업무 테스트 통과. 기존 HTTP 옵션이 순수 값으로 이동하면서 새로 추적되는 생성자·옵션 TYPE·대상 enum TYPE 세 항목만 reviewed inventory에 추가했다. 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 프론트 `npm run check` 통과.
- OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 diff 없음. 타입 재생성 불필요. Farm·Work·Sales의 application 구현을 다른 최상위 모듈에서 참조하는 프로덕션 코드는 0개다. Sales 기능 간 implementation 직접 참조도 API로 전환했다.
- DB·SQL·트랜잭션·업무 동작 변경이 없어 `workE2eTest` 미실행. 전체 검증 이후에는 이 ADR 결과만 추가했다. P2 완료, 다음은 P3 기능별 패키지 이동이다.

### 2026-10-08: P3 — Partner 기능 우선 배치

- Partner 프로덕션 26개와 같은 패키지 테스트 2개를 `sales/partner/{application,domain,repository,web}`로 이동한다. HTTP DTO는 web/dto, 외부 공개 조회는 기존 sales/api/partner, 내부 잠금/예정일 API는 sales/partner/api를 유지한다.
- 클래스·Bean 이름·validation·Entity/DB·검색/잠금/예정일 실행 본문은 그대로다. QueryDSL 및 소비자 import·감사 helper 제한의 FQCN도 같은 이동표로 갱신한다. reviewed inventory는 해당 패키지 FQCN만 치환한다.

- clean 컴파일 및 거래처·예정일·capability·공개 값·의존 그래프·단일 Writer·inventory 집중 검증 통과. 전체 검증은 P3 다섯 기능 이동 후 수행한다. DB/트랜잭션 변경이 없어 PostgreSQL E2E는 이 이동에서 실행하지 않는다.

### 2026-10-08: P3 — Payment 기능 우선 배치

- Payment 프로덕션 35개와 같은 패키지 테스트 2개를 `sales/payment/{application,domain,repository,web}`로 이동한다. P2의 내부 API·대상 SPI와 sales/api/payment 공개 잔액 조회는 유지한다.
- 원장·CTE 집계·잔액·감사·단건/다중 입금·정정의 실행 본문, 트랜잭션·잠금 순서·멱등 namespace·금액 정책·JSON은 동일하다. Entity 이름·SQL·DB 제약을 변경하지 않는다. 소비자/QueryDSL/테스트·감사 helper FQCN을 갱신한다.

- clean 컴파일과 입금·배분·원장 계약·단건 입금·아키텍처/inventory 집중 검증 통과. 전체 검증은 P3 전체 이동 후 수행한다. DB/트랜잭션 변경이 없어 이 단계 PostgreSQL E2E 미실행.

### 2026-10-08: P3 — Document 기능 우선 배치

- 공통 전표 기능을 `sales/document/{application,domain,repository,web}`로 이동한다. Document 소유 입금 API와 회계/경매 SPI, sales/api/document 외부 조회·값은 유지한다. 숫자 채번 저장소 테스트도 Document 소유 테스트 패키지로 옮긴다.
- 전표 생성·예약·출고·snapshot·receipt·가격 projection·일괄 조회·금융 검토·A5 출력과 기존 트랜잭션/잠금 순서는 동일하다. 생성 v1 필드 fixture는 클래스 FQCN key만 갱신하고 필드 목록·저장 JSON·지문·replay는 변경하지 않는다.

- Document 프로덕션 50개·기능 테스트 7개와 채번 테스트 1개를 이동했다. 실행 본문은 package/import 외 동일함을 대조했다. clean 컴파일과 판매·재고·snapshot·v1 생성 지문·채번·출고·값/의존 그래프/inventory 집중 검증 통과. 초기 수납액 변경 제한 검사의 하드코딩된 이전 Entity 경로를 새 경로로 갱신한 뒤 재검증했다. 전체 검증은 P3 전체 이동 후 수행한다.

### 2026-10-08: P3 — Direct 기능 우선 배치

- Direct의 회계/검토/금액 정책·저장소·입금 유스케이스를 `sales/direct/{application,domain,repository}`로 이동한다. 단건 입금 API는 direct/api, Document 회계 SPI 구현과 Payment 대상 SPI 구현은 기존 클래스가 계속 수행한다. HTTP 진입점은 기존 Payment web 조합을 유지하며 새 web/integration/위임 계층을 만들지 않는다.
- 금액 원천·가격·검토 차단·projection·입금·호출자 트랜잭션 참여·잠금/원장 의미는 동일하다. Entity/Repository는 Direct 내부에 유지하고 소비자/QueryDSL/테스트의 FQCN만 갱신한다.

- Direct 프로덕션 13개·테스트 2개 이동. clean 컴파일과 가격/금액 정책·판매·입금/배분·값/그래프/inventory/단일 Writer 집중 검증 통과. 전체 검증은 P3 전체 이동 후 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E는 미실행.

### 2026-10-08: P3 — Auction 기능 우선 배치

- Auction 출하·lot·시도·원본 결과·후속 결정·반환 도착·대금 연결을 `sales/auction/{application,domain,repository,web}`로 이동한다. 순수 대금/결과 값은 auction/api에 유지하며 Document/Payment SPI의 기존 구현이 연결한다.
- 생성/반환 Mutation 단일 Writer·snapshot·receipt·결정과 도착의 구분·lot 선잠금·대금/배분/정정·취소 보호·readOnly/최상위 트랜잭션을 유지한다. 소비자/QueryDSL/테스트의 FQCN과 reviewed inventory만 같은 이동표로 갱신한다.

- Auction 프로덕션 56개·테스트 4개 이동. clean 컴파일과 경매 추적·수량/결과/반환 정책·대금·판매/배분·아키텍처/inventory 집중 검증 통과. 전체 검증은 Sales 완료 경계 검사와 함께 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E는 미실행.

### 2026-10-08: P3 완료 경계 검사

- Sales의 기존 application/domain/repository/controller/dto 루트를 재도입할 수 없도록 기존 경로 검사를 강화한다. 기존 Sales 기능 그래프 검사에서 다른 기능의 application 구현 직접 참조도 차단한다. 기능 간 API/SPI 호출·단일 Writer·저장소 소유권·공개 값 검사는 유지한다. Farm/Work의 이행 중 기존 경로 지원은 P4/P6까지 유지한다.
- 이번 P3는 프로덕션 180개·소유 기능 테스트 18개를 이동했다. 모듈 밖 공개 범위·클래스/Bean 이름·DB·HTTP·업무 동작은 변경하지 않는다. 새 integration/전달 계층 없음. 전체 검증 및 생성 명세 확인 후 완료로 기록한다.

P3 완료 검증 결과:

- 강화된 Sales 경로·기능 API/SPI 경계·소유권 parser·공개 값·기능 그래프·저장소 소유권·단일 Writer·정확한 inventory 검사 통과. 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 diff 없음. 타입 재생성 불필요. 기존 Sales 계층 루트의 class artifact 0개. Farm/Work/Sales application 구현의 다른 최상위 모듈 참조 각각 0개.
- P2 완료 커밋과 대조해 P3 프로덕션 180개 파일은 package/import 외 실행 본문 변경이 없고, 생성 v1 필드 목록도 그대로임을 확인했다. reviewed inventory는 생성 보고서와 정확히 일치한다.
- DB·SQL·트랜잭션·잠금·수량/금액 정책 변경 없음. `workE2eTest` 미실행. PostgreSQL 핵심 회귀는 P6 최종 검증 범위다. 전체 검증 이후 변경은 이 ADR 완료 기록과 문서 링크뿐이다.
- 사용자 요청 범위인 P3까지 완료. P4/P5/P6는 진행하지 않는다. 목표 설계의 최종 통합·archive 이동은 P6에서 수행하며 이 ADR은 유지한다.

### 2026-10-08: P4 — Farm 기준정보·구조

- Variety/Material/Structure를 기능 우선 application/domain/repository/web로 이동한다. 기존 Farm 내부의 기준정보·위치·수량 조회와 JPA 연관관계, 품종 재사용·비활성화·Engine 전파·배치 정책/감사·일괄 조회를 유지한다. 새 전달 Adapter/Port/API 없음.
- 클래스/Bean/Entity 이름·테이블·트랜잭션·잠금 순서·HTTP 계약을 유지한다. QueryDSL import·소비자·테스트·reflection/문자열의 FQCN을 같은 이동표로 갱신한다. reviewed inventory는 해당 FQCN만 치환한다.
- P4는 업무별 패키지 배치와 기존 의존 방향 보존 범위다. Farm 내부의 기존 기능 간 저장소 조회·Entity 연관관계를 임의로 금지하거나 새 계약으로 일괄 감싸지 않는다. Mutation 내부와 CLI 진입점은 P5에 남긴다.

- 프로덕션 53개·같은 패키지 테스트 2개 이동. clean 컴파일 및 품종 조회/복합키/감사·농장 구조·배치/profile/감사·architecture/public values/inventory/단일 Writer 집중 검증 통과. 전체 검증은 P4 전체 이동 후 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E 미실행.

### 2026-10-08: P4 — Work 기능 우선 배치

- Work operation/target/effect/correction의 구현·Entity·HTTP DTO를 기능별 application/domain/web/dto로 옮긴다. 외부 공개 API/SPI는 P2의 위치와 계약을 유지한다. 기존 aggregate 연관관계와 실행·저장 순서를 유지한다.
- 기존 Repository 19개는 Entity/조회 생산자로 소유권을 정한다: operation 7, target 7, effect 2, correction 3. 진행·입고 참조·실행 대사 projection은 target, child count는 operation, 보정 대사 row는 correction 내부에 둔다. Spring Data Custom/Impl은 해당 Repository와 같은 패키지로 옮긴다. 세 HTTP Controller는 operation/web에 둔다.
- Work의 Farm 직접 의존은 추가하지 않는다. 기존 Work → 공개 SPI 호출/Farm 구현, Farm → Work 공개 API 호출의 컴파일·runtime 방향을 유지한다. 최상위/MANDATORY/readOnly·Receipt 지문·snapshot·효과/보정 codec·batch flush·capability·오류 계약은 동일하다. 새 전달 Adapter/API/Port 없음.

- Work 프로덕션 127개·같은 패키지 테스트 29개 이동. clean 컴파일과 Work 기능 단위 테스트·작업/입고 포트 통합·공개 값·의존 방향·inventory·단일 Writer 집중 검증 통과. Custom/Impl·JPQL constructor projection 새 FQCN과 v1 필드 fixture도 함께 갱신했다. 전체 검증은 P4 전체 이동 후 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E 미실행.

### 2026-10-08: P4 — Farm 난 묶음·그룹·현황

- Orchid/Collection/Status 구현·Entity·Repository·web/dto를 기능 우선으로 옮긴다. 기존 farm/api의 Reader/Writer/metrics 및 farm/spi 사용 여부·대사 계약, orchid/integration의 실제 Work SPI 구현은 유지한다.
- OrchidGroup Entity와 Repository의 FQCN만 바꾸고 상태 변경/생성/저장 단일 Writer는 기존 Engine으로 유지한다. `farm/{application,domain,repository}/orchid/mutation` 하위 전체는 P5 대상이므로 이번 prefix 이동에서 명시적으로 제외한다. CLI/Gradle mainClass도 변경하지 않는다.
- 현재 값/과거 snapshot·조회 배치·collection 소속/활성·현황/집계·순수 policy·쓰기를 포함한 유스케이스/잠금/트랜잭션은 그대로다. 새 전달 Adapter/Port/API 없음. 기존 integration의 참조만 새 소유 패키지로 갱신한다.

- Orchid/Collection/Status 프로덕션 82개·같은 패키지 테스트 10개 이동. clean 컴파일과 난 묶음 조회/생성/변경/취소/소속/사용 여부·현황/집계·일괄 조회·단일 Writer·공개 값/inventory 집중 검증 통과. Mutation 패키지·Gradle CLI mainClass는 이동하지 않았다. 전체 검증은 P4 전체 이동 후 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E 미실행.

### 2026-10-08: P4 — Farm 입고·구조 변경

- 입고와 분갈이/분주/합식/자리 이동 구현·Entity·Repository·HTTP DTO를 inbound/transformation 기능 우선 배치로 이동한다. 기존 inbound/integration·transformation/integration의 실제 Work SPI 구현과 보정 책임은 유지하며 참조만 갱신한다.
- Work API 직접 호출과 Work 소유 SPI의 Farm 구현을 유지한다. 계획/취소/선잠금·품종 재사용·입고-Work 연결·구조 변경 실행기/Strategy·순수 수량 배분·snapshot/Mutation 연결·완료 책임·최상위/호출자 트랜잭션·오류·감사 순서는 동일하다.
- 경로 이동을 이유로 새 interface/Port/Gateway/Adapter/integration을 추가하지 않는다. Mutation Engine은 기존 위치의 단일 Writer이며 운영 CLI·저장 지문/Receipt는 변경하지 않는다.

- Inbound/Transformation 프로덕션 54개·같은 패키지 테스트 3개 이동. clean 컴파일과 입고/포트·구조 변경·이동/수량 배분·선잠금 Adapter·공개 값/의존 방향/inventory/단일 Writer 집중 검증 통과. 새 integration/계약 계층 없음. 전체 검증은 P4 완료 경계 검사와 함께 수행한다. DB/트랜잭션 변경이 없어 이 이동의 PostgreSQL E2E 미실행.

### 2026-10-08: P4 완료 경계 검사

- Work의 기존 role-first 루트를 차단하고 Farm의 이전 루트에는 P5 Mutation 하위만 허용하도록 기존 경로 검사를 강화한다. Farm 기존 기능 간 조회와 연관관계는 동일하며 Entity/Repository의 다른 최상위 모듈 유출·Work의 Farm 의존·단일 Writer·integration SPI 구현·공개 값 검사를 유지한다.
- P4 전체 프로덕션 316개(Farm 189, Work 127)·소유 기능 테스트 44개를 이동했다. P4 시작 커밋과 대조해 실행 본문은 package/import/JPQL의 클래스 FQCN 외 동일하고 v1 필드 목록도 동일함을 확인했다. 명시적 Mutation 하위 제외로 Engine/원장/CLI 내부는 이동하지 않았다. 전체 검증 및 생성 명세 확인 후 완료로 기록한다.

P4 완료 검증 결과:

- 강화된 기능 우선 경로 검사 및 공개 값·모듈 방향·저장소 소유권·integration SPI·단일 Writer·소유권 parser·정확한 inventory 집중 검증 통과. 전체 `test spotlessCheck` 778개 통과, 실패·오류·skip 0개. 임시 heap 2 GiB 사용. 프론트 `npm run check` 통과.
- OpenAPI 재생성 157 operations/132 paths/292 schemas, 생성 diff 없음. 타입 재생성 불필요. reviewed inventory는 생성 보고서와 정확히 일치하며 패키지 변경 외 신규 승인 항목은 없다.
- Work의 이전 계층 소스 0개. Farm 이전 계층 소스는 명시적 P5 Mutation 33개뿐이다. 이 범위 밖의 이전 class artifact는 0개다. Farm/Work/Sales application 구현의 다른 최상위 모듈 참조 각각 0개. Mutation 단일 Writer·CLI mainClass는 동일하다.
- P4 시작 커밋과 대조해 Farm 189·Work 127 파일의 실행 본문은 package/import/JPQL 클래스 FQCN 외 동일하며, 모든 v1 필드 목록도 그대로다. 구조 변경 정책·수량/금액·입고/실행/취소·잠금·트랜잭션·SQL·DB 제약 변경 없음. `workE2eTest` 미실행. PostgreSQL 핵심 회귀는 P6 최종 검증 범위다.
- 전체 검증 이후 변경은 이 ADR 결과와 문서 소스 링크뿐이다. P4 완료. 다음은 P5 Mutation 내부 재배치이며 최종 문서 통합/archive 이동은 P6에서 수행한다.

### 2026-10-08: P5 — Mutation 내부 책임별 배치

- 기존 33개를 engine 5, ledger 16(domain 7/repository 7/Recorder·결과 변환 2), query 3, verification 5, config 4로 이동한다. 소유 패키지 테스트 4개와 소비자·QueryDSL·JPQL/reflection·Gradle 두 CLI mainClass를 같은 이동표로 갱신한다. 공개 Writer/command/result·snapshot은 farm/api에 유지한다.
- Engine의 상태 변경/생성/Repository 쓰기 유일성·MANDATORY·잠금 순서·schema version·지문·canonical snapshot·replay·revision·write fence·대사/기동 검증 실행 본문을 유지한다. Work SPI 구현과 기존 Farm/Work/Sales 업무 책임은 동일하다.
- 분리로 Engine↔Ledger 접근에 필요한 Recorder/Change·결과 Factory의 기존 멤버만 public으로 연다. 새 API/Port/Adapter를 만들지 않으며 Recorder의 Mutation 밖 참조는 architecture 검사로 차단한다. 기존 단일 Writer 검사와 전체 공개 값 검사도 유지한다.
- Farm의 마지막 이행 경로를 제거하고 기존 계층 루트 재도입을 차단한다. Gradle task 이름·보호 옵션·CLI 종료 코드/출력·읽기 전용 동작은 유지한다. 문서 통합/archive 이동은 P6에 남긴다.

P5 완료 검증 결과:

- clean 컴파일과 아키텍처·단일 Writer·지문/snapshot 호환·Mutation/원장 집중 검증 통과. 백엔드 전체 `test` 779개(실패·오류·skip 0), `spotlessCheck`, 프론트 `npm run check` 통과.
- Testcontainers PostgreSQL에서 원장 schema·대사·CLI 회귀 4개 통과. 기존 ACTIVE 원장을 구성한 테스트 DB에서 이동한 두 CLI를 별도 JVM으로 실행하고 종료 코드·대사 결과·기동 검증과 난 묶음/원장 5개 테이블의 실행 전후 데이터 동일성을 확인했다. 실제 운영 백업 복원본 검증과 전체 PostgreSQL 핵심 회귀는 이번 실행 범위가 아니며 P6·운영 체크포인트에 남긴다.
- P5 시작 커밋과 대조해 이동한 프로덕션 33개 파일의 실행 본문은 package/import/필요한 접근 범위 외 동일하며, 저장 JSON fixture도 그대로다. reviewed 공개 계약 inventory는 대응 FQCN만 갱신했고 승인 멤버를 추가하지 않았다.
- Farm/Work/Sales 이전 계층의 프로덕션 소스와 Mutation 이전 FQCN 참조는 제거했다. 단일 Writer·트랜잭션·잠금·지문·snapshot·replay·revision·write fence와 기존 API/DB 정책은 유지한다. 새 integration/위임 Adapter/Port 없음.
- OpenAPI 재생성 결과 157 operations/132 paths/292 schemas이며 전체 명세·slice 차이 0. 프론트 생성 타입 갱신은 필요하지 않다.
- 전체 검증 이후 변경은 문서 완료 기록과 소스 링크뿐이다. 사용자 요청 범위인 P5까지 완료하며 P6 최종 문서 통합·archive 이동은 후속 작업으로 남긴다.

### 2026-10-08: P6 — 최종 경계·문서 통합

- 소유권 판별기의 이행용 role-first 기능 경로 지원을 제거했다. Farm/Work/Sales는 기능 우선 구조와 최상위 API/SPI만 허용하고, 지원 모듈의 기존 계층은 유지한다. 음성 fixture도 현재 기능별 내부 application으로 이동해 직접 호출·메서드 참조 우회 검출을 유지한다. 기존 경로를 허용하던 fixture는 거절 사례로 전환했다.
- integration 3개 패키지·9개 구현을 재검토했다. 이전 감사의 7개에 추가된 FarmWorkTargetResolver는 Work target SPI의 선택·대상 해석·정렬 잠금/활성 검증을, FarmWorkCorrectionAdapter는 Work correction SPI의 원본 참조·사용 여부·수량 검증과 잠금·Mutation 보정을 수행한다. 모두 실제 Work 공개 SPI 공급자다. 유지 9개, 추가 제거 0개이며 허용 API를 장식하는 새 Adapter/Port는 없다.
- 전후 관계는 동일하다: 컴파일 Farm 구현 → Work 공개 SPI ← Work application, 런타임 Work → SPI의 Farm 구현 → Farm 소유 조회/정책/잠금/Mutation. Farm 안에서 허용된 Work API 호출은 직접 유지한다. Work의 Farm 직접 의존을 새로 허용하지 않는다.
- 명명 검토: Gateway 두 계약은 Work가 요청하는 Farm 참조/입고 계획 경계이므로 유지한다. 기존 기능별 AuditSupport는 snapshot·변경 사실 기록, WorkOperationSupport는 내부 Clock/actor·제목 조립을 담당하므로 기계적 개명·추가 추상화를 하지 않는다. 완료된 importer/cutover runtime은 재도입하지 않는다.
- 현행 아키텍처에 실제 모듈·Sales 기능 허용표, 완료 책임, 공개 범위, 선택적 Port/SPI/integration, DTO·명명, Mutation/Effect/Audit 구분과 보존 원칙을 통합했다. 가상 slip/settlement 모델이나 Work → Farm 컴파일 방향을 적용하지 않는다. 최초 목표 설계는 archive/plans로 옮기고 현행 기준·ADR 링크를 명시한다.
- CI는 기존 Verify의 backend check/bootJar, PostgreSQL E2E/benchmark, frontend, OpenAPI drift 구성을 유지한다. 새 경계 검사는 기존 test/check, P5 CLI 회귀는 기존 workE2eTest에서 실행되므로 별도 중복 job을 추가하지 않는다.

P6 검증 중 발견한 기존 벤치마크 fixture 누락:

- 첫 `workBenchmark`의 검색 테스트가 `DIRECT_AMOUNT_SOURCE_MISSING`으로 실패했다. 수만 건의 DIRECT 전표만 SQL로 생성하고 V43 이후 Direct 소유 금액 행을 생성하지 않아 첫 목록 조회에서 정합성 검증이 실패한 것이다. Work 벤치마크와 PostgreSQL 전체 E2E는 통과했다.
- 검색 fixture에 동일 전표의 원문 금액을 가진 direct_sales 행을 추가했다. 한 행짜리 마지막 페이지에서 기존 Direct 가격·Payment 유효 배분·대사 evidence의 일괄 조회 3회를 포함하도록 판매 쿼리 기대값을 `count / 500 + 4`에서 `count / 500 + 7`로 수정했다. 경매 기대값 `count / 500 + 6`, bind 상한 500, 총건수·페이지 크기·501/5001/70001건·keyword 공백 1/20 검증은 유지한다. 프로덕션 조회·업무 정책은 변경하지 않는다.
- 두 벤치마크 재실행 통과. 501/5001/70001건의 판매 쿼리는 8/17/147회, 경매 쿼리는 7/16/146회로 정확한 일괄 조회 식과 일치하며, 공백 수에 따라 쿼리가 추가되지 않는다.

P6 완료 검증 결과:

- 아키텍처 집중 검증 후 clean `check bootJar`와 백엔드 전체 `test` 779개 통과. 벤치마크 fixture 보완 뒤 `check bootJar`를 다시 실행해 최종 `test` 779개·`spotlessCheck`·패키징 성공을 확인했다. 실패·오류·skip 0.
- 전체 `workE2eTest` 818개 통과. Flyway·DB constraint·write fence·원장/Mutation·생성/취소/보정·예약/출고·정산/입금·잠금/경쟁·Receipt/재전송·rollback·일괄 조회 및 두 운영 CLI 회귀를 실제 Testcontainers PostgreSQL에서 검증했다. `workBenchmark -PworkBenchmarkEnforce=true` 2개도 fixture 보완 후 통과했고 결과 JSON의 queryLimitsEnforced=true를 확인했다. E2E 성공 이후 변경은 work-benchmark 태그의 독립 fixture와 문서뿐이므로 818개 전체 E2E를 반복하지 않았다.
- 프론트 `npm run check`의 포맷·생성 타입·테스트·lint·build 통과. OpenAPI 생성 안정성 3개 통과. OpenAPI 157 operations/132 paths/292 schemas와 프론트 타입 재생성 후 명세·slice·생성 타입 차이 0. 이후 변경은 API에 영향을 주지 않는 벤치마크 fixture와 문서다.
- Verify workflow의 검사 항목을 로컬에서 실행했다. 기존 임시 init script의 테스트 heap 2 GiB를 사용했으며 저장소 heap/CI 설정은 변경하지 않았다. 검토된 4개 inventory는 생성 보고서와 일치하고 승인 항목을 추가하지 않았다. clean 후 Farm/Work/Sales 이전 계층 소스·main/test class artifact 0. 수정 문서의 상대 Markdown 링크 모두 정상.
- P6 프로덕션 실행 코드·DB/Flyway·업무 정책·API·트랜잭션·잠금·저장 형식 변경 없음. 실제 운영 백업 복원·재배포는 이번 로컬 검증 범위가 아니며 기존 배포 절차를 따른다.
- 목표 설계의 확정된 규칙을 현행 아키텍처에 통합했고 최초 목표 문서는 `docs/archive/plans/green-house-backend-architecture-final.md`에 보관했다. 목차·features·archive 안내와 ADR 링크를 갱신했으며 이 ADR은 결정·검증 기록으로 유지한다. P0~P6 전환 완료. 최종 검증 이후 변경은 이 완료 기록뿐이다.
