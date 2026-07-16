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
| `orchid-command.openapi.yaml` | 난 묶음 생성, 다중 생성·분갈이 작업, 수정, 이동, 배치 |
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

기간 작업 실행 API는 난 묶음 범위의 일반 기록형 작업과 자리 이동·분갈이·분주·합식·폐기 계획, 입고 기록 범위의 포트 작업 계획을 지원한다. 대상 미리보기, 생성, 기간·상태·범위별 목록, 상세, 대상별 진행·실행·건너뛰기, 작업 시작·일시중지·재개·취소·완료, 난 묶음 통합 이력을 제공한다.

`POST /api/work-operations/record`는 농장 전체, 동, 물리 배드, 논리 구역, 난 묶음 범위의 기록형 작업을 대상 스냅샷과 함께 즉시 완료한다. `WORK_OPERATION_V2_ENABLED=true`에서는 사용자 수동 `POST /api/work-records`를 거부하고, 비활성화하면 기존 작성 흐름으로 복귀한다. 기존 `WorkRecord` 조회·취소와 이동·입고 시스템 기록은 유지한다.

자리 이동·분갈이·분주·합식·입고 포트 작업은 계획 생성 시 대상을 스냅샷으로 확정하되 위치나 구조를 변경하지 않는다. 분갈이·분주·합식 실행 회차는 `POST /api/work-operations/{workOperationId}/structure-change-executions`에서 계획 대상 일부와 원본별 수량, 복수 결과를 처리하고 누적 작업 수량을 갱신한다. 기존 합식 완료 API는 이전 클라이언트 호환용이다. 다중 생성은 대상 없는 즉시 구조 변경 API로 유지한다.

입고 관리 화면의 즉시 실행은 `POST /api/work-operations/inbound-potting-executions`를 사용한다. 서버는 단일 대상 `WorkOperation`을 생성·시작·실행·완료하고 결과 작업을 반환하며, 별도의 포트 작업 `WorkRecord`는 만들지 않는다. 기존 `POST /api/inbound-records/{inboundRecordId}/potting`은 호환 응답인 `InboundRecordResponse`를 유지하면서 내부적으로 같은 실행기를 사용한다.

폐기는 대상별 완료 요청의 `resultDetails.discardQuantity`만큼 현재 가용 수량에서 차감한다. 일부 폐기는 기존 상태와 잔여 수량을 유지하고, 전량 폐기는 수량 0과 `폐기` 상태로 전환한다. 입력한 폐기 수량과 관계없이 해당 난 묶음의 폐기 처리는 한 번의 대상 실행으로 완료된다.

완료된 구조 변경 작업의 보정은 `POST/GET /api/work-operations/{workOperationId}/corrections`로 실행·조회한다. 생성 요청의 `orchidGroupAdjustments`에는 원본 작업이 만든 결과 난 묶음만 지정할 수 있으며 수량·상태 전후 값은 응답의 `effectDetails.adjustments`에서 확인한다. 후속 운영 데이터가 연결된 결과와 예약 수량을 침해하는 변경은 거부한다.
