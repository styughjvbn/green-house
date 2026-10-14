# Backend 평가 계획

> 보관 문서: 당시 코드·감사·검증 이력이며 현재 구현의 기준이 아니다. 미해결/운영 검증/보류 상태는 [현재 작업 목록](../10-remediation-progress.md)을 따른다.

## 1. 기준과 현재 단계

- 조사일: 2026-10-03, Asia/Seoul.
- 조사 기준: 현재 작업 트리, HEAD `08b50dc7`, 브랜치 `feature/work-correction-audit-events`.
- 이번 산출물: 이 계획과 [01-system-map.md](01-system-map.md). 전체 구조 조사와 평가 준비 단계다.
- 소스·설정·DB·OpenAPI·기존 문서는 수정하지 않는다. 품질 등급, 최종 결론, 리팩터링 제안은 이번 단계에 포함하지 않는다.
- 문서는 설계 의도 확인 자료다. 구현 사실은 실제 Controller, application, domain, Repository, migration, 테스트 소스와 저장된 OpenAPI를 대조해 기록한다.
- 정적 구조를 조사한 결과와 실행으로 증명한 결과를 구분한다. 이번 단계에서는 애플리케이션·테스트·migration·원장 CLI를 실행하지 않았다.

## 2. 평가 범위

| 영역 | 포함 범위 | 근거 진입점 |
| --- | --- | --- |
| 실행·조립 | 단일 Spring Boot 애플리케이션, Bean 구성, 환경 분기, startup runner | `backend/build.gradle.kts`, `BackendApplication.java`, `application.yml` |
| 모듈·계층 | common, audit, auth, demo, farm, work, partner, sales, auction, settlement, dashboard, analytics, print 전체 | `backend/src/main/java/com/greenhouse/backend/`, 각 `package-info.java` |
| 업무 쓰기 | 난 묶음·입고·그룹·기준정보·작업·판매·경매·정산·입금의 생성, 수정, 상태 변경, 취소, 보정 | application public 진입 메서드부터 저장 경계까지 |
| 업무 조회 | 상세, 목록, 페이지, 검색, viewport, 통합 이력, 그래프, 대시보드, 분석, 출력 데이터 | Controller → query service → Repository/projection → assembler |
| 영속성 | JPA 매핑, 식별자·sequence, DB 제약, 잠금, JDBC/QueryDSL/JPQL/native SQL, Flyway | `backend/src/main/resources/db/migration/`, 각 repository |
| 상태 이력 | Mutation/revision, Work 효과·보정·접수, Sales 재고 이동·스냅샷, Auction 시도·상태 이력, Payment 이벤트, Audit 이벤트 | 해당 Entity·저장 지점·조회 조립 지점 |
| 모듈 계약 | application DTO, port 구현, 식별자 연결, 현재 값과 과거 값의 구분 | `WorkTargetResolver`, `WorkEffectHandler`, `OrchidGroupUsageInspector` 등 |
| API·보안 | 요청 validation, 공통 성공·실패, capability, 인증·세션, demo 제한, 조건부 API | Controller/command/DTO, `SecurityConfig`, `docs/api/slices/` |
| 전환·운영 | 원장 importer/rehearsal/cutover/startup guard, 현재 migration 적용 경로, 배포 설정과 CI의 backend 검사 | Gradle CLI task, migration, `backend/Dockerfile`, `.github/workflows/verify.yml` |
| 테스트 | 순수 규칙, H2 통합, PostgreSQL/HTTP, 동시성·rollback·query count·migration 회귀 | `backend/src/test/`, Gradle test tag 설정 |

프론트엔드는 backend 계약 소비 지점이 필요한 후속 API 조사에서만 참조한다. 독립적인 UI 평가는 범위에 넣지 않는다.

## 3. 평가 기준

아래는 이후 상세 평가의 확인 기준이며 현재 구현에 대한 판정이 아니다.

| 기준 | 확인할 질문 | 필요한 증거 |
| --- | --- | --- |
| 도메인 정확성 | 수량·예약·금액·배치·상태 전이가 어디서 결정되는가? action과 실제 실행 검증이 같은 의미인가? | Entity/policy 메서드, command validation, capability, 경계값 테스트 |
| 데이터 보존 | 취소·보정이 원본 사실을 보존하는가? 과거 snapshot을 현재 값으로 다시 만드는가? | snapshot 생성 시점, 삭제/cascade, 효과·원장·이벤트 연결, 보존 테스트 |
| 트랜잭션 | 최상위 유스케이스와 하위 호출이 같은 원자 경계인가? 중간 실패 시 무엇이 rollback되는가? | 실제 `@Transactional`, proxy 호출, JDBC 연결, rollback 테스트 |
| 동시성·멱등성 | 잠금 순서, version, UNIQUE/CHECK, 원자 claim과 재요청 지문이 무엇을 보호하는가? | Repository SQL, migration, 충돌·병렬 실행 테스트 |
| 모듈 경계 | Entity/Repository/table의 소유권과 application/port 계약이 실제로 일치하는가? | import·타입 의존, 조회 SQL, architecture inventory, adapter 호출 |
| 조회 정확성·규모 | 검색·정렬·total·전체 집계의 범위가 맞는가? 페이지와 collection 로딩, ID batch, query count는 어떻게 동작하는가? | Repository query, assembler, 데이터 건수별 회귀·벤치마크 |
| API 계약 | Controller/validation과 저장된 OpenAPI가 맞는가? 오류 code, action, runtime 날짜가 일관되는가? | slice, 실제 command/DTO, HTTP contract 테스트, 생성 스크립트 |
| 인증·운영 경계 | 일반·인증 비활성화·demo·CLI에서 접근·actor·세션·기동 조건이 무엇인가? | security/filter/config/runner와 조건별 테스트 |
| 시간·migration | UTC 저장과 농장 업무일이 분리되는가? backfill·제약·복구·재실행의 전제가 무엇인가? | Clock/TimeConfig, migration 본문, PostgreSQL migration 테스트 |
| 검증 가능성 | 기존 테스트가 어떤 요구를 실제 검증하는가? H2와 PostgreSQL의 확인 범위가 무엇인가? | 테스트 메서드·assertion, 실행 task/tag, 실제 실행 보고서 |

평가 원칙의 기준 문서는 [AGENTS.md](../../AGENTS.md)와 [docs/04-architecture.md](../../docs/04-architecture.md)의 백엔드 구현 기준이다. 문서의 준수 선언 자체를 검증 결과로 사용하지 않는다. 파일 길이·클래스 수·이름만으로 품질을 판정하지 않는다.

## 4. 조사 순서와 단계별 확인 항목

### 단계 0 — 의도와 조사 기준 고정

- 먼저 `docs/00-index.md`, `01-overview.md`, `02-domain-model.md`, `04-architecture.md`, `06-api-guide.md` 확인.
- `docs/api/API_INDEX.md`에서 관련 slice를 찾고 `DOMAIN_RULES.md`, `API_GAP_ANALYSIS.md`를 참고.
- 판매·인증·작업 정책과 ADR-001/002는 해당 코드 경로의 의도를 확인하는 보조 자료로 사용.
- HEAD·브랜치·기존 변경과 실행 여부를 기록. archive와 과거 계획의 설명을 현재 구현으로 승격하지 않음.

### 단계 1 — 전체 inventory와 시스템 지도 작성: 이번 단계

- 전체 main/test/resource 파일 목록, 모듈별 클래스, Entity, Repository, Controller, application service 확인.
- Java/Spring/DB 라이브러리, 단일 프로젝트 구성, profile, 조건부 Bean, runner·CLI를 분리.
- 실제 import 방향과 port를 통한 런타임 호출 방향을 따로 표시.
- 주요 쓰기/읽기 경로를 public 메서드와 저장·조립 지점까지 추적.
- 클래스·메서드의 transaction annotation을 확인하고 caller transaction 참여와 독립 진입을 구분.
- migration·테스트·OpenAPI slice의 위치를 시스템 요소에 연결.
- 완료 기준: 13개 모듈을 빠짐없이 설명하고 사용자가 요구한 시스템 지도 항목마다 실제 파일·심볼 근거를 제공.
- 정적 확인 결과: 전체 Controller와 15개 OpenAPI slice의 method/path 143개 대조, Entity/Repository 파일 inventory, 문서의 파일 링크·명시 클래스/메서드 존재 확인. schema·runtime 검증과 구분해 시스템 지도에 기록.
- 산출물: `00-audit-plan.md`, `01-system-map.md`. 현재 여기까지 수행.

### 단계 2 — Farm과 Mutation의 도메인·영속 경계 상세 평가

- 농장 구조·배치 용량·위치 충돌·예약·현재 상태 invariant 확인.
- 직접 명령, 이동/변환, 폐기, 생성 취소, reconciliation, 보정, 보상 각각의 write set 추적.
- Engine의 잠금 전후 replay, fingerprint 정규화, revision 증가, recorder/fence/Entry 저장 순서 확인.
- 사용자 그룹 소속·상속과 자동 그룹의 조회 조건 확인.
- 입고 종류별 생성·수정·포트 실행·취소, Work 연결과 형제 입고 잠금 확인.
- 품종/자재 코드 발급, 활성화·삭제 조건, 자연키 검증 확인.
- importer·PREPARING/ACTIVE·startup guard·write fence의 실행 조건과 실제 PostgreSQL 보장 확인.

### 단계 3 — Work의 계획·실행·취소·보정 상세 평가

- 대상 resolve → 잠금·검증 → snapshot 확정 → aggregate 생성 경로 확인.
- 기록형/구조 변경/포트 workflow, handler/strategy registry와 capability의 관계 확인.
- 대상 완료, 부분 실행, 연관 폐기, 전체 완료, 남은 작업 종료, 단건·일괄 취소 구분.
- 효과·접수·membership·보정 접수의 키 범위, 원문 지문, 중복 요청과 실패 후 재시도 확인.
- Work가 정의한 port와 Farm 구현의 data/transaction 계약 확인.
- 직접·전파 이력, 정형 상세, 관계 카드·그래프에서 과거 snapshot과 현재 참조의 의미 확인.

### 단계 4 — Partner·Sales·Auction·Settlement의 업무 경계 상세 평가

- 거래처 활성/유형, ID 조회·검색·잠금과 과거 거래처 표시 확인.
- 일반·경매 전표 생성/수정/완료/취소의 allocation·예약·출고·복구를 각각 추적.
- 전표 번호, CREATION/OUTBOUND snapshot, 재고 이동·Mutation 연결, 경매 출하 생성/삭제 경계 확인.
- lot 결과·차수·반환·수량·상태 이력과 금액 범위 확인.
- 정산 초기화·재계산의 결과 선택, 중복 연결, snapshot 보존, 입금액 보존 확인.
- 일반/경매 입금의 잠금 순서를 개별 경로로 확인. 부분입금·초과입금·replay·원장/잔액/audit 원자성 확인.
- 설정·잔액 조회 중 기본 행 생성, startup 재구성 등 HTTP 쓰기 이외의 write path 포함.

### 단계 5 — 전체 read path와 API·보안·운영 교차 평가

- 목록·페이지·상세·옵션·호환 전체 조회·viewport·통합 이력의 경계 및 total/정렬 확인.
- readOnly/isolation, lazy association, collection fetch, row별 조회, batch 크기 확인.
- Dashboard/Analytics/Print가 소비하는 소유 모듈 집계·문서 계약과 보고 기간 확인.
- Controller·validation·slice·오류·action·조건부 노출 대조. 새 OpenAPI 생성은 별도 검증 단계에서 수행.
- 일반 인증·demo·auth disabled·공개 API, 쿠키 lifecycle, CORS, 요청 식별자·actor 확인.
- audit 누락/no-op/민감정보 처리와 저장 실패 rollback 확인.
- DB 설정, 시간, migration·CLI·기동·CI의 전제를 backend 기준으로 확인.

### 단계 6 — 실행 검증과 상세 평가 결과 정리: 후속 단계

- 먼저 직접 관련 단위·통합 테스트만 실행. 필요한 실패/경계 시나리오를 특정.
- 후속 평가가 기능 단위 검증에 도달하면 backend `./gradlew test`를 한 번 실행하고 실행 환경·결과를 기록.
- PostgreSQL SQL·constraint·Flyway·잠금·멱등성·수량·금액·transaction 검증은 관련 `workE2eTest` 대상으로 확인.
- query count는 기존 회귀 및 `workBenchmark -PworkBenchmarkEnforce=true`의 실제 assert 기준 확인. 응답 시간은 환경·데이터 규모를 함께 기록.
- H2 결과로 PostgreSQL fence/DDL/원자 SQL의 동작을 입증했다고 쓰지 않음.
- 각 결과에 요구/관찰/파일·메서드/검증/영향 범위/불확실성을 연결. 미실행·환경 제한·재현 불가를 명시.
- 최종 품질 판단과 개선 검토는 이러한 증거가 모인 후 별도 단계에서 작성. 이번 산출물에는 포함하지 않음.

## 5. 제외 항목과 현재 검증 한계

- 이번 단계: 코드 변경, 새로운 테스트 작성, 리팩터링 제안, 품질 점수·최종 평가, API/타입 재생성, Gradle/DB/CLI 실행 제외.
- 실제 운영 DB의 내용·migration 적용 상태·원장 ACTIVE 여부·트래픽·성능은 미확인. repository의 설정·DDL·테스트 존재만 확인.
- frontend UI/상태 관리/브라우저 A5 레이아웃, 정밀 CAD/GIS, 외부 서비스 신규 연동, 미구현 자동 매칭·예치금·가져오기 기능 제외.
- 외부 라이브러리 내부 구현·취약점 전수 조사·침투 테스트·실제 인프라 변경·운영 데이터 수정 제외.
- `docs/archive/`와 `db/migration-archive/`는 현재 동작의 기준으로 사용하지 않음. 보존 파일의 존재와 활성 migration 경로는 구분.
- 모든 main 파일의 목록·선언을 조사하고 주요 유스케이스 본문을 추적했지만, 모든 DTO 필드와 모든 SQL·테스트 assertion을 전수 평가한 단계는 아니다.

## 6. 후속 평가 기록 방식

후속 평가 문서도 `backend-audit/` 아래에 둔다. 다음 최소 항목을 사용한다.

| 항목 | 내용 |
| --- | --- |
| 평가 대상 | 모듈, 유스케이스, Controller/application 메서드 |
| 확인 기준 | 현행 정책 또는 코드가 보장해야 하는 불변식 |
| 실제 관찰 | 호출·저장·조회·DB 제약의 구체적 동작 |
| 근거 | repository 상대 파일 경로, 클래스·메서드, 필요시 행 번호 |
| 검증 상태 | 소스 확인 / 기존 테스트 확인 / 실행 성공 / 실패 / 미실행 |
| 적용 범위 | profile·환경·데이터 조건·관련 호출 경로 |
| 남은 확인 | 실행 또는 추가 추적이 필요한 사실. 개선안과 분리 |
