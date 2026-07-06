# 정산·입금 자동 매칭 확장 설계서

## 1. 문서 목적

이 문서는 농장 경영 시스템의 판매 관리 영역에서 다음 요구를 함께 처리하기 위한 설계 방향을 정리한다.

```text
1. 출하일 기준 경매장 판매전표 생성
2. 경매장 판매전표와 경매 lot 연결
3. 경매 lot과 경매일 단위 정산 묶음 연결
4. 경매장 정산 묶음과 실제 계좌 입금 매칭
5. 일반 거래처 판매전표 기준 입금 관리
6. 예치금 입금 및 차감 관리
7. 부분입금 처리
8. 자동 매칭 및 수동 확인 이력 보존
```

초기 구현에서는 테이블 수를 과도하게 늘리지 않는다. 핵심 관계는 관계형 컬럼으로 보존하고, 은행 거래 원본, 자동 매칭 후보, 예치금 사용 이력, 파싱 규칙처럼 형태가 자주 변하는 데이터는 `JSONB`로 저장한다. 운영 복잡도가 커지면 JSONB에 담긴 데이터를 별도 테이블로 분리한다.

---

## 2. 최종 설계 판단

핵심 판단은 다음과 같다.

```text
출하 기준 문서        = SalesSlip
경매 lot 추적 기준    = AuctionShipmentLot
실제 판매 결과 기준   = AuctionResultLine
경매일 입금 확인 기준 = AuctionSettlement
일반 입금/예치금 기준 = PartnerPaymentEvent
```

경매장 판매전표와 경매일 단위 정산 묶음은 직접 FK로 연결하지 않는다.

이유는 다음과 같다.

```text
1. 판매전표는 출하일 기준이다.
2. 경매 정산 묶음은 경매일 기준이다.
3. 하나의 출하전표에 포함된 lot이 여러 경매일에 나뉘어 팔릴 수 있다.
4. 하나의 경매일 정산 묶음에는 여러 출하일의 lot이 섞일 수 있다.
5. 따라서 SalesSlip과 AuctionSettlement는 실제로 N:M 관계가 된다.
```

직접 N:M 테이블을 만들기보다는 아래 경로로 간접 조회한다.

```text
SalesSlip
→ SalesSlipItem
→ AuctionShipmentLot
→ AuctionAttempt
→ AuctionResultLine
→ AuctionSettlementLine
→ AuctionSettlement
```

---

## 3. 초기 구현 테이블 전략

초기에는 아래 수준으로 시작한다.

### 3.1 기존 또는 이미 설계된 테이블

```text
business_partners
sales_slips
sales_slip_items
auction_shipments
auction_shipment_lots
auction_attempts
auction_result_lines
auction_lot_status_history
```

### 3.2 새로 추가할 테이블

```text
partner_settlement_settings
auction_settlements
auction_settlement_lines
partner_payment_events
partner_balance_summaries
```

`partner_balance_summaries`는 예치금 잔액과 미배정 입금액을 빠르게 보여주기 위한 요약 테이블이다. 모든 값을 이벤트에서 계산할 수도 있지만, 운영 화면 응답성과 구현 단순화를 위해 초기부터 두는 편이 좋다.

### 3.3 나중에 분리할 수 있는 테이블

초기에는 만들지 않는다.

```text
bank_transactions
settlement_payment_matches
receivables
incoming_payments
payment_allocations
partner_credit_ledger
auction_house_settings
auction_result_import_batches
auction_result_import_rows
```

위 테이블들은 초기에는 `partner_payment_events.raw_payload`, `partner_payment_events.match_payload`, `partner_payment_events.allocation_payload`, `partner_payment_events.balance_snapshot_json`, `partner_settlement_settings.rule_json` 안에 저장한다.

---

## 4. 관계 요약

```text
business_partners
1 ─ 1 partner_settlement_settings
1 ─ N sales_slips
1 ─ N auction_shipments
1 ─ N auction_settlements
1 ─ N partner_payment_events
1 ─ 1 partner_balance_summaries

auction_shipments
1 ─ N auction_shipment_lots
1 ─ 1 sales_slips

sales_slips
1 ─ N sales_slip_items

sales_slip_items
N ─ 1 auction_shipment_lots

auction_shipment_lots
1 ─ N auction_attempts

auction_attempts
1 ─ N auction_result_lines

auction_settlements
1 ─ N auction_settlement_lines

auction_settlement_lines
N ─ 1 auction_result_lines
N ─ 1 auction_shipment_lots

partner_payment_events
N ─ 1 business_partners
N ─ 0..1 sales_slips
N ─ 0..1 auction_settlements
N ─ 0..1 parent partner_payment_events
```

---

## 5. 기존 테이블 수정사항

## 5.1 business_partners

현재 테이블은 그대로 유지한다.

```text
id
created_at
updated_at
address
memo
name
owner_name
phone
partner_type
is_active
```

`business_partners`에는 거래처의 공통 신상 정보만 둔다.

정산 지연일, 자동 정산 여부, 입금자명 alias, 예치금 허용 여부는 `partner_settlement_settings`로 분리한다.

---

## 5.2 sales_slips

판매전표는 일반 판매와 경매 판매를 모두 표현한다.

추가 또는 확인할 컬럼:

```sql
ALTER TABLE sales_slips
ADD COLUMN IF NOT EXISTS sales_type VARCHAR(30) NOT NULL DEFAULT 'DIRECT',
ADD COLUMN IF NOT EXISTS auction_shipment_id BIGINT NULL REFERENCES auction_shipments(id),
ADD COLUMN IF NOT EXISTS expected_payment_date DATE NULL,
ADD COLUMN IF NOT EXISTS paid_amount BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS remaining_amount BIGINT NULL,
ADD COLUMN IF NOT EXISTS payment_meta_json JSONB NULL;
```

권장 제약:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_slips_auction_shipment
ON sales_slips(auction_shipment_id)
WHERE auction_shipment_id IS NOT NULL;
```

의미:

```text
sales_type = DIRECT
- 일반 도매/소매 판매전표
- 판매전표 기준으로 입금 관리

sales_type = AUCTION
- 경매장 출하 판매전표
- 출하일 기준으로 생성
- auction_shipment_id와 1:1 연결
- 정산은 직접 하지 않고 AuctionSettlement에서 처리
```

`remaining_amount`는 다음 방식으로 관리한다.

```text
remaining_amount = total_amount - paid_amount
```

단, 경매 판매전표는 최초 금액이 0원일 수 있다. 경매 정산 입금은 `auction_settlements` 기준으로 관리한다.

---

## 5.3 sales_slip_items

경매장 판매전표 품목과 경매 lot을 연결한다.

```sql
ALTER TABLE sales_slip_items
ADD COLUMN IF NOT EXISTS auction_shipment_lot_id BIGINT NULL REFERENCES auction_shipment_lots(id);
```

권장 제약:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_slip_items_auction_lot
ON sales_slip_items(auction_shipment_lot_id)
WHERE auction_shipment_lot_id IS NOT NULL;
```

의미:

```text
SalesSlip 1 ─ N SalesSlipItem
SalesSlipItem N ─ 1 AuctionShipmentLot
```

개념적으로는 다음과 같다.

```text
경매장 판매전표 1 ─ N 경매 lot
```

DB에서는 `sales_slip_items.auction_shipment_lot_id`를 통해 연결한다.

---

## 5.4 auction_result_lines

초기에는 정산 상태 컬럼을 추가하지 않는 것을 권장한다.

정산 포함 여부는 아래 관계로 계산한다.

```text
auction_result_lines
→ auction_settlement_lines
→ auction_settlements
```

성능 문제가 생기면 나중에 다음 컬럼을 추가한다.

```sql
ALTER TABLE auction_result_lines
ADD COLUMN settlement_status VARCHAR(30) NOT NULL DEFAULT 'UNSETTLED';
```

---

# 6. 신규 테이블 설계

## 6.1 partner_settlement_settings

모든 거래처에 적용 가능한 정산 규칙 테이블이다.

경매장뿐 아니라 일반 도매상, 소매상, 예치금 거래처에도 사용한다.

```sql
CREATE TABLE partner_settlement_settings (
    id BIGSERIAL PRIMARY KEY,

    partner_id BIGINT NOT NULL UNIQUE REFERENCES business_partners(id),

    settlement_unit VARCHAR(30) NOT NULL DEFAULT 'SALES_SLIP',
    payment_delay_days INTEGER NOT NULL DEFAULT 0,
    payment_day_mode VARCHAR(30) NOT NULL DEFAULT 'CALENDAR_DAY',

    auto_match_enabled BOOLEAN NOT NULL DEFAULT false,
    auto_settle_enabled BOOLEAN NOT NULL DEFAULT false,

    amount_tolerance BIGINT NOT NULL DEFAULT 0,
    depositor_aliases JSONB NULL,

    allow_prepayment BOOLEAN NOT NULL DEFAULT false,
    credit_auto_apply_enabled BOOLEAN NOT NULL DEFAULT false,

    rule_json JSONB NULL,
    memo TEXT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### settlement_unit 값

```text
SALES_SLIP
- 판매전표 1장 단위로 입금 확인
- 일반 도매/소매 거래처 기본값

MONTHLY_BATCH
- 월 단위 묶음 정산
- 추후 확장

AUCTION_DATE
- 경매장 + 경매일 단위로 입금 확인
- 경매장 기본값
```

### depositor_aliases 예시

```json
[
  "양재",
  "양재화훼",
  "양재경매장"
]
```

### rule_json 예시

```json
{
  "auctionDays": ["MON", "THU"],
  "resultArrivalTime": "18:00",
  "resultReceiveMethod": "EMAIL",
  "senderEmails": ["result@example.com"],
  "parsingRule": {
    "dateColumn": "일자",
    "varietyColumn": "품종명",
    "gradeColumn": "등급",
    "quantityColumn": "분수량",
    "unitPriceColumn": "단가",
    "amountColumn": "금액",
    "failedKeywords": ["유찰"],
    "returnKeywords": ["반환", "반품"]
  }
}
```

초기에는 `auction_house_settings`를 따로 만들지 않고, 경매장 전용 규칙도 `rule_json`에 넣는다. 추후 경매 결과 자동 수집/파싱 기능이 커지면 `auction_house_settings`로 분리한다.

---

## 6.2 auction_settlements

경매장 + 경매일 단위 정산 묶음이다.

```sql
CREATE TABLE auction_settlements (
    id BIGSERIAL PRIMARY KEY,

    auction_house_id BIGINT NOT NULL REFERENCES business_partners(id),
    auction_date DATE NOT NULL,

    result_received_at TIMESTAMP NULL,
    expected_payment_date DATE NULL,

    gross_amount BIGINT NOT NULL DEFAULT 0,
    fee_amount BIGINT NOT NULL DEFAULT 0,
    deduction_amount BIGINT NOT NULL DEFAULT 0,
    expected_deposit_amount BIGINT NOT NULL DEFAULT 0,

    paid_amount BIGINT NOT NULL DEFAULT 0,
    remaining_amount BIGINT NOT NULL DEFAULT 0,

    status VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_WAITING',

    payment_meta_json JSONB NULL,
    memo TEXT NULL,

    confirmed_at TIMESTAMP NULL,
    confirmed_by VARCHAR(100) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_auction_settlement_house_date
        UNIQUE (auction_house_id, auction_date)
);
```

### status 값

```text
CREATED
- 정산 묶음만 생성됨

PAYMENT_WAITING
- 입금 대기

PARTIALLY_PAID
- 일부 입금됨

PAID
- 정산 완료

AMOUNT_MISMATCH
- 예상액과 입금액이 맞지 않음

REVIEW_REQUIRED
- 사용자 확인 필요

CANCELLED
- 취소
```

### payment_meta_json 예시

```json
{
  "autoMatch": {
    "enabled": true,
    "lastCheckedAt": "2026-07-10T14:35:00",
    "candidateCount": 1,
    "confidenceScore": 95
  },
  "matchedPayments": [
    {
      "paymentEventId": 1001,
      "amount": 1280000,
      "matchedAt": "2026-07-10T14:36:00",
      "matchType": "AUTO"
    }
  ],
  "history": [
    {
      "at": "2026-07-10T14:36:00",
      "type": "AUTO_PAID",
      "message": "입금액과 예정액이 일치하여 자동 정산 완료"
    }
  ]
}
```

초기에는 별도 `auction_settlement_status_history` 테이블을 만들지 않고 `payment_meta_json.history`에 보존한다. 추후 감사 이력이 중요해지면 별도 테이블로 분리한다.

---

## 6.3 auction_settlement_lines

정산 묶음에 포함된 실제 경매 결과 행이다.

```sql
CREATE TABLE auction_settlement_lines (
    id BIGSERIAL PRIMARY KEY,

    settlement_id BIGINT NOT NULL REFERENCES auction_settlements(id),
    auction_result_line_id BIGINT NOT NULL REFERENCES auction_result_lines(id),
    auction_shipment_lot_id BIGINT NOT NULL REFERENCES auction_shipment_lots(id),

    quantity INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    amount BIGINT NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',

    line_meta_json JSONB NULL,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uk_settlement_line_result_line
        UNIQUE (auction_result_line_id)
);
```

### status 값

```text
UNPAID
PAID
PARTIALLY_PAID
EXCLUDED
REVIEW_REQUIRED
```

`auction_shipment_lot_id`는 `auction_result_line → auction_attempt → auction_shipment_lot` 경로로 계산할 수 있지만, 정산 화면 조회 성능과 구현 단순화를 위해 중복 저장한다.

---

## 6.4 partner_payment_events

계좌 입금, 예치금 입금, 입금 배정, 자동 매칭 후보, 수동 확인, 연결 해제 같은 모든 결제 관련 이벤트를 저장한다.

초기에는 `bank_transactions`, `incoming_payments`, `payment_allocations`, `partner_credit_ledger`, `settlement_payment_matches`를 만들지 않고 이 테이블 하나와 JSONB로 처리한다.

```sql
CREATE TABLE partner_payment_events (
    id BIGSERIAL PRIMARY KEY,

    partner_id BIGINT NULL REFERENCES business_partners(id),

    event_type VARCHAR(40) NOT NULL,
    event_date DATE NOT NULL,
    event_time TIME NULL,

    amount BIGINT NOT NULL DEFAULT 0,
    unapplied_amount BIGINT NOT NULL DEFAULT 0,

    target_type VARCHAR(30) NULL,
    target_id BIGINT NULL,

    parent_event_id BIGINT NULL REFERENCES partner_payment_events(id),

    payment_method VARCHAR(30) NULL,
    depositor_name VARCHAR(100) NULL,
    description TEXT NULL,

    external_uid VARCHAR(200) NULL,
    status VARCHAR(30) NOT NULL,

    raw_payload JSONB NULL,
    match_payload JSONB NULL,
    allocation_payload JSONB NULL,
    balance_snapshot_json JSONB NULL,

    memo TEXT NULL,
    created_by VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

권장 인덱스:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_payment_external_uid
ON partner_payment_events(external_uid)
WHERE external_uid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_partner_payment_partner_date
ON partner_payment_events(partner_id, event_date);

CREATE INDEX IF NOT EXISTS idx_partner_payment_target
ON partner_payment_events(target_type, target_id);
```

### event_type 값

```text
PAYMENT_RECEIVED
- 계좌, 현금, 수동 입력 등 실제 입금 발생

PAYMENT_ALLOCATED
- 입금액을 판매전표 또는 경매 정산 묶음에 배정

PREPAYMENT_RECEIVED
- 예치금 입금

CREDIT_APPLIED
- 예치금을 전표 또는 정산 대상에 차감 적용

CREDIT_REFUND
- 예치금 환불

AUTO_MATCH_CANDIDATE
- 자동 매칭 후보 생성

AUTO_MATCH_CONFIRMED
- 자동 정산 완료

MANUAL_MATCH_CONFIRMED
- 사용자가 수동으로 입금 연결

MATCH_REJECTED
- 추천 매칭 거절

PAYMENT_UNLINKED
- 연결 해제

ADJUSTMENT
- 수동 보정
```

### target_type 값

```text
SALES_SLIP
AUCTION_SETTLEMENT
NONE
```

### status 값

```text
UNAPPLIED
PARTIALLY_APPLIED
FULLY_APPLIED
CANDIDATE
CONFIRMED
REJECTED
CANCELLED
```

### 계좌 입금 원본 raw_payload 예시

```json
{
  "source": "BANK_CSV",
  "bankName": "농협",
  "accountMasked": "123-****-456",
  "transactionDate": "2026-07-10",
  "transactionTime": "14:32:00",
  "depositAmount": 1280000,
  "balanceAfter": 4300000,
  "depositorName": "양재화훼",
  "description": "양재화훼 정산"
}
```

### 자동 매칭 match_payload 예시

```json
{
  "targetType": "AUCTION_SETTLEMENT",
  "targetId": 77,
  "score": 95,
  "reasons": [
    "AMOUNT_EXACT_MATCH",
    "PAYMENT_DATE_IN_RANGE",
    "DEPOSITOR_ALIAS_MATCH"
  ],
  "amountTolerance": 1000,
  "expectedAmount": 1280000,
  "actualAmount": 1280000
}
```

### 배정 allocation_payload 예시

```json
{
  "allocations": [
    {
      "targetType": "SALES_SLIP",
      "targetId": 501,
      "allocatedAmount": 300000
    },
    {
      "targetType": "SALES_SLIP",
      "targetId": 502,
      "allocatedAmount": 200000
    }
  ]
}
```

### 예치금 balance_snapshot_json 예시

```json
{
  "creditBalanceBefore": 1000000,
  "creditUsed": 300000,
  "creditBalanceAfter": 700000,
  "receivableBalanceAfter": 0
}
```

---

## 6.5 partner_balance_summaries

거래처별 현재 예치금/미배정 입금 요약 테이블이다.

```sql
CREATE TABLE partner_balance_summaries (
    id BIGSERIAL PRIMARY KEY,

    partner_id BIGINT NOT NULL UNIQUE REFERENCES business_partners(id),

    credit_balance BIGINT NOT NULL DEFAULT 0,
    unapplied_payment_amount BIGINT NOT NULL DEFAULT 0,
    receivable_balance BIGINT NOT NULL DEFAULT 0,

    last_payment_event_id BIGINT NULL REFERENCES partner_payment_events(id),

    summary_json JSONB NULL,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
```

초기에는 다음 값만 정확히 관리한다.

```text
credit_balance
- 예치금 잔액

unapplied_payment_amount
- 들어왔지만 아직 전표/정산에 배정되지 않은 금액

receivable_balance
- 일반 판매전표 미수금 요약
```

경매장 정산 미수금은 `auction_settlements` 기준으로 별도 집계해도 된다.

---

# 7. 핵심 업무 흐름

## 7.1 경매장 출하 판매전표 생성

```text
1. 경매장 출하 기록 생성
   AuctionShipment 생성

2. 출하 lot 생성
   AuctionShipmentLot N개 생성

3. 출하일 기준 경매장 판매전표 생성
   SalesSlip.salesType = AUCTION
   SalesSlip.saleDate = AuctionShipment.shipmentDate
   SalesSlip.partnerId = AuctionShipment.auctionHouseId
   SalesSlip.auctionShipmentId = AuctionShipment.id

4. lot별 판매전표 품목 생성
   SalesSlipItem.auctionShipmentLotId = AuctionShipmentLot.id
   수량 = 출하 수량
   단가 = 0 또는 참고 단가
   금액 = 0 또는 참고 금액
```

경매 판매전표는 “출하 증빙/문서” 역할이다. 실제 입금 정산 기준은 아니다.

---

## 7.2 경매 결과 수신 후 정산 묶음 생성

```text
1. 경매 결과 수신
2. AuctionAttempt 생성 또는 갱신
3. AuctionResultLine 생성
4. 경매장 + 경매일 기준 AuctionSettlement 조회
5. 없으면 생성
6. 판매 금액이 있는 AuctionResultLine을 AuctionSettlementLine으로 연결
7. gross_amount, expected_deposit_amount, remaining_amount 갱신
```

정산 묶음 생성 기준:

```text
auction_house_id + auction_date
```

중복 생성 방지:

```text
UNIQUE (auction_house_id, auction_date)
```

---

## 7.3 경매장 입금 확인

### 수동 확인 1차 구현

```text
1. 경매장 정산 탭에서 정산 묶음 선택
2. 실제 입금액, 입금일, 메모 입력
3. PartnerPaymentEvent 생성
   event_type = PAYMENT_RECEIVED
   target_type = AUCTION_SETTLEMENT
   target_id = auction_settlement.id
4. PartnerPaymentEvent 생성
   event_type = PAYMENT_ALLOCATED 또는 MANUAL_MATCH_CONFIRMED
5. AuctionSettlement.paid_amount 갱신
6. AuctionSettlement.remaining_amount 갱신
7. 금액이 모두 맞으면 status = PAID
8. 일부만 입금되면 status = PARTIALLY_PAID
9. 차이가 있으면 status = AMOUNT_MISMATCH 또는 REVIEW_REQUIRED
```

### 자동 매칭 후속 구현

```text
1. 은행 CSV, 문자, 이메일, API에서 입금 이벤트 수집
2. PartnerPaymentEvent 생성
   event_type = PAYMENT_RECEIVED
   status = UNAPPLIED
3. partner_settlement_settings 기준으로 후보 검색
4. match_payload에 점수와 이유 저장
5. 90점 이상이면 자동 확정
6. 60~89점이면 확인 필요
7. 60점 미만이면 미매칭
```

---

## 7.4 일반 거래처 판매전표 입금 처리

```text
1. 일반 판매전표 생성
   SalesSlip.salesType = DIRECT
   SalesSlip.totalAmount = 품목 금액 합계
   SalesSlip.paidAmount = 0
   SalesSlip.remainingAmount = totalAmount

2. 거래처 정산 설정 조회
   settlement_unit = SALES_SLIP
   payment_delay_days 기준 expected_payment_date 계산

3. 입금 발생
   PartnerPaymentEvent 생성
   event_type = PAYMENT_RECEIVED

4. 판매전표와 금액/입금자명/예정일 기준 매칭

5. 배정 이벤트 생성
   event_type = PAYMENT_ALLOCATED
   target_type = SALES_SLIP
   target_id = sales_slip.id

6. SalesSlip.paid_amount 갱신
7. SalesSlip.remaining_amount 갱신
8. payment_status 갱신
```

상태 예시:

```text
미입금
부분입금
입금 완료
확인 필요
```

---

## 7.5 예치금 처리

### 예치금 입금

```text
1. 거래처가 선입금
2. PartnerPaymentEvent 생성
   event_type = PREPAYMENT_RECEIVED
   status = UNAPPLIED
   amount = 입금액
   unapplied_amount = 입금액
3. PartnerBalanceSummary.credit_balance 증가
```

### 예치금으로 판매전표 정산

```text
1. 판매전표 생성
2. 거래처의 credit_balance 확인
3. credit_auto_apply_enabled = true이면 자동 차감 후보 생성
4. 사용자가 확인하거나 자동 적용
5. PartnerPaymentEvent 생성
   event_type = CREDIT_APPLIED
   target_type = SALES_SLIP
   target_id = sales_slip.id
6. SalesSlip.paid_amount 증가
7. PartnerBalanceSummary.credit_balance 감소
```

### 예치금 환불

```text
1. 환불 처리
2. PartnerPaymentEvent 생성
   event_type = CREDIT_REFUND
3. PartnerBalanceSummary.credit_balance 감소
```

---

## 7.6 부분입금 처리

부분입금은 대상의 `paid_amount`와 `remaining_amount`로 처리한다.

예시:

```text
판매전표 총액: 1,000,000원
1차 입금: 300,000원
2차 입금: 700,000원
```

처리:

```text
1차 PAYMENT_ALLOCATED 300,000
SalesSlip.paid_amount = 300,000
SalesSlip.remaining_amount = 700,000
SalesSlip.payment_status = 부분입금

2차 PAYMENT_ALLOCATED 700,000
SalesSlip.paid_amount = 1,000,000
SalesSlip.remaining_amount = 0
SalesSlip.payment_status = 입금 완료
```

경매 정산 묶음도 동일하다.

```text
AuctionSettlement.paid_amount
AuctionSettlement.remaining_amount
AuctionSettlement.status
```

---

# 8. 자동 매칭 규칙

초기 자동 매칭은 규칙 기반 점수로 처리한다.

```text
금액 정확히 일치: +60점
입금일이 예정일 ±2일: +20점
입금자명 alias 일치: +20점
같은 금액 후보가 여러 개: -40점
금액 차이가 허용 오차 초과: -50점
이미 다른 대상에 배정된 입금: -100점
```

처리 기준:

```text
90점 이상
- 자동 정산 완료 가능

60~89점
- 추천 매칭
- 사용자 확인 필요

60점 미만
- 미매칭
```

자동 처리 금지 조건:

```text
1. 같은 금액의 후보가 여러 개 존재
2. 입금액과 예상액 차이가 허용 오차 초과
3. 입금자명 alias가 전혀 맞지 않음
4. 이미 다른 대상에 전액 배정된 입금
5. 대상 거래처가 비활성 상태
```

---

# 9. JSONB 우선 저장과 추후 테이블 분리 전략

초기에는 JSONB로 저장하고, 운영 복잡도가 커지면 아래처럼 분리한다.

| 초기 JSONB 위치 | 추후 분리 테이블 | 분리 조건 |
|---|---|---|
| `partner_payment_events.raw_payload` | `bank_transactions` | 은행 거래내역을 많이 조회하거나 중복 검증이 중요해질 때 |
| `partner_payment_events.match_payload` | `settlement_payment_matches` | 자동 매칭 후보/거절 이력을 별도 관리해야 할 때 |
| `partner_payment_events.allocation_payload` | `payment_allocations` | 입금 1건이 여러 전표에 자주 나뉘어 배정될 때 |
| `partner_payment_events.balance_snapshot_json` | `partner_credit_ledger` | 예치금 입출금 장부를 정확히 출력해야 할 때 |
| `sales_slips.paid_amount`, `auction_settlements.paid_amount` | `receivables` | 전표/정산/수동청구를 하나의 미수금 모델로 통합해야 할 때 |
| `partner_settlement_settings.rule_json` | `auction_house_settings` | 경매 결과 수신/파싱 설정이 커질 때 |
| `partner_settlement_settings.rule_json.parsingRule` | `auction_parser_rules` | 경매장별 파서 버전 관리가 필요할 때 |
| `auction_settlements.payment_meta_json.history` | `auction_settlement_status_history` | 정산 상태 변경 감사 이력이 중요해질 때 |

---

# 10. 화면 수정사항

## 10.1 판매 관리 탭 구조

```text
판매 관리
├─ 판매 전표
├─ 출하·경매 추적
├─ 경매장 정산
└─ 거래처 관리
```

## 10.2 거래처 관리 탭

거래처 상세에 `정산 설정` 영역을 추가한다.

표시/입력 항목:

```text
정산 단위
- 판매전표 단위
- 월 정산 단위
- 경매일 단위

입금 지연일
- 0일, 3일, 4일, 7일 등

입금자명 후보
- 복수 입력

자동 매칭 사용 여부
자동 정산 완료 사용 여부
금액 허용 오차
예치금 허용 여부
예치금 자동 차감 여부
```

경매장인 경우 `경매 결과 수신/파싱 설정` 접이식 영역을 추가한다.

```text
경매 요일
결과 도착 예상 시간
결과 수신 방식
발신 이메일/전화번호
파싱 규칙 JSON 또는 간단 컬럼 매핑 UI
```

초기에는 `rule_json`으로 저장한다.

## 10.3 경매장 정산 탭

목록 컬럼:

```text
경매장
경매일
정산 예정일
총 낙찰액
예상 입금액
입금액
잔액
상태
포함 결과 수
확인 필요 여부
```

상세 영역:

```text
정산 요약
포함 경매 결과 행 목록
연결된 입금 이벤트
자동 매칭 후보
상태 변경 이력
```

버튼:

```text
입금 확인
자동 매칭 실행
수동 입금 연결
연결 해제
정산표 출력
확인 필요 처리
```

## 10.4 판매 전표 상세

일반 판매전표 상세에 입금 영역을 추가한다.

```text
총액
입금액
잔액
입금 상태
입금 예정일
연결된 입금 이벤트
예치금 차감 내역
```

버튼:

```text
입금 확인
예치금 적용
자동 매칭 실행
입금 연결 해제
```

---

# 11. API 초안

## 11.1 거래처 정산 설정

```http
GET /api/business-partners/{partnerId}/settlement-settings
PUT /api/business-partners/{partnerId}/settlement-settings
```

## 11.2 경매 정산

```http
GET /api/auction-settlements?auctionHouseId=&from=&to=&status=
GET /api/auction-settlements/{settlementId}
POST /api/auction-settlements/rebuild?auctionHouseId=&auctionDate=
POST /api/auction-settlements/{settlementId}/confirm-payment
POST /api/auction-settlements/{settlementId}/run-auto-match
POST /api/auction-settlements/{settlementId}/unlink-payment
```

## 11.3 일반 판매전표 입금

```http
POST /api/sales-slips/{salesSlipId}/confirm-payment
POST /api/sales-slips/{salesSlipId}/apply-credit
POST /api/sales-slips/{salesSlipId}/run-auto-match
POST /api/sales-slips/{salesSlipId}/unlink-payment
```

## 11.4 입금 이벤트

```http
GET /api/partner-payment-events?partnerId=&from=&to=&status=
POST /api/partner-payment-events/manual-payment
POST /api/partner-payment-events/prepayment
POST /api/partner-payment-events/import-bank-csv
POST /api/partner-payment-events/{eventId}/allocate
POST /api/partner-payment-events/{eventId}/reject-match
```

## 11.5 거래처 잔액

```http
GET /api/business-partners/{partnerId}/balance-summary
```

---

# 12. 구현 순서

## 1단계: 경매장 판매전표 - lot - 정산 묶음 연결

```text
1. sales_slips.sales_type 확인/추가
2. sales_slips.auction_shipment_id 확인/추가
3. sales_slip_items.auction_shipment_lot_id 추가
4. auction_settlements 추가
5. auction_settlement_lines 추가
6. 경매 결과 수신 시 auction_house_id + auction_date 기준 정산 묶음 생성
7. 판매 금액이 있는 auction_result_lines를 settlement line으로 연결
```

## 2단계: 정산 설정 일반화

```text
1. partner_settlement_settings 추가
2. 거래처 관리 탭에 정산 설정 UI 추가
3. 경매장/일반 거래처 모두 동일 설정 사용
4. 경매장 전용 파싱 규칙은 rule_json에 저장
```

## 3단계: 수동 입금 확인

```text
1. partner_payment_events 추가
2. partner_balance_summaries 추가
3. 경매 정산 묶음 수동 입금 확인
4. 일반 판매전표 수동 입금 확인
5. 부분입금 상태 처리
```

## 4단계: 예치금 처리

```text
1. PREPAYMENT_RECEIVED 이벤트 생성
2. partner_balance_summaries.credit_balance 갱신
3. CREDIT_APPLIED 이벤트 생성
4. 판매전표에 예치금 차감 적용
```

## 5단계: 계좌 자동 매칭

```text
1. 은행 CSV 업로드
2. PAYMENT_RECEIVED 이벤트 자동 생성
3. 금액/입금일/입금자명 기반 후보 계산
4. match_payload 저장
5. 고신뢰 후보 자동 확정
6. 애매한 후보 확인 필요 처리
```

## 6단계: 테이블 분리 고도화

운영하면서 복잡도가 커지면 아래 순서로 분리한다.

```text
1. bank_transactions
2. payment_allocations
3. partner_credit_ledger
4. settlement_payment_matches
5. receivables
6. auction_house_settings / auction_parser_rules
```

---

# 13. 최종 요약

초기 구현에서 새로 추가할 핵심 테이블은 다음 5개다.

```text
partner_settlement_settings
auction_settlements
auction_settlement_lines
partner_payment_events
partner_balance_summaries
```

기존 테이블 수정은 다음이 핵심이다.

```text
sales_slips.sales_type
sales_slips.auction_shipment_id
sales_slips.expected_payment_date
sales_slips.paid_amount
sales_slips.remaining_amount
sales_slips.payment_meta_json

sales_slip_items.auction_shipment_lot_id
```

초기 설계 원칙은 다음과 같다.

```text
1. 돈과 상태의 핵심 관계는 관계형 FK로 보존한다.
2. 변동성이 큰 원본/매칭/이력/파싱 규칙은 JSONB로 둔다.
3. SalesSlip과 AuctionSettlement는 직접 연결하지 않는다.
4. SalesSlip → Lot → ResultLine → Settlement 경로로 간접 연결한다.
5. 일반 거래처와 경매장의 정산 규칙은 partner_settlement_settings로 일반화한다.
6. 예치금과 부분입금은 partner_payment_events와 partner_balance_summaries로 시작한다.
7. 운영 복잡도가 실제로 생기면 JSONB 영역을 별도 테이블로 분리한다.
```

---

# 14. 구현 현황

2026-07-06 기준 1~2단계 중 다음 항목을 구현했다.

```text
완료
- auction_settlements, auction_settlement_lines 모델
- 경매장 + 경매일 기준 정산 묶음 생성과 멱등 재구성
- 낙찰 금액이 있는 auction_result_lines 연결
- 정산 목록·상세·재구성 API
- 판매 관리의 경매장 정산 탭 API 연결
- partner_settlement_settings 모델과 조회·저장 API
- 거래처 관리 탭의 정산 설정 UI
- 경매 정산 예상 입금일의 달력일·영업일 계산

후속
- 수수료·공제액 계산 규칙
- 수동 입금, 부분입금, 예치금, 자동 매칭
- 일반 판매전표 예상 입금일 연동
- 공휴일을 반영한 영업일 계산
```

현재 경매 결과를 생성하는 별도 수신 API가 없으므로 애플리케이션 시작 시 기존 낙찰 결과를 멱등 재구성하고, 운영 화면에서 선택한 정산을 다시 계산할 수 있게 했다. 결과 수신 명령이 추가되면 같은 재구성 서비스를 해당 트랜잭션에서 호출한다.

설계와 현재 구현의 차이: 경매장 `rule_json`은 저장·편집만 가능하고 결과 자동 수신·파싱에는 아직 연결되지 않았다. 정산 설정은 일반 거래처에도 저장되지만 일반 판매전표의 예상 입금일 계산은 3단계 입금 처리와 함께 연결한다.
