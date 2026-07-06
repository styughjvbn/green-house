# Backend Modular Monolith Guide

`backend`는 하나의 Spring Boot 애플리케이션과 하나의 DB를 유지하되, 도메인별 모듈 경계를 명확히 둔다. 목표는 지금은 단순한 배포/트랜잭션을 유지하고, 판매 관리처럼 커지는 도메인을 나중에 별도 서비스로 뽑을 수 있게 만드는 것이다.

## 모듈 경계

```text
com.greenhouse.backend
├── common      공통 응답, 예외, BaseEntity, 공통 유틸
├── farm        농장 구조, 동/배드/구역, 난 묶음, 위치/재고 상태
├── work        작업 이력, 작업 유형
├── partner     거래처 마스터
├── sales       판매전표, 판매전표 품목, 판매 화면 유스케이스
├── auction     경매 출하, lot, 경매 시도, 경매 결과, lot 상태 이력
├── settlement  거래처 정산 규칙, 경매 정산 묶음, 입금/예치금 확장 지점
├── dashboard   대시보드 조회 모델
└── print       출력 조회 모델
```

## 레이어 규칙

각 모듈은 아래 레이어를 기준으로 나눈다.

```text
<module>
├── domain       엔티티, 값 객체, enum, 도메인 행위
├── repository   JPA Repository, Query Repository
├── application  트랜잭션 유스케이스 서비스
├── controller   HTTP API 어댑터
└── dto          요청/응답 DTO
```

규칙은 다음과 같다.

1. `domain`은 `application`, `controller`, `dto`, `repository`를 몰라야 한다.
2. `repository`는 `controller`, `dto`, `application`을 몰라야 한다.
3. `controller`는 `repository`에 직접 접근하지 않고 `application`만 호출한다.
4. `controller`는 JPA Entity를 직접 응답하지 않고 DTO를 반환한다.
5. 다른 모듈의 DB 변경은 Repository 직접 호출보다 Application Service 또는 이벤트를 우선한다.

이 규칙은 `ModularMonolithArchitectureTest`에서 일부 강제한다.

## 현재 허용하는 모듈 의존성

초기 구현 속도를 위해 같은 DB와 같은 트랜잭션을 사용한다. 따라서 일부 application service가 다른 모듈 repository를 직접 호출하는 것은 당장 금지하지 않는다. 다만 아래 방향으로 점진적으로 줄인다.

```text
현재 허용
sales.application -> partner.repository
sales.application -> farm.repository
sales.application -> auction.repository

점진 개선 목표
sales.application -> partner.application 또는 partner.port
sales.application -> farm.application 또는 farm.port
sales.application -> auction.application 또는 auction.port
```

## 판매 관리 분리 전략

판매 관리는 향후 독립 서비스 후보이다.

```text
sales + auction + settlement + partner 일부
= 향후 sales-service 후보
```

하지만 당장은 물리적으로 서버/DB를 나누지 않는다. 이유는 출하, lot 생성, 판매전표 생성, 농장 재고 변경이 하나의 업무 흐름 안에서 일어나기 때문이다.

현재 권장 흐름은 다음과 같다.

```text
1. 단일 Spring Boot 애플리케이션 유지
2. sales / auction / settlement 패키지 경계 강화
3. API prefix는 /api/sales, /api/auction, /api/settlements로 분리
4. JSONB로 결제/정산 확장 필드를 흡수
5. 운영 복잡도가 커지면 payment/receivable/ledger 테이블로 분리
6. 마지막 단계에서 sales-service로 물리 분리 검토
```

## 패키지 의존성 점검

```bash
./gradlew test
```

위 명령으로 기존 통합 테스트와 ArchUnit 기반 아키텍처 테스트를 함께 실행한다.

## 리팩터링 우선순위

1. 컨트롤러가 엔티티를 직접 반환하지 않도록 DTO 유지
2. 서비스가 너무 커지면 유스케이스 단위 서비스로 분리
   - `CreateDirectSalesSlipService`
   - `CreateAuctionSalesSlipService`
   - `RecordPartnerPaymentService`
   - `CreateAuctionSettlementService`
3. 판매 관리가 농장 repository를 직접 호출하는 부분을 `FarmInventoryService` 또는 port로 이동
4. 경매 정산/입금/예치금 기능은 `settlement` 내부에서 먼저 JSONB 기반으로 구현
5. 데이터 구조가 안정되면 `payment`, `receivable`, `ledger` 하위 모듈로 분리
