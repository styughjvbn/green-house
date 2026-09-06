# 전체 백엔드 리팩터링 계획

- 기준일: 2026-09-05
- 기준 코드: `feature/orchid-group-mutation-engine`, `3aa8fc54`
- 상태: 단계별 구현 진행 중. 1~3차 구현 범위와 남은 작업은 아래 실행 기록 참고.
- 작업 브랜치: `feature/backend-refactoring`.
- 범위: **13개 모듈 전체**, Controller·application·domain·Repository·DTO·설정·DB migration·테스트·CI.
- 목표: **확장에는 열려 있고 수정에는 닫힌 구조(OCP)**. 새 기능을 추가할 때 기존 유스케이스와 타 모듈 내부를 수정하는 범위를 줄인다.
- 관련 상세: [Mutation Engine 및 호출부](orchid-group-mutation-refactoring-detail.md).

## 1. 판단과 전체 범위

현재 백엔드는 모듈러 모놀리스이며 주요 업무 흐름과 테스트 기반이 이미 있다.
문제는 모듈 이름과 레이어가 나뉘어 있어도, Entity·HTTP DTO·조회 쿼리·정책 분기를 통해 내부 구현이 연결되는 지점이 남아 있다는 것이다.
Mutation Engine 정리는 전체 리팩터링의 한 작업이다.

이번 계획은 다음 두 가지를 함께 달성한다.

1. 현재 코드를 일관된 책임과 읽기 순서로 정리한다.
2. 작업 유형·입력 경로·정산 정책·분석 지표 등 예상되는 확장 지점에서 기존 실행기를 수정하지 않고 구현이나 정의를 추가할 수 있게 한다.

OCP는 모든 미래 요구를 기존 코드 수정 없이 처리한다는 뜻은 아니다.
**동일한 규칙을 따르는 변형은 추가로 처리하고, 수량 보존·결제 의미처럼 공통 불변식 자체가 바뀌면 소유 도메인을 명시적으로 수정한다.**
공통 규칙 변경을 숨기는 설정 파일이나 범용 엔진을 만들지 않는다.

### 모듈별 범위와 작업 연결

| 모듈 | 정리할 범위 | 주 작업 |
|---|---|---|
| common | 오류·페이지·시간·실행자 계약, 도메인 전용 공통 타입의 소유권 | B-03 |
| audit | 감사 입력·실행 맥락·민감 필드 처리·동일 트랜잭션 저장 | B-13 |
| auth | 인증·세션·쿠키·권한·HTTP 오류 작성 | B-12 |
| demo | 차단 정책·요청 제한·실행자 대체·인증 조립 | B-12 |
| farm | structure·status·orchid·collection·inbound·variety·material·transformation 전체 | B-08, B-10 |
| work | 유형 정의·계획·대상·실행·효과·보정·이력·capability 전체 | B-09, B-16 |
| partner | 기준 정보·활성 판정·외부 조회·잠금·감사 | B-04 |
| sales | 생성·수정·예약·출고·취소·입금·조회·스냅샷·타 모듈 호출 | B-07 |
| auction | 출하 생성·lot·시도·결과·반환·상태 이력·조회 | B-05 |
| settlement | 설정·정산·입금 이벤트·잔액·재구축·조회 | B-06 |
| analytics | 판매·재고·거래처·작업 집계의 소유권·조회 모델·기간 | B-11 |
| dashboard | 모듈별 요약 조합과 미구현 지표 계약 | B-14 |
| print | 출력용 조회 계약·문서 데이터·A5 호환 | B-14 |

전역 작업 B-01·02·15는 모든 모듈에 적용한다. B-17은 Mutation 운영 전환 후 제거 작업이다.
전체 대상이라는 이유로 모든 클래스를 변경하지 않는다. 유지할 구조도 모듈별로 판단하고 검증한다.

## 2. 추가 검토에서 확인한 구조 문제

### 2.1 application API가 Entity 전달 통로가 된 곳

- [BusinessPartnerReader](../../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java)는 `BusinessPartner`를 반환하고 여러 업무 모듈이 사용한다.
- [AuctionShipmentCreator](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java)는 외부에서 만든 `AuctionShipment`를 받아 저장만 한다.
- [AuctionShipmentMaterializer](../../backend/src/main/java/com/greenhouse/backend/sales/application/AuctionShipmentMaterializer.java)와 `AuctionShipmentLotFactory`는 Sales에서 Auction Entity를 생성한다.
- [AuctionDataReader](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionDataReader.java)는 정산에 `AuctionResultLine` 등 Entity를 제공한다.
- [OrchidGroupReader](../../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java)와 Work handler 계약에도 유사한 의존이 있다.

Repository import를 금지하는 것만으로는 확장 시 변경 전파를 막을 수 없다. Entity 생성·변경·조회의 최종 소유권과 공개 application 계약을 함께 고쳐야 한다.

### 2.2 조회가 모듈 경계를 우회

[SalesAnalyticsRepository](../../backend/src/main/java/com/greenhouse/backend/analytics/repository/SalesAnalyticsRepository.java)는 Sales뿐 아니라 Farm·Partner·Settlement·Work의 Q Entity를 직접 사용한다.
[AuctionSettlementRepository](../../backend/src/main/java/com/greenhouse/backend/settlement/repository/AuctionSettlementRepository.java)의 `findUnsettledSoldResultLines`는 Auction Entity를 직접 조회한다.
Native SQL 여부와 무관한 소유권 문제다.

분석 컬럼 하나를 추가할 때 타 모듈의 JPA 구조를 알아야 하는 구조를, 소유 모듈의 집계 application 값 계약으로 바꾼다.
Repository projection을 그대로 외부 계약으로 공개하지 않는다.

### 2.3 정책과 표현·실행의 변경 이유가 섞임

- [AuctionTrackingService](../../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java)에 조회 조립·결과 상태별 수량 계산·결과 행 생성·반환 허용 조건이 함께 있다.
- [ExpectedPaymentDateCalculator](../../backend/src/main/java/com/greenhouse/backend/settlement/application/ExpectedPaymentDateCalculator.java)는 규칙 계산과 Repository 조회를 같이 한다.
- [AnalyticsQueryService](../../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java)는 기간 계산, Object 배열 해석, 입금 상태 문자열 추론, 색상·링크·문구 조립을 함께 한다. `LocalDate.now()` 직접 호출도 남아 있다.
- [AuthController](../../backend/src/main/java/com/greenhouse/backend/auth/AuthController.java)와 `SessionCookieRefreshFilter`에 같은 세션 쿠키 작성 규칙이 나뉘어 있다.

계산·판정은 순수 domain policy, 조회는 소유 Repository, 유스케이스 순서는 application, HTTP/표현 변환은 adapter/assembler로 구분한다.

### 2.4 확장 registry와 분산된 조건문이 공존

[WorkEffectProcessor](../../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java)와 `StructureChangeStrategyRegistry`는 구현 목록을 등록하고 중복을 검사한다. 유지할 좋은 기반이다.
하지만 [WorkType](../../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkType.java)의 workflow·등록 허용·handler 선택과 여러 실행 service의 지원 코드 목록은 분리되어 있다.
새 시스템 작업을 추가할 때 여러 조건문을 함께 수정해야 한다.

반대로 유한한 상태 전이의 `switch`까지 없앨 필요는 없다. **확장 가능한 작업 정의와 닫힌 lifecycle 규칙을 구분**한다.

### 2.5 전역 규칙의 불일치와 누락

- 오류 JSON을 GlobalExceptionHandler, SecurityConfig, DemoProtectionFilter가 각각 작성한다.
- Partner 페이지는 잘못된 값을 보정하지만 Material·Auction은 오류로 거절한다. 통일 시 기존 HTTP 동작 변경 여부를 구분해야 한다.
- [DemoProtectionFilter](../../backend/src/main/java/com/greenhouse/backend/demo/DemoProtectionFilter.java)의 정산 설정 차단 경로는 `/api/partner-settlement-settings`인데 실제 [Controller](../../backend/src/main/java/com/greenhouse/backend/settlement/controller/PartnerSettlementSettingsController.java)는 `/api/business-partners/{partnerId}/settlement-settings`다. 경로 불일치는 확인했으며 실제 HTTP 차단 회귀를 추가할 필요가 있다.
- common의 `OrchidGroupUsageInspector`는 기술 공통 기능이라는 모듈 설명과 달리 난 묶음 전용 업무 계약이다.

포맷 통일과 동작 수정은 같은 커밋에 섞지 않는다. 오류 코드·차단 동작·페이지 정책·업무일 수정은 별도 `fix` 또는 계약 변경으로 추적한다.

### 2.6 누적 데이터 조회와 검증 공백

- `OrchidGroupCollectionService.getCollections`는 collection별로 membership와 그룹 상세를 조회하는 `toResponse`를 반복 호출한다.
- Work 보정 상세도 보정별 effect 조회를 반복한다.
- 정산·입금 이벤트 목록은 제한 없는 List 조회 경로가 있다. 운영 목록과 작은 기준 정보 옵션을 구분해 다룬다.
- 기본 테스트의 기존 disabled 49건, PostgreSQL E2E의 CI 미연결, Entity/Q 타입을 통한 경계 우회를 놓치는 architecture 검사도 정리 대상이다.

Mutation 내부의 중복·flush 위험·Work 멱등성 간극은 [상세 F1~F10](orchid-group-mutation-refactoring-detail.md)에 유지한다.
관찰한 코드 구조, 재현된 실패, 아직 검증할 위험을 구분하며 모든 클래스의 장애 가능성을 전수 확인한 것으로 보지 않는다.

## 3. 전체에 적용할 설계 기준

### 3.1 모듈 외부 계약

```text
Controller / 향후 입력 adapter
→ application 유스케이스 (transaction)
→ 자기 모듈의 domain + Repository
→ 다른 모듈의 공개 application 계약
```

- 각 모듈의 외부 진입점과 내부 구현을 명시한다. `application` 안에 있다는 이유만으로 모두 공개 API로 취급하지 않는다.
- 공개 계약은 필요한 식별자·값·불변 command/result로 한정한다. Entity, Q 타입, Repository projection, HTTP Request/Response DTO를 모듈 간 업무 계약으로 전달하지 않는다.
- 단순 조회·명령은 제공 모듈의 application API를 사용한다. 실제 의존 역전이 필요한 조회나 외부 연동만 호출 측 port로 둔다.
- 각 계약에 잠금 여부, 호출 트랜잭션 필요 여부, snapshot 시점, idempotency 범위, 건수 상한을 설명한다.
- 현재 `farm → work`, `sales → farm/auction/settlement/partner` 등 비순환 방향을 유지한다. port 추출을 이유로 역방향 의존을 추가하지 않는다.
- JPA 연관을 ID로 바꿔도 DB FK와 역사적 연결은 유지할 수 있다. DDL이 필요한 부분은 별도 migration으로 처리한다.
- 이식 중 필요한 Entity 예외는 정확한 호출자 목록으로 추적하고 축소한다. `domain.*` 전체 허용으로 검사를 우회하지 않는다.

패키지는 기존 `application|domain|repository|controller|dto`와 기능 경계를 유지한다.
공개 계약과 adapter의 하위 패키지는 실제 분리 작업에서 필요한 범위로만 만든다. 모듈 전체 재배치는 책임 분리와 별도 커밋으로 진행한다.

### 3.2 안정적으로 유지할 규칙과 확장 지점

| 확장 축 | 추가하는 곳 | 안정적으로 유지할 곳 | 이번 계획의 깊이 |
|---|---|---|---|
| 기존 효과 모델을 쓰는 새 작업 유형 | 작업 정의·handler·codec 또는 기존 handler에 연결한 정의 | 작업 lifecycle·효과 저장·진행률·Mutation 호출 규칙 | 현재 여러 유형을 기준으로 정리 |
| 새 대상 선택 방법 | Work 선택 계약을 구현하는 대상 resolver | 확정 snapshot·실행·이력 전파 | 기존 범위들을 기준으로 정리 |
| 새 배치 규격/수용 규칙 | Farm 배치 규격·순수 정책 | 구역 소유권·중복 배치·잠금 | 기존 판/행잉/사용자 규격 보존 |
| 판매 입력 채널 | Sales application command로 변환하는 입력 adapter | 예약→출고→취소 및 금액 규칙 | 내부 command 경계 확보, 채널 구현은 후속 |
| 경매장별 결과 파일/API | Auction의 결과 입력 adapter/normalizer | lot 잠금·수량·상태·시도 이력 | 내부 결과 command 정리, 외부 연결은 후속 |
| 정산·입금일 계산의 변형 | Settlement 계산 policy/설정 해석 | 원장·입금 사실·잔액 불변식 | 현재 일수 모드·수동 입금 기준 분리 |
| 분석 지표·대시보드 항목 | 소유 모듈의 집계 계약 + 조립기 | 원본 업무 쓰기 경로 | 현재 지표를 소유 모듈로 이동 |
| 감사 대상 추가 | 소유 모듈의 명시적 snapshot factory | 감사 저장·요청 맥락·실패 시 rollback | 기존 AuditRecorder 유지·보강 |
| 인증 저장소 변경 | Spring Security의 기존 provider/UserDetailsService 경계 | 업무 서비스와 세션 계약 | 현재 세션 책임 정리, 신규 인증 미구현 |
| 출력 문서 종류 | 문서 데이터 provider와 출력 모델 | 전표·정산 업무 로직 | 판매 출력 데이터 분리, 신규 출력은 후속 |

사진·QR·IoT·외부 주문도 이후 독립 기능에서 소유 모듈의 command/query를 사용한다.
미구현 기능을 위한 빈 interface, 임시 endpoint, 동작 없는 Bean을 지금 생성하지 않는다.

### 3.3 OCP 적용 범위의 제한

- 단계별로 운영 코드·테스트·문서의 증감을 따로 확인한다. 새 클래스를 추가할 때는 대체·삭제한 기존 책임과 호출 단계를 함께 검토한다. 파일 분리나 의존 수 감소만으로 완료 판단하지 않는다.
- 한 필드 wrapper, 단순 전달 service, 구·신 API 병존을 남기지 않는다. 기존 Entity의 정책 메서드나 application service의 private method로 충분하면 그 안에서 정리한다.
- 같은 변형이 실제로 둘 이상 존재하거나 확정된 기능이 기존 코드를 여러 곳 수정하게 할 때 strategy/port를 도입한다.
- 상속 기반 BaseService, 범용 CRUD engine, 전역 command bus, 임의 JSON rule engine은 도입하지 않는다.
- DIRECT와 AUCTION은 업무 유형이다. 외부 판매 채널을 무조건 새로운 SalesType으로 추가하지 않는다.
- 작업 타입 등록은 수량 불변식을 우회하는 권한이 아니다. 새로운 물리 효과가 필요하면 typed Mutation command와 검증을 명시적으로 확장한다.
- registry에 등록되지 않은 작업·중복 key는 명시적으로 실패한다. 기본 handler로 조용히 처리하지 않는다.
- 새 API/enum 추가에 따른 OpenAPI·생성 타입·권한 선언 변경은 정상적인 계약 확장이다.

### 3.4 트랜잭션·조회·오류·스타일

- public application 유스케이스가 원자 경계를 소유한다. 필수 후속 작업은 동기 호출로 같은 트랜잭션에 포함한다.
- Entity마다 상태를 나중에 다시 읽어 과거를 복원하지 않고, 생성·출고·완료 시점의 snapshot을 함께 저장한다.
- 전체 유스케이스의 잠금 순서를 고정한다. Reader를 호출한 뒤 이미 끝난 트랜잭션의 잠금을 계속 유효하다고 가정하지 않는다.
- 조회용 정규화 값과 결과를 typed record로 표현한다. 검색마다 늘어나는 positional 인자와 `Object[]`를 줄인다.
- root page→ID 기준 일괄 연관 조회를 유지한다. 소유권 정리 후 N+1이 늘어나면 경계 정리가 완료된 것으로 보지 않는다.
- 업무 정책의 오류 의미와 HTTP 매핑을 분리하고, 일반 MVC·Security filter·Demo filter에서 동일 envelope를 작성한다.
- 통화·수량 타입 확대, 에러 코드 변경, 조회 상한 도입은 외부 계약 영향을 별도 검토한다.
- 단순 생성자 주입은 Lombok, 부수 효과 있는 반복은 명시적 루프, 순수 변환은 stream을 우선한다.
- format/import/중괄호 기준을 도구로 고정하되 module별 포맷 커밋과 의미 변경 커밋을 분리한다.
- 업무일은 Clock·TimeConfig 기준. 일반 응답 mapper와 저장된 fingerprint의 canonical mapper는 구분한다.

## 4. 모듈별 상세 계획

### common — 기술 공통 기능과 업무 계약 분리

근거: `GlobalExceptionHandler`, `RequestActorProvider`, `OrchidGroupUsageInspector`, `TimeConfig`, `PageResponse`.

1. HTTP 오류 작성과 의미 있는 업무 오류 계약을 분리. Security/Demo도 공통 serializer를 사용하되 상태·코드를 보존.
2. 페이지 입력값과 검색 정규화의 최소 공통 값을 정의. Partner의 보정과 Auction의 거절을 조용히 바꾸지 않고 계약 차이부터 기록.
3. 실행 요청의 worker와 인증된 감사 actor를 구분. Demo가 요청 worker를 대체하는 현재 정책 유지.
4. 난 묶음 usage 계약을 Farm이 소유하도록 정리하되 Work→Farm 순환을 만들지 않음. Farm adapter가 Work application 조회값을 변환하고, Sales는 Farm의 usage port를 구현하는 방식으로 이전.
5. `LocalDate.now()` 직접 업무 사용을 Clock으로 교체하고 이를 architecture 검사로 보호.

검증: MVC/filter 오류 envelope 호환, UTC/서울 날짜 경계, 기존 페이지 오류 동작, Demo worker와 audit actor 구분, 모듈 비순환.

### audit — 기존 저장 확장 경계 유지, 입력 책임 축소

근거: `AuditRecorder`, `JpaAuditRecorder`, `AuditEventWriter`, `AuditRequestContext`, 모듈별 `*AuditSupport`.

1. AuditRecorder interface와 동기 저장을 유지. 새 모듈 감사 추가가 JpaAuditRecorder 수정을 요구하지 않게 함.
2. 긴 위치·대상·실행 맥락 인자를 의미 있는 불변 값으로 묶음. 기존 저장 JSON·이벤트 의미는 유지.
3. servlet/security context 해석을 adapter로 분리해 CLI에서도 명시적 맥락으로 감사를 기록할 수 있게 함.
4. 민감 필드 제외는 각 모듈의 명시적 snapshot에서 수행. Entity 전체를 reflection으로 직렬화하는 자동 감사는 만들지 않음.
5. no-op 감사와 변경 필드 계산의 공통 절차만 통일. Mutation ledger와 업무 audit의 책임을 합치지 않음.

검증: 기존 감사 JSON·변경 필드, 민감 값 미저장, 감사 실패 시 원본 변경 rollback, HTTP/CLI context, 새 감사 입력 fixture.

### auth — 세션 처리·쿠키·인증 조립 분리

근거: `AuthController`, `AuthService`, `SecurityConfig`, `SessionCookieRefreshFilter`.

1. 로그인/로그아웃 HTTP 경계와 인증·세션 lifecycle 처리를 분리. AuthService가 단순 응답 mapper만 맡는 현재 이름/책임 불일치 정리.
2. 쿠키 생성·갱신·만료를 하나의 writer로 통일해 속성 변경이 여러 문자열 수정으로 퍼지지 않게 함.
3. 사용자 조회는 기존 Spring Security 확장점을 유지. 향후 DB 계정 추가가 업무 모듈 변경을 요구하지 않도록 함.
4. 운영·테스트 비활성·Demo 구성을 명시적으로 조립. 기본 권한과 세션 방식은 유지.
5. 신규 endpoint의 접근 정책을 테스트에 등록하는 절차를 마련. 범용 동적 권한 엔진은 도입하지 않음.

검증: 로그인 실패/성공, me, 로그아웃, 세션 갱신/만료, ADMIN/WORKER 권한, 쿠키 중복·속성, 기존 AuthIntegrationTests.

### demo — 제한 정책을 filter의 전달 처리에서 분리

근거: `DemoProtectionFilter`, `DemoAuthenticationFilter`, `DemoProperties`, 정산 설정 Controller 경로.

1. 금지 동작 판정, 요청 크기, rate limit 계산을 독립적으로 검사할 수 있게 분리.
2. 정산 설정 경로 불일치의 HTTP 회귀를 추가한 뒤 별도 `fix`로 정리.
3. filter는 정책 결과를 HTTP 응답으로 변환하고 체인 진행을 담당. Clock은 조립 지점에서 주입.
4. 신규 endpoint 추가 시 Demo에서 허용/차단 판단 누락을 찾는 정책 테스트 유지.
5. 현재 단일 프로세스 제한 의미를 보존. 공유 rate-limit 저장소나 분산 인증은 실제 배포 요구 발생 시 결정.

검증: auth/설정 차단, 일반 읽기·쓰기 허용, 요청 한도·날짜 경계·Retry-After, 모드별 조립, Demo actor 적용.

### partner — 여러 업무 모듈이 의존하는 기준 계약 정리

근거: `BusinessPartnerReader`, `BusinessPartnerService`, `BusinessPartnerAuditSupport`.

1. 이름·유형·활성 여부를 제공하는 application 값과 잠금이 필요한 command 계약을 분리.
2. 외부 Entity 반환을 제거하고 Sales/Auction/Settlement의 사용처를 단계별 전환.
3. 거래처 기준 정보와 정산 설정을 계속 각각 Partner/Settlement가 소유. 두 모듈을 합치지 않음.
4. 운영 페이지와 전체 활성 선택지 API의 사용 목적·상한 정책을 구분. 페이지 입력 정책 변경은 계약 작업으로 분리.
5. snapshot/redaction은 현재 명시적 방식을 유지하고 중복 문자열 정규화만 필요한 만큼 정리.

검증: 비활성/잘못된 유형 거절, 정렬·검색, 여러 거래처 잠금 순서, 개인정보 감사 회귀, 참조 모듈 계약 테스트.

### farm — Mutation 이외 기능까지 전체 정리

1. **structure/placement:** `BedPlacementProfileService`의 규격 정규화·중복·수용량 비교를 순수 domain policy로 이동. enum ordinal에 숨은 강도 순서를 명시적 의미로 바꿈. 기본·CUSTOM 규격 의미 유지.
2. **status/query:** `FarmQueryService`, `FarmStatusService`의 전체/viewport/범위별 조회 모델을 구분. 맵의 경량 조회와 일괄 로딩을 유지하고 새로운 조회가 전체 Entity graph에 기대지 않게 함.
3. **orchid/mutation:** 단일 상태 writer·ledger·배치검증은 하위 상세의 순서로 분리. Farm 밖에서 물리 상태 변경 불가 유지.
4. **collection:** 목록 root와 membership/그룹 상세를 일괄 조회. 보관·구성원 추가/해제 정책과 이력 보존은 aggregate가 보호. 운영 목록의 상한은 UI 호환을 포함해 결정.
5. **inbound:** 계획/입고/포트 실행 경계를 유지하고 command·결과 factory를 공유. 장차 CSV 입고도 같은 유스케이스를 호출하도록 HTTP DTO 의존을 끊음.
6. **variety/material:** CRUD는 단순하게 유지. 품종 정규화·중복과 code 생성 책임을 분리. Material의 마지막 ID+1 기반 code 생성은 병렬 생성 충돌 여부를 테스트한 뒤 보강.
7. **transformation:** 작업 입력→실행 계획→Mutation→결과·계보 조립 분리. 단일/복수 원본 호환과 속성 상속 유지.

검증: 농장 viewport query 상한, collection 수 증가 시 고정 batch 조회, 품종/자재 병렬 생성, 배치 규격 경계, 입고 취소·재실행, 수량·revision·계보.

### work — 유형 추가가 여러 실행기 수정으로 퍼지지 않도록 정리

근거: `WorkType`, `WorkEffectProcessor`, `WorkEffectStore`, `WorkTargetSelection`, 각 실행 service와 `WorkOperationDetailService`.

1. 시스템 작업별 handler·workflow·대상 종류·등록 모드를 정의하는 불변 descriptor를 모음. capability와 실행 handler 선택이 같은 정의를 사용하도록 함.
2. active/system 설정에 따른 허용 여부와 작업·대상의 상태 전이는 기존 domain policy가 유지. Entity가 Spring registry를 조회하지 않도록 application이 정의를 제공.
3. 기존 handler 등록·중복 검사를 재사용하고 definition↔handler↔codec 누락도 시작/계약 테스트에서 검사.
4. 계획·진행·즉시 실행·구조 변경·포트·보정의 최상위 트랜잭션은 분리 유지. Controller는 목적별 분리가 유용한 범위만 나누며 URL은 유지.
5. 대상 해석은 snapshot 생성 이전 경계로 제한. 새로운 선택 방식이 기존 실행 이력을 재해석하게 하지 않음.
6. handler에 전달하는 Work Entity를 필요한 실행 맥락·ID 값으로 줄임. Farm 구현체가 Work aggregate를 직접 변경하지 못하게 함.
7. 효과 결과의 typed codec, 상세 assembler와 보정 effect 일괄 조회 적용. 기존 JSON은 호환 reader로 유지.
8. 요청·효과·Mutation 멱등성 간극은 B-16의 별도 계약 보강으로 처리.

검증: 현재 모든 작업 유형의 definition/capability 동일, 테스트 전용 새 정의·handler 등록 시 processor 수정 불필요, 타입 미등록 실패, 부분 실행·보정·이력, query count.

### sales — 판매 업무를 소유하고 외부 상태는 application 계약으로 조율

근거: `SalesSlipCreationService`, `SalesSlipStatusService`, `SalesSlipActionResolver`, `SalesPaymentService`, `AuctionShipmentMaterializer`.

1. 직접/경매 생성에서 공유하는 입력·품목·예약 순서와 유형별 차이를 분리. 현재 두 생성 구현을 명시적 타입 등록 계약으로 연결.
2. capability 계산과 쓰기 검증이 동일한 판정 정책을 사용하도록 정리. 조회용 판정에 쓰기 잠금이나 변경 부수 효과를 넣지 않음.
3. Sales는 Auction 생성 command를 전달하고 shipment/lot 식별자를 받음. Auction Entity 생성·저장은 Auction으로 이동.
4. 생성/출고 snapshot은 Farm의 값 계약으로 받아 Sales가 소유한 역사적 사실로 저장. 조회 시 현재 그룹으로 과거를 재구성하지 않음.
5. 재고 이동·예약·복구 호출과 Sales movement 기록을 명시적인 유스케이스 순서로 유지. Engine/Legacy 변환 중복 제거.
6. 입금은 Settlement에 금액·대상·멱등키·날짜 값을 전달하고 결과를 받음. 타 모듈 `SettlementAuditSupport`를 직접 조립하는 의존도 공개 계약으로 축소.
7. 온라인 주문/CSV 입력은 향후 별도 adapter에서 Sales command로 변환. 새 채널마다 기존 재고 처리기를 복제하지 않음.

검증: 유형별 생성/수정/출고/취소, action↔쓰기 허용 일치, 생성·출고 snapshot, 입금·잔액, 전표 번호 동시성, 기존 상세 query 상한.

### auction — 결과 입력 형식과 lot 도메인 판단 분리

근거: `AuctionTrackingService`, `AuctionShipmentCreator`, `AuctionShipmentLifecycleService`, `AuctionShipmentLot`, `AuctionAttempt`.

1. 외부 Entity를 저장하는 API를 shipment 생성/취소 command·result로 변경. 결과 item↔lot 연결은 명시적 식별자로 반환해 배열 index 의존을 줄임.
2. lot 조회 조립과 결과 기록 유스케이스 분리. 검색의 긴 인자 목록은 typed criteria로 변경.
3. SOLD/PARTIALLY_SOLD/FAILED/RETURN_INFERRED의 계산을 순수 결과 policy로 모으고 aggregate가 수량·전이·이력을 최종 보호.
4. 결과 입력은 경매장 형식과 무관한 내부 command로 정규화. 수동 HTTP 입력도 이 경계를 사용.
5. 금액 계산의 정수 곱셈 범위, 중복 경매일/차수, 반환 가능 수량을 경계값 테스트로 고정. 타입 변경 필요 시 API/DB 변경과 분리하지 않음.
6. 정산용 결과와 상태 판정은 값 조회 API로 제공. 새 importer가 Auction Repository를 직접 사용하지 못하게 함.

검증: 현재 결과 상태 전부, 부분낙찰·재경매·반환·취소 이력, 동일 lot 병렬 결과 입력, shipment 생성 rollback, 조회 4회 상한 유지.

### settlement — 정산 계산·입금 원장·외부 조회 책임 분리

근거: `AuctionSettlementService`, `AuctionSettlementRepository`, `ExpectedPaymentDateCalculator`, `PaymentLedgerService`, `PartnerBalanceService`.

1. Auction 결과 값 계약과 Settlement가 소유한 '이미 연결된 결과 ID' 조회를 조합. 다른 모듈 Entity를 직접 읽는 JPQL 제거.
2. 미정산 후보는 bounded batch로 처리하고 각 batch의 연결 ID를 조회. 전체 결과 ID를 한꺼번에 모으는 방식으로 대체하지 않음.
3. 정산선 생성 입력은 결과 시점의 ID·수량·단가·금액 값. 기존 연결과 snapshot은 유지.
4. 예상 입금일은 설정 로딩과 순수 계산으로 분리. 현재 주말 제외 규칙을 보존하며 공휴일·외부 달력은 승인된 기능에서 추가.
5. 금액·잔액·입금 상태와 재구축 허용 범위를 domain policy에 모음. 입금된 정산 재계산의 현재 정책부터 테스트로 확인.
6. 수동 입금 원장의 공통 기능은 재사용하고 Sales/Settlement 각 대상의 상태 변경은 해당 소유 모듈에 유지. 범용 PaymentTarget Entity 추상화는 만들지 않음.
7. `getOrCreate` 설정과 잔액 GET의 초기 생성/잠금 부수 효과를 명시. 문서상 현재 동작을 단순 `readOnly` 변경으로 깨뜨리지 않음.
8. 무제한 정산·입금 목록은 page API와 기존 클라이언트 전환을 함께 계획. startup rebuild는 trigger와 유스케이스 분리, 중복 기동·입금 동시 실행 검증.

검증: 부분/완납/초과입금·중복키·동시입금, 정산 재실행·재구축과 입금 경합, 잔액 유실 없음, 신규/기존 설정 초기화, page 안정 정렬.

### analytics — 원본 모듈 내부 구조 대신 집계 계약에 의존

근거: `SalesAnalyticsRepository`, `AnalyticsQueryService`.

1. Sales/Work/Farm/Partner/Settlement별 집계와 조회값을 소유 모듈의 application API로 이동.
2. `Object[]` 결과를 의미 있는 typed row로 바꾸고 외부에는 application 값으로 변환해서 제공.
3. 소유 모듈의 완료/판매가능/입금 상태 정의를 사용. 문자열 포함 검사로 업무 상태를 새로 추론하지 않음.
4. 기간·비교 월 계산을 순수 값으로 분리하고 기본 날짜를 Clock·TimeConfig 기준으로 수정.
5. 표시 문구·색상·링크는 먼저 assembler로 분리. 프론트로 이전할 때는 API 계약 변경으로 별도 작업.
6. 모듈별 독립 page를 합쳐 전체 ranking이라고 반환하지 않음. 전체 집계·정렬·필터 의미와 query count를 유지하는 일괄 계약 설계.
7. 성능상 별도 보고용 projection이 꼭 필요하면 측정 후 ADR로 결정. 경계를 지키기 위해 대량 데이터를 application에서 join하는 방식도 피함.

검증: 현재 지표 값, 0건/부분입금/월말/서울 날짜 경계, global ranking·누락 없음, 규모별 쿼리·응답 크기, 원본 쓰기 모듈에 분석 표현 의존 없음.

### dashboard — 작은 요약 조립 경계 유지

근거: `DashboardQueryService`, Farm의 `FarmMetricsReader`.

1. FarmMetricsReader의 값 반환 방식 유지. 새 지표도 Work/Sales 등의 소유 모듈 요약 계약으로 받음.
2. 지표별 provider가 필요한 시점은 둘 이상의 독립 지표 집합을 조합할 때로 제한. 현재 28줄 서비스를 framework로 바꾸지 않음.
3. 현재 고정 `0`, `null`로 채우는 필드의 구현 상태를 명시. 실제 값을 채우는 것은 기능 추가로 별도 추적.
4. UI 표현과 요약의 업무 의미를 구분하고 목록 Entity 전체를 가져와 count하지 않음.

검증: 현재 응답 호환, 소유 모듈 summary 계약, query 상한, 미구현 값의 의미. 새 지표가 농장 쓰기 service 변경을 요구하지 않는지 확인.

### print — 관리 화면 응답과 출력 데이터 결합 축소

근거: `PrintQueryService`, `PrintController`.

1. Sales 관리용 Response를 그대로 사용하는 내부 의존을 출력 목적의 application 값 계약으로 변경.
2. 현재 HTTP 응답은 adapter가 기존 형태로 조립해 호환 유지. 단순 변환 작업으로 출력 API 필드를 바꾸지 않음.
3. 추후 정산표·작업표는 문서별 provider/model로 추가. 문서가 한 종류인 동안 범용 renderer를 선구현하지 않음.
4. 금액·상태를 Print에서 다시 계산하지 않고 원본 도메인의 확정 값 사용.

검증: 출력 데이터 golden fixture, 판매 snapshot·금액 일치, 관리 화면 전용 capability 조회 의존 축소. 출력 UI/계약 변경 시 A5 미리보기와 frontend 검증 포함.

## 5. 실행 순서와 PR 단위

아래 번호는 전체 계획의 작업 단위다. 큰 작업은 같은 번호 아래 계약→소비자 전환→기존 경로 제거 PR로 분리한다.
각 PR은 정상 동작과 테스트를 유지하며 완료된 상태로 합칠 수 있어야 한다.

| 작업 | 내용·산출물 | 선행 조건 | 완료 기준 |
|---|---|---|---|
| B-01 | 전체 공개 계약·Entity/Q/JPQL 의존·트랜잭션·API 호환 목록. 신규 우회 방지 검사 | 없음 | 13개 모듈 예외가 구체적이고 증가하지 않음 |
| B-02 | module별 fixture, disabled 테스트 복구/대체, PostgreSQL CI, formatter 기준 | B-01 | 실행/skip 수 확인, 포맷과 의미 변경 분리 |
| B-03 | common의 오류·시간·실행자·페이지 값 계약 정리 | B-01~02 | HTTP/모드/시간 호환 검사 |
| B-04 | Partner 공개 값 계약과 소비자 전환 | B-01~02 | 외부 Partner Entity 사용 축소·잠금 유지 |
| B-05 | Auction 생성/결과/조회 계약과 결과 policy | B-04 | Sales/Settlement가 Auction Entity 생성·수정하지 않음 |
| B-06 | Settlement 원장·계산·결과 연결·목록 정리 | B-04~05 | 금액/동시성/재실행/조회 검증 |
| B-07 | Sales action 정책·유형 생성·입금·출하·재고 계약 | B-04~06, Farm 값 계약 | 예약/출고/입금 흐름 및 query 상한 유지 |
| B-08 | Farm 전 기능의 정책·조회·command 경계 | B-01~03 | 배치·목록·기준 정보·입고·계보 검증 |
| B-09 | Work 정의·handler/codec·대상·상세·capability | B-01~03, B-08 관련 계약 | 새 유형 등록 검증 + 기존 작업 전체 회귀 |
| B-10 | Mutation 엔진·배치·대사·전환 책임 분리 | B-02, B-08~09 관련 계약 | 실제 PostgreSQL + writer inventory |
| B-11 | Analytics 소유 모듈 집계 API 전환 | B-04~09의 관련 조회 계약 | 지표·ranking 동일, Q/Entity 우회 제거 |
| B-12 | Auth/Demo 세션·쿠키·제한 정책·오류 writer | B-03 | 3개 실행 모드 및 실제 API 접근 회귀 |
| B-13 | Audit 입력/맥락·snapshot 계약 정리 | B-03 | 각 모듈 감사 호환·rollback |
| B-14 | Dashboard/Print 값 계약·조립 책임 | 관련 소유 모듈 조회 계약 | 응답/출력 호환·조회 상한 |
| B-15 | 전체 경계 검사 강화·문서·빌드/설정 검증·확장 시나리오 | B-03~14 | 13개 모듈 전체 완료 기준 충족 |
| B-16 | Work 요청/effect 멱등성 목표의 미구현 부분 보강 | B-09, API/DB 계약 설계 | 같은 내용 replay·다른 내용 충돌·동시성 |
| B-17 | 운영 전환 안정화 후 Legacy/전환 코드 제거 | 기존 운영 gate 전부 | 단일 writer, 이력 보존, 복구 절차 |

권장 진행:

```text
공통 기준·테스트 B-01~03
→ 기반 계약 Partner B-04, Farm B-08
→ Auction B-05, Work B-09, Auth/Demo B-12, Audit B-13
→ Settlement B-06, Mutation B-10
→ Sales B-07
→ Analytics B-11, Dashboard/Print B-14
→ 전체 검증 B-15
```

이는 의존 순서를 뜻한다. 독립적인 책임 분리까지 모든 모듈의 작업 종료를 기다릴 필요는 없다.
B-16은 구조 정리와 분리한 동작/DB 계약 변경이며, ADR-001 완료 판단 전에 결과를 명시한다.
B-17의 운영 gate가 미충족이어도 나머지 백엔드 리팩터링을 멈추지 않는다. 코드만 보고 운영 전환을 완료로 판단하지 않는다.

### 기존 Mutation 상세와 중복 방지

| 하위 상세 작업 | 전체 계획에서 수행하는 위치 |
|---|---|
| PR-01 테스트, PR-02 포맷 | B-01~02에서 전체 모듈과 함께 수행 |
| PR-03~05 command/엔진/배치 | B-10 |
| PR-06 Work·Inbound | B-08~10의 해당 경계 |
| PR-07 Work 상세 | B-09 |
| PR-08 이관·대사 | B-10 |
| PR-09 Sales–Farm | B-07, Legacy 제거가 필요한 부분은 B-17 |
| 별도 Work 멱등성, Legacy 제거 | B-16, B-17 |

## 6. 확장성 검증 방법

코드가 짧아졌는지만으로 완료 처리하지 않는다. 정의한 확장 축에 테스트용 구현을 붙여 기존 중심 코드가 그대로 동작하는지 확인한다.
미구현 제품 기능을 대신 만들어 검증하지 않는다.

| 검증 실험 | 추가할 것 | 변경되지 않아야 할 부분 |
|---|---|---|
| 기존 효과 모델을 쓰는 작업 유형 | 테스트 정의·handler·codec | WorkEffectProcessor, 상태 전이, 효과 저장 |
| 같은 결과 command를 만드는 다른 입력 형식 | 테스트용 converter/adapter | Auction 결과 policy·lot Entity의 공통 규칙 |
| 현재 입금일 계산의 다른 구현 | 테스트 policy와 설정 | PaymentLedger·잔액·입금 멱등성 |
| 새로운 감사 대상 | 대상 snapshot/event fixture | JpaAuditRecorder·Audit Repository |
| 다른 요약 데이터 공급자 | 테스트 summary provider | Farm/Sales 쓰기 유스케이스 |
| 다른 계정 조회 구현 | 테스트 UserDetailsService/provider | Sales/Work 등 업무 모듈 |
| 다른 출력 데이터 조립기 | 테스트 document provider | Sales 금액·상태·재고 처리 |

공통 불변식이나 공개 계약 자체가 달라지는 요구는 위 실험의 '기존 코드 무수정' 대상에서 제외한다.
외부 API·DB contract 확장은 생성물·migration·권한 선언을 함께 수정하는 정상 변경이다.

## 7. 검증·호환·완료 기준

### 각 단계의 기본 검증

- 단순 정규화·계산·정책은 순수 단위 테스트. 메서드 위임을 그대로 따라 하는 mock 테스트는 늘리지 않음.
- 모듈 API는 실제 입력/결과·잠금·실패 원자성을 통합 테스트로 확인.
- 수량·금액·입금·재구축·migration·concurrency는 PostgreSQL 검증 필수.
- collection/Work 상세/분석 등 조회는 1/10/50 또는 실제 업무 규모에서 SELECT 증가 양상 확인.
- Security·Demo는 실제 Controller 경로로 401/403/차단/허용 확인. policy 단위 테스트만으로 완료하지 않음.
- 새 architecture 검사에 기존 위반을 광범위하게 면제하지 않음. 허용 예외를 기능 단위로 줄여 최종 제거.

```bash
cd backend
./gradlew test --no-daemon
./gradlew workE2eTest --no-daemon
./gradlew workBenchmark -PworkBenchmarkEnforce=true --no-daemon
./gradlew bootJar --no-daemon
```

benchmark는 조회 변경 전후의 필요한 시점에 실행한다. 최종 검증에서는 전체 기본·PostgreSQL 테스트와 패키징을 실행한다.
현재 PostgreSQL 테스트 task 이름은 Work이지만 내용은 여러 도메인을 포함한다. 추후 이름/태그를 정리하면 CI·문서에서 기존 호출 호환을 함께 처리한다.

### API·DB 변경 처리

1. 내부 리팩터링은 HTTP 경로·응답·저장 JSON·fingerprint·오류 의미를 보존.
2. 정책 보강·페이지 API·오류 코드·타입 변경은 Controller/DTO/테스트를 먼저 수정.
3. `python3 scripts/generate_openapi.py`로 명세와 slice 생성.
4. `frontend`에서 `npm run api:types`, `npm run check` 실행. API 타입 생성물 수동 수정 금지.
5. API별 조회 방식 변경은 실제 화면 소비자 전환을 포함. 기존 전체 목록을 조용히 잘라 반환하지 않음.
6. 배포된 Flyway 파일 수정 금지. 필요한 경우 nullable 추가→검증 가능한 backfill→제약 적용 순서로 신규 migration 작성.
7. 인쇄·인증·capability처럼 사용자 흐름에 영향이 있으면 해당 화면의 integration/E2E도 확인.

### 전체 완료 기준

- 13개 모듈마다 유지/변경 판단과 검증 근거가 있음.
- Entity·Repository·Q 타입·직접 JPQL을 통한 타 모듈 내부 접근이 제거되거나, 운영 전환 때문에 남은 정확한 예외와 제거 조건이 있음.
- 외부 application 계약과 내부 구현이 구분되고 신규 우회를 architecture test가 탐지함.
- 업무 규칙은 소유 도메인에 있고 capability·실행 검증이 같은 판정을 사용함.
- 새 기능 추가의 주요 변화가 해당 정의·policy·adapter에 제한되는 확장 실험 통과.
- 현재 API·스냅샷·금액·수량·시간·감사 의미 보존 또는 별도 계약 변경으로 명시.
- 실제 PostgreSQL 실행 결과와 skip 건수 확인. 테스트 task 성공만으로 migration 검증 완료 처리하지 않음.
- 백엔드 구조 정리 완료와 운영 Mutation cutover 완료를 각각 보고함.

## 8. 문서와 범위 관리

현재 기준은 [아키텍처](../04-architecture.md), [도메인 모델](../02-domain-model.md), [기능 요약](../03-feature-summary.md), [API 가이드](../06-api-guide.md), 각 기능 문서와 ADR이다.
[로드맵](../08-roadmap.md)의 향후 기능은 확장 축을 판단하는 참고이며 구현 승인으로 간주하지 않는다.

실제 변경 시 갱신:

- 모듈 경계·공개 계약·조회·transaction 규칙 → `04-architecture.md`.
- 업무 정책·기능 범위 → `02-domain-model.md`, 해당 `features/*`, 필요한 `api/DOMAIN_RULES.md`.
- API → `06-api-guide.md`의 생성 절차와 도메인 slice.
- CI·설정·migration·운영 도구 → `07-deployment.md`, Demo 변경은 `demo-operations.md`.
- Mutation 전환 코드 이동/제거 → 전환 inventory와 writer architecture test.
- 완료된 개선 계획 → 현행 정책 문서 반영 후 archive/plans로 이동.

MSA 전환, 범용 workflow/rule engine, 이벤트 버스·분산 transaction, 미구현 은행/경매/외부 채널 연동은 이번 리팩터링 구현 범위에 넣지 않는다.
확장 가능한 경계를 만드는 것과 미래 기능을 미리 만드는 것을 구분한다.

## 9. 계획 수립 당시 검토 및 검증 기록

- 13개 모듈의 소유권·주요 application·domain·Repository·공개 호출부와 설정/테스트 구조를 확인했다. 모든 파일의 모든 분기를 전수 검증한 결과는 아니다.
- 같은 기준 코드에서 직전 검토 시 기본 테스트 실행 190건 통과, 기존 disabled 49건. PostgreSQL E2E 22건 통과, skip 0건.
- 이번 범위 확장에서는 문서만 수정했으므로 위 테스트 결과를 기준선으로 재사용했다. 추가로 발견한 후보 문제를 테스트로 재현·수정한 것은 아니다.
- 백엔드 코드·API·DB 변경 및 운영 환경 접근 없음. 문서 링크와 변경 형식 검증 수행.

## 10. 실행 기록 — 1차 기반 정리

2026-09-05, `feature/backend-refactoring`. 아래는 이번에 적용한 범위이며 B-01~17 전체 완료를 뜻하지 않는다.

| 작업 | 적용한 변경 | 남은 범위 |
|---|---|---|
| B-01 | 13개 모듈의 컴파일 의존 검사, Entity/Q/HTTP DTO 137쌍·직접 JPQL 2곳의 정확한 이식 목록, 신규 직접 시간 조회 방지 | 공개 유스케이스별 잠금·snapshot·트랜잭션 계약 상세화, 목록의 실제 의존 제거 |
| B-02 | 배치·배치 규격 테스트 6건의 독립 fixture 복구, PostgreSQL CI job, EditorConfig | 기존 disabled 43건, 전체 formatter 도입·모듈별 적용 |
| B-03 | 공통 오류 직렬화, Farm 소유 usage port와 Work adapter, 요청 작업자 의미 회귀, 분석 업무일 Clock 사용 | 페이지 정책 차이, 남은 5개 클래스의 직접 시간 조회, 공통 규칙의 나머지 이식 |
| B-08 | 사용자 그룹 목록·소속 목록의 member/그룹 일괄 로딩 | Farm 나머지 하위 기능·정책·command 경계 |
| B-11 | 분석 기간을 순수 값으로 분리, 서울 업무일 기본값 적용 | 타 모듈 Q 타입 제거와 집계 계약 이식 |
| B-12 | Controller–세션 처리 분리, 쿠키 writer, 교체 가능한 계정 조회, Demo 경로 정책·시간 주입·rate limit 분리 | 이후 신규 API의 접근 정책 회귀 유지 |

동작을 보존하는 책임 분리와 구분한 수정:

- `fix`: 실제 정산 설정 PUT 경로가 데모 filter를 통과하는 문제를 HTTP 테스트로 재현하고 차단. GET 허용 유지.
- `fix`: 분석 기간의 기본 종료일을 서버 기본 날짜에서 문서상 농장 업무일로 일치시킴. UTC 15:00의 서울 날짜 경계 검증.
- 조회 개선: 사용자 그룹 10개/50개에서 SQL 21회/101회를 재현. 개선 후 1·10·50개 모두 3회 이내이며 소속 그룹 조회는 5회 이내.

확장 검증은 별도 `UserDetailsService`를 등록한 실제 로그인 요청으로 수행했다.
기본 계정 설정의 조건을 일반 Configuration에만 두면 등록 순서에 따라 두 구현이 남는 문제를 확인해,
application Bean 이후 평가되는 기본 계정 자동 구성으로 분리했다. 로그인·세션 유스케이스 변경 없이 대체 구현이 동작한다.

검증:

- `./gradlew test --offline --no-daemon`: 275건 중 232건 통과, 기존 disabled 43건, 실패 0건.
- `./gradlew workE2eTest --offline --no-daemon`: 실제 PostgreSQL 22건 통과, skip/실패 0건.
- `python3 scripts/generate_openapi.py --url http://127.0.0.1:18080/api-docs`: 임시 H2 서버에서 131 operations·110 paths·217 schemas 재생성, 기존 전체 명세와 slice 차이 없음. 임시 서버 종료.
- usage blocker 순서와 Clock 생성 제한 보강 후 관련 6건 추가 회귀 통과. `git diff --check` 통과.
- 프론트 코드·생성 schema 변경 없음. 프론트 검증과 벤치마크는 이번 단계에서 실행하지 않음.
- DB migration과 운영 Mutation cutover 변경 없음. B-04~07·09~10·13~17의 본 작업은 남아 있다.

## 11. 실행 기록 — 2차 기반 계약·배치 정책

2026-09-05, `feature/backend-refactoring`. 1차 변경을 목적별 9개 커밋으로 나눈 뒤 진행했다.
이번 범위는 B-04와 B-08의 일부이며 두 작업 전체의 완료를 뜻하지 않는다.

| 작업 | 적용한 변경 | 남은 범위 |
|---|---|---|
| B-04 | 거래처 application 조회값과 호출 트랜잭션에 참여하는 잠금 API 분리. Settlement 정산 설정·잔액의 거래처 Entity 연관을 ID로 전환. 예상 입금일 계산도 거래처 ID 사용 | Sales·Auction·입금 이벤트의 Entity 연관, 운영 목록·선택지 계약, 남은 Entity 반환 제거 |
| B-08 | 배치 프로필 정규화·중복·강도별 수용량 검사를 순수 domain policy로 이동. 명시적 모드 강도를 검증·응답·감사 정렬에 사용 | Farm 조회·입고·자재·Mutation 관련 나머지 경계와 정책 |
| B-01~02 | 실제 제거된 Entity 의존 5쌍만 이식 목록에서 삭제: 137 → 132. 거래처 값·잠금·입금일·잔액 조회·배치 정책 회귀 추가 | 직접 JPQL 2곳과 기존 disabled 43건을 포함한 나머지 경계·테스트 복구 |

보존한 계약:

- 거래처의 이름·유형·활성 여부는 현재 기준 정보의 복사본이다. 잔액과 분석 조회는 현재 거래처 이름을 사용하며 과거 전표 스냅샷을 변경하지 않는다.
- 잠금은 중복 ID를 제거하고 ID 오름차순으로 획득한다. 잠금만 획득하는 API는 기존 트랜잭션을 필수로 요구하며, 비활성 거래처도 기존 거래 처리에 필요한 조회·잠금은 허용한다. 신규 사용의 활성 검사는 별도로 유지한다.
- 거래처 ID로 바꾼 정산 설정·잔액에도 기존 UNIQUE·FK와 잔액 `@Version`을 유지한다. DB migration은 추가하지 않았다.
- 배치 프로필은 기존 기본 규격과 `CUSTOM:` 규격을 허용한다. 전체 검증이 성공한 뒤 규칙을 교체하고 감사 기록을 남긴다. DTO·enum 문자열과 응답 순서는 유지한다.

책임 분리와 구분한 수정:

- `fix`: PostgreSQL에서 같은 거래처의 정산 설정 최초 조회 8개를 동시에 실행하면 `partner_settlement_settings_partner_id_key` 중복 오류가 발생하는 문제를 재현했다. 거래처 잠금 후 설정 조회·생성을 수행하도록 수정했으며 8개 요청이 동일한 기본 설정을 반환하는 회귀를 추가했다. 설정 변경의 전후 감사 값도 같은 잠금 안에서 읽고 저장한다.

검증:

- Partner 계약 전환 직후 기본 테스트 288건 중 245건 통과, 기존 disabled 43건. PostgreSQL 잠금 유지·역순 입력·동시 입금/재요청·외래키 회귀 4건 통과.
- 정산 설정 수정 후 관련 PostgreSQL 5건 통과. Farm 정책·배치·감사·아키텍처 집중 회귀 통과.
- 최종 `./gradlew test workE2eTest --offline --no-daemon`: 기본 306건 중 263건 통과·기존 disabled 43건, 실제 PostgreSQL 27건 전부 통과. 실패 0건.
- 임시 H2 서버에서 OpenAPI 131 operations·110 paths·217 schemas 재생성. 전체 명세·slice 차이 없음. 임시 서버 종료, `git diff --check` 통과.
- 프론트 코드·생성 schema 변경 없음. 프론트 검증·벤치마크는 실행하지 않았다.

코드 커밋:

- `5ba917e5 refactor: separate partner values and transaction locks`
- `816365cd fix: serialize initial partner settlement settings creation`
- `84a10f65 refactor: isolate bed placement profile domain rules`

다음 이식 순서:

1. B-04의 남은 소비자가 Partner Entity 없이 생성·조회할 수 있도록 각 모듈의 ID·snapshot 계약 전환.
2. 이 계약 위에서 B-05 Auction 생성·결과 연결 API, B-06 Settlement 원장·계산 책임 분리.
3. Farm B-08의 나머지 조회·command 경계와 B-09~10 Mutation/Work 계약을 계속 정리.

## 12. 실행 기록 — 3차 정산·입금 계약

2026-09-06, `feature/backend-refactoring`. B-04의 Settlement 소비자 전환과 B-06의 원장·재구성 책임 분리를 진행했다.
Sales·Auction을 포함한 B-04 전체와 B-06 전체 완료를 뜻하지 않는다.

| 작업 | 적용한 변경 | 남은 범위 |
|---|---|---|
| B-04 | 입금 이벤트·경매 정산의 거래처 Entity 연관을 ID로 전환. Settlement의 Partner Entity 직접 의존 제거. 이름 조회는 Partner application 값의 일괄 조회로 처리 | Sales·Auction의 Entity 연관과 deprecated 조회, 운영 목록·선택지 계약 |
| B-06 | 원장은 application 입금 명령을 받고 이벤트 ID를 반환. 중복 요청의 금액·입금일 검증은 domain 소유. 정산 재구성을 그룹 조회·기존 정산 조회·결과 병합으로 분리 | Auction 결과 연결의 소유권, 전체 원장·금액·상태 정책 정리, 누적 목록 pagination |
| B-01~03 | Entity 의존 8쌍 제거: 132 → 124. 정산의 직접 현재 시각 조회를 제거해 남은 시간 예외 클래스 5 → 4 | 나머지 Entity/Q/HTTP DTO·직접 JPQL 2곳, 기존 disabled 43건과 시간 예외 4개 클래스 |

보존·명시한 계약:

- 원장의 공개 명령은 HTTP DTO와 분리하며 거래처 Entity나 원장 Entity를 다른 모듈로 넘기지 않는다. 잔액의 마지막 입금 이벤트 연결은 Settlement 안에서 이벤트 ID로 해석한다.
- 원장 API는 호출 트랜잭션을 필수로 요구한다. 대상 입금 상태, 입금·연결 이벤트, 잔액, 감사가 함께 반영되거나 rollback된다. 부분입금·초과입금 거절·멱등 키 형식·개인정보 감사 제외 규칙은 유지한다.
- 입금 이벤트와 경매 정산의 거래처 이름은 현재 기준 정보다. root 순서·원본/연결 이벤트 관계·정산 행의 수량과 금액은 유지하며 이름만 일괄 조립한다. 빈 목록은 거래처를 조회하지 않는다.
- 잘못된 유형·없는 경매장은 거절하고, 비활성 경매장의 기존 결과 재구성은 허용한다. 기존 FK·UNIQUE·버전을 유지했으며 DB migration은 없다.
- 결과 수신·입금 확인 시각은 application이 주입된 Clock에서 UTC 값으로 결정한다. 응답은 기존 한국 시간 변환을 유지하고 일괄 재구성은 같은 시각을 사용한다. 이미 연결된 결과는 반복 반영하지 않는다.

검증:

- 원장 명령·결과, 마지막 이벤트 연결 보존, 멱등 요청, 트랜잭션 밖 호출 거절, 후속 실패 rollback, 개인정보 감사 회귀 통과.
- 입금 이벤트·경매 정산 각각 거래처 1·10·50개에서 SQL 2회 이내. 정산 행·목록 순서·변경된 거래처 이름 검증. 빈 목록은 SQL 1회.
- PostgreSQL 집중 회귀 7건 통과: 기존 잠금·직거래 입금·동시 설정 생성에 경매 동시 입금/재요청·입금 전체 rollback·추가 FK 확인 포함.
- 고정 Clock으로 UTC 저장과 한국 날짜 경계 응답, 기존/신규 결과 병합, 일괄 재구성 재실행 검증 통과.
- 최종 `./gradlew test workE2eTest --offline --no-daemon`: 기본 324건 중 281건 통과·기존 disabled 43건, 실제 PostgreSQL 29건 전부 통과. 실패 0건.
- 임시 H2 서버에서 OpenAPI 131 operations·110 paths·217 schemas 재생성. 전체 명세·slice 차이 없음. 임시 서버 종료, `git diff --check` 통과.
- 프론트 코드·생성 schema 변경 없음. 프론트 검증·벤치마크는 실행하지 않았다.

코드 커밋:

- `4b18efbb refactor: define value contracts for the payment ledger`
- `216bfd6e refactor: decouple auction settlements from partner entities`
- `fdc2f974 refactor: make auction settlement rebuilding deterministic`

다음 범위:

1. B-04의 Sales·Auction 거래처 Entity 연관과 공개 조회 반환을 값 계약으로 전환.
2. B-05의 출하 생성·결과 조회 API를 정리해 Sales/Settlement의 Auction 내부 접근 제거.
3. B-06의 남은 금액·상태 정책과 목록 계약, B-08~10 Farm/Work 경계 작업을 이어간다.

## 13. 실행 기록 — 4~6차 계약 이식과 구조 단순화

2026-09-06, `feature/backend-refactoring`. 새 추상화 수를 늘리는 대신 기존 API와 중간 구현을 제거하면서 다음 세 단계를 진행했다. 아래 범위의 완료이며 B-04~06 전체 완료를 뜻하지 않는다.

| 단계 | 변경 | 코드 커밋 |
|---|---|---|
| 4차 · B-04 | Sales·Auction의 거래처 Entity 연관을 ID로 전환하고 Partner의 Entity 반환 API 제거. Sales의 출하·lot 연결도 ID로 전환. 현재 거래처 정보는 일괄 조회 | `ec848eb5` |
| 5차 · B-05 | 출하·lot 생성을 기존 Auction 서비스에 통합하고 Sales의 중간 클래스 두 개 삭제. 원본 품목 ID로 lot 연결. 생성·삭제는 호출 트랜잭션 필수. 출하 선택지는 각 모듈이 자기 데이터만 조회 | `538d7d30` |
| 6차 · B-05~06 | 정산의 결과·lot Entity 연관을 ID와 금액 스냅샷으로 전환. Auction 결과는 application 값으로 조회. 입금 ID wrapper 삭제, 고정 결과 조회의 QueryDSL 파일 두 개 삭제, 입금일 계산은 기존 정산 설정 Entity로 이동 | `0a1c562d` |

복잡성 점검 (`44cdb856` 대비):

- 운영 Java 파일 **576 → 571**. 새 파일 0개, 삭제 5개. 운영 Java는 순증 **30줄**이며 테스트·문서 증가와 구분했다.
- 내부 Entity/Q/HTTP DTO 직접 의존 **124 → 89쌍**. 다른 모듈 Entity를 직접 읽는 명시적 JPQL **2 → 0곳**. QueryDSL 의존까지 전부 제거했다는 뜻은 아니다.
- 출하 생성은 Sales의 출고 조율 → Auction 생성 API로 이어진다. 별도 materializer·lot factory와 배열 순서 의존을 제거했다. 금액 스냅샷 생성과 입금일 정책은 기존 클래스 안에 유지했다.
- 모듈별 일괄 조회로 일부 조회는 SQL 1회가 늘었다. 판매 페이지는 3회, 경매 lot 페이지는 5회, 정산 목록은 3회 이내이며 1·10·50개에서 행별 추가 조회가 없음을 검증했다.
- 정산 재구성은 모든 낙찰 결과의 **ID**를 500개씩 대조한다. 이미 연결한 결과 상세는 읽지 않는다. 누적 데이터에서 ID 순회 비용은 남으므로 이후 운영 목록 pagination과 함께 검토한다. 이를 숨기기 위한 상태 테이블·메시징은 추가하지 않았다.

보존·검증:

- 대표자·전화번호·현재 이름 검색, 경매 품종과 경매장 이름을 이어 붙인 검색, 정렬과 페이지 총건수 유지.
- 사용된 출하가 210건인 경우에도 그 다음 후보에서 미사용 최신 200건을 정확히 반환.
- 출하 품목 매핑·생성/출하 스냅샷·재고 차감과 취소 복구, PostgreSQL의 FK 연결 해제 순서·호출자 rollback 검증.
- 결과 502건을 여러 배치로 대조하고 신규 1건만 기존 정산에 추가. 원본 단가·금액이 나중에 바뀌어도 기존 정산 스냅샷 유지. 반복 실행은 상세·정산 조회 없이 ID 대조만 수행.
- 수동 입금의 부분입금·초과입금 거절·재요청 검증·동시 입금·감사/잔액 rollback과 UTC 시각 계약 유지. 달력일·영업일·주말·0일 지연의 기존 테스트 통과.
- 전체 `./gradlew test workE2eTest --offline --no-daemon`: 기본 **331건 중 288건 통과·기존 disabled 43건**, 실제 PostgreSQL **30건 전부 통과**. 실패 0건. 마지막 반복문·import 정리 후 정산·모듈 경계 18건 추가 확인.
- 임시 H2 서버에서 OpenAPI **131 operations·110 paths·217 schemas** 재생성. 전체 명세·slice 차이 없음. DB migration·프론트 코드·생성 schema 변경 없음. 프론트 검증과 벤치마크는 실행하지 않았다.

다음 범위는 Partner 운영 목록과 남은 조회 의존, B-06의 누적 정산·입금 목록 pagination 및 남은 금액·상태 정책, B-08~10 Farm/Mutation/Work 경계다. 계약을 이식할 때 기존 경로를 함께 제거하고, 클래스 수·호출 단계·조회 비용을 함께 판단한다.

## 14. 실행 기록 — 7차 정산 목록 페이지와 전체 합계

2026-09-06, `feature/backend-refactoring`. B-06 중 누적 정산 목록을 처리했다. 코드·계약·화면 커밋은 `c8a5c6de`이며 B-06 전체 완료를 뜻하지 않는다.

- 정산 root를 서버 페이지로 조회하고 거래처 이름을 일괄 조립한다. 목록에서는 정산 행과 Auction 결과를 읽지 않고 선택 상세에서만 조회한다.
- 예상 입금액·미입금 잔액은 같은 조회 조건의 DB 합계로 제공한다. 목록·count·합계가 조건을 공유하며 PostgreSQL 날짜 매개변수에 명시적인 타입을 적용했다.
- 기존 전체 목록은 최신 500건으로 제한하고 deprecated로 표시했다. 운영 화면의 기존 전체 목록 호출·export·cache key와 브라우저 배열 분할·합산을 제거했다.
- 페이지·크기·선택 정산은 URL, 목록·전체 합계·상세는 기존 Query cache에서 관리한다. 입금·재계산 응답으로 상세를 갱신하기 전에 진행 중인 상세 조회를 취소하고 관련 페이지·합계만 무효화한다.

복잡성 점검 (`0b47ec76` 대비):

- 운영 Java **571 → 573파일**, 순증 **92줄**. 추가 파일은 목록·합계 응답 DTO 두 개다. 기존 Controller → Service → Repository 흐름을 유지하고 별도 조회 서비스·범용 검색 프레임워크는 추가하지 않았다.
- 프론트 운영 코드는 생성 타입을 제외하고 순증 **100줄**이다. 전체 배열 계산과 로컬 페이지·선택 상태를 없앴지만 서버 조회·URL 복원·cache 갱신을 위해 코드가 늘었다. 단순 코드량 감소로 평가하지 않는다.
- 페이지·전체 합계·상세로 HTTP 요청이 나뉘는 비용이 생겼다. 대신 목록에 필요한 데이터량을 제한하고 페이지 이동과 무관한 합계 cache를 유지한다. DB 합계의 누적 데이터 스캔 비용과 상세 추가 조회 비용은 남아 있다.
- 1·10·50건 페이지는 SQL **3회**이며 정산 행·경매 결과 Entity 로딩은 0건이다. 호환 상세 목록은 root 상한을 적용하는 조회가 추가되며 1·10·50건에서 SQL 4회를 확인했다. 새 모듈 간 직접 의존은 추가하지 않았다.

보존·검증:

- 날짜·ID 역순 정렬, 페이지 총건수, 빈 결과·범위 밖 페이지, 크기 보정, 거래처·날짜·상태 필터와 전체 합계를 검증했다. 부분입금 반영, 32비트 범위를 넘는 금액 합계, 501건에서 호환 상한 밖 금액도 전체 합계에 포함되는지 확인했다.
- 최종 `./gradlew test workE2eTest --offline --no-daemon`: 기본 **333건 중 290건 통과·기존 disabled 43건**, PostgreSQL **30건 전부 통과**, 실패 0건. PostgreSQL 날짜 타입 추론 오류는 수정 후 전체 재검증했다.
- OpenAPI **133 operations·112 paths·222 schemas**와 TypeScript schema 재생성. 기존 응답 schema 변경 없이 페이지·합계 경로를 추가했고 기존 목록의 deprecated 설명을 반영했다.
- `frontend`의 `npm run check` 통과. 임시 API fixture를 연결한 실제 Chromium에서 페이지·크기 변경, 뒤로가기, 늦은 상세 응답, 페이지 밖 상세 deep link·새로고침, 입금·재계산 후 갱신, 범위 밖 URL 복원과 기존 목록 미호출을 확인했다. 브라우저 검증은 실제 DB 연결 E2E를 대체하지 않는다. DB migration과 대용량 벤치마크는 이번 범위에 없다.

다음 우선 범위는 B-06의 입금 이벤트 누적 목록과 남은 금액·상태 정책이다. Partner의 선택지·남은 QueryDSL 직접 의존, B-08~10 Farm/Mutation/Work 경계는 이어서 진행한다. 새 구현을 추가할 때 기존 호출과 중복 책임을 함께 제거한다.

## 15. 실행 기록 — 8차 입금 이력 페이지와 정산 상태 계산

2026-09-06, `feature/backend-refactoring`. B-06의 입금 이벤트 목록과 경매 정산 내부의 중복 상태 계산을 정리했다.

| 목적 | 커밋 |
|---|---|
| 경매 정산 재구성·입금 처리의 잔액·상태 계산을 기존 Entity의 한 메서드로 통합 | `ba627947` |
| 입금 이력 서버 페이지·유형 필터, 일반 판매·경매의 화면 전환, 조회 실패와 입금 결과 분리 | `1397db32` |

- 이벤트 유형을 필터링한 뒤 페이지와 총건수를 조회한다. 유형 생략 시 모든 원장 이벤트를 포함하며 입금·연결 이벤트의 보존 정책은 유지한다. 호환 목록은 최신 500개 **이벤트**로 제한했다.
- 기존 이벤트 응답과 페이지 응답을 재사용했다. 원본 입금의 ID만 필요한 연결 이벤트 조회에서 원본 Entity의 전체 fetch를 제거했다.
- 일반 판매·경매 정산의 공통 입금 패널에서 전체 목록 호출·로컬 응답 배열·클라이언트 유형 필터·잔액 차감을 제거했다. 대상·유형·페이지별 Query cache와 서버의 처리 후 잔액을 사용한다. 이력 열림·페이지는 URL에 유지하고 대상이나 목록 조건 변경 시 초기화한다.
- 이력 재조회 실패는 입금 완료 메시지를 덮어쓰지 않는다. 이력 재시도는 조회만 수행하고, 입금 응답 유실 시에는 같은 키·금액·날짜로 입금을 재시도한다. 처리 중 입력을 막고, 이전 대상의 지연 응답이나 완료된 입금이 현재 선택의 이력을 열거나 덮어쓰지 않게 했다.

복잡성 점검 (`1efce4e9` 대비):

- 운영 Java 파일 **573개 유지**, 순증 **37줄**. 새 운영 파일·서비스·DTO·공통 정책 계층 없이 기존 Entity·Controller·Service·Repository를 수정했다. 별도 금액 정책 클래스로 분리하지 않았다.
- 생성 타입을 제외한 프론트 운영 코드 순증 **94줄**. 이력 페이지·URL 복원·로딩/실패 표시에 필요한 코드가 늘었으며, 수동 이벤트 enum·응답 필드 선언을 생성 계약으로 전환했다.
- 이력 패널을 열 때 필요한 페이지를 조회한다. 1·10·50건에서 SQL **3회**(페이지·count·거래처 이름)이며 원본 입금 Entity를 추가 로딩하지 않는다. 이전 조회의 2회보다 count 비용이 늘었지만 전송·적재할 이벤트 수를 제한했다. 깊은 offset과 전체 count 비용은 남아 있다.

보존·검증:

- 재구성 전후 부분입금·완납·추가 낙찰 반영 상태, 잘못된 입금의 무변경, 기존 원장·감사·동시 입금·멱등 재요청 회귀 검증. 거래처·대상 종류·대상 ID·이벤트 유형 필터, 정렬·부모 ID, 빈 결과와 페이지 보정, 502개 이벤트의 보존과 전체 페이지 접근 확인.
- 최종 `./gradlew test workE2eTest --offline --no-daemon`: 기본 **339건 중 296건 통과·기존 disabled 43건**, PostgreSQL **30건 전부 통과**, 실패 0건.
- OpenAPI **134 operations·113 paths·224 schemas**와 TypeScript schema 재생성. 기존 응답 schema 변경 없이 페이지 경로와 두 페이지 envelope schema를 추가했다. `frontend`의 `npm run check` 통과.
- 임시 API fixture와 실제 Chromium에서 지연 조회, URL 복원, 입금 성공 후 이력 조회 실패·재조회, 입금 응답 유실 후 같은 키 재시도, 처리 중 입력 보호, 요청 중 대상 전환, 일반 판매·경매 양쪽의 입금 후 이력·잔액 갱신을 확인했다. 실제 DB를 연결한 브라우저 E2E와 대용량 벤치마크는 실행하지 않았다. DB migration은 없다.

B-06 전체 완료는 아니다. 다음 우선 범위는 일반 판매의 입금 전제조건과 Settlement HTTP DTO의 외부 소비를 기존 domain/application 계약으로 정리하는 작업이다. Partner 선택지·남은 조회 의존과 Farm/Mutation/Work 경계도 남아 있다.
