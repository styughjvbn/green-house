# API 가이드

## 1. 기준 문서

API 상세 명세는 OpenAPI를 기준으로 관리한다.

```text
docs/api/openapi.yaml
docs/api/slices/*.openapi.yaml
```

두 명세는 직접 수정하지 않는다. Controller와 요청/응답 DTO를 구현한 뒤 Springdoc 결과로 전체 명세와 slice를 함께 다시 만든다.

```bash
# DB가 실행 중인 상태에서 백엔드를 임시 포트로 실행해 생성
python3 scripts/generate_openapi.py

# 이미 실행 중인 백엔드를 이용해 생성
python3 scripts/generate_openapi.py --url http://localhost:8080/api-docs
```

생성 과정은 `WORK_OPERATION_V2_ENABLED=true`, `AUTH_ENABLED=false`로 임시 백엔드를 실행한다. 분할 스크립트는 모든 operation이 controller tag 기준으로 정확히 하나의 slice에 포함되는지 검사하며, 새 controller tag의 매핑이 없으면 실패한다.

## 2. 주요 그룹

| 파일 | 역할 |
|---|---|
| `auth.openapi.yaml` | 로그인, 로그아웃, 현재 사용자 조회 |
| `farm-structure.openapi.yaml` | 하우스, 물리 배드, 논리 구역, 난 묶음 조회 |
| `farm-status.openapi.yaml` | 농장 현황 맵, 선택 범위 조회, 대시보드 요약 |
| `orchid-command.openapi.yaml` | 난 묶음 생성, 수정, 이동, 배치 |
| `inventory.openapi.yaml` | 품종 CRUD/삭제, 자재 CRUD/삭제, 입고 기록 생성/수정/포트작업/취소/삭제, 목록 페이지네이션 |
| `orchid-collection.openapi.yaml` | 난 묶음 사용자 그룹과 소속 관리 |
| `derived-orchid-group.openapi.yaml` | 품종·년생·화분 크기 기준 자동 그룹 조회 |
| `work.openapi.yaml` | 작업 유형과 기존 작업 이력 |
| `work-operation.openapi.yaml` | 범위별 농약 작업 실행과 난 묶음 통합 이력 |
| `partner.openapi.yaml` | 거래처, 정산 설정 |
| `sales.openapi.yaml` | 판매 전표, 경매 판매 전표 |
| `analytics.openapi.yaml` | 판매·거래처·작업 분석 |
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
- `python3 scripts/generate_openapi.py` 실행
- seed 데이터 영향

## 6. 신규 작업 실행 API 범위

현재 신규 작업 실행 API는 `HOUSE`, `DERIVED_GROUP`, `USER_COLLECTION`, `MANUAL_SELECTION` 범위의 `PESTICIDE` 작업을 지원한다. 대상 미리보기, 생성, 상세, 대상별 진행·건너뛰기, 작업 시작·일시중지·재개·취소·완료, 난 묶음 통합 이력을 제공한다. 다른 작업 유형을 구현된 것으로 가정하지 않는다.
