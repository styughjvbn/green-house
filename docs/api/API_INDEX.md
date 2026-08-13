# API Index for Codex

이 문서는 관련 API 도메인과 slice 위치를 빠르게 찾기 위한 색인이다.
요청/응답 필드, required 여부, enum, endpoint 목록은 이 문서에 중복하지 않는다.

## 기준

- 전체 명세: `docs/api/openapi.yaml`
- 도메인 명세: `docs/api/slices/*.openapi.yaml`
- OpenAPI 버전: `3.1.0`
- Base URL: `/api`
- 공통 응답: `ApiResponse*` 래퍼 사용
- 생성 명령: `python3 scripts/generate_openapi.py`

## 조회 순서

1. 아래 표에서 관련 도메인과 slice를 찾는다.
2. 요청/응답 계약은 해당 slice 또는 실제 Controller/DTO를 확인한다.
3. 코드만 보고 오해하기 쉬운 정책은 `DOMAIN_RULES.md`를 확인한다.
4. 미구현·과거 초안과의 차이는 `API_GAP_ANALYSIS.md`를 확인한다.
5. 전체 비교와 타입 생성이 필요할 때만 `openapi.yaml` 전체를 읽는다.

## 도메인별 색인

| 도메인 | Slice | Controller tag | Package 후보 | 역할 |
|---|---|---|---|---|
| 인증·앱 컨텍스트 | `auth.openapi.yaml` | `auth-controller` | `com.greenhouse.backend.auth` | 로그인, 로그아웃, 현재 사용자, 농장 업무일자·시간대 |
| 농장 구조 | `farm-structure.openapi.yaml` | `farm-structure-controller`, `orchid-group-query-controller` | `com.greenhouse.backend.farm` | 동, 물리 배드, 논리 구역, 난 묶음 조회 |
| 농장 현황 | `farm-status.openapi.yaml` | `farm-status-controller`, `dashboard-controller` | `com.greenhouse.backend.farm`, `dashboard` | 현황 맵, 줌, 선택 범위, 대시보드 요약 |
| 난 묶음 명령 | `orchid-command.openapi.yaml` | `orchid-group-command-controller`, `bed-placement-controller`, `multi-create-work-operation-controller`, `repot-work-operation-controller` | `com.greenhouse.backend.farm` | 난 묶음 생성·수정·이동·배치와 구조 변경 호환 API |
| 품종·입고·자재 | `inventory.openapi.yaml` | `variety-controller`, `material-controller`, `inbound-record-controller` | `com.greenhouse.backend.farm` | 품종, 입고 기록, 자재 관리 |
| 난 묶음 사용자 그룹 | `orchid-collection.openapi.yaml` | `orchid-group-collection-controller` | `com.greenhouse.backend.farm` | 사용자 그룹과 난 묶음 소속 관리 |
| 난 묶음 자동 그룹 | `derived-orchid-group.openapi.yaml` | `derived-orchid-group-controller` | `com.greenhouse.backend.farm` | 품종·년생·화분 크기 기준 자동 그룹 |
| 작업 유형 | `work.openapi.yaml` | `work-type-controller` | `com.greenhouse.backend.work` | 작업 유형과 등록·실행 capability metadata |
| 작업 실행·이력 | `work-operation.openapi.yaml` | `work-operation-controller` | `com.greenhouse.backend.work` | 작업 계획·실행·보정, 대상 스냅샷, 통합 이력 |
| 거래처 | `partner.openapi.yaml` | `business-partner-controller`, `partner-settlement-settings-controller` | `com.greenhouse.backend.partner`, `settlement` | 거래처와 정산 설정 |
| 판매 전표 | `sales.openapi.yaml` | `sales-controller`, `print-controller` | `com.greenhouse.backend.sales`, `print` | 판매 전표, 출력, 가능한 업무 action |
| 분석 | `analytics.openapi.yaml` | `analytics-controller` | `com.greenhouse.backend.analytics` | 판매·거래처·작업 분석 |
| 경매 | `auction.openapi.yaml` | `auction-tracking-controller`, `auction-settlement-controller` | `com.greenhouse.backend.auction`, `settlement` | lot, 결과, 반환, 수량 보정, 경매 정산 |
| 입금·정산 이벤트 | `payment.openapi.yaml` | `payment-controller` | `com.greenhouse.backend.settlement` | 수동 입금, 거래처 잔액, 입금 이벤트 |

## 관련 정책 문서

| 변경 영역 | 함께 확인할 문서 |
|---|---|
| 판매·경매·정산·입금 | `docs/features/sales-auction-settlement.md` |
| 인증·세션·공개 앱 컨텍스트 | `docs/features/authentication.md` |
| 작업 실행·그룹·이력 | `docs/features/work-operation-and-orchid-collection.md` |
| API 사용·생성 방식 | `docs/06-api-guide.md` |
| 도메인 규칙 | `docs/api/DOMAIN_RULES.md` |

## 주의

`API_GAP_ANALYSIS.md`에 적힌 미구현 또는 제안 API는 실제 구현으로 가정하지 않는다.
프론트와 테스트는 반드시 생성된 OpenAPI 또는 실제 Controller에 노출된 API만 호출한다.
