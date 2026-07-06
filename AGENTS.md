# Git 관리 규칙
## 브랜치 전략

기본 브랜치:

main
develop

작업 브랜치:

feature/*
fix/*
docs/*
chore/*

## 커밋 기준

커밋은 기능 또는 변경 목적 단위로 나눈다.
서로 다른 성격의 변경을 하나의 커밋에 모두 담지 않는다.

예시:

docs: reorganize project documents
feat: add auction lot status history
fix: create move work record in same transaction
chore: update docker compose postgres config

## 커밋 메시지 규칙

type: summary

사용할 type:

feat      기능 추가
fix       버그 수정
docs      문서 수정
style     포맷팅, 세미콜론, 공백 등
refactor  동작 변경 없는 리팩터링
test      테스트 추가/수정
chore     빌드, 설정, 의존성, 기타 작업

---

# 문서 구조

docs/
  00-index.md
  01-overview.md
  02-domain-model.md
  03-feature-summary.md
  04-architecture.md
  05-sales-auction-settlement.md
  06-api-guide.md
  07-deployment.md
  08-roadmap.md

  api/
    openapi.yaml
    API_INDEX.md
    DOMAIN_RULES.md
    API_GAP_ANALYSIS.md
    slices/*.openapi.yaml

  archive/
    old-v2-docs/

문서 역할:

00-index.md                    문서 목차와 읽는 순서
01-overview.md                 프로젝트 목표, 사용자, 운영 방식
02-domain-model.md             핵심 도메인 모델과 도메인 규칙
03-feature-summary.md          구현 기능 요약과 화면 흐름
04-architecture.md             프론트엔드/백엔드 구조, 모듈 경계
05-sales-auction-settlement.md 판매, 경매, 정산, 입금 관리
06-api-guide.md                API 문서 사용 방법
07-deployment.md               로컬 실행, 배포, 백업, 운영 체크리스트
08-roadmap.md                  MVP 이후 확장 계획

`docs/archive/old-v2-docs/`는 참고용 보관 문서다.
새 작업의 기준 문서로 사용하지 않는다.

---

# 문서 수정 규칙

기능 구현으로 인해 도메인, DB, UI 흐름, 운영 정책이 바뀌면 관련 문서를 함께 수정한다.

예시:

도메인 모델 변경          → docs/02-domain-model.md
화면 흐름 또는 기능 변경   → docs/03-feature-summary.md
모듈 구조 변경             → docs/04-architecture.md
판매/경매/정산 정책 변경   → docs/05-sales-auction-settlement.md
API 사용 방식 변경         → docs/06-api-guide.md 또는 docs/api/*
배포/환경/백업 방식 변경   → docs/07-deployment.md
MVP 이후 범위 변경         → docs/08-roadmap.md
프로젝트 목표/운영 방식 변경 → docs/01-overview.md

문서는 구현과 분리된 장식물이 아니다.
다음 작업자가 구조와 정책을 판단할 수 있는 기준으로 유지한다.

단, 코드만 보면 알 수 있는 DTO 필드, enum, required 여부, Controller 목록을 일반 md 문서에 중복 작성하지 않는다.
요청/응답 필드와 validation은 OpenAPI 또는 실제 코드를 기준으로 확인한다.

---

# API 확인 규칙

API를 수정하거나 호출 코드를 작성할 때는 다음 순서로 확인한다.

1. `docs/06-api-guide.md`에서 API 문서 사용 원칙을 확인한다.
2. `docs/api/API_INDEX.md`에서 관련 도메인과 slice 위치를 찾는다.
3. 요청/응답 필드, query parameter, request body, response schema는 `docs/api/slices/*.openapi.yaml` 또는 실제 Controller/Request/Response 코드를 기준으로 확인한다.
4. 전체 `docs/api/openapi.yaml`은 타입 생성, 전체 API 검증, 광범위한 변경 검토가 필요할 때만 읽는다.
5. 코드만 보고 오해하기 쉬운 도메인 정책, 변경 금지 규칙, MVP 이후 범위는 `docs/api/DOMAIN_RULES.md`를 확인한다.
6. `docs/api/API_GAP_ANALYSIS.md`에 미구현으로 표시된 endpoint는 실제 구현으로 가정하지 않는다.

---

# API 문서 갱신 규칙

API를 추가하거나 수정한 경우 다음 순서로 갱신한다.

1. Controller, Request/Response DTO, validation, 테스트를 먼저 수정한다.
2. OpenAPI를 재생성한다.
3. 필요한 경우 도메인별 slice를 갱신한다.
4. API가 새 도메인에 해당하면 `docs/api/API_INDEX.md`에 위치만 추가한다.
5. 도메인 규칙이 바뀐 경우에만 `docs/api/DOMAIN_RULES.md`를 수정한다.
6. 과거 초안과 구현 차이가 바뀌면 `docs/api/API_GAP_ANALYSIS.md`를 수정한다.
7. API 사용 방식 자체가 바뀐 경우 `docs/06-api-guide.md`를 수정한다.

---

# 에이전트 작업 원칙

## 작업 전 확인

- 작업 전 관련 문서를 먼저 확인한다.
- 문서에 없는 큰 구조 변경은 임의로 하지 않는다.
- `archive/old-v2-docs`의 내용은 과거 참고용으로만 사용한다.
- 구현과 현재 문서가 충돌하면 코드와 OpenAPI를 우선 확인하고, 문서 수정 필요 여부를 작업 결과에 남긴다.

## MVP 범위 보호

- MVP 범위를 넘어서는 기능은 임의로 구현하지 않는다.
- 필요한 경우 TODO 또는 문서 제안으로 남긴다.
- 정밀 CAD/GIS 수준의 지도 기능을 만들지 않는다.
- 부모님 세대 사용성을 해치는 복잡한 UI를 피한다.
- A5 출력 기준을 유지한다.

## 농장 도메인 원칙

- 난 묶음은 반드시 논리 구역 기준으로 배치한다.
- 난 묶음 이동은 작업 이력 생성을 포함해야 한다.
- 난 묶음 이동과 이동 이력 생성은 가능하면 하나의 트랜잭션으로 처리한다.
- 운영 데이터 보존 원칙을 우선한다.
- 작업 이력, 이동 이력, 판매/정산 이력은 삭제보다 상태 변경 또는 취소 기록을 우선한다.

## 판매·경매·정산 원칙

- 직접 판매와 경매 출하 추적을 혼동하지 않는다.
- 경매장은 `AUCTION_HOUSE` 유형의 거래처로 관리한다.
- 경매 출하 lot, 경매 시도, 경매 결과, 반환, 정산, 입금은 이력을 보존한다.
- 판매 수량 자동 차감은 MVP 범위가 아니므로 임의로 구현하지 않는다.
- 부분입금, 예치금, 계좌 자동 매칭은 구현 범위를 문서에서 확인한 뒤 작업한다.
- 관련 변경은 `docs/05-sales-auction-settlement.md`와 `docs/api/*`를 함께 확인한다.

## 테스트와 결과 보고

- 빌드 또는 테스트가 가능하면 변경 후 반드시 실행한다.
- 실행하지 못한 테스트는 PR 또는 작업 결과에 명시한다.
- 데이터 마이그레이션, 정산, 입금, 수량 변경 로직은 테스트 없이 완료 처리하지 않는다.

## 에이전트 응답 스타일

- 짧게 작성.
- 결과 중심.
- 불필요한 배경 설명 생략.
- 장황한 서술형 종결 지양.
- 실패/미실행/보류 사항은 짧게 명시.
- 예: `기존 삭제 상태의 파일 그대로 보존.`