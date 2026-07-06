# AGENTS.md

## API 확인 규칙

- API를 수정하거나 호출 코드를 작성할 때는 먼저 `docs/api/API_INDEX.md`에서 관련 도메인을 찾는다.
- 요청/응답 필드, query parameter, request body, response schema는 `docs/api/slices/*.openapi.yaml` 또는 실제 Controller/Request/Response 코드를 기준으로 확인한다.
- 전체 `docs/api/openapi.yaml`은 타입 생성, 전체 API 검증, 광범위한 변경 검토가 필요할 때만 읽는다.
- 코드만 보면 알 수 있는 DTO 필드, enum, required 여부, Controller 목록을 새 md 문서에 중복 작성하지 않는다.
- 코드만 보고 오해하기 쉬운 도메인 정책, 변경 금지 규칙, MVP 이후 범위는 `docs/api/DOMAIN_RULES.md`에만 기록한다.
- `docs/api/API_GAP_ANALYSIS.md`에 미구현으로 표시된 endpoint는 실제 구현으로 가정하지 않는다.

## 문서 갱신 규칙

API를 추가/수정한 경우 다음 순서로 갱신한다.

1. Controller, Request/Response DTO, validation, 테스트를 먼저 수정한다.
2. OpenAPI를 재생성한다.
3. 필요한 경우 도메인별 slice를 갱신한다.
4. API가 새 도메인에 해당하면 `API_INDEX.md`에 위치만 추가한다.
5. 도메인 규칙이 바뀐 경우에만 `DOMAIN_RULES.md`를 수정한다.
6. 과거 초안과 구현 차이가 바뀌면 `API_GAP_ANALYSIS.md`를 수정한다.
