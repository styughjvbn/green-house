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
→ 작성중 저장 또는 출하 완료 저장
→ 출하 완료 시 AuctionShipment 생성
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

거래처 생성·수정은 감사 이벤트로 보존한다. 감사 스냅샷에는 이름·유형·활성 상태만 저장하고 대표자명, 전화번호, 주소, 메모는 값 대신 변경 필드명만 기록한다.

## 3. 일반 판매 전표

```text
BusinessPartner
  └─ SalesSlip
      └─ SalesSlipItem
```

규칙:

- 판매 품목은 `SalesSlipItemAllocation`으로 하나 이상의 난 묶음에 배분한다.
- allocation을 만들 때 예약 반영 전 난 묶음 정보를 `CREATION` 스냅샷으로 같이 저장한다.
- 전표 금액은 품목별 수량과 단가의 합계만 관리한다. 공급가액·부가세 정책과 서버 필드가 도입되기 전에는 화면에서 세액을 임의 역산하지 않는다.
- 배분 합계는 품목 수량과 같아야 한다.
- 배분 대상 난 묶음은 판매 품목과 같은 품종이어야 한다.
- 전표 저장 시 난 묶음 `reserved_quantity`를 증가시킨다.
- `작성중` 일반 판매 전표는 수정할 수 있다.
- 수정 시 기존 예약 수량을 해제한 뒤 새 allocation 기준으로 다시 예약한다.
- 수정 예약 해제와 재예약은 각각 `SALES_RELEASE`, `SALES_RESERVE` 이력으로 남긴다.
- 수정으로 거래처가 바뀌면 이전 거래처와 신규 거래처의 미수 잔액을 모두 다시 계산한다.
- 같은 거래처의 전표 생성·수정·취소·입금은 거래처 행을 먼저 잠근 뒤 미수 잔액을 다시 계산한다. 잔액 요약에는 낙관적 버전도 저장해 동시 갱신 유실을 막는다.
- 현재 수정은 기존 품목 행 재사용 방식이며, 품목 개수 변경은 지원하지 않는다.
- 출고 완료 전표로 상태 변경될 때 실제 `quantity` 차감과 `reserved_quantity` 해제가 같이 일어난다.
- 출고·출하 완료는 allocation의 난 묶음을 ID 순서로 잠그고 `OUTBOUND` 스냅샷을 저장한 뒤 경매 shipment/lot 생성과 재고 차감을 수행한다.
- 전표 취소 시에는 삭제하지 않고 `취소` 상태로 전환한다.
- `작성중` 취소는 예약 수량만 해제한다.
- `출고 완료` 취소는 차감된 실제 수량을 복구한다.
- 입금 이력이 있는 일반 판매 전표는 취소할 수 없다.
- 전표 상세 화면의 수정·완료·취소·입금 버튼은 응답의 `availableActions`를 기준으로 표시한다. 서버는 입금 이력과 경매 결과·정산 연결까지 확인해 이 값을 계산하며, 변경 API에서도 같은 규칙을 다시 검증한다.
- 입금 확인은 전표 단위로 기록한다.
- 일반 판매 상태는 `작성중`, `출고 완료`, `취소`만 허용하며 생성 시 `취소` 상태는 허용하지 않는다.
- 판매 목록 화면은 페이지 API를 기준으로 조회한다. 호환용 전체 목록 API는 최신 500건까지만 반환한다.
- 스냅샷은 품종·수량·예약 수량·상태·위치를 과거 시점 분석용으로 보존한다. 기존 자료의 migration 스냅샷은 시점 값을 정확히 복원할 수 없으므로 `MIGRATED_CURRENT_STATE`로 표시한다.

## 4. 경매 판매 전표와 출하 생성

현재 기준 도메인 흐름:

```text
AUCTION_HOUSE 거래처 선택
→ 경매 판매 전표 작성
→ 전표 저장
→ 예약 수량 반영
→ 출하 완료 시 AuctionShipment 생성
→ SalesSlipItem 항목별 AuctionShipmentLot 생성
```

즉, 일반적인 운영 시나리오에서는 사용자가 먼저 경매 판매 전표를 작성하고, 그 전표를 기준으로 출하 기록과 lot가 만들어진다.

경매 판매도 일반 판매와 동일하게 품목별 난 묶음 allocation을 먼저 저장한다. lot는 그 allocation을 원천으로 생성된 전표 품목을 따라간다.

취소 정책:

- 경매 전표도 `취소` 상태를 지원한다.
- 경매 결과나 정산에 연결되지 않은 출하 전표만 취소 가능하다.
- 취소 시 생성된 shipment, lot는 제거하고 난 묶음 수량은 복구한다.
- 경매 판매 상태는 `작성중`, `출하 완료`, `취소`만 허용하며 일반 판매의 `출고 완료`를 사용할 수 없다.

필드 매핑:

- `saleDate` → `AuctionShipment.shipmentDate`
- `partnerId` → `AuctionShipment.auctionHouseId`
- `SalesSlipItem.itemName` → `AuctionShipmentLot.varietyName`
- `SalesSlipItem.genus` → `AuctionShipmentLot.itemName`
- `SalesSlipItem.spec` → `AuctionShipmentLot.shipmentGrade`
- `SalesSlipItem.quantity` → `AuctionShipmentLot.shippedQuantity`

`boxes`는 현재 MVP에서 별도 입력 없이 `null`로 둔다.

경매 출하 선택지는 아직 전표로 변환되지 않은 최신 출하 200건까지만 반환한다.

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

- 경매 판매 전표가 `출하 완료`로 전환될 때 `AuctionShipmentLot` 생성
- 출하 후 `POST /api/auction-lots/{id}/results` 로 수동 경매 결과 입력
- 낙찰/부분 낙찰/유찰/반환 추정을 lot 단위로 기록
- 반환 추정 후 실제 반환일은 `confirm-return`으로 확정
- lot의 결과·반환·수량·상태 변경은 lot 행을 잠근 트랜잭션에서 수행한다. 같은 lot의 같은 경매일·차수 결과는 DB에서도 중복을 허용하지 않는다.

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

애플리케이션 시작 시 정산되지 않은 낙찰 결과만 찾아 기존 정산에 추가하거나 새 정산을 만든다. 이미 정산선에 반영된 과거 결과 전체를 매번 재구축하지 않는다.

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
- 수동 입금 요청은 대상별 `idempotencyKey`를 필수로 받는다. 같은 키·금액·입금일 재요청은 기존 결과를 반환하고, 다른 금액 또는 입금일로 키를 재사용하면 거부한다.
- 잘못 매칭된 입금의 취소·보정 이벤트 API는 아직 제공하지 않으며 후속 범위다.
- 거래처 정산 설정 변경, 수동 입금 이벤트 생성, 대상 전표·정산의 입금 상태 변경은 감사 이벤트로 함께 보존한다.
- 감사 스냅샷에는 입금자명과 메모를 중복 저장하지 않는다.
