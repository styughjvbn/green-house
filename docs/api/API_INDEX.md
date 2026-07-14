# API Index for Codex

이 문서는 Codex가 API 영역을 빠르게 찾기 위한 색인이다.  
요청/응답 필드 상세는 이 문서에 중복하지 말고 `docs/api/openapi.yaml`, `docs/api/slices/*.openapi.yaml`, 실제 Controller/Request/Response 코드를 확인한다.

## 기준

- 기준 명세: `docs/api/openapi.yaml`
- OpenAPI 버전: `3.1.0`
- 현재 구현 API: `83` operations / `64` path entries
- schema 수: `126`
- Base URL: `/api`
- 공통 응답: `ApiResponse*` 래퍼 사용

## Codex 조회 순서

1. 먼저 이 파일에서 관련 도메인과 패키지를 찾는다.
2. 도메인 규칙이 필요하면 `docs/api/DOMAIN_RULES.md`를 읽는다.
3. 요청/응답 필드가 필요하면 해당 `docs/api/slices/*.openapi.yaml`만 읽는다.
4. 전체 비교, 타입 생성, 명세 검증이 필요할 때만 `docs/api/openapi.yaml` 전체를 읽는다.
5. 코드에서 바로 알 수 있는 필드/타입/required 여부를 새 md 문서에 중복 작성하지 않는다.

## 도메인별 색인

### 농장 구조

- slice: `docs/api/slices/farm-structure.openapi.yaml`
- package 후보: `com.greenhouse.backend.farm`
- controller tags: `farm-structure-controller`
- 역할: 동, 물리 배드, 논리 구역, 난 묶음 조회 API
- operations: 7

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/bed-zones` | `getBedZones` | `-` | `200:ApiResponseListBedZoneResponse` |
| `GET` | `/api/bed-zones/{bedZoneId}` | `getBedZone` | `-` | `200:ApiResponseBedZoneResponse` |
| `GET` | `/api/houses` | `getHouses` | `-` | `200:ApiResponseListHouseResponse` |
| `GET` | `/api/houses/{houseId}` | `getHouse` | `-` | `200:ApiResponseHouseResponse` |
| `GET` | `/api/orchid-groups` | `getOrchidGroups` | `-` | `200:ApiResponseListOrchidGroupResponse` |
| `GET` | `/api/physical-beds` | `getPhysicalBeds` | `-` | `200:ApiResponseListPhysicalBedResponse` |
| `GET` | `/api/physical-beds/{physicalBedId}` | `getPhysicalBed` | `-` | `200:ApiResponsePhysicalBedResponse` |

### 농장 현황

- slice: `docs/api/slices/farm-status.openapi.yaml`
- package 후보: `com.greenhouse.backend.farm`
- controller tags: `farm-status-controller`, `dashboard-controller`
- 역할: 농장 현황 지도, 줌 단계, 선택 대상 난 묶음, 대시보드 요약 API
- operations: 4

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/dashboard/summary` | `getSummary` | `-` | `200:ApiResponseDashboardSummaryResponse` |
| `GET` | `/api/farm-status/map` | `getMap` | `-` | `200:ApiResponseFarmStatusMapResponse` |
| `GET` | `/api/farm-status/orchid-groups` | `getOrchidGroups_1` | `-` | `200:ApiResponseFarmStatusOrchidGroupListResponse` |
| `GET` | `/api/farm-status/zoom` | `getZoom` | `-` | `200:ApiResponseFarmStatusZoomResponse` |

### 난 묶음 명령/정밀 배치

- slice: `docs/api/slices/orchid-command.openapi.yaml`
- package 후보: `com.greenhouse.backend.farm`
- controller tags: `orchid-group-command-controller`, `bed-placement-controller`
- 역할: 난 묶음 생성·수정·삭제·이동, 정밀 배치 프로필 API
- operations: 7

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/bed-zones/{bedZoneId}/placement-profile` | `get_1` | `-` | `200:ApiResponseBedZonePlacementProfileResponse` |
| `PUT` | `/api/bed-zones/{bedZoneId}/placement-profile` | `update_1` | `BedZonePlacementProfileRequest` | `200:ApiResponseBedZonePlacementProfileResponse` |
| `POST` | `/api/orchid-groups` | `create_1` | `OrchidGroupCreateRequest` | `201:ApiResponseOrchidGroupResponse` |
| `DELETE` | `/api/orchid-groups/{orchidGroupId}` | `delete` | `-` | `200:ApiResponseVoid` |
| `PATCH` | `/api/orchid-groups/{orchidGroupId}` | `update_2` | `OrchidGroupUpdateRequest` | `200:ApiResponseOrchidGroupResponse` |
| `PATCH` | `/api/orchid-groups/{orchidGroupId}/move` | `move` | `OrchidGroupMoveRequest` | `200:ApiResponseOrchidGroupResponse` |

### 작업 이력

- slice: `docs/api/slices/work.openapi.yaml`
- package 후보: `com.greenhouse.backend.work`
- controller tags: `work-record-controller`
- 역할: 작업 이력과 작업 유형 관리 API
- operations: 6

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/work-records` | `getWorkRecords` | `-` | `200:ApiResponseListWorkRecordResponse` |
| `POST` | `/api/work-records` | `create` | `WorkRecordCreateRequest` | `201:ApiResponseWorkRecordResponse` |
| `PATCH` | `/api/work-records/{workRecordId}/cancel` | `cancel_1` | `WorkRecordCancelRequest` | `200:ApiResponseWorkRecordResponse` |
| `GET` | `/api/work-types` | `getWorkTypes` | `-` | `200:ApiResponseListWorkTypeResponse` |
| `POST` | `/api/work-types` | `createWorkType` | `WorkTypeCreateRequest` | `201:ApiResponseWorkTypeResponse` |
| `PATCH` | `/api/work-types/reorder` | `reorderWorkTypes` | `WorkTypeReorderRequest` | `200:ApiResponseListWorkTypeResponse` |
| `PATCH` | `/api/work-types/{workTypeId}` | `updateWorkType` | `WorkTypeUpdateRequest` | `200:ApiResponseWorkTypeResponse` |

### 품종/입고/자재

- slice: `docs/api/slices/inventory.openapi.yaml`
- package 후보: `com.greenhouse.backend.farm`
- controller tags: `variety-controller`, `material-controller`, `inbound-record-controller`
- 역할: 품종 CRUD/삭제, 자재 CRUD/삭제, 입고 기록 생성·수정·포트 작업·취소·삭제 API
- operations: 20

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/inbound-records` | `getInboundRecords` | `-` | `200:ApiResponseInboundRecordPageResponse` |
| `POST` | `/api/inbound-records` | `create_4` | `InboundRecordCreateRequest` | `201:ApiResponseInboundRecordResponse` |
| `GET` | `/api/inbound-records/{inboundRecordId}` | `getInboundRecord` | `-` | `200:ApiResponseInboundRecordResponse` |
| `PATCH` | `/api/inbound-records/{inboundRecordId}` | `update_5` | `InboundRecordUpdateRequest` | `200:ApiResponseInboundRecordResponse` |
| `DELETE` | `/api/inbound-records/{inboundRecordId}` | `delete` | `-` | `200:ApiResponseVoid` |
| `POST` | `/api/inbound-records/{inboundRecordId}/cancel` | `cancel` | `InboundRecordCancelRequest` | `200:ApiResponseInboundRecordResponse` |
| `POST` | `/api/inbound-records/{inboundRecordId}/potting` | `potting` | `InboundRecordPottingRequest` | `200:ApiResponseInboundRecordResponse` |
| `GET` | `/api/materials` | `getMaterials` | `-` | `200:ApiResponseMaterialPageResponse` |
| `POST` | `/api/materials` | `create_3` | `MaterialCreateRequest` | `201:ApiResponseMaterialResponse` |
| `GET` | `/api/materials/{materialId}` | `getMaterial` | `-` | `200:ApiResponseMaterialResponse` |
| `PATCH` | `/api/materials/{materialId}` | `update_4` | `MaterialUpdateRequest` | `200:ApiResponseMaterialResponse` |
| `DELETE` | `/api/materials/{materialId}` | `delete_2` | `-` | `200:ApiResponseVoid` |
| `PATCH` | `/api/materials/{materialId}/deactivate` | `deactivate_1` | `-` | `200:ApiResponseMaterialResponse` |
| `GET` | `/api/varieties` | `getVarieties` | `-` | `200:ApiResponseVarietyPageResponse` |
| `POST` | `/api/varieties` | `create_1` | `VarietyCreateRequest` | `201:ApiResponseVarietyResponse` |
| `GET` | `/api/varieties/genera` | `getGenera` | `-` | `200:ApiResponseListString` |
| `GET` | `/api/varieties/{varietyId}` | `getVariety` | `-` | `200:ApiResponseVarietyResponse` |
| `PATCH` | `/api/varieties/{varietyId}` | `update_2` | `VarietyUpdateRequest` | `200:ApiResponseVarietyResponse` |
| `DELETE` | `/api/varieties/{varietyId}` | `delete_1` | `-` | `200:ApiResponseVoid` |
| `PATCH` | `/api/varieties/{varietyId}/deactivate` | `deactivate` | `-` | `200:ApiResponseVarietyResponse` |
| `GET` | `/api/varieties/{varietyId}/orchid-groups` | `getOrchidGroups_1` | `-` | `200:ApiResponseListVarietyConnectedOrchidGroupResponse` |

### 신규 작업 실행

- slice: `docs/api/slices/work-operation.openapi.yaml`
- package: `com.greenhouse.backend.work`
- controller tag: `work-operation-controller`
- 역할: 동 전체 농약 작업 대상 미리보기·스냅샷·완료와 난 묶음 통합 이력
- operations: 5

| Method | Path | Operation |
|---|---|---|
| `POST` | `/api/work-operations/target-preview` | 대상 미리보기 |
| `POST` | `/api/work-operations` | 작업 생성과 대상 스냅샷 확정 |
| `GET` | `/api/work-operations/{workOperationId}` | 작업 상세 |
| `POST` | `/api/work-operations/{workOperationId}/complete` | 작업과 대상 일괄 완료 |
| `GET` | `/api/orchid-groups/{orchidGroupId}/work-history` | 기존·신규 통합 이력 |

초기 구현 제한: `sourceScopeType=HOUSE`, 작업 유형 코드 `PESTICIDE`만 지원한다.

### 난 묶음 사용자 그룹

- slice: `docs/api/slices/orchid-collection.openapi.yaml`
- package: `com.greenhouse.backend.farm`
- controller tag: `orchid-group-collection-controller`
- 역할: 사용자 그룹 생성·수정·보관, 난 묶음 소속 추가·해제·역조회
- operations: 8

| Method | Path | Operation |
|---|---|---|
| `GET` | `/api/orchid-group-collections` | 활성/보관 사용자 그룹 목록 |
| `POST` | `/api/orchid-group-collections` | 사용자 그룹 생성 |
| `GET` | `/api/orchid-group-collections/{collectionId}` | 사용자 그룹 상세 |
| `PATCH` | `/api/orchid-group-collections/{collectionId}` | 이름·설명·목적 수정 |
| `POST` | `/api/orchid-group-collections/{collectionId}/archive` | 사용자 그룹 보관 |
| `POST` | `/api/orchid-group-collections/{collectionId}/members` | 난 묶음 소속 일괄 추가 |
| `DELETE` | `/api/orchid-group-collections/{collectionId}/members/{orchidGroupId}` | 난 묶음 소속 해제 |
| `GET` | `/api/orchid-groups/{orchidGroupId}/collections` | 난 묶음의 사용자 그룹 역조회 |

### 난 묶음 자동 그룹

- slice: `docs/api/slices/derived-orchid-group.openapi.yaml`
- package: `com.greenhouse.backend.farm`
- controller tag: `derived-orchid-group-controller`
- 역할: 품종·현재 년생·화분 크기 기준 실시간 그룹 집계와 구성원 조회
- operations: 2

| Method | Path | Operation |
|---|---|---|
| `GET` | `/api/orchid-groups/derived-groups` | 자동 그룹 목록과 묶음 수·총수량·위치 수 집계 |
| `GET` | `/api/orchid-groups/derived-groups/{groupKey}/members` | 현재 조건에 해당하는 구성원 조회 |

### 거래처

- slice: `docs/api/slices/partner.openapi.yaml`
- package 후보: `com.greenhouse.backend.partner`
- controller tags: `business-partner-controller`, `partner-settlement-settings-controller`
- 역할: 거래처 조회·등록과 거래처 정산 설정 API
- operations: 4

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/business-partners` | `getPartners` | `-` | `200:ApiResponseListBusinessPartnerResponse` |
| `POST` | `/api/business-partners` | `create_2` | `BusinessPartnerCreateRequest` | `201:ApiResponseBusinessPartnerResponse` |
| `GET` | `/api/business-partners/{partnerId}/settlement-settings` | `get` | `-` | `200:ApiResponsePartnerSettlementSettingsResponse` |
| `PUT` | `/api/business-partners/{partnerId}/settlement-settings` | `update` | `PartnerSettlementSettingsRequest` | `200:ApiResponsePartnerSettlementSettingsResponse` |

### 판매 전표

- slice: `docs/api/slices/sales.openapi.yaml`
- package 후보: `com.greenhouse.backend.sales`
- controller tags: `sales-controller`
- 역할: 판매 전표 조회·생성·출력, 경매 출하 전표 후보 API
- operations: 5

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/sales-slips` | `getSalesSlips` | `-` | `200:ApiResponseListSalesSlipResponse` |
| `GET` | `/api/sales-slips/page` | `getSalesSlipPage` | `-` | `200:ApiResponseSalesSlipPageResponse` |
| `GET` | `/api/analytics/sales` | `getSalesAnalytics` | `-` | `200:ApiResponseSalesAnalyticsResponse` |
| `GET` | `/api/analytics/partners` | `getPartnerAnalytics` | `-` | `200:ApiResponsePartnerAnalyticsResponse` |
| `GET` | `/api/analytics/work` | `getWorkAnalytics` | `-` | `200:ApiResponseWorkAnalyticsResponse` |
| `POST` | `/api/sales-slips` | `createSalesSlip` | `SalesSlipCreateRequest` | `201:ApiResponseSalesSlipResponse` |
| `GET` | `/api/sales-slips/auction-shipments` | `getAuctionShipmentOptions` | `-` | `200:ApiResponseListAuctionShipmentOptionResponse` |
| `GET` | `/api/sales-slips/{salesSlipId}` | `getSalesSlip` | `-` | `200:ApiResponseSalesSlipResponse` |
| `GET` | `/api/sales-slips/{salesSlipId}/print` | `getSalesSlipPrintData` | `-` | `200:ApiResponseSalesSlipResponse` |
| `GET` | `/api/sales-slips/print` | `getPrintableSalesSlips` | `-` | `200:ApiResponseSalesSlipPageResponse` |

### 출하·경매 추적/정산

- slice: `docs/api/slices/auction.openapi.yaml`
- package 후보: `com.greenhouse.backend.auction`
- controller tags: `auction-tracking-controller`, `auction-settlement-controller`
- 역할: 경매 lot 조회·상태 변경·반환 확인·수량 보정·경매 정산 API
- operations: 10

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/auction-lots` | `getLots` | `-` | `200:ApiResponseAuctionLotPageResponse` |
| `GET` | `/api/auction-lots/{id}` | `getLot` | `-` | `200:ApiResponseAuctionLotResponse` |
| `POST` | `/api/auction-lots/{id}/adjust-quantity` | `adjust` | `AuctionLotAdjustmentRequest` | `200:ApiResponseAuctionLotResponse` |
| `POST` | `/api/auction-lots/{id}/confirm-return` | `confirmReturn` | `AuctionLotReturnRequest` | `200:ApiResponseAuctionLotResponse` |
| `PATCH` | `/api/auction-lots/{id}/status` | `changeStatus` | `AuctionLotStatusRequest` | `200:ApiResponseAuctionLotResponse` |
| `GET` | `/api/auction-lots/{id}/timeline` | `getTimeline` | `-` | `200:ApiResponseAuctionLotResponse` |
| `GET` | `/api/auction-settlements` | `getSettlements` | `-` | `200:ApiResponseListAuctionSettlementResponse` |
| `POST` | `/api/auction-settlements/rebuild` | `rebuild` | `-` | `200:ApiResponseAuctionSettlementResponse` |
| `GET` | `/api/auction-settlements/{settlementId}` | `getSettlement` | `-` | `200:ApiResponseAuctionSettlementResponse` |
| `GET` | `/api/auction-tracking/summary` | `getSummary_1` | `-` | `200:ApiResponseAuctionTrackingSummaryResponse` |

### 입금/정산 이벤트

- slice: `docs/api/slices/payment.openapi.yaml`
- package 후보: `com.greenhouse.backend.settlement`
- controller tags: `payment-controller`
- 역할: 수동 입금 확인, 입금 이벤트 조회, 거래처 잔액 요약 API
- operations: 4

| Method | Path | Operation | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/auction-settlements/{settlementId}/confirm-payment` | `confirmAuctionPayment` | `ManualPaymentRequest` | `200:ApiResponseAuctionSettlementResponse` |
| `GET` | `/api/business-partners/{partnerId}/balance-summary` | `getBalance` | `-` | `200:ApiResponsePartnerBalanceSummaryResponse` |
| `GET` | `/api/partner-payment-events` | `getEvents` | `-` | `200:ApiResponseListPartnerPaymentEventResponse` |
| `POST` | `/api/sales-slips/{salesSlipId}/confirm-payment` | `confirmSalesSlipPayment` | `ManualPaymentRequest` | `200:ApiResponseSalesSlipResponse` |

## 주의

`docs/api/API_GAP_ANALYSIS.md`에 적힌 미구현/제안 API는 실제 구현으로 가정하지 않는다.  
프론트나 테스트 코드를 작성할 때는 반드시 OpenAPI 또는 Controller 코드에 노출된 API만 호출한다.
