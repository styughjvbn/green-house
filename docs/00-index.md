# 난 농장 관리 시스템 문서 인덱스 v3

## 문서 목적

이 문서 모음은 `green-house` 프로젝트의 현재 구현을 기준으로 유지보수와 다음 작업을 돕기 위한 최소 문서 세트다.

기존 v2 문서는 요구사항, 기능정의, UI/UX, DB, API, 개발계획, 변경로그가 분리되어 있었고 같은 내용이 여러 문서에 반복되었다. v3에서는 다음 원칙을 따른다.

- **코드와 OpenAPI가 상세 명세의 기준**이다.
- 문서는 **도메인 규칙, 화면 흐름, 구현 방향**만 정리한다.
- 이미 완료된 개발 계획이나 오래된 설계·변경 로그는 `archive/`로 이동한다.
- API 상세 요청/응답은 `api/openapi.yaml`과 `api/slices/*.openapi.yaml`을 본다.

## 문서 구조

```text
docs/
  00-index.md
  01-overview.md
  02-domain-model.md
  03-feature-summary.md
  04-architecture.md
  06-api-guide.md
  07-deployment.md
  08-roadmap.md

  features/
    README.md
    sales-auction-settlement.md
    authentication.md
    work-operation-and-orchid-collection.md
    demo-operations.md

  api/
    openapi.yaml
    API_INDEX.md
    DOMAIN_RULES.md
    API_GAP_ANALYSIS.md
    slices/*.openapi.yaml

  archive/
    plans/
      README.md
    old-v2-docs/
```

## 문서별 역할

| 파일 | 역할 |
|---|---|
| `01-overview.md` | 프로젝트 배경, 목표, 사용자, 현재 범위 |
| `02-domain-model.md` | 핵심 도메인 모델과 관계 |
| `03-feature-summary.md` | 화면/기능별 현재 구현 요약 |
| `04-architecture.md` | 프론트엔드/백엔드/DB 구조와 모듈 경계 |
| `06-api-guide.md` | OpenAPI 사용 방법과 API 그룹 |
| `07-deployment.md` | 로컬 실행, 운영 배포, 백업 체크리스트 |
| `08-roadmap.md` | 이후 확장 후보와 우선순위 |
| `api/API_INDEX.md` | 도메인별 OpenAPI slice와 endpoint 위치 |
| `api/DOMAIN_RULES.md` | API만으로 판단하기 어려운 도메인 규칙 |
| `api/API_GAP_ANALYSIS.md` | 과거 초안과 현재 구현 API의 차이 |
| `scripts/data-audit/audit-event-analysis.sql` | 운영 변경 감사 이벤트 조회 예시 |

## 기능·운영 특화 문서

| 파일 | 역할 |
|---|---|
| [`features/sales-auction-settlement.md`](features/sales-auction-settlement.md) | 판매, 경매 출하, 정산, 입금 정책 |
| [`features/authentication.md`](features/authentication.md) | 세션 인증·인가와 데모 인증 |
| [`features/work-operation-and-orchid-collection.md`](features/work-operation-and-orchid-collection.md) | 작업 실행, 대상 스냅샷, 그룹·전파 정책 |
| [`features/demo-operations.md`](features/demo-operations.md) | 운영 PC의 데모 DB·Kubernetes·초기화·모니터링 절차 |

구현이 완료된 계획서와 과거 설계는 `archive/`에 보관한다. 현재 정책을
판단할 때는 상위 문서와 `features/`, 코드, OpenAPI를 기준으로 사용한다.

## 관리 규칙

- 새 기능을 구현하면 관련 문서 1~2개만 수정한다.
- API 필드 설명을 문서에 길게 복사하지 않는다.
- 테이블 컬럼 전체 목록은 DB 마이그레이션과 엔티티를 기준으로 확인한다.
- 구현이 끝난 계획 문서는 `archive/plans/`로 이동하고 현행 문서 링크를 남긴다.
- 운영상 중요한 의사결정만 문서에 남긴다.
