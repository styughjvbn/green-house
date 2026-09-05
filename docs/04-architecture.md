# 아키텍처

## 1. 전체 구조

```text
green-house/
 ├─ backend/      Spring Boot
 ├─ frontend/     Next.js
 ├─ docs/
 └─ docker-compose.yml
```

## 2. 기술 스택

### Frontend

- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui와 Radix UI primitives
- React 기반 상태 관리
- 지도/드래그 UI는 기능 단위 컴포넌트로 분리
- 농장 현황 맵은 구조와 경량 난 묶음 배치를 한 번에 조회하고, 정규화·실제
  좌표 계산을 분리한다. 동·다이·구역·난 묶음 사각형은 Leaflet Canvas
  renderer를 사용하며 확대 단계에 필요한 HTML 라벨만 생성한다.
- 농장 현황의 검색·선택 레이어는 기본 구조 레이어와 분리하고 좌표 계산
  결과를 메모이제이션한다.
- 난 묶음 관리의 앱 라우트는 검색 파라미터만 feature의 `OrchidManagementRoutePage`로 전달한다. `RoutePage`는 `GET /farm-status/orchid-management`로 초기 2~4개 다이와 전체 다이 순서만 준비한다. 이후 관리 맵은 React Query cache를 기준으로 현재 viewport와 앞뒤 각각 표시 개수만큼의 이동 버퍼를 부분 조회하고, 방문한 범위를 재사용한다. URL 동기화와 현재 표시 범위 계산은 `useBedViewport`가 담당하며 전체 농장 구조를 선조회하지 않는다.
- 선택 이력은 동·다이·구역·난 묶음 범위별 페이지 API로 조회한다. 요약은 첫 20건만 사용하고 난 묶음 상세는 10건 단위로 조회한다. 선택 키·상세 페이지별 메모리 캐시와 진행 요청 공유·취소를 적용하고, 캐러셀 이동 상태는 선택 상태와 분리해 단순 스와이프가 이력 조회를 유발하지 않게 한다.
- 입고 관리와 작업 관리가 공통으로 사용하는 포트 실행·농장 배치 UI는 `entities/farm/ui`에 두고 저장 API는 각 `features/*`에서 연결한다.
- 판매와 inventory의 서버 페이지 목록은 TanStack Query로 관리한다. 두 기능 모두 URL을 조회 조건의 단일 기준으로 사용하고 서버와 클라이언트가 같은 파서와 query option을 공유한다. 판매 전표의 상세 선택도 `slipId` URL 상태로 관리해 deep link와 브라우저 탐색을 지원하며 상세 서버 상태를 local state에 복제하지 않는다. 서버 컴포넌트는 현재 URL 조건을 prefetch해 hydration하며, 공통 URL 페이지 훅은 검색 초안과 URL 변경만 담당한다.
- 작업 관리는 URL을 조회 범위·보기 방식·필터·페이지의 단일 기준으로 사용한다. 서버 진입 컴포넌트인 `WorkRecordRoutePage`는 현재 목록 또는 캘린더 query만 prefetch해 hydration하고, 작업 유형과 농장 전체 배치 정보는 등록 또는 실행 다이얼로그를 열 때 조회한다. 클라이언트 `WorkRecordPage`는 보기 전환과 등록 다이얼로그의 열림 상태만 관리하고, 등록 다이얼로그가 자체 참조 데이터의 로딩과 오류를 처리한다. 목록과 캘린더는 공통 작업 동작 훅과 상세 패널을 사용한다. 캘린더는 전용 기간 API를 한 번 호출하고, 작업 등록·실행 후 관련 작업 및 농장 query를 무효화한다.
- 작업 관리는 조회·상태 변경을 `model/operation`, 등록 상태와 대상 계산을 `model/registration`, 작업 유형별 표현 구성을 `model/work-types`로 구분한다. 화면은 `ui/list`, `ui/calendar`, `ui/detail`, `ui/registration`, `ui/work-types`에서 기능별로 구성한다. 대상 출처, 등록 가능 모드, 실행 workflow는 백엔드 capability를 사용하고 `workTypeDefinition.ts`에는 안내 문구 같은 표현 규칙만 둔다.
- 자리 이동 실행은 같은 품종의 원본 투입 수량을 실행 회차에서 합친 뒤 결과 합계를 차감한다. 별도 폐기 작업에 필요한 원본별 차감은 ID 순서의 결정적인 내부 배분을 사용하며 결과 계보와 직접 연결하지 않는다.

### Backend

- Spring Boot
- Java 21
- Lombok (`@RequiredArgsConstructor`, `@Getter`, `@Slf4j` 중심)
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway Migration

Lombok은 생성자 주입, 반복 getter, 표준 로거처럼 동작을 바꾸지 않는 보일러플레이트 제거에 적극 사용한다. 다만 JPA 엔티티의 상태 전이·생성 규칙, 테스트용 생성자, 검증·조립 로직처럼 명시성이 필요한 코드는 수동 구현을 유지한다.

### Infra

- Docker Compose
- PostgreSQL
- 운영 초기에는 미니 PC 또는 개인 서버 가능
- 외부 공개 시 Nginx/HTTPS 적용
- 데모는 별도 Kubernetes namespace와 `greenhouse_demo` DB를 사용한다.
- 운영·데모 애플리케이션 계정과 Flyway 계정을 각각 분리한다.
- 데모 요청은 `ROLE_DEMO` 인증 주체와 서버 측 API 제한을 적용한다.

## 3. 백엔드 구조

MSA가 아니라 **모듈러 모놀리스**로 관리한다.

실제 모듈 경계:

```text
common
audit
auth
farm
work
partner
sales
auction
settlement
dashboard
analytics
print
demo
```

### common

- 공통 응답
- MVC 예외 응답과 인증·데모 filter의 실패는 같은 `ErrorResponse`를 사용한다. filter는 공통 JSON writer로 직렬화한다.
- 페이지 목록 응답은 `PageResponse<T>`로 통일한다.
- 예외 처리
- 공통 유틸
- 공통 검증
- 요청의 작업자 이름 정규화와 인증된 감사 actor는 별도 계약이다. 데모의 요청 작업자 대체가 감사 actor 판정을 대신하지 않는다.

### audit

- 업무 데이터 변경 전후 스냅샷과 변경 필드를 PostgreSQL에 동기 저장한다.
- 도메인 엔티티 대신 독립 이벤트 DTO를 받아 다른 업무 모듈에 의존하지 않는다.
- 요청 ID, 세션 ID, 인증 계정명, 브라우저 인스턴스 ID를 변경 이벤트와 연결한다.
- 감사 저장 실패는 같은 트랜잭션의 원본 변경도 롤백한다.

### farm

- 동
- 물리 배드
- 논리 구역
- 논리 구역별 수용량과 난 묶음 숫자 배치 구간
- 난 묶음
- 품종
- 입고 기록
- 자재
- 배드 정밀 설정
- 자리 이동
- 입고 조회, 입고 명령, 포트 실행을 별도 application service로 분리한다.
- 입고 작업 스냅샷과 메모 조립은 전용 factory가 담당한다.
- 품종 목록의 난 묶음·최근 입고일·최근 작업일은 페이지 단위로 일괄 조회한다.
- 난 묶음 계보는 `work` 엔티티를 직접 참조하지 않고 `workOperationId` 값으로 연결한다.
- 난 묶음 취소·보정의 사용 여부 port는 Farm이 소유한다. Sales는 이 port를 구현하고, Farm adapter는 Work의 개수 조회를 Farm blocker로 변환한다. Work가 Farm에 의존하지 않는다. 차단 사유는 기존 입고→판매→작업 순서를 명시적으로 유지한다.
- 사용자 그룹 목록은 그룹 목록→소속 일괄 조회→난 묶음 상세 일괄 조회 순서로 조립한다. 목록의 각 그룹마다 조회를 반복하지 않는다.
- 난 묶음 물리 상태 변경과 revision ledger는 `farm.orchid.mutation`이 소유한다. Work·Sales·Inbound는 typed command와 식별자 계약으로 이 경계를 호출하고 업무 lifecycle은 각 모듈에 유지한다.
- cutover 이전 이력도 같은 Mutation header와 `orchid_group_mutation_entries`의 `BASELINE`·`CREATE`·`CHANGE`·`DELETE`로 저장한다. 모든 Entry는 연속 revision과 full snapshot 규칙을 사용하며 현재 행이 없는 삭제 그룹은 terminal `DELETE`로 보존한다.
- 전환 전용 importer는 승인된 complete state-chain manifest만 적재한다. Work 효과는 Work application의 제한된 source 조회·연결 API를 사용하고 Lineage 연결은 소유 모듈인 `farm`에서 수행한다. 별도 migration 모듈이나 과거 전용 Entry 모델은 두지 않는다.
- ledger rehearsal 대사는 `farm`의 현재 상태·revision chain과 모듈별 read-only application 계약을 조합한다. 각 모듈은 Work 진행 상태와 효과 연결, Sales 활성 allocation과 예약 수량처럼 자신이 소유한 정합성만 판정하며 데이터를 자동 보정하지 않는다.
- ledger coverage가 `ACTIVE`이면 PostgreSQL write fence가 transaction-local Mutation context 없는 `orchid_groups` INSERT·UPDATE와 모든 DELETE를 차단한다. 커밋 시에는 변경 revision에 대응하는 MutationEntry도 확인한다.
- state-chain 적재가 시작된 `PREPARING`과 `ACTIVE` coverage의 실행 인스턴스는 `minimumWriterVersion` 이상인 `ENGINE` writer mode여야 한다. startup guard는 부분 적재 위의 Legacy 재기동을 거부한다. manifest 적재는 원자적이고 재실행 가능하며 최종 대사를 통과한 경우에만 한 번에 ACTIVE로 전환한다.
- PREPARING 동안 DB fence는 아직 활성화되지 않으므로 운영 manifest 적재에는 외부 write-stop이 필수다. 모든 write path의 Engine routing이 끝나기 전에는 ACTIVE로 전환하지 않는다.
- 현재 Farm·Inbound·Work·Sales의 알려진 난 묶음 writer는 `LEGACY|ENGINE` 단일 경로 스위치를 공유한다. `ENGINE` 선택 시 Work 효과와 Sales 재고 이동은 같은 transaction에서 Mutation ID·correlation ID를 연결하며 dual write하지 않는다.
- PREPARING 전환 코드의 routing flag 호출자, `OrchidGroup` 직접 상태 변경자, 생성자와 repository write 호출자는 실행 가능한 architecture test의 명시적 inventory로 고정한다. 신규 writer는 inventory 허용 항목만 늘리지 않고 먼저 typed Engine command로 편입한다.
- 기본값은 운영 호환을 위한 `LEGACY`다. 최신 운영 백업을 복원한 격리 DB에서 complete state-chain 적재, ENGINE 시나리오 회귀와 `ACTIVE` 전환 rehearsal을 마친 뒤 aggregate 전체를 한 번에 전환하고, 검증 완료 전에는 운영 `ACTIVE` coverage를 만들지 않는다.
- 운영 `ACTIVE`에서는 모든 인스턴스를 `ENGINE`으로 고정하고 DB fence로 legacy 실행을 차단한다. 안정화 후 routing flag와 legacy 직접 writer를 제거하며, 기존 Work·Sales·Lineage 사실 데이터는 별도 소비 전환 없이 삭제하지 않는다.
- 전환 코드의 수명은 `features/orchid-group-mutation-transition.md`의 `TARGET`, `TRANSITION_ONLY`, `LEGACY_RETIRE`, `DATA_RETAIN` inventory를 기준으로 판단한다. 코드 제거 gate와 데이터 보존 기간을 분리하고, writer 호출자 분류는 architecture test로 고정한다.

`farm`의 각 계층은 동일한 기능 경계를 사용한다.

```text
application|domain|repository|controller|dto/
 ├─ structure/       동·물리 배드·논리 구역·배치 용량
 ├─ status/          농장 현황·확대 단계 조회
 ├─ orchid/          난 묶음 조회·명령·이동
 ├─ collection/      사용자 난 묶음 그룹
 ├─ inbound/         입고·포트 실행
 ├─ variety/         품종 기준 정보
 ├─ material/        자재 기준 정보
 └─ transformation/  분갈이·분주·합식·다중 생성·계보
```

저장소가 없는 현황 기능처럼 계층에 구현이 필요하지 않은 경우 해당 하위 패키지는 생략한다.

### work

- 작업 유형
- 작업 이력
- `WorkOperation` 기반 이동 작업 이력
- 신규 작업 전체 단위와 상태 전이
- 실제 난 묶음 대상 해석과 스냅샷
- 작업 유형별 효과 handler와 효과 적용 감사 기록
- 자리 이동·입고 포트 계획의 전용 실행 handler와 분갈이·분주·합식 공통 N:M 실행기
- 원본 대상 없는 작업 효과 실행 facade와 생성 결과 ID 연결
- `WorkOperation`과 작업 효과 연결 기반 난 묶음 이력 및 실행 회차 중심 계보 조회
- 입고 포트 계획과 작업 상세의 난 묶음·위치 참조 조회는 `work`가 port를 정의하고 `farm`이 구현한다. `work`는 농장 테이블이나 저장소를 직접 참조하지 않는다.
- 작업 계획·진행·조회·구조 변경·입고 포트 계획·즉시 실행은 각각 application service로 분리한다.
- 구조 변경 작업 기록은 기록 전용 application service가 계획 aggregate 생성과 기존 구조 변경·폐기·포트 실행기를 한 트랜잭션으로 조합한다. 입력 검증 실패 시 중간 계획이나 일부 결과를 남기지 않는다.
- 작업 목록·캘린더는 대상 배열을 제외한 요약 응답을 사용하고 진행률과 가능한 전체 작업 action은 대상·실행 상태의 DB 집계로 조립한다. 대상별 상세는 사용자가 작업을 선택할 때 단건 조회한다.
- 분갈이·분주·합식은 공통 구조 변경 실행기와 작업별 Strategy를 사용한다. 기존 분갈이·분주 단일 대상 요청도 변환기를 거쳐 같은 실행 코어로 위임하고, 기존 합식 완료 API만 호환 경로로 남아 있다. 난 묶음 저장소가 필요한 Strategy 구현은 `farm` 모듈에 둔다.
- 효과 실행과 효과 감사 저장을 분리하고 모든 신규 효과는 공통 저장 컴포넌트를 사용한다. 구조 변경 실행의 `WorkAppliedEffect`는 원본 `SOURCE`와 결과 `RESULT`를 연결하는 계보 노드다.
- state-chain importer용 Work source 조회와 Mutation link API는 상태 변경 효과만 제한해 제공한다. `farm` importer가 Work Repository나 테이블을 직접 읽지 않게 하는 전환용 모듈 경계다.
- 신규 즉시 완료 작업은 대상별 멱등성 조회를 반복하지 않고 효과 INSERT와 실행 상태 UPDATE를 모아 JDBC batch로 flush한다. 기존 작업 재실행 경로는 효과 키 조회와 DB UNIQUE 제약으로 멱등성을 유지한다.
- DB의 `timestamp without time zone` 시점 값은 UTC로 저장한다. 업무일자는 `Asia/Seoul` 기준으로
  계산하고 API 응답의 시점 값은 UTC에서 `Asia/Seoul`로 변환한다.

`work`의 `application`, `domain`, `dto` 계층은 동일한 기능별 하위 패키지로 구성한다.

```text
application|domain|dto/
 ├─ operation/   작업 계획·실행·조회·상태 전이·작업 유형
 ├─ target/      대상 선택·스냅샷·실행 상태·외부 대상 gateway
 ├─ effect/      효과 실행·감사·구조 변경과 입고 포트 계약
 └─ correction/  완료 작업 보정과 보정 대상 조회
```

### partner

- 거래처

### sales

- 판매 전표
- 판매 품목
- A5 출력 데이터
- 전표 품목 allocation의 신규 생성과 작성중 수정 복사는 `SalesSlipAllocationFactory`의 단일 생성 지점을 사용한다.
- 출고·출하 완료는 `SalesSlipOutboundService`가 현재 allocation을 고정된 배치로 만든 뒤 난 묶음을 잠그고, 경매 shipment/lot 생성과 재고 차감을 순서대로 조율한다.
- `SalesOrchidGroupSnapshot`은 allocation 생성 전의 `CREATION`과 잠긴 출하 배치의 재고 차감 전 `OUTBOUND`를 각각 같은 트랜잭션에서 보존한다. Controller나 응답 mapper에서 현재 난 묶음 값으로 재구성하지 않는다.

### auction

- 경매 lot
- 경매 시도
- 경매 결과 행
- 반환 확인
- 수량 보정

### settlement

- 수동 입금 확인
- 부분입금
- 거래처 잔액
- 입금 이벤트
- 거래처 정산 설정
- 경매 정산과 정산 행

### auth / demo

- 서버 세션 기반 로그인·로그아웃·현재 사용자 확인
- 역할 기반 API 접근 제어
- 데모 인증 주체, 변경 API 제한, 요청 횟수·본문 크기 제한
- `auth`가 데모 필터를 조립하며 `demo`는 `auth` 타입을 참조하지 않아 모듈 순환을 만들지 않는다.
- 로그인·로그아웃의 세션 lifecycle은 AuthService, 쿠키 생성·갱신·만료는 한 writer가 담당한다.
- 기본 계정은 application의 계정 조회 Bean이 없는 경우에만 자동 구성한다. 다른 `UserDetailsService`를 등록해도 로그인·세션 유스케이스를 변경하지 않는다.
- Demo filter는 요청 전달과 오류 응답만 조율하고, 차단 경로 판정과 UTC 기준 요청 횟수 집계는 분리한다. Clock은 Auth 조립 지점에서 주입한다.
- `demo → common` 의존은 공통 오류 직렬화에 사용한다. 요청 제한은 기존처럼 프로세스·클라이언트별로 유지한다.

### dashboard / analytics

- 대시보드 운영 요약
- 농장·판매·거래처·작업 분석 조회
- 분석 기간의 기본 종료일은 주입된 Clock의 농장 업무일이며 서버 기본 시간대를 사용하지 않는다.

## 4. 계층 구조

```text
controller
service/application
domain/entity
repository
dto
```

원칙:

- Controller는 요청/응답 처리만 담당한다.
- Service는 유스케이스와 트랜잭션을 담당한다.
- Entity는 DB 매핑과 최소 도메인 규칙을 가진다.
- Repository는 데이터 접근만 담당한다.
- 다른 모듈의 Repository를 직접 참조하지 않고 해당 모듈의 application API 또는 port를 사용한다.
- 외부로 노출되는 구조는 DTO로 제한한다.
- `ModularArchitectureTests`는 `analytics`, `auth`, `demo`를 포함한 실제 13개 모듈의 선언 의존성, 순환, 타 모듈 Repository 직접 접근을 검사한다. 계층형 업무 모듈은 표준 레이어 규칙도 검사한다.
- `ModuleBoundaryInventoryTest`는 컴파일된 의존성과 `@Query`를 추가 검사한다. 기존 Entity·Q 타입·HTTP DTO 결합과 직접 시간 조회의 예외는 `backend/src/test/resources/architecture/`에서 정확한 호출자별로 추적하며 이식 시 삭제한다. 신규 우회와 남아 있는 불필요한 예외 모두 실패 조건이다. SQL 별칭·동적 쿼리는 별도 코드 검토가 필요하다.
- 분석 Repository는 조회 행 타입만 반환하며 API 응답 DTO 조립은 application 계층에서 담당한다.

Persistence 조회 규칙:

- 단순 식별자·고정 조건 CRUD는 Spring Data JPA 메서드를 사용한다.
- 동적 검색·정렬·집계 조건은 QueryDSL을 사용한다.
- 고정된 관계 일괄 로딩과 projection은 JPQL을 사용할 수 있다.
- Native SQL은 PostgreSQL 원자 연산이나 DB 고유 분석 기능처럼 이유가 명확한 경우에만 저장소 내부에서 사용한다.
- 페이지 조회에 collection fetch join을 적용하지 않는다. 먼저 root를 페이지 조회한 뒤 연관 collection을 ID `IN` 조회로 조립한다.
- 목록 응답 조립 중 반복문 안에서 Repository를 호출하지 않고 필요한 ID를 모아 일괄 조회한다.

### 4.1 백엔드 구현 기준

#### 모듈 소유권과 호출 방향

- Entity, Repository, DB table은 각각 하나의 업무 모듈이 소유한다. 소유 모듈 밖에서는 해당 Repository나 internal 구현을 직접 참조하지 않는다.
- 다른 모듈의 기능이 필요하면 제공 모듈의 application API를 호출한다. 호출 측의 도메인 흐름에 필요한 조회 계약은 호출 측에 port를 두고 소유 모듈이 구현할 수 있다.
- 모듈 간 계약은 필요한 값만 전달한다. 외부 모듈 Entity를 장기간 보관하거나 응답 조립 편의를 위해 aggregate 전체를 넘기지 않는다.
- Partner의 새 조회 계약은 이름·유형·활성 여부를 복사한 application 값이다. Settlement의 정산 설정·잔액은 거래처 ID로 연결하고 기존 DB 외래키를 유지한다. 남은 Sales·Auction·입금 원장의 Entity 연관은 단계별로 전환하며, deprecated Entity 조회를 새 호출부에 사용하지 않는다.
- 외부 시스템은 application port 뒤의 adapter로 추가한다. 외부 시스템 DTO와 오류를 domain에 전파하지 않는다.

```text
호출 모듈 application → 제공 모듈 application API
호출 모듈 port ← 제공 모듈 adapter

금지: 호출 모듈 → 타 모듈 Repository/DB table
```

#### 유스케이스와 트랜잭션

- Controller는 HTTP 변환과 validation 진입만 담당하고 application service가 유스케이스와 트랜잭션을 소유한다.
- 쓰기 유스케이스는 하나의 public application method를 원자 경계로 삼는다. 중간 service 호출이 별도 트랜잭션을 암묵적으로 만들거나 self invocation에 의존하지 않게 한다.
- PostgreSQL 엔티티 ID는 테이블별 sequence와 `allocationSize = 50`을 사용한다. Hibernate JDBC batch와 insert 정렬을 활성화하며, 대량 저장은 같은 트랜잭션에서 동일 엔티티를 연속 저장해 JDBC batch가 유지되게 한다.
- Entity는 자기 상태의 불변식과 전이를 지키고 application service는 aggregate 조회, 순서 제어, 모듈 간 조율을 담당한다. 여러 Service에서 같은 상태 조건을 검사하면 Domain Policy 또는 상태 전이 메서드로 모은다.
- 배치 수용 프로필은 HTTP DTO를 domain 값으로 변환한 뒤 순수 정책에서 정규화·중복·수용량을 검사하고, 전체 검증이 끝나야 기존 규칙을 교체한다. 모드 강도는 enum 선언 순서와 분리하며 검증·응답·감사 정렬이 같은 강도 기준을 사용한다.
- 네트워크·파일·사용자 대기처럼 실패와 지연을 통제하기 어려운 작업은 DB 트랜잭션 안에서 수행하지 않는다.
- 여러 행을 잠글 때는 ID 오름차순처럼 잠금 순서를 고정한다. 재고, 잔액, 순번, 상태 변경에는 도메인 검사와 함께 version, 비관적 잠금, UNIQUE/CHECK 또는 원자 갱신 중 필요한 DB 보호를 둔다.
- Partner 잠금 API는 호출자의 트랜잭션을 필수로 요구하고 거래처 ID 오름차순으로 잠근다. 잠금 획득만 하는 호출이 독립 트랜잭션을 열고 즉시 반환하는 방식은 허용하지 않는다. 잔액 생성·갱신은 거래처 잠금 후 잔액 행 잠금 순서를 유지한다.
- 정산 설정의 최초 조회도 기본값 생성이 가능한 쓰기 유스케이스다. 거래처를 먼저 잠그고 설정을 다시 조회해 동시 최초 조회의 중복 생성을 막는다. 설정 변경도 같은 거래처 잠금 안에서 변경 전후 감사 값을 저장한다.

#### API 계약과 프론트엔드 경계

- 상태 전이, 가능한 action, workflow, 대상 종류, 수정 가능 여부, 업무일자는 백엔드 도메인 계약이 결정한다. 프론트가 여러 필드나 enum 이름을 조합해 같은 규칙을 다시 만들지 않도록 `availableActions`, capability, derived status, metadata 또는 runtime context로 제공한다.
- capability는 업무 의미만 표현한다. 화면 문구, 색상, 아이콘, 레이아웃 같은 표현 정보는 응답에 넣지 않고 프론트가 capability를 UI에 매핑한다.
- 성공과 실패는 공통 `ApiResponse`와 `ErrorResponse` envelope를 유지한다. 프론트가 예외 메시지 문자열을 해석하지 않도록 의미 있는 실패는 HTTP status와 안정적인 error code로 구분한다.
- API enum, capability, 요청/응답 DTO가 바뀌면 Controller·테스트를 먼저 수정하고 `python3 scripts/generate_openapi.py`와 `frontend`의 `npm run api:types`를 순서대로 실행한다. OpenAPI와 생성 TypeScript schema는 직접 수정하지 않는다.
- 클라이언트의 날짜 입력 기본값은 공개 runtime context의 `businessDate`와 `timeZone`을 사용한다. 백엔드 내부 업무일 계산과 같은 `TimeConfig` 기준을 유지해 브라우저 시간대와 배포 서버 시간대에 따라 날짜가 달라지지 않게 한다.

#### 조회와 응답 조립

| 문제 유형 | 기본 선택 |
|---|---|
| 식별자·고정 조건 CRUD | Spring Data JPA |
| 동적 조건·정렬·일반 집계 | QueryDSL |
| 고정 projection·연관 일괄 조회 | JPQL |
| CTE·Window Function·PostgreSQL 원자 연산 | 근거를 남긴 Native SQL |

- root 목록과 collection을 한 쿼리에 억지로 합치지 않는다. 페이지 또는 제한된 root ID를 먼저 조회하고 연관 데이터를 `IN` 쿼리로 읽어 application 계층에서 조립한다.
- DTO mapper가 lazy association을 순회하지 않게 조회 범위를 명시한다. mapper 호출 전 필요한 연관 데이터가 이미 로딩됐는지 확인한다.
- 목록·옵션·분석 조회에는 pagination, 날짜 범위 또는 명시적 최대 건수 중 하나를 둔다. 장기 누적 테이블의 무제한 `findAll`을 API 경로에 사용하지 않는다.
- DB 집계로 표현 가능한 값을 전체 Entity 조회 후 Java에서 다시 집계하지 않는다. 다만 데이터량이 작고 규칙 표현이 더 명확한 경우에는 측정 근거를 남기고 단순 구현을 유지할 수 있다.

#### 이력과 스냅샷

- 작업 효과, 출하, 전표, 정산처럼 과거 사실은 현재 Entity 상태와 분리해 보존한다. 과거 응답을 현재 난 묶음·거래처 값으로 다시 계산하지 않는다.
- 스냅샷은 이력이 확정되는 생성·완료 경계에서 같은 트랜잭션으로 저장한다. 스냅샷 생성이 필요한 후속 기능은 이 경계를 확장하고 여러 Controller 또는 mapper에서 임의로 복제하지 않는다.
- 이력 데이터는 물리 삭제보다 상태 변경, 취소, 보정 레코드를 우선한다. 보정은 원본과 변경 전후 값을 추적할 수 있어야 한다.

#### 시간, migration, 검증

- DB 시점은 UTC로 저장하고 농장 업무일 계산은 `Asia/Seoul` 기준 `TimeConfig`와 주입된 `Clock`을 사용한다.
- Flyway migration은 `nullable 추가 → backfill → 제약 적용`처럼 기존 운영 데이터가 통과할 수 있는 순서를 사용한다. 대용량 table 변경은 lock 범위와 운영 적용 시간을 별도로 검토한다.
- 수량·금액·정산·migration 변경은 정상 흐름뿐 아니라 rollback과 중복 요청을 검증한다. 동시성 보강은 병렬 실행 테스트, N+1 보강은 query count 상한 테스트를 둔다.
- PostgreSQL 문법, lock, constraint, 원자 갱신은 H2 결과만 신뢰하지 않고 Testcontainers 또는 실제 PostgreSQL 검증을 수행한다.

## 5. 프론트엔드 구조

기능 중심 구조를 유지한다.

```text
src/
 ├─ app/        Next.js 라우트·메타데이터 진입점
 ├─ features/   기능 단위 화면/상태/API
 ├─ entities/   도메인 타입과 도메인 공통 UI
 ├─ shared/     전역 공통 API·유틸·UI·PWA 런타임
 └─ widgets/    큰 공통 레이아웃
```

원칙:

- `app/*/page.tsx`는 얇게 유지한다.
- 실제 UI와 상태 로직은 `features/*`에 둔다.
- `app`과 다른 feature는 `features/<name>/index.ts`에 공개된 API만 참조한다. feature는
  `app` 또는 `widgets`에 의존하지 않고, `shared`와 `entities`는 상위 레이어에 의존하지 않는다.
  이 의존 방향은 ESLint `no-restricted-imports`로 검사한다.
- API 타입은 OpenAPI 또는 `entities` 타입과 맞춘다.
- OpenAPI에서 생성한 `shared/api/generated/openapi.d.ts`를 API enum과 capability 타입의
  기준으로 사용한다. 전체 client 코드는 생성하지 않고 기존 feature API 계층을 유지한다.
  `npm run api:types`로 갱신하며 `npm run check`가 생성물 drift를 검사한다.
- 루트 Server Component는 백엔드 런타임 컨텍스트를 조회하고 `businessDate`, `timeZone`을
  Client Context로 전달한다. 날짜 입력 기본값은 브라우저 UTC 날짜로 계산하지 않는다.
- 농장 현황의 선택·줌 요청은 직전 요청을 취소하고 최신 요청만 화면 상태에 반영한다.
- 작업 유형 응답은 등록 가능 모드, 워크플로, 대상 종류, 설정 수정 가능 여부를 제공한다. 프론트엔드는 작업 코드 목록을 다시 조합하지 않고 이 capability로 등록 화면과 실행 화면을 구성한다.
- 프론트 API 호출은 인증 요청을 제외하고 공통 `requestApi`를 사용해 쿠키·클라이언트 식별자·인증 만료·오류 메시지 처리를 공유한다.
- 모달은 공통 Radix Dialog 기반을 우선 사용해 포커스 트랩, Esc 닫기, 포커스 복귀를 보장한다.
- 화면별 복잡한 상태는 페이지 내부에 몰아넣지 않는다.
- 범용 UI는 `shared/ui`에 두고 shadcn/Radix primitive는
  `shared/ui/primitives`에 둔다. 특정 도메인이나 기능에 종속된 UI는
  `entities/*/ui` 또는 `features/*/ui`에 유지한다.
- PWA 브라우저 런타임과 전용 스타일은 `shared/pwa`에 둔다. Next.js가 위치를
  규정하는 manifest는 `app/manifest.ts`, 서비스 워커와 아이콘은 `public/`에 둔다.

### 5.1 프론트엔드 구현 기준

#### 상태 소유권

구현 전에 상태를 다음 중 하나로 분류한다.

| 종류 | 기준 | 저장 위치 |
|---|---|---|
| Server state | API가 원본이며 재조회·무효화 대상 | React Query cache |
| URL state | 공유, 새로고침, deep link, back/forward 복원이 필요 | path/search params |
| Local UI state | 입력 중 값, dialog, hover, 임시 선택처럼 짧게 유지 | 가장 가까운 컴포넌트 또는 응집된 hook |
| Derived state | 기존 props·state·query 결과로 계산 가능 | 렌더 중 계산, 비용이 클 때만 memoization |
| Shared client state | API나 URL로 표현할 수 없고 먼 형제 트리가 함께 변경 | 필요한 최소 범위의 Context |

- Query 결과를 local state나 Context에 복사하지 않는다. 수정 응답은 cache를 갱신하고 필요한 query만 무효화한다.
- props나 query 결과에 맞추기 위한 `useEffect` state 동기화를 만들기 전에 derived 계산, event handler, URL, React Query로 대체할 수 있는지 확인한다.
- Context는 런타임 컨텍스트처럼 넓은 트리에서 안정적으로 공유해야 하는 값에만 사용한다. feature 내부 편의를 위한 전역 Context는 만들지 않는다.
- 목록의 탭·필터·정렬·페이지와 상세 선택 중 사용자 탐색 맥락에 포함되는 값은 URL을 단일 기준으로 사용한다. 폼 입력 중간값과 dialog 열림 여부는 URL에 두지 않는다.

#### 도메인 계약과 표현 책임

- 상태 전이, 가능한 업무 action, 수량·금액 계산, 파생 업무 상태, 변경 금지 조건, 도메인 validation의 source of truth는 백엔드다.
- 프론트가 여러 응답 필드, 날짜, null 여부, raw JSON을 조합해 업무 의미를 추론해야 한다면 TypeScript helper를 만들기 전에 `availableActions`, capability, derived status, 정형 DTO 또는 metadata API를 검토한다.
- 빠른 피드백을 위한 입력 형식·범위 검사는 프론트에도 둘 수 있다. 서버가 최종 검증하며, 서버 규칙 변경 시 조용히 달라질 수 있는 복잡한 계산은 복제하지 않는다.
- 색상, 아이콘, 문구, 배치, 확대·축소, 선택·hover, 애니메이션은 프론트 표현 책임이다. 백엔드가 CSS나 화면 문구를 제공하지 않는다.
- API enum과 capability는 생성 OpenAPI 타입을 참조한다. API DTO와 form draft, table row, view model은 목적이 다를 때 별도 타입으로 명시적으로 변환한다.

#### Server Component와 데이터 패칭

- `app` route는 params 검증과 feature RoutePage 호출만 담당한다. 현재 URL에서 바로 필요한 초기 데이터는 feature의 Server Component에서 prefetch하고 Client Component에 hydration한다.
- 사용자 interaction이 많은 지도, 다중 선택, 폼, dialog는 Client Component에 둔다. `use client` 개수보다 전달되는 데이터 크기와 상태 소유권을 기준으로 경계를 정한다.
- 서버 prefetch와 클라이언트 `useQuery`는 같은 query option과 key factory를 사용한다. key에는 filter, page, size, scope 등 실제 요청 결과를 바꾸는 조건을 모두 포함한다.
- 서로 독립적인 초기 요청은 `Promise.all`로 실행한다. 선택·검색처럼 연속 호출되는 요청은 `AbortSignal`을 전달하거나 request token으로 최신 응답만 commit한다.
- mutation 후 전체 feature를 습관적으로 무효화하지 않는다. 상세 cache 직접 갱신, 관련 목록 무효화, 다른 도메인 무효화를 실제 변경 영향에 맞게 구분한다.
- 인증 API처럼 응답 처리 의미가 다른 경우를 제외하고 feature API는 공통 `requestApi`를 사용한다.
- 실패 응답은 `ApiError`로 변환해 HTTP status, 안정적인 error code, details를 보존한다. UI용 message를 만드는 과정에서 구조화 정보를 버리지 않는다.

#### 컴포넌트와 hook

- 컴포넌트는 파일 길이가 아니라 변경 이유, 상태 공유 범위, UI 책임을 기준으로 분리한다. JSX 몇 줄을 감싸는 것만으로 새 컴포넌트를 만들지 않는다.
- page나 section이 조회 상태, form state, dialog state, 도메인 변환을 모두 소유하면 응집된 feature hook 또는 하위 section으로 나눈다. hook 하나가 모든 화면 상태와 callback을 반환하는 God Object가 되지 않게 한다.
- 동일한 변경 이유가 두 feature 이상에서 반복될 때만 `shared`로 이동한다. 농장 도메인 공통 타입·UI는 `entities/farm`, 한 feature에만 필요한 추상화는 해당 feature에 유지한다.
- 다른 feature와 `app`·`widgets`는 `features/<name>/index.ts`의 공개 API만 사용한다. public API는 실제 외부 사용 항목만 export한다.
- `useMemo`, `useCallback`, `memo`는 큰 목록·지도 또는 identity 안정성이 실제 dependency와 렌더 범위를 줄이는 경우에만 사용한다. 성능 판단이 불명확하면 Profiler나 기존 map E2E로 측정한다.

#### Form, 오류, 접근성

- form draft와 submit/pending/error는 form 또는 응집된 hook 가까이에 둔다. 수정 초기값을 effect로 계속 동기화하지 않고 dialog key, 명시적 open event, form 초기화 함수 중 하나를 사용한다.
- submit 중복을 막고 서버 validation 메시지를 공통 API 오류 처리로 노출한다. loading, empty, error를 동시에 참으로 만들 수 있는 별도 boolean 조합보다 Query 상태나 명시적 union을 사용한다.
- dialog는 `shared/ui/primitives/dialog.tsx`를 우선 사용한다. 입력에는 label, 아이콘 버튼에는 접근 가능한 이름, 비동기 버튼에는 disabled/pending 상태를 제공한다.
- 태블릿 주요 동작은 hover 없이 사용할 수 있어야 한다. 반복 사용 버튼과 선택 대상은 충분한 touch target을 확보한다.

#### 테스트와 변경 완료 기준

- URL parser·writer, 날짜·payload 변환, selection coordinator처럼 React와 분리 가능한 규칙은 pure function unit test로 검증한다.
- 서버 capability와 상태 전이는 백엔드 단위·통합 테스트를 기준으로 검증한다. 프론트 테스트에서 같은 전이 규칙을 다시 구현하지 않는다.
- mutation과 cache 갱신, back/forward, dialog focus, 지도 연속 선택처럼 경계를 넘는 흐름은 회귀 위험에 따라 integration 또는 E2E 테스트를 추가한다.
- API contract 변경은 Controller·DTO·테스트 수정 후 OpenAPI와 생성 타입을 갱신한다.
- 프론트 변경 완료 전 `cd frontend && npm run check`를 실행한다. 실행하지 못한 검증과 기존 경고는 결과에 남긴다.

## 6. 데이터 보존 원칙

운영 데이터는 삭제보다 이력 보존을 우선한다.

보존 우선 데이터:

- 작업 이력
- 자리 이동 이력
- 판매 전표
- 경매 lot 상태 이력
- 입금 이벤트
- 원본 가져오기 데이터

삭제가 필요한 경우에도 물리 삭제보다 상태 변경 또는 비활성화를 우선 검토한다.

## 7. 백엔드 리팩터링 검증

`work` 리팩터링 검증은 브라우저 E2E와 분리하고 실제 PostgreSQL을 사용하는 두 Gradle 작업으로 실행한다.

```bash
cd backend
./gradlew workE2eTest
./gradlew workBenchmark
./gradlew workBenchmark -PworkBenchmarkEnforce=true
```

- `workE2eTest`: RANDOM_PORT의 실제 HTTP 요청으로 대상 미리보기, 일반·즉시 완료 작업,
  작업과 대상 상태 전이, 분갈이 수량·계보, 계획형 구조 변경, 요청 키 멱등성,
  작업 상세·분갈이 결과·난 묶음 통합 이력을 검증한다. 같은 PostgreSQL 환경에서 판매일별 전표 번호의 동시 원자 증가도 검증한다.
- `workBenchmark`: 작업 100건과 대상 2,000건을 고정 생성하고 작업 목록·상세·난 묶음 통합 이력
  조회의 쿼리 수를 검증한다. API별 3회 워밍업 후 20회 측정한 median/p95는
  `backend/build/work-benchmark/results.json`에 기록한다.
- 기능 결과와 DB 불변식은 자동 실패 조건으로 사용한다. 응답 시간은 실행 환경 영향을 받으므로
  전후 결과를 수동 비교하고 CI의 강한 실패 조건으로 사용하지 않는다. 기본 벤치마크는
  리팩터링 전 기준값도 남길 수 있도록 쿼리 상한을 기록만 하며, `-PworkBenchmarkEnforce=true`를
  지정한 경우에만 상한 초과로 실패한다.
- 전후 비교가 필요하면 각 대상 커밋에서 `clean workE2eTest workBenchmark`를 실행하고 생성된
  `results.json`을 각각 `before.json`, `after.json`으로 별도 보관한다.
- `CoreQueryRegressionTest`는 기본 테스트에서 농장 viewport 3회, 경매 lot 페이지 4회, 판매 전표 상세 3회의 SQL 상한을 검증한다. 판매 전표 상세는 allocation과 서버 판정 액션을 각각 묶음 조회한다.
- 사용자 그룹 목록은 1·10·50개에서 SQL 3회, 난 묶음별 소속 그룹 조회는 5회 이내인지 검증한다. 보관·탈퇴 제외와 소속 순서도 함께 확인한다.
- 기본 검증과 별도로 CI의 `backend-postgres` job이 Docker 사용 가능 여부와 `workE2eTest`를 실행한다. PostgreSQL 테스트를 실행하지 못한 경우 완료로 취급하지 않는다.
- 백엔드의 편집 기준은 `backend/.editorconfig`를 따른다. 이 설정 자체는 자동 formatter나 CI 포맷 검사가 아니며, 기존 전체 파일을 일괄 포맷하지 않는다.

## 8. 프론트엔드 맵 성능 E2E

난 묶음 관리 맵의 리팩터링 전후 비교는 `frontend/e2e/map-performance`의
Playwright 시나리오를 사용한다. production build, 실제 Spring Boot 백엔드,
PostgreSQL 전용 DB에서 workers 1과 고정 viewport로 실행한다.

제품 동작에는 측정 분기를 추가하지 않는다. Playwright 선택자는 맵·다이·구역·난 묶음·
선택 이력·이력 항목에 부여한 안정적인 `data-testid` 계약을 사용한다. 네트워크 응답
완료 후 두 프레임이 지난 시점을 렌더 완료로 기록한다. 요청 수, 응답 크기, 렌더 시간,
마운트 수와 DOM 증감은 실패 조건이 아니라 전후 비교 지표로만 저장한다.

일반 기준 측정은 전용 DB를 매번 재생성한다. 디버그 실행은 `--reuse-db`로 DB 준비
단계를 생략하고 Flyway를 비활성화해 기존 E2E DB 상태를 보존한다.

측정 데이터와 실행 방법은 `frontend/e2e/map-performance/README.md`를 기준으로 한다.
