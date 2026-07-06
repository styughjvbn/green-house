# Backend 모듈러 모놀리스 변경 계획

## 목표

`backend`는 하나의 Spring Boot 애플리케이션으로 유지한다.
대신 도메인별 패키지 경계를 명확히 나누고, 판매 관리 영역이 커져도 유지보수 가능하게 만든다.

현재 진행할 범위는 **구조 정리 + 판매 관리 확장 준비**까지다.

---

# 1. 패키지 구조 정리

현재 구조를 아래 기준으로 유지/정리한다.

```text
com.greenhouse.backend
├── common
├── farm
├── work
├── partner
├── sales
├── auction
├── settlement
├── dashboard
└── print
```

## 모듈 책임

```text
common
- 공통 응답
- 공통 예외
- BaseEntity
- 공통 유틸

farm
- 동
- 배드
- 구역
- 난 묶음
- 농장 위치/상태

work
- 작업 이력
- 작업 유형

partner
- 거래처
- 거래처 유형

sales
- 판매전표
- 판매전표 품목
- 직접 판매 전표
- 경매장 판매전표

auction
- 경매 출하
- 경매 lot
- 경매 시도
- 경매 결과
- lot 상태 이력

settlement
- 거래처 정산 설정
- 경매장 정산 묶음
- 입금 이벤트
- 예치금 요약
- 자동 매칭 준비

dashboard
- 대시보드 조회 전용

print
- 출력 조회 전용
```

---

# 2. 각 모듈 내부 구조

각 도메인 모듈은 아래 구조를 기본으로 한다.

```text
<module>
├── domain
├── repository
├── application
├── controller
└── dto
```

## 레이어 책임

```text
domain
- Entity
- Enum
- 도메인 메서드

repository
- JPA Repository
- DB 조회

application
- 트랜잭션 유스케이스
- 도메인 조합 로직

controller
- API 요청/응답
- application 호출

dto
- Request
- Response
```

---

# 3. 의존성 규칙

## 기본 규칙

```text
controller → application → repository → domain
dto는 controller/application에서 사용
domain은 다른 레이어를 몰라야 함
```

## 금지

```text
controller → repository 직접 호출 금지
controller → entity 직접 반환 금지
domain → dto 의존 금지
domain → repository 의존 금지
repository → dto 의존 금지
```

## 허용

초기에는 같은 서버/DB를 사용하므로 application 간 호출은 허용한다.

```text
sales.application → partner.repository
sales.application → auction.repository
sales.application → farm.repository
sales.application → settlement.application
```

단, 추후 아래 방향으로 줄인다.

```text
sales.application → partner.application
sales.application → auction.application
sales.application → farm.application
sales.application → settlement.application
```

---

# 4. 판매 관리 관련 모듈 분리

판매 관리 기능은 하나의 `sales`에 몰아넣지 않는다.

```text
partner
= 거래처 마스터

sales
= 판매전표

auction
= 경매 출하/lot/결과

settlement
= 정산/입금/예치금
```

---

# 5. 경매장 판매전표 관계

## 관계 구조

```text
AuctionShipment
1 ─ 1 SalesSlip

SalesSlip
1 ─ N SalesSlipItem

SalesSlipItem
N ─ 1 AuctionShipmentLot
```

## 기준

```text
AuctionShipment
= 출하일 + 경매장 기준

SalesSlip
= 출하일 기준 경매장 판매전표

AuctionShipmentLot
= 실제 경매장으로 보낸 lot
```

## 코멘트

`SalesSlip`과 `AuctionShipmentLot`을 직접 연결하지 않고, `SalesSlipItem`을 통해 연결한다.

---

# 6. 경매장 정산 묶음 관계

## 관계 구조

```text
AuctionShipmentLot
1 ─ N AuctionAttempt

AuctionAttempt
1 ─ N AuctionResultLine

AuctionSettlement
1 ─ N AuctionSettlementLine

AuctionSettlementLine
N ─ 1 AuctionResultLine
```

## 기준

```text
AuctionSettlement
= 경매장 + 경매일 기준 정산 묶음

AuctionSettlementLine
= 정산 묶음에 포함된 실제 경매 결과 행
```

## 코멘트

`AuctionSettlement`은 `AuctionShipmentLot`이 아니라 `AuctionResultLine`과 연결한다.
부분판매/재경매가 있으면 하나의 lot이 여러 경매일 정산에 걸릴 수 있기 때문이다.

---

# 7. SalesSlip과 AuctionSettlement 관계

직접 연결하지 않는다.

```text
SalesSlip
→ SalesSlipItem
→ AuctionShipmentLot
→ AuctionAttempt
→ AuctionResultLine
→ AuctionSettlementLine
→ AuctionSettlement
```

## 코멘트

출하일 기준 문서와 경매일 기준 정산 묶음은 기준이 다르다.

```text
SalesSlip.saleDate
= 출하일

AuctionSettlement.auctionDate
= 경매일
```

---

# 8. 초기 추가 테이블

이번 변경에서 우선 추가할 테이블은 아래로 제한한다.

```text
partner_settlement_settings
auction_settlements
auction_settlement_lines
partner_payment_events
partner_balance_summaries
```

---

# 9. partner_settlement_settings

거래처별 정산 규칙.

```text
id
partner_id
settlement_unit
payment_delay_days
payment_day_mode
auto_match_enabled
auto_settle_enabled
amount_tolerance
depositor_aliases_json
allow_prepayment
credit_auto_apply_enabled
rule_json
memo
created_at
updated_at
```

## settlement_unit

```text
SALES_SLIP
= 판매전표 기준 정산

AUCTION_DATE
= 경매일 기준 정산

MONTHLY_BATCH
= 월 단위 묶음 정산
```

---

# 10. auction_settlements

경매장 + 경매일 단위 정산 묶음.

```text
id
auction_house_id
auction_date
expected_payment_date
expected_amount
actual_paid_amount
difference_amount
status
paid_at
payment_meta_json
memo
created_at
updated_at
```

## status

```text
PAYMENT_WAITING
PAID
PARTIALLY_PAID
AMOUNT_MISMATCH
REVIEW_REQUIRED
CANCELLED
```

---

# 11. auction_settlement_lines

정산 묶음에 포함된 경매 결과 행.

```text
id
settlement_id
auction_result_line_id
auction_shipment_lot_id
quantity
unit_price
amount
status
created_at
updated_at
```

## status

```text
UNPAID
PAID
EXCLUDED
REVIEW_REQUIRED
```

---

# 12. partner_payment_events

거래처 입금/예치금/수동 정산 이벤트.

```text
id
partner_id
event_type
event_date
amount
source_type
status
target_type
target_id
raw_payload_json
match_result_json
allocation_json
memo
created_at
updated_at
```

## event_type

```text
PAYMENT_RECEIVED
PREPAYMENT_RECEIVED
PREPAYMENT_USED
MANUAL_ADJUSTMENT
REFUND
```

## source_type

```text
MANUAL
BANK_CSV
BANK_SMS
BANK_API
```

## target_type

```text
SALES_SLIP
AUCTION_SETTLEMENT
PARTNER_CREDIT
NONE
```

## 코멘트

초기에는 입금, 매칭, 배정 이력을 별도 테이블로 쪼개지 않고 JSONB에 저장한다.

---

# 13. partner_balance_summaries

거래처별 예치금/미수금 요약.

```text
id
partner_id
credit_amount
receivable_amount
last_event_id
summary_json
created_at
updated_at
```

## 코멘트

정확한 장부 테이블은 나중에 필요할 때 분리한다.
초기에는 현재 잔액을 빠르게 보여주는 요약 테이블로 둔다.

---

# 14. 기존 테이블 수정

## sales_slips

추가/확인할 컬럼.

```text
sales_type
auction_shipment_id
expected_payment_date
paid_amount
remaining_amount
payment_status
payment_meta_json
```

## sales_slip_items

추가/확인할 컬럼.

```text
auction_shipment_lot_id
```

## 코멘트

경매장 판매전표 품목은 `auction_shipment_lot_id`를 가진다.
일반 판매 품목은 기존처럼 `orchid_group_id`를 사용할 수 있다.

---

# 15. JSONB로 초기 처리할 항목

초기에는 아래 항목을 테이블로 만들지 않는다.

```text
은행 거래 원본
자동 매칭 후보
매칭 점수 상세
입금 배정 상세
예치금 사용 상세
정산 규칙 상세
경매장 파싱 규칙
수동 조정 이력 상세
```

저장 위치 예시:

```text
partner_settlement_settings.rule_json
auction_settlements.payment_meta_json
partner_payment_events.raw_payload_json
partner_payment_events.match_result_json
partner_payment_events.allocation_json
partner_balance_summaries.summary_json
```

---

# 16. 추후 분리할 테이블 후보

운영 중 복잡도가 커지면 아래 테이블로 분리한다.

```text
receivables
incoming_payments
payment_allocations
partner_credit_ledger
bank_transactions
settlement_payment_matches
auction_parser_rules
auction_result_sources
```

## 분리 기준

```text
부분입금이 많아짐
입금 1건이 여러 전표에 배정됨
예치금 이력을 장부처럼 봐야 함
은행 거래내역 자동 수집을 본격 적용함
경매장별 파싱 규칙이 복잡해짐
```

---

# 17. 현재 진행할 코드 변경

## 1단계

```text
backend 패키지 구조 점검
모듈별 책임에 맞지 않는 파일 위치 수정
```

## 2단계

```text
partner_settlement_settings 엔티티/리포지토리/서비스 추가
```

## 3단계

```text
auction_settlements 엔티티/리포지토리/서비스 추가
auction_settlement_lines 엔티티/리포지토리 추가
```

## 4단계

```text
partner_payment_events 엔티티/리포지토리/서비스 추가
partner_balance_summaries 엔티티/리포지토리 추가
```

## 5단계

```text
SalesService 분리
```

분리 후보:

```text
SalesQueryService
CreateDirectSalesSlipService
CreateAuctionSalesSlipService
SalesSlipNumberGenerator
```

## 6단계

```text
경매 결과 수신 시 AuctionSettlement 자동 생성
```

처리 흐름:

```text
AuctionResultLine 생성
→ auction_house_id + auction_date 기준 AuctionSettlement 조회/생성
→ AuctionSettlementLine 생성
→ AuctionSettlement.expected_amount 재계산
```

## 7단계

```text
입금 이벤트 수동 등록 기능 추가
```

처리 흐름:

```text
PartnerPaymentEvent 생성
→ target_type / target_id 기준 정산 대상 반영
→ paid_amount / remaining_amount 갱신
→ PartnerBalanceSummary 갱신
```

---

# 18. API 추가 후보

## 거래처 정산 설정

```text
GET /api/partners/{partnerId}/settlement-settings
PUT /api/partners/{partnerId}/settlement-settings
```

## 경매장 정산 묶음

```text
GET /api/auction-settlements
GET /api/auction-settlements/{settlementId}
POST /api/auction-settlements/{settlementId}/confirm-payment
POST /api/auction-settlements/{settlementId}/mark-review-required
```

## 입금/예치금 이벤트

```text
GET /api/partner-payment-events
POST /api/partner-payment-events
GET /api/partners/{partnerId}/balance-summary
```

---

# 19. 작업 순서 요약

```text
1. 모듈 패키지 구조 확정
2. settlement 도메인 기본 테이블 추가
3. 경매 결과 → 경매 정산 묶음 자동 생성
4. 경매 정산 수동 입금 확인
5. 일반 거래처 판매전표 입금 이벤트 처리
6. 예치금 잔액 요약 처리
7. SalesService 유스케이스별 분리
8. 자동 매칭은 JSONB 기반으로 1차 구현
9. 복잡해진 항목만 후속 테이블로 분리
```

---

# 20. 최종 기준

이번 변경의 핵심은 아래 3가지다.

```text
1. 판매전표는 출하일 기준 문서로 유지한다.
2. 경매장 정산은 경매일 기준 묶음으로 관리한다.
3. 입금/예치금/자동매칭은 초기에는 JSONB로 흡수하고, 운영 후 테이블로 분리한다.
```

---

# 21. 적용 현황

2026-07-06 기준 모듈러 모놀리스 1차 구조 정리를 적용했다.

```text
완료
- common, farm, work, partner, sales, auction, settlement 모듈 경계 유지
- dashboard 조회를 farm에서 dashboard 모듈로 분리
- 판매전표 출력 조회를 sales에서 print 모듈로 분리
- 난 묶음 조회와 명령 컨트롤러 분리
- SalesService를 조회, 생성 조정, 직접판매, 경매판매, 번호 생성으로 분해
- package-info.java로 모듈 책임 명시
- 모듈 의존성과 레이어 금지 규칙을 검증하는 테스트 추가

API 호환
- 기존 API 경로와 응답 계약 유지
- DB 테이블과 데이터 변경 없음
```

현재 허용하는 모듈 의존성:

```text
common     -> 없음
partner    -> common
auction    -> common, partner
farm       -> common, work
work       -> common, farm
sales      -> common, auction, farm, partner, settlement
settlement -> common, auction, partner, sales
dashboard  -> common, farm
print      -> common, sales
```

`farm ↔ work`, `sales ↔ settlement`의 상호 의존은 현재 엔티티 관계와 트랜잭션 유스케이스 때문에 유지한다. 다음 경계 강화 단계에서는 repository 직접 참조를 각 모듈의 application API로 교체하고, 양방향 의존을 이벤트 또는 전용 포트로 줄인다.
