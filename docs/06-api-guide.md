# API 가이드

## 1. 기준 문서

API 상세는 Markdown 문서에 복사하지 않는다.

기준 파일:

```text
docs/api/openapi.yaml
docs/api/slices/*.openapi.yaml
```

`openapi.yaml`은 전체 명세이고, `slices`는 도메인별로 나눈 명세다.

## 2. API 그룹

| 파일 | 역할 |
|---|---|
| `farm-structure.openapi.yaml` | 동, 물리 배드, 논리 구역, 난 묶음 조회 |
| `farm-status.openapi.yaml` | 농장 현황 맵, 줌, 선택 대상 난 묶음, 대시보드 요약 |
| `orchid-command.openapi.yaml` | 난 묶음 생성/수정/삭제/이동/정렬, 배치 프로필 |
| `work.openapi.yaml` | 작업 유형, 작업 이력 |
| `partner.openapi.yaml` | 거래처, 거래처 정산 설정 |
| `sales.openapi.yaml` | 판매 전표, 판매 출력, 경매 출하 전표 후보 |
| `auction.openapi.yaml` | 경매 lot, 상태 변경, 반환 확인, 수량 보정, 경매 정산 |
| `payment.openapi.yaml` | 입금 확인, 거래처 잔액, 입금 이벤트 |

## 3. 공통 응답

기본 성공 응답은 다음 형태를 사용한다.

```json
{
  "data": {},
  "message": null
}
```

에러 응답은 다음 형태를 기준으로 한다.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": []
  }
}
```

프론트엔드에서는 HTTP 상태 코드뿐 아니라 `error.code`를 기준으로 분기하는 것이 안전하다.

## 4. API 작성 규칙

- 날짜는 ISO-8601 형식을 사용한다.
- 목록 API는 기간/상태/거래처/대상 필터를 우선 제공한다.
- 대량 데이터는 날짜 범위를 반드시 고려한다.
- 상태 변경 API는 변경 이력을 남기는 방향을 우선한다.
- 삭제 API는 운영 데이터에 신중하게 적용한다.
- 화면에서 쓰지 않는 필드를 응답에 과도하게 포함하지 않는다.

## 5. 변경 시 체크리스트

API를 추가하거나 변경할 때 다음을 함께 확인한다.

- Controller 요청/응답 DTO
- Service 트랜잭션 경계
- Entity 관계와 DB 마이그레이션
- 프론트엔드 타입
- OpenAPI slice
- 관련 화면의 에러 처리
- 기존 seed 데이터 영향

## 6. 문서화 원칙

- 엔드포인트 목록은 OpenAPI에 둔다.
- Markdown에는 API를 사용하는 업무 흐름만 쓴다.
- API 명세와 코드가 다르면 코드를 기준으로 검증하고 OpenAPI를 갱신한다.
