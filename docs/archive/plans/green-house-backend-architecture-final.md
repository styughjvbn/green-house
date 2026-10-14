# Green-house 백엔드 아키텍처 설계

> **문서 상태:** 보관된 최초 목표 설계 — ADR-004 P0~P6 전환 완료 (2026-10-08)
> **현행 기준:** [04-architecture.md](../../04-architecture.md). 이 문서의 가상 예시·초기 미확정 방향은 신규 작업의 기준이 아니다.
> **결정과 검증:** [ADR-004](../../adr/ADR-004-backend-architecture-migration.md). 실제 Sales 소유권과 Farm → Work 방향을 보존해 구현했다.  
> **작성 기준일:** 2026-10-08  
> **적용 범위:** `backend/src/main/java/com/greenhouse/backend` 및 관련 아키텍처 테스트  
> **핵심 원칙:** 높은 응집도, 낮은 결합도, 명확한 소유권, 최소한의 추상화

## 0. 문서 목적과 적용 기준

이 문서는 Green-house 백엔드의 패키지 배치, 모듈 간 계약, 허용 의존성, 유스케이스 소유권, DTO·클래스 명명 및 OrchidGroup 단일 쓰기 경계를 통일한다. 신규 개발과 점진적 리팩터링의 기준으로 사용한다.

### 기존 구현과의 관계

> 2026-10-08 착수 확인: 현재 Sales 통합과 이관 전용 CLI 제거는 완료되어 있다. Sales 소유권은 `document/direct/auction/payment/partner`, 컴파일 의존은 `Farm → Work`로 유지한다. 아래 초기 현황·목표 트리의 `slip/settlement` 및 `Work → Farm` 예시는 현재 전환의 확정 방향이 아니다. 실제 적용 결정과 현행 허용표는 [ADR-004](../../adr/ADR-004-backend-architecture-migration.md)를 따른다.

- **현재 구현에서 확인한 사실:** 기존 소스 트리는 `farm`, `work`, `sales`, `partner`, `auction`, `settlement` 등 최상위 패키지와 계층 우선 디렉터리가 공존한다. `farm/application/orchid/mutation`에는 명령, 엔진, 원장·조회, 검증 및 전환 관련 클래스가 혼재한다.
- **이번에 결정한 목표:** 주요 업무 모듈은 `farm`, `work`, `sales`로 정리한다. `partner`, `auction`, `settlement` 등의 판매 관련 기능은 `sales` 내부로 통합한다. 기존 Sales 하위 기능 사이에 정의된 **허용 의존 방향은 유지**한다.
- **별도로 확정해야 할 사항:** Sales 하위 기능의 정확한 의존 방향 목록, 현행 코드에서 실제 사용 중인 전환 CLI, 모듈 사이의 모든 역방향 호출은 현재 첨부된 디렉터리 트리만으로 확정할 수 없다. 코드와 아키텍처 테스트를 분석한 후 이 문서의 의존성 표에 반영한다.

이 문서가 DB 테이블, HTTP 계약, 원장 스냅샷 포맷, 저장 지문 또는 운영 배포 절차를 변경하도록 승인하는 것은 아니다. 세부 업무 정책의 기준은 기존 도메인 문서, 코드, Flyway 및 OpenAPI다.

---

## 1. 전체 구조와 모듈 소유권

Green-house 백엔드는 **모듈러 모놀리스**다. 모듈을 Java 패키지로 조직하며 하나의 애플리케이션과 트랜잭션 환경에서 실행한다. MSA 전환을 전제로 불필요한 원격 호출 형태를 흉내 내지 않는다.

| 최상위 모듈 | 소유 책임 | 주요 데이터·상태 |
|---|---|---|
| `farm` | 농장 구조, 식물 그룹, 품종, 입고, 자재, 배치, 물리 재고 및 상태 변경 | `OrchidGroup`, 위치, 수량, 예약 수량, Mutation 원장 |
| `work` | 작업 계획, 실행, 대상 확정, 효과 기록, 이력, 취소·보정 | `WorkOperation`, `WorkOperationTarget`, `WorkAppliedEffect` |
| `sales` | 거래처, 판매 전표, 경매 출하·결과, 정산, 입금 | 판매 전표·배분, 경매 Lot, 정산·입금 기록 |
| 지원 모듈 | `analytics`, `audit`, `auth`, `dashboard`, `print`, `demo`, `common` 등 | 각 지원 기능에 해당하는 고유 책임 |

**통합은 책임의 삭제가 아니다.** `sales` 아래의 `partner`, `slip`, `auction`, `settlement`, `payment`는 독립적인 기능 영역과 모델을 계속 소유한다. 판매 통합을 이유로 거대한 `SalesService`나 통합 Entity를 만들지 않는다.

### 세 가지 서로 다른 관계

1. **소유권:** 어떤 모듈이 규칙과 데이터를 변경할 권한을 갖는가.
2. **컴파일 의존성:** 어느 패키지의 타입을 Java 코드에서 참조할 수 있는가.
3. **런타임 호출:** 업무 처리 중 누가 누구를 실제로 호출하는가.

이 셋을 하나의 화살표로 취급하지 않는다. 특히 Port/SPI를 구현해 컴파일 의존 방향을 유지하더라도 런타임 호출과 트랜잭션 결합은 별도로 남는다.

---

## 2. 최종 설계 원칙

1. **모듈 소유권** — 어떤 최상위 모듈이 소유하는가? `farm`, `work`, `sales` 중 데이터와 업무 책임을 기준으로 결정한다.
2. **기능 소유권** — 어떤 기능이 소유하는가? 특정 기능의 유스케이스라면 `{module}/{feature}/application`에 배치한다.
3. **유스케이스 조율** — 여러 기능을 조율해야 하는가? **해당 업무의 완료 책임을 가진 기능의 `application`**에서 조율한다. 특정 기능에 속하지 않는 독립적인 업무 흐름인 경우에만 최상위 `{module}/application`에 배치한다.
4. **공개 계약** — 외부 모듈에서 사용하는가? 그렇다면 **소유 모듈의 `api`**를 통해 제공하며 내부 구현과 영속성 모델을 노출하지 않는다. 공개 계약은 필요한 최소 범위로 유지한다. 동일 최상위 모듈 내부 기능 사이에는 기능별 내부 `api`를 둘 수 있다.
5. **의존 방향** — 기존에 허용된 의존 방향으로 해결 가능한가? 가능하면 공개 API를 직접 사용하고 불필요한 Port/Adapter를 추가하지 않는다.
6. **의존성 격리** — 외부 의존성의 격리나 의존성 역전이 필요한가? 필요할 때만 **호출 측 내부 Outbound Port와 Adapter**를 도입한다. 다른 모듈이 구현체를 제공해야 하는 경우에는 **공개 SPI**를 정의한다. 단순 호출이나 기술 사용만을 이유로 추상화하지 않는다.
7. **중앙 쓰기 경로** — 식물 그룹 상태를 변경하는가? 반드시 Farm의 Mutation Writer를 거치도록 한다. **업무별 판단**과 **Mutation의 상태 변경 실행 책임**을 구분한다.
8. **모듈 경계 검증** — 모듈 경계를 위반하는가? 모듈 간 **순환 컴파일 의존**과 다른 모듈의 내부 구현 접근을 금지하고, 허용된 공개 계약을 통해서만 접근한다. 가능한 경우 아키텍처 테스트로 강제한다. 런타임 호출 순환과 트랜잭션·잠금 문제는 별도로 점검한다.

추가 구현 지침: **독립적인 변경 이유가 없으면 클래스를 분리하지 않는다.** 단순 전달 래퍼, 의미 없는 인터페이스, 사용되지 않는 일반화 계층은 만들지 않는다.

---

## 3. 패키지 구조 및 이름 규칙

### 3.1 기본 계층: 모듈 → 기능 → 역할

```text
com.greenhouse.backend/
├── farm/
│   ├── api/                 # 다른 최상위 모듈에 공개
│   ├── spi/                 # 다른 모듈의 구현이 필요한 공개 확장 계약 (선택)
│   ├── application/         # 특정 기능에 속하지 않는 Farm 전체 유스케이스 (선택)
│   ├── web/                 # 위 유스케이스의 HTTP 진입점 (선택)
│   ├── orchid/
│   │   ├── application/
│   │   ├── domain/
│   │   ├── repository/
│   │   └── web/
│   │       └── dto/
│   ├── mutation/            # Farm 내부 OrchidGroup 단일 Writer 서브시스템
│   ├── inbound/
│   ├── structure/
│   ├── transformation/
│   ├── variety/
│   ├── material/
│   ├── collection/
│   ├── status/
│   └── integration/         # 공개 SPI 구현 등 필요한 연동 (선택)
├── work/
│   ├── api/
│   ├── spi/
│   ├── application/
│   ├── web/
│   ├── operation/
│   ├── execution/
│   ├── target/
│   ├── effect/
│   ├── correction/
│   └── integration/         # 실제 Port/SPI 구현·기술 격리가 필요한 경우만 (선택)
├── sales/
│   ├── api/
│   ├── spi/
│   ├── application/
│   ├── web/
│   ├── partner/
│   ├── slip/
│   ├── auction/
│   ├── settlement/
│   ├── payment/
│   └── integration/         # 실제 Port/SPI 구현·기술 격리가 필요한 경우만 (선택)
├── analytics/
├── audit/
├── auth/
├── dashboard/
├── print/
├── demo/
└── common/
```

**주의:** 위 트리는 가능한 위치를 보여 주는 **목표 템플릿**이다. 클래스가 없으면 디렉터리를 생성하지 않는다. `work/operation`, `work/execution`, `work/effect`의 최종 구분도 실제 책임을 확인하며 결정한다. 모든 기능에서 같은 깊이의 계층을 강요하지 않는다.

### 3.2 디렉터리 책임

| 경로 | 역할 | 금지 또는 주의 |
|---|---|---|
| `{module}/api` | **최상위 모듈 외부**에 공개하는 인터페이스·Command·Result·읽기 전용 값 | Entity, Repository, 내부 Service 구현 노출 금지 |
| `{module}/spi` | **다른 모듈이 구현하는** 공개 역방향 확장 계약 | 필요 없는 SPI 선제 생성 금지 |
| `{module}/application` | 특정 하위 기능에 속하지 않는 **독립적인 교차 기능 유스케이스** | 모든 요청의 중앙 허브로 사용 금지 |
| `{module}/web` | 위 유스케이스의 HTTP Controller·전용 DTO | 업무 규칙·트랜잭션 조율 금지 |
| `{module}/{feature}/api` | 같은 최상위 모듈 안의 **허용된 다른 기능**에 제공하는 계약 | 외부 최상위 모듈에 무심코 공개 금지 |
| `{feature}/application` | 기능의 유스케이스, 트랜잭션, 실행 순서 | 다른 기능의 Repository 직접 사용 금지 |
| `{feature}/domain` | Entity, Value Object, 순수 도메인 정책 | HTTP, 외부 모듈 Service 의존 금지 |
| `{feature}/repository` | JPA, QueryDSL, 저장·조회 구현, DB Projection | 타 기능에서 직접 접근 금지 |
| `{feature}/web` | Controller 및 HTTP 전용 DTO | Domain에서 역참조 금지 |
| `{feature}/integration` | Outbound Port 구현, 공개 SPI Adapter 등 | 새 계층을 만들기 위한 무의미한 래퍼 금지 |
| `{feature}/application/port/out` | 호출 측이 소유하는 **내부 Outbound Port** (필요 시) | 외부 모듈에서 직접 참조하지 않음 |

`api`/`spi`/`web`는 **각각 공개 계약, 공급자 확장 계약, HTTP 전달 계층**이다. 이름이 같아 보여도 범위와 목적이 다르다.

`application`, `domain`, `repository`, `web`는 기능 규모에 맞춰 필요한 만큼만 만든다. `application`에 클래스 하나만 있으면 반드시 서브패키지로 쪼개야 한다는 규칙은 없다.

### 3.3 최상위 API와 기능 API 구분

```text
sales/api/                  # Work, Analytics 등 Sales 외부 모듈 소비자용
sales/partner/api/          # Sales 내부에서 허용된 다른 기능 소비자용
sales/partner/application/  # Partner 유스케이스 구현
```

Java `public`과 실제 아키텍처 공개는 다르다. 패키지 이름만으로 접근이 제한되지 않으므로 아키텍처 테스트가 공개 범위를 강제해야 한다.

---

## 4. 모듈 및 기능 간 의존성

### 4.1 최상위 모듈 경계

기본적인 신규 코드 설계 방향은 다음과 같이 검토한다.

```text
Work  ----> Farm 공개 API
Sales ----> Farm 공개 API

[역방향 협력]
Farm --> Farm 소유 공개 SPI <-- Work/Sales의 구현 Adapter
```

이 그림은 **신규 설계의 기본값**이지 현행 코드가 이미 그렇게 되어 있다는 주장은 아니다. 기존 코드의 Work ↔ Farm Port 구현, Farm ↔ Sales 참조 조사와 승인 목록 이관이 먼저 필요하다.

- 다른 최상위 모듈의 `api`는 **선언된 의존 방향에 한해** 사용한다.
- 다른 최상위 모듈의 `domain`, `repository`, 내부 `application`, `web/dto`에 직접 의존하지 않는다.
- 데이터베이스는 하나를 공유할 수 있지만 **각 테이블의 쓰기 소유권은 하나**로 고정한다.
- 기존의 유효한 외래키는 모듈 분리 때문에 무조건 제거하지 않는다. 외래키 존재와 다른 모듈의 Repository를 호출할 권한은 별개다.
- `audit`, `analytics`, `print` 등 지원 모듈의 구체적인 허용 의존성은 현재 사용처를 조사하여 명시한다.

### 4.2 Sales 내부 하위 기능

`partner`, `slip`, `auction`, `settlement`, `payment`는 **Sales의 하위 기능**이지 모두 독립된 최상위 모듈이 아니다. 그러나 하위 기능 간 의존을 무제한 허용하지 않는다.

**결정:** 기존에 정의되어 있는 **허용 의존 방향을 보존**한다. 이 문서만으로 기존 허용표를 임의로 다시 작성하거나 반대로 뒤집지 않는다.

```text
허용된 방향:
    sales/<caller>/application
        --> sales/<provider>/api

금지:
    sales/<caller>/application
        -X-> sales/<provider>/repository
        -X-> sales/<provider>/domain/Entity
        -X-> sales/<provider>/application/구현체
```

아래는 **설명용 예시**다. 실제 프로젝트의 허용 방향인지 확정한 표가 아니다.

| 가정한 방향 | 사용 예시 | 적용 조건 |
|---|---|---|
| `slip → partner` | 판매 전표에서 거래처 상태 조회 | 기존 허용표에 있을 때 |
| `slip → auction` | 출하 완료 시 경매 Lot 생성 | 기존 허용표에 있을 때 |
| `settlement → auction` | 경매 결과로 정산 계산 | 기존 허용표에 있을 때 |

**적용 전 반드시 채울 의존성 기록**

| 호출 기능 | 제공 기능 | 공개 계약 | 허용 여부 | 근거/기존 테스트 |
|---|---|---|---|---|
| `slip` | `partner` | 미확정 | **현행 규칙 확인** | 미확정 |
| `slip` | `auction` | 미확정 | **현행 규칙 확인** | 미확정 |
| `settlement` | `auction` | 미확정 | **현행 규칙 확인** | 미확정 |
| 기타 | 기타 | 미확정 | **현행 규칙 확인** | 미확정 |

기능 간 순환 **컴파일 의존**은 금지한다. 허용 방향만으로 해결되지 않는 실제 업무는 **완료 책임이 있는 기능의 application을 우선** 검토하고, 그래도 단일 기능 소유가 불가능할 때만 최상위 `sales/application`에 조율 기능을 둔다.

---

## 5. 유스케이스 소유권과 HTTP 진입점

### 5.1 한 기능이 업무 완료를 책임지는 경우 — 기능별 Application

판매 전표의 출하 완료는 Slip이 완료 책임을 진다고 가정하면 다음과 같이 배치한다.

```text
HTTP PATCH /api/sales-slips/{id}/outbound
    -> sales/slip/web/SalesSlipController
    -> sales/slip/application/SalesSlipOutboundService
        -> (허용된 경우) sales/auction/api/AuctionShipmentCreator
        -> farm/api/orchid/OrchidGroupMutationWriter
        -> Slip 소유 상태와 이력 확정
```

- 다른 기능을 **몇 개 호출하는지**는 배치 기준이 아니다.
- 어떤 기능이 업무 결과의 완료·실패·멱등성·트랜잭션을 책임지는지가 기준이다.
- `sales/application`을 무조건 거치게 하지 않는다.

### 5.2 특정 기능에 속하지 않는 독립 업무 흐름 — 최상위 Application

다음 `SalesClosingService`는 **가상의 예시**다. 현재 실제 유스케이스가 존재함을 의미하지 않는다.

```text
sales/
├── web/
│   ├── SalesClosingController.java
│   └── dto/
│       └── SalesClosingRequest.java
├── application/
│   └── SalesClosingService.java
├── slip/api/
├── auction/api/
└── settlement/api/
```

```text
HTTP POST /api/sales-closing
    -> sales/web/SalesClosingController
    -> sales/application/SalesClosingService
        -> sales/slip/api
        -> sales/auction/api
        -> sales/settlement/api
```

이 경우에도 최상위 Application은 **허용된 내부 공개 계약만** 사용한다. 하위 기능의 Repository나 Entity에 직접 접근할 수 있는 특권 계층이 아니다.

### 5.3 HTTP DTO와 Application 입력

```java
// sales/slip/web/dto/SalesSlipCreateRequest.java
public record SalesSlipCreateRequest(Long partnerId, List<ItemRequest> items) {
    // HTTP 검증 및 필요한 변환
}

// sales/slip/application/CreateSalesSlipCommand.java
public record CreateSalesSlipCommand(Long partnerId, List<SalesItemInput> items) {}

// sales/slip/web/SalesSlipController.java
@RestController
@RequiredArgsConstructor
class SalesSlipController {
    private final SalesSlipCreationService service;

    @PostMapping("/api/sales-slips")
    SalesSlipResponse create(@Valid @RequestBody SalesSlipCreateRequest request) {
        return SalesSlipResponse.from(service.create(toCommand(request)));
    }
}
```

위 코드는 **역할을 설명하는 개념 예시**이며 `ItemRequest`, `SalesItemInput`, `toCommand` 등의 완전한 정의는 생략했다. 현재 API나 Validation 계약을 대체하는 실행 가능한 패치가 아니다.

**같은 입력을 단순 복사만 하는 경우**에는 HTTP Request와 Application Command를 무조건 중복 정의하지 않는다. 애플리케이션 계약을 Controller에서 직접 바인딩할 수 있다. 단, HTTP 세부 사항이 내부 유스케이스나 외부 모듈에 전파되지 않아야 하며 기존 OpenAPI·검증 규칙과 호환되어야 한다.

---

## 6. API, Outbound Port, SPI, Adapter 선택

| 목적 | 도입할 구조 | 판단 기준 |
|---|---|---|
| 허용된 방향으로 다른 모듈 기능 호출 | **제공 측 Public API 직접 호출** | 기본 선택 |
| 호출 측이 외부 서비스·인프라를 추상화해야 함 | **호출 측 내부 Outbound Port + Adapter** | 구현 교체, 장애·프로토콜 격리, 테스트 가능성 등이 실제로 필요할 때 |
| 역방향 의존 없이 다른 모듈이 구현체를 제공해야 함 | **요청 측 소유 Public SPI + 제공 측 Adapter** | 컴파일 의존 방향을 보존해야 할 때 |
| 후속 알림·비핵심 파생 처리 | Event | 즉시 원자적 반영이 필요하지 않을 때 |

### 6.1 직접 API 호출 — 기본값

```java
// farm/api/orchid/OrchidGroupReader.java
public interface OrchidGroupReader {
    OrchidGroupState findById(Long orchidGroupId);
}

// work/target/application/WorkTargetValidationService.java
@Service
@RequiredArgsConstructor
class WorkTargetValidationService {
    private final OrchidGroupReader orchidGroupReader;

    public void validateTarget(Long id) {
        OrchidGroupState state = orchidGroupReader.findById(id);
        // Work가 소유한 작업 대상 규칙을 검증한다.
    }
}
```

`OrchidGroupReader`의 반환값은 JPA Entity가 아닌 공개 읽기 모델이어야 한다. 이미 이 호출이 허용되어 있다면 `WorkFarmReaderPort → FarmReaderAdapter → FarmReader` 같은 전달 전용 3단계를 만들지 않는다.

### 6.2 내부 Outbound Port — 호출 측 내부에 소유

```text
work/operation/
├── application/
│   ├── WorkNotificationService.java
│   └── port/out/NotificationSender.java
└── integration/notification/EmailNotificationAdapter.java
```

외부 이메일 API에 의존하는 구현을 격리해야 한다면 Work가 `NotificationSender`라는 내부 Port를 정의한다. 이 Port는 Work 외부에 공개할 필요가 없다. 직접 사용하는 구체 기술과 오류는 Adapter 쪽에 가둔다.

### 6.3 공개 SPI — 다른 모듈이 구현

```java
// farm/spi/SalesUsageInspector.java
public interface SalesUsageInspector {
    boolean hasActiveAllocation(Long orchidGroupId);
}

// sales/integration/farm/SalesUsageAdapter.java
@Component
@RequiredArgsConstructor
class SalesUsageAdapter implements SalesUsageInspector {
    private final SalesAllocationQueryService salesQuery;

    @Override
    public boolean hasActiveAllocation(Long orchidGroupId) {
        return salesQuery.hasActiveAllocation(orchidGroupId);
    }
}
```

Farm은 자신의 SPI만 참조하고 Sales가 이를 구현한다. 컴파일 의존은 `Sales → Farm`으로 남지만, Farm에서 해당 인터페이스를 호출할 때 런타임은 Sales의 구현으로 진입한다. 따라서 **다른 모듈 내부 Service를 직접 참조하지 않았다는 사실만으로 트랜잭션·락·순환 호출이 안전해지는 것은 아니다.**

SPI는 Outbound Port와 상호 배타적인 개념이 아니다. **다른 모듈이 구현하도록 공개된 Port**라고 이해할 수 있다.

### 6.4 이벤트의 사용 범위

- 통계 후처리, 외부 알림 등은 이벤트 활용을 검토할 수 있다.
- 출고와 물리 수량 차감처럼 반드시 동일 원자 트랜잭션에서 성립해야 할 핵심 업무는 **비동기 이벤트로 임의 분리하지 않는다.**
- 이벤트 도입으로 멱등성·재시도·장애 복구 비용이 증가하는 경우 동기 호출을 유지한다.

---

## 7. Farm Mutation 단일 Writer 설계

### 7.1 목적과 경계

`farm/mutation`은 **모든 `OrchidGroup` 쓰기를 중앙집중화하는 기술적·일관성 유지 서브시스템**이다. `farm`에서 별도의 최상위 업무 모듈로 승격하지 않는다.

**Mutation이 책임지는 것**

- OrchidGroup 생성·변경·취소 등 실제 상태 쓰기의 단일 진입 경로
- 수량, 예약 수량, 상태, 배치 등 공통 불변식 검증 및 잠금 상태의 재검증
- revision, Mutation, Entry, 관계 및 full snapshot 기록
- 저장 지문, 멱등성, replay 정합성 등 기존 원장 계약 보존
- PostgreSQL write fence·writer startup guard와의 일관성 유지

**Mutation이 책임지지 않는 것**

- 작업 계획·일정·실행 완료 판정 (`work`)
- 판매 전표·경매·정산의 업무 정책 (`sales`)
- HTTP validation, 응답 표현, 프론트 UI 정책
- 업무별 감사 사실의 소유권 이전 (`audit`, `work`, `sales`)

업무 소유자는 명령 전송 전에 업무 규칙을 검사하며, Mutation은 경쟁 상태에서도 공통 불변식을 깨지 않도록 **최종 방어선**을 유지한다.

### 7.2 권장 패키지

```text
farm/
├── api/orchid/
│   ├── OrchidGroupReader.java
│   ├── OrchidGroupMutationWriter.java
│   ├── command/
│   │   ├── OrchidGroupMutationCommand.java
│   │   ├── MoveOrchidGroupsMutationCommand.java
│   │   ├── ReserveOrchidGroupsMutationCommand.java
│   │   └── ...
│   └── model/
│       ├── OrchidGroupState.java
│       └── OrchidGroupMutationResult.java
├── orchid/
│   ├── application/
│   ├── domain/OrchidGroup.java
│   ├── repository/OrchidGroupRepository.java
│   └── web/
└── mutation/
    ├── engine/
    │   ├── OrchidGroupMutationEngine.java
    │   ├── OrchidGroupMutationCommandNormalizer.java
    │   ├── OrchidGroupMutationCommandFingerprint.java
    │   └── OrchidGroupMutationReplayResolver.java
    ├── ledger/
    │   ├── domain/
    │   │   ├── OrchidGroupMutation.java
    │   │   ├── OrchidGroupMutationEntry.java
    │   │   └── OrchidGroupStateSnapshot.java
    │   ├── repository/
    │   │   ├── OrchidGroupMutationRepository.java
    │   │   └── OrchidGroupMutationEntryRepository.java
    │   └── OrchidGroupMutationRecorder.java
    ├── query/
    │   ├── OrchidGroupMutationQueryService.java
    │   └── OrchidGroupMutationGraphQueryService.java
    ├── verification/
    │   ├── OrchidGroupLedgerReconciliationService.java
    │   └── OrchidGroupLedgerReconciliationReport.java
    └── config/
        ├── OrchidGroupLedgerWriterConfiguration.java
        └── OrchidGroupLedgerWriterStartupGuard.java
```

클래스명은 **목표 배치의 예시**이며 실제 파일의 소유권·의존성을 분석해 분류한다. `ledger`의 Entity와 Repository를 명시적으로 구분하기 위해 하위 `domain/`, `repository/`를 사용했다. 구현이 작으면 불필요한 중첩을 줄여도 된다.

### 7.3 변경 경로

```text
[Work/Sales/Farm 유스케이스]
    -> farm/api/orchid/OrchidGroupMutationWriter
        -> farm/mutation/engine/OrchidGroupMutationEngine
            -> farm/orchid/domain/OrchidGroup
            -> Farm 소유 Repository
            -> farm/mutation/ledger/Recorder + Repository
```

- 외부 호출자는 Engine 구현, 원장 Entity·Repository를 직접 호출하지 않는다.
- Farm 내부의 `orchid/application`도 동일한 단일 Writer 경계를 통과한다.
- Engine이 `farm/orchid/application`이나 `work/application`으로 재진입하지 않는다.
- Mutation이 사용하는 OrchidGroup Entity·Repository는 **Farm 소유의 내부 데이터 접근**이며, 외부 모듈 Repository 접근 금지와 충돌하지 않는다.
- 조회 API는 `mutation/query`와 Farm 공개 읽기 API로 분리할 수 있지만, 조회가 쓰기 우회를 만들면 안 된다.
- 외부 공개 Writer는 무제한 범용 DB 조작 API가 아니다. **실제 필요한 typed command와 결과만 노출**하고 업무 정책 우회가 가능한 기술 내부 메서드는 공개하지 않는다.

### 7.4 Mutation, Work Effect, Audit 구분

| 사실 | 소유 | 의미 |
|---|---|---|
| `OrchidGroupMutation` | Farm | 식물 그룹이 어떻게 변경되었는지의 상태·revision 원장 |
| `WorkAppliedEffect` | Work | 어떤 작업 실행에 어떤 효과가 적용되었는지 |
| `AuditEvent` | Audit 및 해당 업무 변경 기록자 | 누가 어떤 맥락에서 어떤 변경을 수행했는지 |

하나의 업무 실행에서 세 기록이 생성될 수 있다. 중복처럼 보인다고 서로 합치거나 하나의 정보만으로 나머지를 재구성하지 않는다. 기존 Mutation ID, correlation ID, 업무 참조 및 감사 연결의 의미를 유지한다.

### 7.5 완료된 전환 도구의 처리

기존 아키텍처 문서는 2026-10-06 이후 원장 이관용 importer·cutover CLI를 제거했다고 기술한다. 그러나 첨부된 디렉터리 트리에는 `OrchidGroupLedgerCutover*`, `OrchidGroupStateChainMigration*` 등의 이름이 남아 있다.

이는 **소스 스냅샷·브랜치·문서 간 차이일 가능성**이 있으므로 즉시 삭제하거나 `mutation/verification`으로 무조건 이동하지 않는다. 현재 사용 여부, Gradle task, Bean 조건, 운영 배포 상태, 참조 및 Git 이력을 조사하여 **현재 실행 코드 / 일회성 전환 도구 / 미사용 유산 코드**를 먼저 구분한다.

---

## 8. DTO와 값 계약 위치

| 목적 | 배치 | 이름 예시 |
|---|---|---|
| HTTP 전용 요청·응답 | `{feature}/web/dto` 또는 `{module}/web/dto` | `SalesSlipCreateRequest`, `SalesSlipResponse` |
| 다른 최상위 모듈 공개 | `{module}/api[/feature]` | `OrchidGroupState`, `OrchidGroupMutationResult` |
| 같은 모듈의 기능 간 공개 | `{module}/{feature}/api` | `PartnerInfo`, `AuctionShipmentInfo` |
| 기능 유스케이스 내부 Command/Result | `{feature}/application` (필요 시 `command/`, `model/`) | `CreateSalesSlipCommand` |
| 도메인 값 객체 | `{feature}/domain` | `BedZoneCapacity` |
| Persistence Projection/Row | `{feature}/repository` | `OrchidGroupNameRow` |

**기본 규칙:** 'DTO인가?'보다 **누가 소유하고 누구에게 공개되는가?**를 먼저 판단한다.

- 외부 계약에 JPA Entity, Hibernate 프록시, Repository Projection이나 타 모듈 HTTP DTO를 넣지 않는다.
- 공개 값 계약은 필요한 식별자와 스칼라·불변 값만 전달한다. 모듈 간 범용 Entity 그래프를 전달하지 않는다.
- `Request`, `Response`는 HTTP 표현에, `Command`, `Result`는 실행 의도·결과에 사용한다.
- HTTP DTO와 Application 입력이 동일한 의미·제약이라면 중복 래퍼를 강제하지 않는다. 이 경우 타입 소유 위치와 OpenAPI·Validation 영향은 명시적으로 검토한다.
- 최상위 `dto/` 폴더에 모든 기능의 Request/Response를 다시 모으지 않는다.

---

## 9. 클래스 및 패키지 명명 규칙

### 9.1 패키지

- 패키지는 **소문자 단수형의 구체적인 명사**를 사용한다: `farm`, `orchid`, `mutation`, `ledger`, `auction`, `payment`.
- 업무 명칭이 먼저다: `sales/slip/application`을 `sales/application/slip`보다 우선한다.
- 예외는 기술적 단일 책임이 뚜렷한 `mutation/{engine,ledger,query,verification,config}`와 최상위 공개 경계 `api`, `spi`이다.
- 새 `util`, `helper`, `manager`, `support`, `misc` 패키지는 원칙적으로 만들지 않는다.
- `internal` 폴더를 일괄로 만드는 대신 공개 경계를 좁게 정의하고 나머지를 **기본 비공개**로 간주한다.

### 9.2 클래스 역할 접미사

| 접미사 | 사용 기준 | 예시 |
|---|---|---|
| `Controller` | HTTP 진입점 | `SalesSlipController` |
| `Service` | 유스케이스 실행·트랜잭션 조율 | `SalesSlipOutboundService` |
| `Command` | 상태 변경 의도 | `MoveOrchidGroupsCommand` |
| `Query` / `Criteria` | 검색 조건 | `AuctionLotSearchCriteria` |
| `Result` | Application 처리 결과 | `OrchidGroupMutationResult` |
| `Response` / `Request` | HTTP 입출력 | `SalesSlipResponse` |
| `Reader` | 의미 있는 조회 계약 | `OrchidGroupReader` |
| `Writer` | 의미 있는 쓰기 계약 | `OrchidGroupMutationWriter` |
| `Repository` | 저장소 접근 | `OrchidGroupRepository` |
| `Engine` | 중앙 상태 변경 실행기 | `OrchidGroupMutationEngine` |
| `Recorder` | 변경 사실·원장 기록 | `OrchidGroupMutationRecorder` |
| `Resolver` | 식별자·해석 결과 결정 | `OrchidGroupMutationReplayResolver` |
| `Policy` | 규칙·불변식의 결정 | `OrchidGroupStatusPolicy` |
| `Assembler` | 여러 값을 응답/결과로 조립 | `SalesSlipResponseAssembler` |
| `Adapter` | Port/SPI의 구체적 구현 | `SalesUsageAdapter` |
| `Handler` | 명령 또는 작업 종류별 구현 전략 | `DiscardWorkHandler` |
| `Validator` | 특정 입력·상태 검증 | `MutationCommandValidator` |
| `Configuration` / `Properties` | Spring 구성·설정 바인딩 | `OrchidGroupLedgerWriterConfiguration` |

**접미사는 선택 사항이 아니라 책임의 표현**이다. 단, 클래스마다 반드시 위 접미사를 붙이라는 뜻은 아니다. 도메인 Entity, Enum, 값 객체는 업무 개념명 자체를 사용한다.

`Helper`, `Support`, `Utils`, `Manager`, `Processor`, `Gateway`는 기계적으로 전면 금지하지 않지만, 실제 책임을 위 표의 구체적인 역할로 표현할 수 있으면 교체한다. `Gateway`는 명확한 통합 경계의 의미가 있을 때만 유지한다. Spring Data의 `RepositoryImpl` 등 프레임워크 관례와 연결된 이름은 근거 없이 바꾸지 않는다.

### 9.3 이름 길이와 호환성

- 패키지가 `farm/api/orchid/command`라는 문맥을 제공한다면 `CreateOrchidGroupMutationCommand` → `CreateOrchidGroupCommand` 등의 축약을 검토할 수 있다.
- 반면 원장의 명시적 개념인 `OrchidGroupMutation`, `OrchidGroupMutationEntry`는 도메인 의미를 유지한다.
- **1차 구조 이동에서는 기존 클래스 이름과 공개 직렬화 계약을 유지**한다. 이름 축약은 별도 변경으로 수행한다.
- FQCN 변경이 reflection, Spring Bean 탐색, 테스트 fixture, 직렬화, 저장 JSON 등과 연관되는지 확인한다.

---

## 10. 트랜잭션, 동시성, 원장 보존

디렉터리 리팩터링을 핑계로 운영 불변식을 약화하지 않는다.

1. **유스케이스 완료 책임자가 최상위 트랜잭션을 소유한다.** 같은 트랜잭션 안에서 실행되는 하위 Application API는 원자적 처리 경계를 명시한다.
2. **Mutation은 단일 Writer**다. `OrchidGroup`의 영속 상태 변경과 Mutation/Entry 기록은 동일한 트랜잭션에서 확정한다.
3. **모듈 통합이 잠금 순서를 바꾸지 않는다.** Work 실행, 예약·출고, 보정·취소 등의 기존 선잠금, 재검증, 멱등 접수 순서를 보존한다.
4. **예약과 출고는 구별한다.** Sales 전표 저장의 예약 변경과 실제 출고의 수량 차감은 서로 다른 업무 단계다.
5. **이력 보존을 우선한다.** Work Effect, 판매 스냅샷, Mutation, Audit, Receipt를 단순 리팩터링 중 삭제·재계산하지 않는다.
6. **HTTP 재전송 계약을 유지한다.** 요청 지문, 최초 응답 snapshot, idempotency key의 namespace, JSON 필드의 정규화와 보존 정책을 바꾸지 않는다.
7. **읽기 계약의 반환 시점**을 명확히 한다. 현재 상태와 과거 스냅샷을 같은 값으로 대체하지 않는다.
8. DB 스키마/Flyway 변경은 별도의 설계·운영 검증이 필요한 작업이며 **패키지 이동과 결합하지 않는다.**

구체적 잠금 순서와 운영 이관 제약은 기존 `docs/04-architecture.md`, `docs/02-domain-model.md`, `docs/07-deployment.md`와 테스트를 기준으로 확인한다.

---

## 11. 아키텍처 테스트 및 완료 조건

### 11.1 자동 검증 항목

| 검사 | 예상 결과 |
|---|---|
| 최상위 모듈 허용 컴파일 의존성 | 선언된 방향만 통과 |
| Sales 내부 기능 허용 방향 | 기존 허용 의존표만 통과 |
| 모듈/기능 순환 컴파일 의존성 | 실패 처리 |
| 타 모듈/기능 내부 Application 구현 직접 접근 | 실패 처리 |
| 타 소유자의 Entity·Repository·DB Projection 접근 | 실패 처리 |
| Public API DTO의 Entity·HTTP DTO 누출 | 실패 처리 |
| Mutation 외 `OrchidGroup` 직접 쓰기 | 실패 처리 |
| `OrchidGroup` 저장과 Mutation Entry 누락 | DB 통합·경쟁·롤백 검증 |
| SPI 런타임 순환/트랜잭션 안전성 | 호출 경로 및 통합 테스트로 검토 |

기존 `ModularArchitectureTests`, `ModuleBoundaryInventoryTest` 및 PostgreSQL write fence 검증을 출발점으로 삼는다. 도구는 기존 ArchUnit을 유지하거나 필요 시 Spring Modulith를 추가할 수 있지만, **프레임워크 도입 자체가 목표는 아니다.**

### 11.2 구조 변경 후 회귀 범위

- **Farm:** 생성, 단건·일괄 수정, 이동, 입고, 생성 취소, 상태·revision 원장, 읽기 API.
- **Work:** 즉시 기록, 기간 작업, 분갈이·분주·합식, 폐기, 취소·보정, Work Effect 및 Mutation 연결.
- **Sales:** 전표 생성·수정·취소, 예약·해제, 출고, 경매 Lot, 경매 결과, 정산·입금.
- **공통:** API/OpenAPI drift, 컴파일, 모듈 의존성, 트랜잭션 rollback, 멱등 재전송, 데이터 정합성 대사.

기존 코드가 보유한 모든 경로에 대해 새로운 테스트를 무조건 추가하는 대신, **변경된 경계와 영향받는 호출 경로**를 우선 검증한다. 다만 최종 병합 전에는 기존 전체 CI 및 PostgreSQL 핵심 회귀 검증을 통과해야 한다.

---

## 12. 단계별 전환 계획

| 단계 | 작업 | 변경 원칙 | 완료 조건 |
|---|---|---|---|
| 0 | **현황 인벤토리**: Java 패키지/호출 그래프, 공개 API, SPI, Sales 허용 방향, 운영 전환 CLI 확인 | 기존 동작 변경 없음 | 실제 허용 의존표와 클래스 소유권 확정 |
| 1 | **Sales 통합 경계**: `partner`, `auction`, `settlement` 등 Sales 내부 기능 위치 확정 | 도메인 규칙·테이블·HTTP 경로 유지 | 모듈 경계 테스트 통과 |
| 2 | **기능 우선 패키지 재배치**: `farm`, `work`, `sales` 내부 역할별 이동 | 파일 위치/FQCN 변화 위주, 로직 변경 최소화 | 컴파일·단위 테스트 통과 |
| 3 | **Public API 정리**: 최상위 `api`와 기능 내부 `api` 공개 범위 축소 | 기존 계약·호환 정책 보존 | 내부 구현 직접 참조 제거 |
| 4 | **Port/SPI 정리**: 필요 없는 위임 래퍼 제거, 역방향 협력 명시 | 필요할 때만 인터페이스 유지 | 허용 의존과 런타임 호출 검증 |
| 5 | **Mutation 내부 정돈**: 엔진/원장/조회/대사/설정 분리 | 단일 Writer·DB 원장 불변식 유지 | 원장 대사 및 DB 회귀 검증 |
| 6 | **명명 정규화**: 불명확한 `Support`/`Gateway` 등 점검 | 동작과 저장 형식 변화 없음 | 역할과 이름 일치 |
| 7 | **문서·검증 동기화**: 기존 구조 설명, 아키텍처 테스트, 개발 규칙 갱신 | 구현 상태를 정확히 기록 | 구조·코드·문서 일치 |

**이 단계는 순서 제안이며 구현 완료 상태가 아니다.** 개별 기능의 긴밀한 수정이 필요하다면 작은 PR로 나누되, 리팩터링과 업무 정책 변경을 가능한 한 별도 PR로 분리한다.

### 변경하지 않는 항목

- 기존 HTTP 경로, 요청/응답 의미, OpenAPI 계약
- Flyway 이력 및 운영 DB의 기존 데이터
- 저장된 Mutation/Entry/Receipt 지문·스냅샷 계약
- 기존 업무별 트랜잭션과 잠금 순서
- Sales 하위 기능 사이의 이미 정의된 허용 의존성
- 운영 환경의 writer guard, write fence, backup/recovery 정책

---

## 13. 신규 클래스 배치 체크리스트

새 클래스 또는 패키지를 만들기 전에 다음 질문에 답한다.

- [ ] 어떤 최상위 모듈이 이 데이터·업무를 소유하는가?
- [ ] 어떤 기능이 **업무 완료 책임**을 지는가?
- [ ] 기존 기능 `application`에서 처리할 수 있는가, 아니면 독립적인 최상위 유스케이스인가?
- [ ] 외부 최상위 모듈이 호출하는가? 그렇다면 `{module}/api` 계약이 최소화되어 있는가?
- [ ] 같은 모듈의 다른 기능이 호출하는가? 그렇다면 허용 방향 및 `{feature}/api` 범위에 맞는가?
- [ ] Outbound Port/SPI/Adapter가 실제로 필요한가? 전달만 하는 래퍼는 아닌가?
- [ ] Entity·Repository·HTTP DTO 등 내부 구현이 경계를 넘어 유출되지 않는가?
- [ ] `OrchidGroup` 쓰기라면 Mutation Writer만 통과하는가?
- [ ] Transaction, lock, idempotency, audit, revision, 저장 형식이 유지되는가?
- [ ] 기존 아키텍처 테스트에 위반이 생기지 않는가?

---

## 14. 용어 요약

| 용어 | 정의 |
|---|---|
| 최상위 모듈 | `farm`, `work`, `sales` 등 독립된 업무 소유·공개 계약 경계 |
| 기능(Feature) | 모듈 내부 `slip`, `auction`, `orchid` 등 구체 업무 책임 |
| Public API | 제공 측이 소유하고 허용된 소비자가 호출하는 Java 계약 |
| Internal Feature API | 같은 최상위 모듈의 허용된 다른 기능만 사용하도록 한 계약 |
| Outbound Port | **호출 측**이 필요 기능을 정의한 내부 추상화 |
| SPI | 외부 모듈이 구현하도록 **공개**한 Port 성격의 계약 |
| Adapter | Port/SPI를 구현해 실제 다른 모듈 또는 기술과 연결하는 클래스 |
| Application Service | 유스케이스 실행, 업무 단계 조율, 트랜잭션 소유 |
| Mutation Engine | OrchidGroup 모든 쓰기를 수행하는 Farm 소유 단일 실행기 |
| Ledger | Mutation/Entry/revision/snapshot을 기록·조회하는 변경 원장 |
| HTTP DTO | 웹 전송 계층에 종속된 요청·응답 모델 |

---

## 15. 관련 기준 자료

- `docs/04-architecture.md` — 현행 구현의 모듈별 규칙, 트랜잭션, 아키텍처 테스트 및 운영 제약
- `docs/02-domain-model.md` — Farm/Work/Sales 도메인과 원장·효과·정산의 실제 의미
- `docs/06-api-guide.md` — 현재 공개 HTTP 계약 및 멱등 재전송 규칙
- `docs/07-deployment.md` — 상태 원장, Flyway, 복원·검증·배포 제약
- `docs/features/orchid-group-mutation-transition.md` — Mutation 전환 및 복구 정책 (실제 저장소에서 확인)
- `backend-tree.txt` — 검토 당시 제공된 Java 디렉터리 스냅샷

**우선순위:** 현재 실행 코드·DB 제약·OpenAPI 및 이미 운영 중인 저장 계약을 임의로 덮어쓰지 않는다. **이 문서는 목표 패키지 설계와 앞으로의 개발 규칙**을 정의하며, 기존 구현과 충돌할 때는 차이를 명시한 뒤 별도의 전환 작업으로 해결한다.
