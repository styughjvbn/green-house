# 기능·운영 특화 문서

현재 구현된 기능의 도메인 정책, 운영 절차와 진행 중 개선 계획을 다룬다.

- [판매·경매·정산](sales-auction-settlement.md)
- [인증·인가](authentication.md)
- [작업 실행·난 그룹](work-operation-and-orchid-collection.md)
- [난 묶음 Mutation Engine 전환 코드 수명](orchid-group-mutation-transition.md)
- [전체 백엔드 리팩터링 계획](backend-refactoring-plan.md) — 13개 모듈의 책임·확장 경계와 단계별 검증 기준
- [Mutation Engine 리팩터링 상세](orchid-group-mutation-refactoring-detail.md) — 전체 계획의 하위 상세
- [데모 환경 운영](demo-operations.md)

전체 문서 읽는 순서는 [`../00-index.md`](../00-index.md), API 상세는
[`../06-api-guide.md`](../06-api-guide.md)와 `../api/`를 기준으로 확인한다.

- [2026-09-08 Engine 전환 검증](orchid-engine-cutover-20260908.md) — 최신 저장 백업 복원·ACTIVE·멱등성·API smoke 결과
