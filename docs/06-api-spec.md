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

응답에는 논리 구역 정보, 난 묶음 목록, 최근 작업일 요약을 포함한다.

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
POST /api/orchid-groups/{orchidGroupId}/move
```

요청 예시:

```json
{
  "toBedZoneId": 21,
  "worker": "부모님",
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

### 작업 이력 수정

```http
PATCH /api/work-records/{workRecordId}
```

### 작업 이력 삭제

```http
DELETE /api/work-records/{workRecordId}
```

MVP에서는 삭제 API를 제공하더라도 실제 운영에서는 삭제보다 비활성화 또는 취소 상태를 우선 고려한다.

---

## 5. 작업 유형 API

MVP에서는 기본 작업 유형을 코드 또는 enum으로 제공할 수 있다.

MVP 이후 사용자 정의 작업 유형을 지원하기 위해 다음 API를 고려한다.

### 작업 유형 목록 조회

```http
GET /api/work-types
```

### 작업 유형 생성

```http
POST /api/work-types
```

요청 예시:

```json
{
  "name": "차광막 정리",
  "code": "CUSTOM_SHADE_CLEANUP",
  "sortOrder": 20
}
```

### 작업 유형 비활성화

```http
PATCH /api/work-types/{workTypeId}/deactivate
```

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

### 판매 상태 변경

```http
PATCH /api/sales-slips/{salesSlipId}/status
```

요청 예시:

```json
{
  "salesStatus": "출고 완료"
}
```

MVP에서는 상태 변경만 처리한다. MVP 이후 난 묶음 수량 자동 차감과 연결한다.

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
