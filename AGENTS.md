# Git 관리 규칙
## 브랜치 전략

main
develop
feature/*
fix/*
chore/*
docs/*

권장 흐름:

main      # 안정 버전
develop   # 통합 개발 브랜치
feature/* # 기능 개발
fix/*     # 버그 수정
docs/*    # 문서 수정
chore/*   # 설정/환경/정리 작업

예시:

feature/farm-map-ui
feature/orchid-group-crud
feature/orchid-group-move
feature/sales-slip-print
fix/move-work-record-transaction
docs/update-domain-model
chore/docker-compose-postgres

## 커밋 기준

커밋은 기본적으로 기능 단위로 한다.
모든 변경 사항을 하나의 커밋에 담지 않는다.

## 커밋 메시지 규칙

Conventional Commits 형식을 따른다.

type: summary

사용할 type:

feat      기능 추가
fix       버그 수정
docs      문서 수정
style     포맷팅, 세미콜론, 공백 등
refactor  동작 변경 없는 리팩터링
test      테스트 추가/수정
chore     빌드, 설정, 의존성, 기타 작업


# 문서 수정 규칙

기능 구현으로 인해 도메인, API, DB, UI 흐름이 바뀌면 관련 문서를 함께 수정한다.

예시:

API 경로 변경 → docs/06-api-spec.md 수정
테이블 구조 변경 → docs/07-database-design.md 수정
농장맵 동작 변경 → docs/04-feature-spec.md, docs/05-ui-ux-spec.md 수정
MVP 범위 변경 → docs/01-project-overview.md, docs/02-requirements.md 수정
v2 기준 변경 → docs/12-change-log-v2.md 수정

문서는 구현과 분리된 장식물이 아니라, 다음 작업자가 판단할 기준이다.

# 에이전트 작업 원칙

AI 에이전트 또는 Codex는 다음 원칙을 따른다.

작업 전 관련 문서를 먼저 확인한다.
문서에 없는 큰 구조 변경은 임의로 하지 않는다.
MVP 범위를 넘어서는 기능은 구현하지 않고, 필요한 경우 TODO 또는 문서 제안으로 남긴다.
정밀 CAD/GIS 수준의 지도 기능을 만들지 않는다.
난 묶음은 반드시 논리 구역 기준으로 배치한다.
난 묶음 이동은 작업 이력 생성을 포함해야 한다.
판매 수량 자동 차감은 MVP 범위가 아니므로 임의로 구현하지 않는다.
A5 출력 기준을 유지한다.
부모님 세대 사용성을 해치는 복잡한 UI를 피한다.
운영 데이터 보존 원칙을 우선한다.
빌드 또는 테스트가 가능하면 변경 후 반드시 실행한다.
실행하지 못한 테스트는 PR 또는 작업 결과에 명시한다.