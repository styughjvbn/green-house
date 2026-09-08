# 2026-09-08 Engine 전환 검증

사용자가 최신 백업으로 데이터를 재구성하고 추가 대기 없이 전환·안정화를 진행하도록
승인했다. 저장소에서 확인한 최신 백업을 PostgreSQL 18의 별도 DB에 복원했다.
기존 로컬 DB와 운영 Kubernetes에는 쓰지 않았다.

## 입력과 보존 결과

- 입력: `temp/green-house_20260826_030001.dump.gz` (Flyway V20)
- 입력 SHA-256: `4b243c7bd095109bcc31018f1fa53acd50902cd569c63d591b872b09a87412dc`
- manifest: `scripts/data-audit/orchid-state-chain-migration-manifest.json`, schema 2
- cutover key: `deb9b0c1-d3a1-4b91-8a5e-202609080001`
- 적용 업무일: `2026-09-08`, 최소 writer version: `2.0.0`
- Flyway V21~V26 적용과 Hibernate 검증 성공. 기존 V1~V24 파일 수정 없음.
- 난 묶음 269, Work 44, 효과 55, Lineage 16, 판매 전표 148 보존.
- Work 효과 42건·Lineage 16건의 Mutation 연결 확인. 과거 즉시 요청 키 18건은 원문을 추정하지 않고 receipt로 이관.
- 이 백업의 계획·진행 중 Work는 0건이다. 진행 중 Work 및 전환 전 판매 후속 처리는 PostgreSQL 회귀 fixture로 별도 검증한다.

## 전환과 검증

| 단계 | 결과 |
|---|---|
| PLAN | `PRE_BASELINE`, ready=true, issues=[] |
| IMPORT | Mutation 314, Entry 348, 현존 그룹 269, 삭제 tombstone 그룹 6 |
| 같은 IMPORT 재실행 | 신규 0, replay 314 |
| VERIFY → ACTIVE | 모두 ready=true, issues=[] |
| 새 Engine 서버 기동 | 모드 설정 없이 startup guard 통과 |
| 복제 DB API smoke | 난 묶음 생성, 병렬 즉시 Work, 변경 payload 409, 계획 회차 완료·재요청, 판매 예약·출고 재요청·취소와 수량 복구 |
| smoke 후 대사 | ACTIVE, 그룹 272, Mutation 320, Entry 356, ready=true, issues=[] |

판매 취소 재요청은 기존 정책대로 400으로 거절하고 복구 수량이 다시 늘지 않음을
확인했다. 첫 HTTP 응답과 재조회 시각의 PostgreSQL 소수점 정밀도 차이는 동일 결과
ID·업무 효과 재사용과 구분했다.

## 복원 산출물

- 컨테이너: `greenhouse-cutover-20260908`, PostgreSQL `127.0.0.1:55438`
- 깨끗한 전환 DB: `greenhouse_cutover`; API 쓰기 검증 복제본: `greenhouse_smoke`
- 전환 후 백업: `temp/cutover/green-house_engine_20260908.dump`
- 백업 SHA-256: `3696b7f14aa7545be49429a8231f16d96323d80a0f0ad2ad5722dec751b42525`
- 단계별 JSON: `temp/cutover/transition-results.json`
- API smoke 기록: `temp/cutover/api-smoke-results.json`
- 최종 대사: `temp/cutover/final-reconciliation.json`, smoke 대사: `temp/cutover/smoke-reconciliation.json`
- 로컬 백엔드 접속 환경: `temp/cutover/backend.env` (Git 제외)

백업과 원시 검증 파일은 운영 데이터가 포함될 수 있어 Git에 추가하지 않는다.
이 보고서의 격리 DB 검증 완료는 운영 DB 교체·Kubernetes 배포 완료를 의미하지 않는다.
새 이미지 배포 시 해당 환경의 DB도 동일한 절차로 ACTIVE 전환해야 한다.

## 코드 회귀

- 기본 460건·실제 PostgreSQL 96건, 실패·skip 0건. 수량 변경, 병렬 요청, 전체 batch 롤백, Work 완료 재시도, 과거 Sales 출고 복구, DB write fence·state-chain을 포함한다.
- `format check workE2eTest bootJar`, 프론트 `npm run check`, 생성 타입 일치 검증 통과.
- OpenAPI 136 operations·115 paths·228 schemas, 생성 명세와 TypeScript 차이 없음.
- 복원 스크립트는 비웹 Flyway·Hibernate 검증으로 바꾸고 전환 미완료 시 종료 코드 2를 반환한다. 문법 검사와 stub 기반 비웹 실행 성공·실패·로그 정리를 검증했다. 기존 DB를 지우는 전체 reset 실행은 하지 않았다.
