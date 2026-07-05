# API 명세서 초안 v2.2

## 1. 공통 규칙

### Base URL

```text
/api
```

### 응답 형식

```json
{
  "data": {},
  "message": null
}
```

### 에러 응답 형식

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": []
  }
}
```

### 날짜 형식

- 요청/응답 날짜는 ISO-8601 문자열을 사용한다.
- 예: `2026-06-01`

---

## 2. 농장 구조 API

### 동 목록 조회

```http
GET /api/houses
```

### 동 상세 조회

```http
GET /api/houses/{houseId}
```

### 물리 배드 목록 조회

```http
GET /api/physical-beds?houseId=3
```

### 물리 배드 상세 조회

```http
GET /api/physical-beds/{physicalBedId}
```

### 논리 구역 목록 조회

```http
GET /api/bed-zones?houseId=3&physicalBedId=8
```

### 논리 구역 상세 조회

```http
GET /api/bed-zones/{bedZoneId}
```

응답에는 논리 구역 정보와 난 묶음 목록을 포함한다. 최근 작업 요약은 작업 이력 API를 조합해 프론트엔드에서 표시한다.

---

## 3. 난 묶음 API

### 난 묶음 목록 조회

```http
GET /api/orchid-groups?houseId=3&physicalBedId=8&bedZoneId=15&status=정상
```

### 난 묶음 생성

```http
POST /api/orchid-groups
```

요청 예시:

```json
{
  "bedZoneId": 15,
  "genus": "카틀레야",
  "varietyName": "카틀레야 A",
  "quantity": 120,
  "potSize": "4치",
  "ageYear": 2,
  "status": "정상",
  "sortOrder": 1,
  "memo": null
}
```

### 난 묶음 수정

```http
PATCH /api/orchid-groups/{orchidGroupId}
```

### 난 묶음 삭제

```http
DELETE /api/orchid-groups/{orchidGroupId}
```

### 난 묶음 순서 변경

```http
PATCH /api/orchid-groups/reorder
```

현재 MVP 구현에서는 별도 순서 변경 API를 제공하지 않는다. 구역 내 정렬은 생성/이동 시 `sortOrder` 기준으로 관리하며, 수동 순서 변경 API는 이후 범위로 둔다.

요청 예시:

```json
{
  "bedZoneId": 15,
  "items": [
    { "orchidGroupId": 1, "sortOrder": 1 },
    { "orchidGroupId": 2, "sortOrder": 2 },
    { "orchidGroupId": 3, "sortOrder": 3 }
  ]
}
```

### 난 묶음 전체 이동

```http
PATCH /api/orchid-groups/{orchidGroupId}/move
```

요청 예시:

```json
{
  "toBedZoneId": 21,
  "memo": "위치 정리"
}
```

처리 규칙:

- 난 묶음의 `bedZoneId`를 변경한다.
- 대상 구역의 마지막 순서로 배치한다.
- 위치 이동 작업 이력을 자동 생성한다.

---

## 4. 작업 이력 API

### 작업 이력 목록 조회

```http
GET /api/work-records?targetType=BED_ZONE&targetId=15&workType=농약&from=2026-01-01&to=2026-12-31
```

### 작업 이력 생성

```http
POST /api/work-records
```

요청 예시:

```json
{
  "workType": "농약",
  "workDate": "2026-06-01",
  "targetType": "BED_ZONE",
  "targetId": 15,
  "materialName": "약제명",
  "dilutionRatio": "1000배",
  "quantity": null,
  "worker": "부모님",
  "memo": "예방 살포"
}
```

### 위치 이동 작업 이력 예시

```json
{
  "workType": "위치 이동",
  "workDate": "2026-06-20",
  "targetType": "ORCHID_GROUP",
  "targetId": 1,
  "fromBedZoneId": 15,
  "toBedZoneId": 21,
  "quantity": "120분",
  "worker": "부모님",
  "memo": "배치 정리"
}
```

### 작업 이력 수정 - MVP 이후

```http
PATCH /api/work-records/{workRecordId}
```

### 작업 이력 삭제 - MVP 이후

```http
DELETE /api/work-records/{workRecordId}
```

현재 MVP 구현에서는 작업 이력 수정/삭제 API를 제공하지 않는다. 운영 정책도 삭제보다 보존을 우선한다.

---

## 5. 작업 유형 API

작업 유형은 `work_types` 테이블로 관리한다. 기본 유형은 seed로 제공하고, 커스텀 유형은 설정 화면에서 생성/수정/비활성/정렬한다.

제공 API:

```http
GET /api/work-types?includeInactive=false
POST /api/work-types
PATCH /api/work-types/{workTypeId}
PATCH /api/work-types/reorder
```

작업 유형 템플릿은 입력 필드와 목록/상세 표시값을 결정한다. 상세 요청/응답 예시는 `11. 작업 이력 관리 API`를 따른다.

---

## 6. 판매 API

### 거래처 목록 조회

```http
GET /api/customers?keyword=화원
```

### 거래처 생성

```http
POST /api/customers
```

### 판매 전표 목록 조회

```http
GET /api/sales-slips?customerId=1&from=2026-01-01&to=2026-12-31
```

### 판매 전표 생성

```http
POST /api/sales-slips
```

요청 예시:

```json
{
  "saleDate": "2026-06-20",
  "customerId": 1,
  "paymentStatus": "미입금",
  "salesStatus": "작성중",
  "paymentMethod": null,
  "memo": "다음 주 추가 출고 예정",
  "items": [
    {
      "orchidGroupId": null,
      "itemName": "카틀레야 A",
      "genus": "카틀레야",
      "spec": "4치",
      "quantity": 10,
      "unitPrice": 15000,
      "memo": null
    }
  ]
}
```

### 판매 전표 상세 조회

```http
GET /api/sales-slips/{salesSlipId}
```

### 판매 전표 출력 데이터 조회

```http
GET /api/sales-slips/{salesSlipId}/print
```

### 판매 상태 변경 - MVP 이후

```http
PATCH /api/sales-slips/{salesSlipId}/status
```

요청 예시:

```json
{
  "salesStatus": "출고 완료"
}
```

현재 MVP 구현에서는 판매 상태 변경 API를 제공하지 않는다. 판매 상태는 전표 생성 시 입력하고, 별도 수정/상태 변경은 MVP 이후 기능으로 둔다.

---

## 7. 농장 현황 조회 API

농장 현황 화면은 전체 조회용이다. 선택 대상에 따라 포함되는 난 묶음 목록의 범위가 달라진다.

### 농장 현황 맵 데이터 조회

```http
GET /api/farm-status/map
```

포함 데이터:

- 동 목록
- 동별 상태 요약
- 동별 상태 이상 개수
- 동별 분갈이 예정 개수
- 동별 최근 작업일

### 선택 대상 난 묶음 목록 조회

```http
GET /api/farm-status/orchid-groups?targetType=HOUSE&targetId=1
GET /api/farm-status/orchid-groups?targetType=PHYSICAL_BED&targetId=3
GET /api/farm-status/orchid-groups?targetType=BED_ZONE&targetId=15
```

응답에는 선택 대상에 포함되는 난 묶음 목록과 위치 정보가 포함된다.

응답 예시:

```json
{
  "data": {
    "targetType": "HOUSE",
    "targetId": 1,
    "targetName": "1동",
    "items": [
      {
        "orchidGroupId": 1,
        "varietyName": "카틀레야 A",
        "quantity": 120,
        "status": "정상",
        "physicalBedName": "2배드",
        "bedZoneName": "우측"
      }
    ]
  },
  "message": null
}
```

### 농장 현황 줌 단계 데이터 조회

```http
GET /api/farm-status/zoom?level=HOUSE&houseId=1
GET /api/farm-status/zoom?level=PHYSICAL_BED&houseId=1
GET /api/farm-status/zoom?level=BED_ZONE&physicalBedId=3
```

프론트엔드는 이 데이터를 이용해 전체 농장 보기, 물리 배드 보기, 논리 구역 보기를 단계적으로 구성한다.

---

## 8. 대시보드 API

### 대시보드 요약 조회

```http
GET /api/dashboard/summary
```

포함 데이터:

- 전체 동 수
- 전체 물리 배드 수
- 전체 논리 구역 수
- 분갈이 예정 개수
- 최근 작업일
- 상태 이상 개수

### 농장맵 데이터 조회

```http
GET /api/dashboard/farm-map
```

포함 데이터:

- 동 목록
- 물리 배드 목록
- 논리 구역 목록
- 논리 구역별 난 묶음 요약
- 논리 구역별 최근 작업일 요약
---

## 12. 판매 전표 관리 API

8단계 범위에서는 거래처 등록/조회와 판매 전표 생성/조회만 제공한다. A5 출력은 9단계에서 구현한다.

### 거래처 목록 조회

```http
GET /api/customers?keyword=화원
```

### 거래처 등록

```http
POST /api/customers
```

요청:

```json
{
  "name": "거래처명",
  "ownerName": "대표자",
  "phone": "010-0000-0000",
  "address": "주소",
  "memo": "메모"
}
```

### 판매 전표 목록 조회

```http
GET /api/sales-slips?customerId=1&from=2026-06-01&to=2026-06-30
```

### 판매 전표 상세 조회

```http
GET /api/sales-slips/{salesSlipId}
```

### 판매 전표 생성

```http
POST /api/sales-slips
```

요청:

```json
{
  "saleDate": "2026-06-24",
  "customerId": 1,
  "paymentStatus": "미입금",
  "salesStatus": "작성중",
  "paymentMethod": "현금",
  "memo": "메모",
  "items": [
    {
      "orchidGroupId": null,
      "itemName": "카틀레야 A",
      "genus": "카틀레야",
      "spec": "4치",
      "quantity": 2,
      "unitPrice": 15000,
      "memo": "품목 메모"
    }
  ]
}
```

규칙:

- `saleDate`, `customerId`, `items`는 필수이다.
- 품목별 `amount`는 `quantity * unitPrice`로 계산한다.
- 전표 `totalAmount`는 품목 금액 합계로 계산한다.
- 전표 번호는 판매일 기준 순번으로 자동 생성한다.
- 판매 품목은 MVP에서 자유 입력 기반이며, 난 묶음 수량 자동 차감은 하지 않는다.

---

## 13. 판매 전표 출력 API

9단계 범위에서는 판매 전표 A5 출력 화면에 필요한 데이터를 조회한다. 응답 구조는 판매 전표 상세 조회와 동일하며, 프론트엔드는 이 데이터를 A5 레이아웃으로 렌더링한다.

### 판매 전표 출력 데이터 조회

```http
GET /api/sales-slips/{salesSlipId}/print
```

응답:

```json
{
  "data": {
    "id": 1,
    "slipNumber": "S20260624-001",
    "saleDate": "2026-06-24",
    "customer": {
      "id": 1,
      "name": "거래처명",
      "ownerName": "대표자",
      "phone": "010-0000-0000",
      "address": "주소",
      "memo": null
    },
    "totalAmount": 60000,
    "paymentStatus": "미입금",
    "salesStatus": "작성중",
    "paymentMethod": "현금",
    "memo": "메모",
    "items": []
  },
  "message": null
}
```

규칙:

- 존재하지 않는 전표 ID는 공통 `NOT_FOUND` 에러 응답을 반환한다.
- 출력 데이터 조회는 데이터를 변경하지 않는다.
- 브라우저 인쇄 CSS는 프론트엔드에서 적용한다.

---

## 11. 작업 이력 관리 API

7단계 범위에서는 작업 이력 등록과 조회를 제공한다. 작업 유형은 기본 제공 유형과 커스텀 유형을 함께 사용하며, 설정 화면에서 관리한다. 작업 이력 수정/삭제 API는 이후 단계로 남긴다.

### 작업 이력 목록 조회

```http
GET /api/work-records?targetType=BED_ZONE&targetId=15&workType=농약&from=2026-06-01&to=2026-06-30
```

쿼리 파라미터는 모두 선택값이다.

- `targetType`: `FARM`, `HOUSE`, `PHYSICAL_BED`, `BED_ZONE`, `ORCHID_GROUP`
- `targetId`: `FARM` 외 대상 선택 시 사용
- `workType`: 작업 유형명. 비활성 작업 유형의 기존 이력 조회에도 사용한다.
- `from`, `to`: ISO 날짜

### 작업 이력 등록

```http
POST /api/work-records
```

요청:

```json
{
  "workTypeId": 1,
  "workDate": "2026-06-24",
  "targetType": "BED_ZONE",
  "targetId": 15,
  "materialName": "살균제",
  "dilutionRatio": "1000배",
  "quantity": "2L",
  "worker": "작업자",
  "memo": "작업 메모"
}
```

규칙:

- `workTypeId`, `workDate`, `targetType`은 필수이다.
- `targetType=FARM`이면 `targetId` 없이 등록할 수 있다.
- 그 외 대상 유형은 실제 존재하는 `targetId`가 필요하다.
- 비활성 또는 시스템 작업 유형은 일반 작업 기록 생성에 사용할 수 없다.
- 응답에는 `workTypeId`, 작업 유형명 스냅샷 `workType`, `workTypeTemplate`을 포함한다.

### 작업 유형 목록 조회

```http
GET /api/work-types?includeInactive=false
```

응답은 작업 유형 객체 목록이다.

```json
{
  "data": [
    {
      "id": 1,
      "code": "PESTICIDE",
      "name": "농약",
      "template": "PESTICIDE",
      "defaultType": true,
      "systemType": false,
      "active": true,
      "sortOrder": 1
    }
  ],
  "message": null
}
```

### 작업 유형 생성

```http
POST /api/work-types
```

```json
{
  "name": "관수",
  "template": "MEMO"
}
```

### 작업 유형 수정

```http
PATCH /api/work-types/{workTypeId}
```

```json
{
  "name": "관수",
  "template": "STATUS",
  "active": true
}
```

### 작업 유형 정렬

```http
PATCH /api/work-types/reorder
```

```json
{
  "orderedIds": [1, 2, 3]
}
```

작업 유형 템플릿 값은 `PESTICIDE`, `FERTILIZER`, `REPOT`, `CLEANUP`, `STATUS`, `MEMO`, `MOVEMENT`이다. `MOVEMENT`는 시스템 유형으로 일반 생성 폼에서는 선택하지 않는다.

---

## 10. 난 묶음 위치 이동 API

6단계 범위에서는 난 묶음 전체를 다른 논리 구역으로 이동한다. 이동 성공 시 위치 이동 작업 이력이 자동 생성된다.

```http
PATCH /api/orchid-groups/{orchidGroupId}/move
```

요청:

```json
{
  "toBedZoneId": 16,
  "worker": "작업자",
  "memo": "위치 정리"
}
```

규칙:

- `toBedZoneId`는 필수이다.
- 이동 단위는 난 묶음 전체이다.
- 목적지는 반드시 논리 구역이다.
- 이동 후 목적 구역의 마지막 `sortOrder` 다음 값으로 배치한다.
- 이동 성공 시 `work_records`에 `workType=위치 이동`, `targetType=ORCHID_GROUP`, `fromBedZoneId`, `toBedZoneId`를 저장한다.
- 같은 구역으로 이동 요청하면 위치와 작업 이력은 변경하지 않고 현재 난 묶음 응답을 반환한다.

응답은 `OrchidGroupResponse` 공통 성공 응답을 사용한다.

---

## 9. 난 묶음 변경 API

5단계 범위에서는 난 묶음 생성, 수정, 삭제만 제공한다. 위치 이동은 6단계에서 별도 API로 구현하며, 수정 API에서는 `bedZoneId`를 변경하지 않는다.

### 난 묶음 생성

```http
POST /api/orchid-groups
```

요청:

```json
{
  "bedZoneId": 15,
  "genus": "카틀레야",
  "varietyName": "카틀레야 A",
  "quantity": 120,
  "potSize": "4치",
  "ageYear": 2,
  "status": "정상",
  "placementType": "TRAY",
  "trayCount": 1,
  "memo": "메모"
}
```

규칙:

- `bedZoneId`, `varietyName`, `quantity`, `status`는 필수이다.

---

## 배드 정밀 배치 API

### 배치 프로필

- `GET /api/bed-zones/{bedZoneId}/placement-profile`
- `PUT /api/bed-zones/{bedZoneId}/placement-profile`

논리 구역의 구간과 규격·화분 크기·배치 모드별 수용량을 조회하거나 일괄 저장한다. 실제 배치가 있는 구간은 삭제할 수 없다.

### 배치 추천

- `GET /api/orchid-groups/{orchidGroupId}/placement-recommendations?houseId=...`

추천 상태, 필요 배치 모드, 목적 구역, 구간별 수량·점유 단위와 경고를 반환한다.

### 정밀 이동

기존 `PATCH /api/orchid-groups/{orchidGroupId}/move` 요청에 다음 필드를 추가한다.

```json
{
  "toBedZoneId": 10,
  "placementMode": "STANDARD",
  "placements": [
    { "segmentId": 50, "quantity": 96, "trayCount": 4 }
  ],
  "reorganizeDueDate": null,
  "worker": "관리자",
  "memo": "추천 배치"
}
```

수용량이 변경된 경우 `409 CAPACITY_CONFLICT`를 반환한다. 기존 단순 이동 요청은 구간 미지정 이동으로 호환한다.
- `quantity`는 1 이상이어야 한다.
- 생성 위치는 반드시 논리 구역 기준이다.
- 같은 논리 구역 안의 마지막 `sortOrder` 다음 값으로 생성한다.

### 난 묶음 수정

```http
PATCH /api/orchid-groups/{orchidGroupId}
```

요청 필드는 생성과 같지만 `bedZoneId`는 받지 않는다. 품종명, 수량, 상태, 메모 등 상세 정보만 수정한다.

### 난 묶음 삭제

```http
DELETE /api/orchid-groups/{orchidGroupId}
```

응답은 공통 성공 응답을 사용한다.

```json
{
  "data": null,
  "message": null
}
```

---

## 14. 출하·경매 추적 API

### lot 조회

```http
GET /api/auction-lots?from=&to=&market=&variety=&grade=&status=&reviewOnly=&returnOnly=&waitingOnly=&keyword=&page=0&size=20
GET /api/auction-lots/{lotId}
GET /api/auction-lots/{lotId}/timeline
GET /api/auction-tracking/summary
```

`reviewOnly`는 매칭 실패, 수량 불일치, 반환 추정 등 운영자 확인 대상을 반환한다. `returnOnly`는 반환 추정, `waitingOnly`는 재경매 대기 lot을 조회한다.

목록은 서버 페이지네이션을 사용한다. `page`는 0부터 시작하고 `size`는 기본 20, 최대 100이다.

```json
{
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 1156,
    "totalPages": 58
  },
  "message": null
}
```

요약 API는 페이지와 무관한 전체 데이터 집계이며, 전체 lot 엔티티를 적재하지 않고 DB 집계 쿼리를 사용한다.

### 반환 확인

```http
POST /api/auction-lots/{lotId}/confirm-return
```

```json
{
  "returnedQuantity": 20,
  "worker": "관리자",
  "memo": "일부 도착 확인"
}
```

`반환추정` 또는 `부분반환` 상태에서 호출한다. 확인 수량이 현재 대기 수량보다 적으면 `PARTIALLY_RETURNED`, 같으면 `RETURNED`로 변경하고 상태 이력을 생성한다. 수량을 생략하면 현재 대기 수량 전체를 확인하는 기존 요청과 호환한다.

### 수량 보정

```http
POST /api/auction-lots/{lotId}/adjust-quantity
```

```json
{
  "soldQuantity": 50,
  "waitingQuantity": 30,
  "returnedQuantity": 20,
  "worker": "관리자",
  "memo": "현장 확인"
}
```

판매 + 대기 + 반환 수량은 출하 수량과 같아야 한다.

### 상태 변경

```http
PATCH /api/auction-lots/{lotId}/status
```

상태, 변경 사유, 작업자, 메모를 받고 상태 이력을 생성한다. 수동 매칭, 재고 자동 이동, 정산 생성 API는 아직 제공하지 않는다.

### 경매 출하 전표 후보

```http
GET /api/sales-slips/auction-shipments
```

아직 판매 전표와 연결되지 않은 출하 기록과 포함 lot을 반환한다.

### 경매 출하 전표 생성

```http
POST /api/sales-slips
```

```json
{
  "salesType": "AUCTION",
  "saleDate": "2026-07-05",
  "auctionShipmentId": 10,
  "customerId": null,
  "memo": "음성 출하",
  "items": []
}
```

서버는 출하일을 판매일로 사용하고 경매장 거래처를 자동 생성·재사용한다. lot별 품목을 출하 수량과 0원 단가로 구성한다. 일반 판매는 `salesType=DIRECT`이며 기존 요청처럼 유형을 생략해도 된다.
