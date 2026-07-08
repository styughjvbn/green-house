# API 가이드

## 1. 기준 문서

API 상세 명세는 OpenAPI를 기준으로 관리한다.

```text
docs/api/openapi.yaml
docs/api/slices/*.openapi.yaml
```

## 2. 주요 그룹

| 파일 | 역할 |
|---|---|
| `farm-structure.openapi.yaml` | 하우스, 물리 배드, 논리 구역, 난 묶음 조회 |
| `farm-status.openapi.yaml` | 농장 현황 맵, 선택 범위 조회, 대시보드 요약 |
| `orchid-command.openapi.yaml` | 난 묶음 생성, 수정, 이동, 배치 |
| `inventory.openapi.yaml` | 품종 CRUD/삭제, 자재 CRUD/삭제, 입고 기록 생성/수정/포트작업/취소/삭제, 목록 페이지네이션 |
| `work.openapi.yaml` | 작업 유형, 작업 이력 |
| `partner.openapi.yaml` | 거래처, 정산 설정 |
| `sales.openapi.yaml` | 판매 전표, 경매 판매 전표 |
| `auction.openapi.yaml` | 경매 lot, 결과 입력, 상태 변경, 반환 확인, 수량 보정, 정산 |
| `payment.openapi.yaml` | 입금 확인, 거래처 잔액, 입금 이벤트 |

## 3. 판매/경매 API 규칙

일반 판매 전표:

- `salesType = DIRECT`
- `partnerId` 필수
- `items` 필수
- `auctionShipmentId` 사용 안 함

경매 판매 전표:

- `salesType = AUCTION`
- `partnerId`는 `AUCTION_HOUSE` 거래처여야 함
- `items` 필수
- `salesStatus = 작성중 | 출하 완료`
- `작성중` 저장 시에는 allocation 예약만 반영
- `출하 완료` 시 `AuctionShipment`, `AuctionShipmentLot` 생성
- `auctionShipmentId`는 생성 요청에서 사용하지 않음

즉, 현재 기준 경매 흐름은 `기존 출하 기록 선택 후 전표 생성`이 아니라 `전표를 먼저 저장하고 출하 완료 시 출하 기록과 lot 생성`이다.

경매 결과 입력:

- `POST /api/auction-lots/{id}/results`
- `attemptStatus`는 `SOLD`, `PARTIALLY_SOLD`, `FAILED`, `RETURN_INFERRED`
- `SOLD`, `PARTIALLY_SOLD`는 `resultLines` 필요
- `FAILED`, `RETURN_INFERRED`는 서버가 대기 수량 기준 결과 행을 자동 생성

## 4. 공통 응답

성공:

```json
{
  "data": {},
  "message": null
}
```

에러:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": []
  }
}
```

## 5. 변경 체크리스트

- Controller 요청/응답 DTO
- Service 트랜잭션 경계
- Entity 관계와 DB 반영
- 프론트 요청 payload
- OpenAPI 갱신
- seed 데이터 영향
