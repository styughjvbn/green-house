# 판매·경매·정산 관리

## 1. 목적

판매 관리는 일반 판매 전표, 경매장 출하 전표, 경매 lot 추적, 경매 정산, 입금 확인을 함께 다룬다.

기본 흐름:

```text
일반 판매
→ 판매 전표
→ 입금 확인

경매 판매
→ 경매장용 판매 전표 생성
→ 전표 저장 시 AuctionShipment 생성
→ 전표 항목별 AuctionShipmentLot 생성
→ 경매 결과 / 유찰 / 반환 추적
→ 경매 정산
→ 입금 확인
```

## 2. 거래처

거래처 유형:

- `WHOLESALE`
- `RETAIL`
- `AUCTION_HOUSE`

경매장도 거래처로 관리한다. 다만 일반 거래처와 달리 경매 출하 lot, 정산 묶음, 입금 지연일, 정산 단위 설정이 필요하다.

## 3. 일반 판매 전표

```text
BusinessPartner
  └─ SalesSlip
      └─ SalesSlipItem
```

규칙:

- 판매 품목은 `SalesSlipItemAllocation`으로 하나 이상의 난 묶음에 배분한다.
- 배분 합계는 품목 수량과 같아야 한다.
- 배분 대상 난 묶음은 판매 품목과 같은 품종이어야 한다.
- 전표 저장 시 난 묶음 `reserved_quantity`를 증가시킨다.
- `작성중` 일반 판매 전표는 수정할 수 있다.
- 수정 시 기존 예약 수량을 해제한 뒤 새 allocation 기준으로 다시 예약한다.
- 현재 수정은 기존 품목 행 재사용 방식이며, 품목 개수 변경은 지원하지 않는다.
- 출고 완료 전표로 상태 변경될 때 실제 `quantity` 차감과 `reserved_quantity` 해제가 같이 일어난다.
- 입금 확인은 전표 단위로 기록한다.

## 4. 경매 판매 전표와 출하 생성

현재 기준 도메인 흐름:

```text
AUCTION_HOUSE 거래처 선택
→ 경매 판매 전표 작성
→ 전표 저장
→ AuctionShipment 생성
→ SalesSlipItem 항목별 AuctionShipmentLot 생성
```

즉, 일반적인 운영 시나리오에서는 사용자가 먼저 경매 판매 전표를 작성하고, 그 전표를 기준으로 출하 기록과 lot가 만들어진다.

경매 판매도 일반 판매와 동일하게 품목별 난 묶음 allocation을 먼저 저장한다. lot는 그 allocation을 원천으로 생성된 전표 품목을 따라간다.

필드 매핑:

- `saleDate` → `AuctionShipment.shipmentDate`
- `partnerId` → `AuctionShipment.auctionHouseId`
- `SalesSlipItem.itemName` → `AuctionShipmentLot.varietyName`
- `SalesSlipItem.genus` → `AuctionShipmentLot.itemName`
- `SalesSlipItem.spec` → `AuctionShipmentLot.shipmentGrade`
- `SalesSlipItem.quantity` → `AuctionShipmentLot.shippedQuantity`

`boxes`는 현재 MVP에서 별도 입력 없이 `null`로 둔다.

## 5. 경매 추적

경매는 전표 항목이 아니라 lot 단위로 추적한다.

이유:

- 출하와 실제 판매 시점이 다르다.
- 같은 lot이 유찰 후 다시 경매될 수 있다.
- 일부만 판매되고 일부는 대기 또는 반환될 수 있다.

```text
AuctionShipmentLot
  ├─ AuctionAttempt
  ├─ AuctionResultLine
  └─ AuctionLotStatusHistory
```

현재 운영 입력 방식:

- 경매 판매 전표 저장 시 `AuctionShipmentLot` 생성
- 출하 후 `POST /api/auction-lots/{id}/results` 로 수동 경매 결과 입력
- 낙찰/부분 낙찰/유찰/반환 추정을 lot 단위로 기록
- 반환 추정 후 실제 반환일은 `confirm-return`으로 확정

## 6. AuctionLot 상태

- `WAITING`
- `IN_PROGRESS`
- `SOLD`
- `PARTIALLY_SOLD`
- `REAUCTION_WAITING`
- `RETURN_INFERRED`
- `PARTIALLY_RETURNED`
- `RETURNED`
- `QUANTITY_MISMATCH`
- `REVIEW_REQUIRED`
- `CANCELLED`

상태 변경은 항상 이력으로 남긴다.

## 7. 경매 정산

```text
AuctionSettlement
  └─ AuctionSettlementLine
```

정산에는 판매 완료 lot, 부분 판매분, 수수료 차감 결과가 반영된다.

정산 상태 예시:

- 생성됨
- 입금 대기
- 부분 입금
- 입금 완료
- 보류

## 8. 입금 처리

입금 이벤트는 `PartnerPaymentEvent`로 보존한다.

대상:

- 일반 판매 전표
- 경매 정산
- 대상 미지정 입금

규칙:

- 입금 이벤트는 삭제하지 않는다.
- 잘못 매칭된 경우 취소 또는 보정 이벤트를 추가한다.
