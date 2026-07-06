# API Docs

이 디렉터리는 Codex가 API를 확인할 때 전체 문서를 반복해서 읽지 않도록 만든 색인/규칙/분할 명세다.

## 파일 역할

- `API_INDEX.md`: Codex가 가장 먼저 읽는 API 목차
- `DOMAIN_RULES.md`: 코드만 봐서는 알기 어려운 도메인 규칙
- `API_GAP_ANALYSIS.md`: 과거 API 초안과 실제 OpenAPI 차이
- `openapi.yaml`: 현재 구현 API의 전체 OpenAPI 명세
- `slices/*.openapi.yaml`: 도메인별 OpenAPI 분할본

## 사용 원칙

- API field, request, response, validation은 OpenAPI 또는 실제 코드를 기준으로 확인한다.
- 문서에는 코드에서 바로 알 수 있는 내용을 반복해서 쓰지 않는다.
- 도메인 규칙과 구현 의도만 `DOMAIN_RULES.md`에 남긴다.
- 전체 OpenAPI는 타입 생성, 스키마 검증, 전체 차이 확인이 필요할 때만 읽는다.

## 현재 명세 요약

- 2026-07-06
- operations: `47`
- path entries: `39`
- schemas: `87`
