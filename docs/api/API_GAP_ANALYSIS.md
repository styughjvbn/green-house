# API Gap Analysis

이 문서는 과거 md/API 초안과 실제 구현 OpenAPI의 차이를 Codex가 오해하지 않도록 정리한다.

## 기준

- 실제 구현 기준: `docs/api/openapi.yaml`
- 실제 구현 operations: `67`
- 실제 구현 path entries: `50`
- 과거 md/추가안에서 수집한 endpoint 후보: `69`
- 후보 중 OpenAPI와 매칭되는 endpoint: `43`
- 후보 중 OpenAPI에 없는 endpoint: `26`

## 판단 규칙

- `openapi.yaml` 또는 Controller에 없는 endpoint는 구현된 API로 가정하지 않는다.
- `{lotId}`, `{id}`, `{settlementId}`처럼 path variable 이름만 다른 경우는 같은 endpoint로 본다.
- query parameter 차이는 path/method가 같으면 같은 endpoint로 본다. 정확한 query 목록은 OpenAPI를 우선한다.

## OpenAPI에 구현되어 있는 주요 endpoint


### 농장 구조

- `GET /api/bed-zones`
- `GET /api/bed-zones/{bedZoneId}`
- `GET /api/houses`
- `GET /api/houses/{houseId}`
- `GET /api/orchid-groups`
- `GET /api/orchid-groups/{orchidGroupId}/lineage`
- `GET /api/physical-beds`
- `GET /api/physical-beds/{physicalBedId}`

### 농장 현황

- `GET /api/dashboard/summary`
- `GET /api/farm-status/map`
- `GET /api/farm-status/orchid-groups`
- `GET /api/farm-status/zoom`

### 난 묶음 명령/정밀 배치

- `GET /api/bed-zones/{bedZoneId}/placement-profile`
- `PUT /api/bed-zones/{bedZoneId}/placement-profile`
- `POST /api/orchid-groups`
- `DELETE /api/orchid-groups/{orchidGroupId}`
- `PATCH /api/orchid-groups/{orchidGroupId}`
- `PATCH /api/orchid-groups/{orchidGroupId}/move`
- `POST /api/work-operations/repot`
- `GET /api/work-operations/{workOperationId}/repot-results`

### 작업 이력

- `GET /api/work-records`
- `POST /api/work-records`
- `GET /api/work-types`
- `POST /api/work-types`
- `PATCH /api/work-types/reorder`
- `PATCH /api/work-types/{workTypeId}`

### 품종/입고/자재

- `GET /api/inbound-records`
- `POST /api/inbound-records`
- `GET /api/inbound-records/{inboundRecordId}`
- `PATCH /api/inbound-records/{inboundRecordId}`
- `DELETE /api/inbound-records/{inboundRecordId}`
- `POST /api/inbound-records/{inboundRecordId}/cancel`
- `POST /api/inbound-records/{inboundRecordId}/potting`
- `GET /api/materials`
- `POST /api/materials`
- `GET /api/materials/{materialId}`
- `PATCH /api/materials/{materialId}`
- `DELETE /api/materials/{materialId}`
- `PATCH /api/materials/{materialId}/deactivate`
- `GET /api/varieties`
- `POST /api/varieties`
- `GET /api/varieties/{varietyId}`
- `PATCH /api/varieties/{varietyId}`
- `DELETE /api/varieties/{varietyId}`
- `PATCH /api/varieties/{varietyId}/deactivate`
- `GET /api/varieties/{varietyId}/orchid-groups`

### 거래처

- `GET /api/business-partners`
- `POST /api/business-partners`
- `GET /api/business-partners/{partnerId}/settlement-settings`
- `PUT /api/business-partners/{partnerId}/settlement-settings`

### 판매 전표

- `GET /api/sales-slips`
- `POST /api/sales-slips`
- `PUT /api/sales-slips/{salesSlipId}`
- `GET /api/sales-slips/auction-shipments`
- `GET /api/sales-slips/{salesSlipId}`
- `GET /api/sales-slips/{salesSlipId}/print`
- `PATCH /api/sales-slips/{salesSlipId}/sales-status`
- `GET /api/sales/orchid-groups/search`

### 출하·경매 추적/정산

- `GET /api/auction-lots`
- `GET /api/auction-lots/{id}`
- `POST /api/auction-lots/{id}/adjust-quantity`
- `POST /api/auction-lots/{id}/confirm-return`
- `PATCH /api/auction-lots/{id}/status`
- `GET /api/auction-lots/{id}/timeline`
- `GET /api/auction-settlements`
- `POST /api/auction-settlements/rebuild`
- `GET /api/auction-settlements/{settlementId}`
- `GET /api/auction-tracking/summary`

### 입금/정산 이벤트

- `POST /api/auction-settlements/{settlementId}/confirm-payment`
- `GET /api/business-partners/{partnerId}/balance-summary`
- `GET /api/partner-payment-events`
- `POST /api/sales-slips/{salesSlipId}/confirm-payment`

## 과거 초안에는 있으나 현재 OpenAPI에 없는 endpoint

### 경매 결과 가져오기

- `POST /api/auction-imports`
- `GET /api/auction-imports/{importBatchId}`
- `GET /api/auction-imports/{importBatchId}/rows`

### 경매 대사/검산

- `GET /api/auction-tracking/reconciliation`

### 경매 정산 생성

- `POST /api/auction-settlements`

### 경매 정산 출력

- `GET /api/auction-settlements/{settlementId}/print`

### 경매 출하 조회

- `GET /api/auction-shipments`
- `GET /api/auction-shipments/{shipmentId}`

### 구버전 대시보드 맵

- `GET /api/dashboard/farm-map`

### 난 묶음 수동 정렬

- `PATCH /api/orchid-groups/reorder`

### 수동 매칭/검수

- `POST /api/auction-result-lines/{lineId}/match`
- `PATCH /api/auction-result-lines/{lineId}/unmatch`

### 입금 이벤트 명령

- `POST /api/partner-payment-events/import-bank-csv`
- `POST /api/partner-payment-events/manual-payment`
- `POST /api/partner-payment-events/prepayment`
- `POST /api/partner-payment-events/{eventId}/allocate`
- `POST /api/partner-payment-events/{eventId}/reject-match`

### 자동 매칭/연결 해제/예치금

- `POST /api/auction-settlements/{settlementId}/run-auto-match`
- `POST /api/auction-settlements/{settlementId}/unlink-payment`
- `POST /api/sales-slips/{salesSlipId}/apply-credit`
- `POST /api/sales-slips/{salesSlipId}/run-auto-match`
- `POST /api/sales-slips/{salesSlipId}/unlink-payment`

### 작업 이력 수정/삭제

- `DELETE /api/work-records/{workRecordId}`
- `PATCH /api/work-records/{workRecordId}`

### 판매 상태 변경

- `PATCH /api/sales-slips/{salesSlipId}/status`

## 현재 OpenAPI에는 있으나 과거 md 초안에서 명시성이 낮았던 endpoint

아래 endpoint는 실제 OpenAPI에 있으므로 호출 가능하다. 다만 과거 문서의 표현과 다를 수 있으므로 request/response는 OpenAPI 기준으로 확인한다.

- `GET /api/bed-zones/{bedZoneId}/placement-profile` — `bed-placement-controller`
- `PUT /api/bed-zones/{bedZoneId}/placement-profile` — `bed-placement-controller`

## Codex 작업 시 주의

- 새로운 API 호출 코드를 만들 때는 이 문서의 미구현 목록에 있는 endpoint를 사용하지 않는다.
- 미구현 API가 필요하면 먼저 Controller/Service/DTO/테스트를 추가하고 OpenAPI를 갱신한다.
- 과거 md 초안에 적힌 동작 규칙은 `DOMAIN_RULES.md`로 옮겨진 항목만 현재 설계 의도로 본다.
