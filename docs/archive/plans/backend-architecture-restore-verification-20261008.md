# 백엔드 아키텍처 전환 — 운영 백업 복원 검증 근거

2026-10-08에 완료한 [ADR-004](../../adr/ADR-004-backend-architecture-migration.md)의 최종 복원 검증 기록이다. 현행 복원 절차는 [배포 가이드](../../07-deployment.md)를 따른다.

## 복원 대상과 환경

- 원본: `temp/green-house_20261006_030001.dump.gz` — 기존 운영 백업 원본을 보존했다.
- 원본 SHA-256: `06be57e9a0998738866b040bcdb28d7d4a9acd7fe655688069d37c595622eac5`
- 격리된 `postgres:18-alpine` 컨테이너, PostgreSQL 18.6. 기존 DB를 변경하지 않았다.
- 복원 시 Flyway V34. 현재 release에 이미 포함된 V35~V51의 17개 migration을 복원본에 적용하고 Hibernate schema validation을 통과했다. 새 migration은 추가하지 않았다.
- 검증 대상: ACTIVE coverage, 난 묶음 320개, public 테이블 57개.

## 실행 결과

실제 Gradle task를 각각 HTTP 서버 없이 실행했다. 대사·기동 CLI 자체는 Flyway를 실행하지 않는다.

| Task | 종료 코드 | 결과 |
|---|---|---|
| `orchidLedgerReconcile` | 0 | `stage=ACTIVE`, `ready=true`, `issues=[]` |
| `orchidLedgerStartupVerify` | 0 | Hibernate schema validation 및 원장 startup guard 성공 |

migration 적용 후, 각 CLI 실행 전후 모든 public 테이블 행·시퀀스와 스키마 정의를 비교했다. 두 CLI 모두 아래 해시가 동일하며 데이터·시퀀스·스키마 변경이 없었다.

| 보존 대상 | 실행 전후 동일한 SHA-256 |
|---|---|
| public 테이블 전체 행 및 시퀀스 | `021afb8b40df54c0298c8521df917d2a4c4afded33faac371367594573eaead0` |
| 전체 스키마 정의 | `818a64e824b3d1f6e9b45822e828cb919acb716d9c6de6bd23027ef4ea2c5439` |

행은 테이블 이름 순서와 정렬한 JSON으로, 시퀀스는 `pg_sequences`로 비교했다. 스키마는 `pg_dump --schema-only --no-owner --no-privileges` 결과에서 PostgreSQL 18의 실행별 `\restrict`/`\unrestrict` 키를 제외하고 비교했다. migration 자체의 변경과 읽기 전용 CLI의 데이터 보존을 구분한다.

원본 백업과 검증 로그는 보존하고 해당 임시 컨테이너는 제거했다. 원자료·접속 비밀번호는 이 문서에 포함하지 않는다. 로컬 실행 산출물은 `temp/architecture-completion-20261008T140337Z/`의 JSON 요약 및 두 task 로그이며 저장소에는 이 검증 근거만 보관한다.

지속 회귀 검사는 [CLI PostgreSQL 테스트](../../../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupLedgerCliPostgresE2ETest.java)가 ACTIVE fixture의 실제 custom dump 복원과 두 CLI 실행 후 모든 public 테이블·시퀀스 보존을 검증한다. 이 자동화 검증과 위 운영 백업 복원 검증은 별도로 수행했다.
