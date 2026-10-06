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
- 경매 정산은 페이지·전체 합계·선택 상세를 각각 Query cache로 관리한다. 페이지와 상세 선택은 URL에 유지하고, 입금·재계산 후 응답으로 상세 cache를 갱신하며 관련 페이지·합계만 무효화한다. 전체 배열을 클라이언트에서 자르거나 합산하지 않는다.
- 판매 전표·정산 상세의 입금 이력도 대상·유형·페이지별 Query cache를 사용한다. 이력의 열림·페이지는 URL에 두고 대상 선택이 바뀌면 초기화한다. 입금 성공 후 서버가 반환한 잔액을 다음 입력 기본값으로 사용하고 해당 대상의 이력을 갱신한다. 이력 조회 실패는 입금 결과와 구분해 재조회할 수 있게 한다.
- 작업 관리는 URL을 조회 범위·보기 방식·필터·페이지의 단일 기준으로 사용한다. 서버 진입 컴포넌트인 `WorkRecordRoutePage`는 현재 목록 또는 캘린더 query만 prefetch해 hydration하고, 작업 유형과 농장 전체 배치 정보는 등록 또는 실행 다이얼로그를 열 때 조회한다. 클라이언트 `WorkRecordPage`는 보기 전환과 등록 다이얼로그의 열림 상태만 관리하고, 등록 다이얼로그가 자체 참조 데이터의 로딩과 오류를 처리한다. 목록과 캘린더는 공통 작업 동작 훅과 상세 패널을 사용한다. 캘린더는 전용 기간 API를 한 번 호출하고, 작업 등록·실행 후 관련 작업 및 농장 query를 무효화한다.
- 작업 관리는 조회·상태 변경을 `model/operation`, 등록 상태와 대상 계산을 `model/registration`, 작업 유형별 표현 구성을 `model/work-types`로 구분한다. 화면은 `ui/list`, `ui/calendar`, `ui/detail`, `ui/registration`, `ui/work-types`에서 기능별로 구성한다. 대상 출처, 등록 가능 모드, 실행 workflow는 백엔드 capability를 사용하고 `workTypeDefinition.ts`에는 안내 문구 같은 표현 규칙만 둔다.
- 작업·입고 변경 후에는 양쪽 목록·상세와 작업 캘린더·그래프 cache를 함께 무효화한다. 두 feature의 공통 query prefix와 갱신 함수는 `entities/farm/model`에 두고 feature 사이의 내부 import나 순환 의존을 만들지 않는다. 활성 query는 즉시 재조회하고 비활성 query는 다음 진입 시 재조회한다.
- 작업 상태 action은 `END_REMAINING`과 `CANCEL`을 구분한다. 전자는 적용 효과를 유지하고 미완료 대상만 닫으며, 후자는 기록 전용 효과를 취소하거나 구조 변경 Mutation을 보상한다. 미완료 대상 종료, 효과 취소, Mutation 보상, 전체 작업 상태 변경은 하나의 최상위 application transaction에서 처리한다.
- 작업 중심 그래프는 Work의 read model을 사용한다. Work가 생성 출처와 실행 의미가 있는 작업 선후 관계를 조립하고 Farm은 Work가 정의한 application port로 Mutation·revision·계보 조각을 제공한다. 작업 상세는 직접 Mutation 효과의 SOURCE/RESULT snapshot을 `투입·잔류·결과` 흐름으로 투영하고 Mutation 노드를 숨기며, 개발용 Mutation 테스트만 원본 기술 그래프와 계보 확장을 제공한다. 동일 Receipt에서 생성됐다는 사실은 그래프 관계로 취급하지 않고 목록 관계 조회에서만 사용한다. Receipt JSON은 멱등 결과의 원본 계약으로 유지하고, 역방향 조회는 그 결과만 정규화한 membership 테이블을 사용한다. 그래프들은 `shared/lib/graph`의 dagre layout을 공유한다.
- 자리 이동 실행은 명시적인 1:1 전량 위치 변경이면 기존 난 묶음을 일괄 `MOVE`하여 ID를 유지한다. 같은 조건의 과거 `TRANSFORM` 이력은 원본 ID의 상태 체인으로 합치고 후속 참조를 이관하며, 제거 ID 대응은 감사 테이블에 보존한다. 그 외에는 같은 품종의 원본 선별 수량을 실행 회차에서 합쳐 상태가 좋은 수량을 먼저 이동하고, 원래 자리에 남은 수량을 폐기한다. 연관 폐기 작업은 `MOVEMENT_DISCARD` 관계로 이동 작업에 연결하며 총 폐기 수량은 원본별 선별 수량 비율로 배분한다. 정수 나머지는 최대 나머지 방식을 사용하고 나머지가 같으면 난 묶음 ID 오름차순으로 결정한다.

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
- 페이지 목록 응답은 `PageResponse<T>`로 통일한다. 입력 처리는 `PageRequests`를 사용하되 기존 API의 엄격한 검증(잘못된 값은 400)과 범위 보정(페이지 0 이상·크기 1~100)을 유지한다. 둘을 임의로 같은 정책으로 바꾸지 않는다.
- 예외 처리
- 공통 유틸
- 공통 검증
- 요청의 작업자 이름 정규화와 인증된 감사 actor는 별도 계약이다. 데모의 요청 작업자 대체가 감사 actor 판정을 대신하지 않는다.

### audit

- 업무 데이터 변경 전후 스냅샷과 변경 필드를 PostgreSQL에 동기 저장한다.
- 도메인 엔티티 대신 독립 이벤트 DTO를 받아 다른 업무 모듈에 의존하지 않는다.
- 요청 ID, 세션 ID, 인증 계정명, 브라우저 인스턴스 ID를 변경 이벤트와 연결한다.
- 감사 저장 실패는 같은 트랜잭션의 원본 변경도 롤백한다. DB recorder는 호출자의 트랜잭션을 필수로 요구한다.
- 감사 실행자·세션·요청 맥락과 감사 대상·위치를 별도 값으로 전달한다. HTTP 맥락은 기존 request adapter가 읽고 CLI는 명시적인 실행자 값을 제공한다. 이벤트 생성과 no-op 처리는 공통 writer가, 민감 필드 제외·업무별 변경 필드 순서는 소유 모듈의 snapshot이 담당한다.

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
- 입고 등록은 application 명령을 직접 받고, 입고 작업 스냅샷과 메모 조립은 전용 factory가 Work application 명령으로 전달한다. 같은 필드를 복사하는 HTTP DTO를 추가하지 않는다.
- 입고 시 품종 선택·재사용·신규 생성은 품종 application이 소유한다. 기존 속·품종명 정규화와 동일 품종 재사용을 유지하고, 입고·난 묶음·Work 기록은 최상위 입고 트랜잭션에서 함께 반영한다.
- 입고의 수정·취소·포트 가능 조건과 수량 선택은 입고 Entity가, 자동·명시 배치 범위 검증은 기존 배치 정책이 소유한다. 입고 응답의 `availableActions`가 화면 동작의 기준이다. 즉시 배치 입고 취소와 완료된 포트 작업 취소는 Work에 연결된 생성 Mutation을 같은 트랜잭션에서 보상하며, 취소된 입고도 삭제하지 않고 목록 이력으로 함께 조회한다.
- 품종 목록의 난 묶음·최근 입고일·최근 작업일은 페이지 단위로 일괄 조회한다.
- 난 묶음 계보는 `work` 엔티티를 직접 참조하지 않고 `workOperationId` 값으로 연결한다.
- 난 묶음 취소·보정의 사용 여부 port는 Farm이 소유한다. Sales는 이 port를 구현하고, Farm adapter는 Work의 개수 조회를 Farm blocker로 변환한다. Work가 Farm에 의존하지 않는다. 차단 사유는 기존 입고→판매→작업 순서를 명시적으로 유지한다.
- 사용자 그룹 목록은 그룹 목록→소속 일괄 조회→난 묶음 상세 일괄 조회 순서로 조립한다. 목록의 각 그룹마다 조회를 반복하지 않는다.
- 난 묶음 물리 상태 변경과 revision ledger는 `farm.orchid.mutation`이 소유한다. Work·Sales·Inbound는 typed command와 식별자 계약으로 이 경계를 호출하고 업무 lifecycle은 각 모듈에 유지한다.
- cutover 이전 이력도 같은 Mutation header와 `orchid_group_mutation_entries`의 `BASELINE`·`CREATE`·`CHANGE`·`DELETE`로 저장한다. 모든 Entry는 연속 revision과 full snapshot 규칙을 사용하며 현재 행이 없는 삭제 그룹은 terminal `DELETE`로 보존한다.
- 전환 전용 importer는 승인된 complete state-chain manifest만 적재한다. Work 효과는 Work application의 제한된 source 조회·연결 API를 사용하고 Lineage 연결은 소유 모듈인 `farm`에서 수행한다. 별도 migration 모듈이나 과거 전용 Entry 모델은 두지 않는다.
- ledger rehearsal 대사는 `farm`의 현재 상태·revision chain과 모듈별 read-only application 계약을 조합한다. 각 모듈은 Work 진행 상태와 효과 연결, Sales 활성 allocation과 예약 수량처럼 자신이 소유한 정합성만 판정하며 데이터를 자동 보정하지 않는다.
- ledger coverage가 `ACTIVE`이면 PostgreSQL write fence가 transaction-local Mutation context 없는 `orchid_groups` INSERT·UPDATE와 모든 DELETE를 차단한다. 커밋 시에는 변경 revision에 대응하는 MutationEntry도 확인한다.
- Farm·Inbound·Work·Sales의 상태 변경은 Engine만 호출한다. Work 효과와 Sales 재고 이동은 같은 트랜잭션에 Mutation ID·correlation ID를 연결한다. Legacy 모드·직접 변경 분기·예약 라우팅 래퍼는 제거했다.
- startup guard는 원장 없는 난 묶음과 PREPARING coverage의 업무 서버 기동을 거부하고 ACTIVE의 최소 writer version을 검사한다. 빈 DB는 Engine에서 신규 생성할 수 있다. 기본 writer version은 `2.0.0`이다.
- 복구용 importer와 cutover CLI는 V20 백업 복구를 위해 유지한다. PREPARING 동안 DB fence가 활성화되지 않으므로 적재 작업은 외부 쓰기를 중지한 DB에서만 수행한다. 검증을 통과한 전체 원장만 ACTIVE로 전환한다.
- Entity 직접 상태 변경자는 Engine과 복구 importer, 생성자·Repository 쓰기는 Engine으로 한정하고 architecture test로 검사한다. 신규 업무 변경은 typed Engine command에 편입한다.
- 과거 Work·Sales·Lineage 데이터와 기존 Flyway 이력은 보존한다. 실행 코드 제거와 복구 도구·데이터 보존 정책은 `features/orchid-group-mutation-transition.md`를 따른다.

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
 └─ transformation/  분갈이·분주·합식·계보
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
- 신규 구조 변경 단건·배치 기록은 Work Receipt 확인 뒤 최초 실행에서만 Farm port로 전체 원본 묶음 ID 순 → 현재·결과 구역 합집합 ID 순으로 선잠금한다. Farm adapter는 호출 트랜잭션을 필수로 요구하며 각 집합을 전체 정렬 후 500개씩 취득한다. 계획의 대상·수량은 잠금 뒤 해석하고 aggregate 생성 전에 실행 원본과의 일치를 검사한다. 기록·응답·Receipt 지문의 배열 순서는 요청 순서를 유지한다.
- 작업 목록·캘린더는 대상 배열을 제외한 요약 응답을 사용하고 진행률과 가능한 전체 작업 action은 대상·실행 상태의 DB 집계로 조립한다. 대상별 상세는 사용자가 작업을 선택할 때 단건 조회한다.
- 분갈이·분주·합식은 공통 구조 변경 실행기와 작업별 Strategy를 사용한다. 기존 분갈이·분주 단일 대상 요청도 변환기를 거쳐 같은 실행 코어로 위임하고, 기존 합식 완료 API만 호환 경로로 남아 있다. 난 묶음 저장소가 필요한 Strategy 구현은 `farm` 모듈에 둔다.
- 효과 실행과 효과 감사 저장을 분리하고 모든 신규 효과는 공통 저장 컴포넌트를 사용한다. 구조 변경 실행의 `WorkAppliedEffect`는 원본 `SOURCE`와 결과 `RESULT`를 연결하는 계보 노드다.
- state-chain importer용 Work source 조회와 Mutation link API는 상태 변경 효과만 제한해 제공한다. `farm` importer가 Work Repository나 테이블을 직접 읽지 않게 하는 전환용 모듈 경계다.
- 신규 즉시 완료 작업은 대상 효과 INSERT와 실행 상태 UPDATE를 JDBC batch로 flush한다. 키가 있는 생성 요청은 Work 접수 기록의 원자 INSERT·행 잠금으로 중복 생성을 막고 요청 지문과 결과 ID를 같은 트랜잭션에 확정한다. 기존 효과 재실행은 원문 지문 비교와 `(workOperationId, effectKey)` DB UNIQUE를 함께 사용한다.
- DB의 `timestamp without time zone` 시점 값은 UTC로 저장한다. 업무일자는 `Asia/Seoul` 기준으로
  계산하고 API 응답의 시점 값은 UTC에서 `Asia/Seoul`로 변환한다.

`work`의 `application`, `domain`, `dto` 계층은 동일한 기능별 하위 패키지로 구성한다.

```text
application|domain|dto/
 ├─ operation/   작업 계획·실행·조회·상태 전이·작업 유형
 ├─ target/      대상 선택·스냅샷·실행 상태·외부 대상 gateway
 ├─ effect/      효과 실행·감사·구조 변경과 입고 포트 계약
 └─ correction/  완료 작업의 감사 이벤트와 보정 대상 조회
```

보정 유스케이스는 Work가 트랜잭션과 감사 이벤트를 소유하고, Work의 application port를 Farm adapter가 구현한다.
보정 접수는 작업 생성 Receipt와 분리하여 요청 지문과 감사 이벤트 ID를 확정한다. 같은 키는 DB 접수 행으로
직렬화하며, 다른 키의 동일 원본 보정은 원본 잠금으로 직렬화한다. 목록의 보정 건수는 페이지 ID 기준 일괄 집계한다.
보정 Mutation의 출처는 감사 이벤트이며, 기술 그래프와 원장 대사에서 원본 작업과 함께 추적한다.

### partner

- 거래처

### sales

- 판매 전표
- 판매 품목
- 일반·경매 생성은 하나의 application 유스케이스에서 품목 생성→예약→완료 시 출고를 공유한다. 거래처 검증·기본 결제 정보·일반 판매의 잔액 처리 차이는 명시적으로 유지한다. 현재 두 유형을 위해 별도 생성기 registry를 추가하지 않는다.
- 판매 생성·수정과 경매 결과 입력은 application 명령을 직접 바인딩한다. 새 입력 채널은 같은 유스케이스를 호출하며 기존 HTTP schema 이름과 validation을 유지한다.
- 수정 capability와 쓰기 검증은 Sales 도메인의 같은 조건을 사용한다. 실제 입금액이나 입금 이벤트가 있으면 수정할 수 없다.
- A5 출력 데이터
- 전표 품목 allocation의 신규 생성과 작성중 수정 복사는 `SalesSlipAllocationFactory`의 단일 생성 지점을 사용한다.
- 출고·출하 완료는 `SalesSlipOutboundService`가 현재 allocation을 고정된 배치로 만든 뒤 난 묶음을 잠그고, 경매 shipment/lot 생성과 재고 차감을 순서대로 조율한다.
- 출하·lot 생성과 삭제는 Auction application API가 소유하며 호출 트랜잭션에 참여한다. Sales는 출하와 lot ID만 보관한다. 원본 품목 ID로 생성된 lot를 연결하고, 판매 품목 배열과 출하 lot 배열의 순서가 같다고 가정하지 않는다.
- `SalesOrchidGroupSnapshot`은 allocation 생성 전의 `CREATION`과 잠긴 출하 배치의 재고 차감 전 `OUTBOUND`를 각각 같은 트랜잭션에서 보존한다. Controller나 응답 mapper에서 현재 난 묶음 값으로 재구성하지 않는다.

### print

- 출력과 판매 화면은 Sales application의 문서·요약 값 계약을 공유한다. Print가 Sales HTTP DTO에 의존하거나 같은 문서 필드를 복제하지 않는다.
- 출력 응답의 금액·스냅샷·현재 거래처 정보·호환 action은 Sales가 제공한다. 기존 HTTP JSON을 유지하기 위해 action 필드도 보존하며 Print에서 업무 판정을 다시 하지 않는다. 문서 종류가 늘기 전 범용 renderer/provider는 만들지 않는다.

### auction

- 경매 lot
- 경매 시도
- 경매 결과 행
- 결과 입력의 대기 수량·차수 중복·낙찰/부분 낙찰/유찰/반환 추정 계산과 반환 가능 조건은 lot 도메인이 소유한다. application은 행 잠금과 입력 전달·응답 조회를 조율한다. 조회 필터와 요약의 검토 대상 분류도 같은 도메인 정의를 사용한다.
- 반환 확인
- 수량 보정

### settlement

- 수동 입금 확인
- 부분입금
- 거래처 잔액
- 입금 이벤트
- 거래처 정산 설정
- 경매 정산과 정산 행
- 경매 정산 재구성·초기화·입금은 거래처→정산→잔액 순서를 공유한다. 초기화는 바깥 transaction을 거절하는 coordinator가 후보를 읽고 정산별 application writer를 호출한다. 각 writer는 해당 거래처를 먼저 잠근 뒤 연결 여부를 재확인하고 경매장·경매일 한 정산을 commit해 중복 기동 시 같은 결과를 재추가하지 않는다. 실패한 정산만 rollback하고 이전 정산의 commit은 남는다. 수동 재계산의 기존 스냅샷·입금액·상태 계산 정책은 유지한다.

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
- Dashboard는 기존 Farm 요약 값만 조립한다. 집계 SQL 5회로 검증하며 별도 provider 계층을 추가하지 않는다. 분갈이 예정·최근 작업은 아직 연결되지 않은 호환 값으로 남는다.
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
- `ModuleBoundaryInventoryTest`는 컴파일된 의존성과 `@Query`를 추가 검사한다. Entity·Q 타입·HTTP DTO 결합과 직접 시간 조회의 예외는 `backend/src/test/resources/architecture/`에서 정확한 호출자별로 추적한다. 별도 application 계약 목록은 모듈 밖에서 사용하는 타입·정확한 메서드/생성자를 검토 대상으로 고정하며 타입 승인만으로 새 public helper 사용을 허용하지 않는다. 신규 우회와 불필요하게 남은 항목 모두 실패 조건이다. 공개 값 계약의 generic/record·사용한 method/constructor·구현 port 안의 Entity·Repository projection·저장 callback도 검사한다. query root는 schema/FQCN·quoted 이름을 정규화하지만 별칭·comma join·동적 쿼리는 별도 코드 검토가 필요하다.
- OrchidGroup writer 검사는 이름 목록 대신 bytecode 필드 쓰기와 내부 위임을 찾아 engine·복구 writer 경계를 검사한다. 외부 필드 쓰기·메서드/생성자 참조도 포함한다. reflection·raw SQL·연관 객체 변경·transaction 안전성은 구조 검사만으로 증명하지 않으며 PostgreSQL write fence·rollback·경쟁 회귀와 함께 판단한다.
- 분석 Repository는 조회 행 타입만 반환하며 API 응답 DTO 조립은 application 계층에서 담당한다.

Persistence 조회 규칙:

- 단순 식별자·고정 조건 CRUD는 Spring Data JPA 메서드를 사용한다.
- 동적 검색·정렬·집계 조건은 QueryDSL을 사용한다.
- 고정된 관계 일괄 로딩과 projection은 JPQL을 사용할 수 있다.
- Native SQL은 PostgreSQL 원자 연산이나 DB 고유 분석 기능처럼 이유가 명확한 경우에만 저장소 내부에서 사용한다.
- 페이지 조회에 collection fetch join을 적용하지 않는다. 먼저 root를 페이지 조회한 뒤 연관 collection을 ID `IN` 조회로 조립한다.
- 목록 응답 조립 중 반복문 안에서 Repository를 호출하지 않고 필요한 ID를 모아 일괄 조회한다.
- 그래프의 출력 노드 상한과 내부 참조 상한은 별도로 적용한다. Work는 자식 작업과 대상·효과·정정 참조를 DB에서 제한하고, 그래프 출처에는 receipt/관계 집계 대신 필요한 입고 scalar 참조만 읽는다. Farm은 표시할 Mutation/Entry를 정한 뒤 위치·Work 참조를 조립하며, 양 끝이 표시되는 Mutation 관계만 제한 조회한다. 결과 계보 라벨은 표시되는 Entry의 Mutation/결과 쌍마다 최초 계보 타입 하나만 scalar로 읽는다. 내부 참조·관계 생략도 `truncated`에 전파하며 부분 그래프를 전체 이력으로 취급하지 않는다. DB 반환 행/Entity 상한은 scan·sort·JSON byte 크기나 단일 DB snapshot 보장과 구분한다.
- Work 목록 요약의 입고 출처는 대상 행의 최초 ID 순서로 중복 제거한 scalar 참조를 읽고, parent/child 연관 수는 DB에서 집계한다. 제외된 대상의 출처와 receipt의 생성 batch 의미를 유지하며 대상 snapshot·페이지 밖 child Entity를 로딩하지 않는다. 상세와 실제 실행의 대상 로딩은 별도 계약이다. SQL 상한과 함께 target/operation Entity 적재 상한을 검사한다.
- 품종의 양수 묶음 개수/수량/판매 가능량은 Farm DB에서 집계한다. 현재 묶음 ID/품종 참조는 scalar cursor로 읽고 Work 날짜 조회에는 500개씩 전달해 품종별 최신일만 보관한다. 자동 그룹 요약은 연령 계산에 필요한 scalar 행을 순차 처리하며 member DTO 목록을 만들지 않는다. member 조회도 연령 필터를 통과한 행만 DTO로 조립한다. cursor는 호출자 트랜잭션 안에서 fetch size 500으로 사용하고 닫는다. 현재 연령은 기존 입고일 우선·UTC 생성 시각의 농장 날짜·요청 업무일 계산을 공통 사용하며, 저장 age_year만으로 대체하지 않는다.
- 난 묶음 계보는 Work 실행 계보에 포함된 직접 연결을 먼저 제외하고, 남은 직접 연결과 실행 계보의 그룹 ID를 합쳐 위치·품종·입고까지 일괄 로딩한다. 직접 연결의 생성 시각/ID 순서와 현재 참조·농장 업무일 기준 연령 계산을 유지하며 과거 효과 snapshot으로 대체하지 않는다.
- Mutation의 복수 입고 생성·구조 변경·일괄 이동은 구역 잠금 뒤 활성 배치 scalar 값을 구역 ID 500개씩 한 번 읽고 요청 안의 새 결과를 구간/빈 자리 index에 반영한다. 자동 배치는 기존과 같은 첫 1칸을 선택하며 결과 순서와 구역 전체의 최대 표시 순서를 유지한다. 구조 변경은 원본 변경 반영 뒤 배치를 읽어 해제 공간을 재사용한다. 이동은 기존 배치 검사를 모두 마친 뒤 결과끼리 비교하며, 복원은 원래 구간·표시 순서도 검증한다. 이 상태는 한 요청에만 사용하며 잠금·재전송/원장/감사 트랜잭션을 대체하지 않는다.
- 배치 profile의 전체 규칙 교체는 정책으로 새 규칙 전체를 검증한 뒤 기존 규칙 삭제를 먼저 flush해 동일 UNIQUE 키의 새 INSERT보다 앞서 처리한다. flush는 commit이 아니며 새 규칙·감사는 최상위 트랜잭션에서 함께 확정/rollback한다. 값이 동일할 때 감사 생략과 규칙 행 교체의 기존 의미는 유지한다.
- 배치 profile 조회·수정·감사는 위치와 용량 규칙만 로딩한다. 재고 묶음 collection을 전용 graph에 포함하지 않아 구역의 그룹 수에 따른 불필요한 Entity 적재와 규칙/그룹 collection 간 join 증폭을 피한다. 실제 재고가 필요한 구역 상세 조회의 graph는 따로 유지한다.

### 4.1 백엔드 구현 기준

#### 모듈 소유권과 호출 방향

- Entity, Repository, DB table은 각각 하나의 업무 모듈이 소유한다. 소유 모듈 밖에서는 해당 Repository나 internal 구현을 직접 참조하지 않는다.
- 다른 모듈의 기능이 필요하면 제공 모듈의 application API를 호출한다. 호출 측의 도메인 흐름에 필요한 조회 계약은 호출 측에 port를 두고 소유 모듈이 구현할 수 있다.
- 모듈 간 계약은 필요한 값만 전달한다. 외부 모듈 Entity를 장기간 보관하거나 응답 조립 편의를 위해 aggregate 전체를 넘기지 않는다.
- Partner 조회는 현재 기준 정보의 application 값을 반환한다. Sales·Auction·Settlement는 거래처 ID로 연결하며 기존 DB 외래키를 유지한다. Entity를 반환하는 호환 조회는 제거했다. 판매 응답의 연락처를 포함한 현재 거래처 정보와 경매장 이름은 ID를 모아 일괄 조회한다.
- Sales는 난 묶음 Entity 대신 ID와 Farm application의 현재 상태 값을 사용한다. 배분·재고 이동의 기존 DB 외래키는 유지하며, 상세 응답은 Sales의 배분·보존 스냅샷과 Farm의 상태 값을 따로 일괄 조회해 조립한다. 현재 상태 조회는 500개 ID씩 처리한다.
- Farm의 외부 난 묶음 조회 계약은 상태 값·판매 선택·호출 트랜잭션 내 잠금으로 제한한다. Entity가 필요한 Farm 내부 유스케이스는 소유 Repository를 사용한다. 공개 Reader의 반환값·입력과 중첩 collection/record에 Entity를 추가하면 architecture 검증이 실패한다.
- 기존 판매 전표의 수정·상태 전환·입금은 root를 먼저 잠그고 소유 품목·배분·역사 스냅샷을 일괄 로딩한다. 배분과 스냅샷 collection은 별도 쿼리로 초기화해 다중 collection fetch join과 대상별 lazy 조회를 피한다. 수정의 flush 후에도 같은 managed aggregate로 재예약·감사·최종 응답을 처리한다. Farm의 재잠금과 예약 전·해제 후·출고 직전 snapshot, 최종 현재 상태 조회는 각각의 시점 계약으로 유지한다.
- 경매 변경은 lot root만 잠근 뒤 기존 시도의 결과 행을 일괄 로딩해 cascade flush·응답 mapper의 시도별 조회를 피한다. 완료 접수 replay는 이 로딩보다 먼저 처리한다. 쓰기 응답은 lot의 collection 순서와 새 시도의 append 위치를 유지하고 결과 행은 생성 ID 순으로 읽으며, 결과·반환의 최초 응답은 자식 ID가 확정된 뒤 같은 트랜잭션의 접수 snapshot에 보존한다. 조회용 정렬을 쓰기 응답에 적용하거나 shipment fetch로 부모 잠금을 추가하지 않는다.
- 전표의 입금 상태 감사 스냅샷은 Sales가, 경매 정산 상태와 입금 이벤트의 감사 스냅샷은 Settlement가 소유하며 공통 Audit 값 계약으로 기록한다. 모듈별 감사 helper는 내부 전용이고 Sales의 입금은 Settlement의 원장·잔액 application API만 사용한다. 입금 이벤트 생성과 대상의 상태 변경은 서로 다른 감사 사실로 유지하며 최상위 입금 트랜잭션에 함께 참여한다. Sales 소유 전표 입금 감사도 기존 조회와의 호환을 위해 `SETTLEMENT_MANAGEMENT` 출처를 보존한다. 감사 출처를 코드의 소유 모듈명에 맞춰 임의 변경하지 않는다.
- 정산이 사용하는 경매 결과 값은 Auction 소유 scalar projection에서 application 값으로 변환한다. 결과·시도·lot·출하 Entity 그래프를 적재하거나 Repository projection을 모듈 밖에 전달하지 않는다. ID를 받는 참조 조회는 중복을 제거해 500개씩 처리한다. 정산 금융 snapshot 입력과 응답의 현재 표시 참조는 각 단계에서 조회하며 같은 값으로 합치지 않는다. 분할 조회의 표시 정보 전체가 하나의 DB snapshot이라는 보장은 추가하지 않는다.
- Work 효과 handler는 Work가 만든 application 실행 값을 받으며 Work Entity에 접근하지 않는다. 효과 저장과 대상·작업 상태 전이는 Work가 기존 유스케이스 트랜잭션 안에서 처리한다. 대상 위치는 저장된 스냅샷의 값을 복사해 전달한다. 재실행 시 기존 효과를 먼저 조회하고, 새 완료 기록은 대상마다 중복 조회를 추가하지 않는다.
- Farm의 품종 기반 즉시 작업은 기존 원본 조회/잠금 시점의 품종명 값을 Work 실행 API에 전달한다. 자동 이력 제목 생성은 Work 안에서 처리하며 다른 모듈은 Work의 시각·주체·제목 조립 helper에 의존하지 않는다. 자동 제목과 요청 원문 제목은 별개이며 기존 명령 payload·접수 지문·actor 정규화를 유지한다. 내부 helper의 외부 참조는 컴파일된 dependency architecture 검증으로 차단한다.
- 입고의 포트 취소는 Work의 포트 업무 API로 요청한다. Work가 접수 namespace·사유 정규화·지문과 기존 작업 처리의 membership 의미를 선택하며, 다른 모듈은 Receipt helper·지문 계산기나 저장 callback에 의존하지 않는다. Farm은 Work 소유 업무 port로 입고 검증·보상·입고 감사를 처리한다. 업무 API와 Farm adapter는 호출자 트랜잭션을 필수로 요구하며 최상위 입고 트랜잭션에서 접수와 모든 변경을 함께 확정/rollback한다. 완료 접수는 현재 입고 검증 전에 replay하고, 기존 저장 키·지문 필드·작업 ID 목록을 보존한다. 입고 응답은 기존처럼 현재 참조로 조립하며 생성 응답 snapshot 계약으로 바꾸지 않는다.
- 새로운 비 HTTP 입력 채널은 명시적인 입력 검증·신뢰된 주체·인가·감사 context·DI 트랜잭션 proxy·업무별 재시도 계약을 제공해야 한다. 기존 application method를 호출하는 것만으로 HTTP의 validation·권한/context가 적용된다고 가정하지 않는다. 실제 소비자가 없는 채널이나 모든 서비스의 interface/복제 DTO를 선제적으로 만들지 않는다.
- Work의 고정 유형 규칙은 순수 domain 정의에 두고 실행 분기와 capability가 함께 사용한다. 코드별 workflow·대상 출처·등록 제한은 `WorkTypeDefinition`, 기본 handler·효과 분류·사용자 정의 허용은 `WorkTypeTemplate`이 소유한다. 활성·시스템 여부는 Entity에서 결합한다. 다른 모듈은 코드 상수를 위해 Work Entity를 참조하지 않는다.
- 새 Work 유형은 정의와 필요한 handler·구조 변경 strategy를 등록한다. 기존 registry가 애플리케이션 시작 시 필수 구현의 누락을 검사하며, 같은 유형 목록을 계획·기록·실행 서비스에 복제하지 않는다. 기존 코드 우선 handler와 저장된 template의 fallback을 유지하고, 유형의 효과 분류와 실제 실행 결과의 효과 종류를 임의로 합치지 않는다.
- 구조 변경 효과의 저장 handler 이름은 현재 WorkType code/template와 별도의 역사 계약으로 해석한다. Work 정의가 허용된 저장 이름과 구조 유형을 연결하고 `MOVE`·`MOVEMENT`를 같은 이동 유형으로 인식한다. 계보 application 값은 이 구조 유형을 제공하며 Farm은 실행에 쓰는 strategy의 `lineageType`을 조회에도 사용한다. 별도 문자열 switch나 현재 작업 유형으로 과거 분류를 복원하지 않는다. 구형 target 효과와 실행 회차/원본 행이 있는 효과의 조회 범위는 유지한다.
- 구조 변경 strategy의 누락·계보 관계 누락은 기동 시 거절한다. 새 구조 실행의 결과 handler가 정의와 다르면 효과 저장 전에 실패하며 최상위 트랜잭션의 Farm 변경도 rollback한다. 기존 효과 replay는 현재 handler를 실행하거나 다시 분류해 쓰지 않는다. 새 구조 정의/저장 이름은 등록→실행→상세/계보→취소/replay와 실패 rollback을 검증하며 저장 이름을 임의로 변경하지 않는다.
- Work 효과의 고정 결과는 `WorkExecutionResult` 안에서도 유형별 값으로 유지하며 효과·대상 실행·보정 이력을 저장할 때 기존 JSON으로 변환한다. 이동 identity와 보정 수량 수지도 이 값이 소유하며 handler가 Map에 추가 필드를 붙이지 않는다. 자유 기록·입고 부가 정보·현장 동기화 snapshot과 기존 효과 replay의 JSON은 명시적인 JSON 결과로 보존하고 현재 Entity로 다시 구성하지 않는다.
- 저장 효과 JSON의 필드 유무·null·날짜와 결과 순서를 유지한다. 대상·상세·계보·수량 보정의 공통 해석은 Work 소유의 조회 없는 codec에 둔다. 대상의 숫자 ID 합집합, 상세의 문자열 ID 허용·우선순위, 계보·수량의 숫자 전용 해석은 각각의 기존 정책으로 보존한다. 포트 수량의 위치별 복원은 모든 행과 고유 ID가 유효할 때만 허용하며, 잘못된 투입 수량 snapshot을 조용히 누락하지 않는다. 상세의 연결 행 fallback과 참조 조립, 필드 라벨·표시는 응답 조립 측이 담당한다. 상세 service는 참조와 보정 감사 이벤트를 일괄 조회하며, 보정마다 Repository를 호출하지 않는다. 기존 품종·위치 참조 표시와 저장된 수량·상태 이력의 의미를 임의로 바꾸지 않는다.
- Work 실행의 공통 payload는 허용된 application 명령으로 제한한다. HTTP DTO는 호출 측에서 application 값으로 변환하며 구형 분갈이·분주·합식의 Map 입력은 호환 mapper에서 읽는다. 즉시 기록 접수의 fingerprint에 포함된 요청 metadata·원문·null·날짜·배열/상속 ID 순서는 기존 표현을 보존한다. 지문 계산용 전체 요청 표현을 실행 명령의 간소화된 표현으로 임의 교체하지 않는다. 입고 이력처럼 이미 적용한 효과를 저장하는 경로에는 실행에 사용하지 않는 payload를 전달하지 않는다.
- 포트 전용 실행은 `InboundPottingCommand`를 Work 진행·효과 handler·Farm 실행까지 유지한다. Map은 효과 저장·지문 비교에 사용하고, 구형 대상 완료의 Map 입력은 전용 codec에서 같은 application 명령으로 해석한다. HTTP DTO로 재복원하지 않으며 기존 JSON 키·날짜 표현·null·결과 순서와 효과 identity를 유지한다.
- 외부 시스템은 application port 뒤의 adapter로 추가한다. 외부 시스템 DTO와 오류를 domain에 전파하지 않는다.
- 작업 수량 수지는 Work 소유 실행 스냅샷·정정 감사 이벤트에서 복원하며 현재 Farm Entity를 과거 사실의 근거로 사용하지 않는다. Farm adapter는 결과 잠금 아래 현재 상태·실사·후속 참조를 검증한다. 실사는 Farm 소유 접수/감사 기록 → 난 묶음 → 논리 구역 순으로 잠그고 같은 트랜잭션에서 Mutation과 수량을 확정한다. 두 흐름 모두 최초 실행 스냅샷을 덮어쓰지 않는다.
- 작업 보정은 Work가 접수 → 원본 잠금 → Farm 준비 → 감사 ID 확보 → Farm Mutation 적용 → 원본 작업일·감사 결과·접수 완료를 조율한다. Farm은 잠금 아래 현재 상태·후속 참조·실사·수량을 검증하고 변경 전후 값과 원본 Mutation 참조를 계획 값으로 반환한다. 준비와 적용은 같은 명령·호출 트랜잭션에서 실행하며 Farm 포트는 `MANDATORY`로 잠금을 유지한다. 저장 callback이나 Work Entity를 Farm에 넘기지 않는다.
- 보정 날짜의 미래일·생성 취소 병행 제한·무변경 판단과 최종 감사 JSON 조립은 Work가 소유한다. 감사 ID는 Farm Mutation의 출처를 만들기 전에 확보하고, 날짜 변경은 이미 잠근 원본 Entity에 적용한다. 빈 난 묶음 조정·빈 수량 정정 목록의 날짜 전용 요청은 Farm 보정 포트를 호출하지 않는다. 동일 값 난 묶음 행을 포함한 요청은 기존 Farm 검증을 유지하며, 최종 응답의 참조 조회는 기존 조회 계약을 따른다. 접수·감사·작업일·Mutation은 함께 확정/rollback하고, 원본 기간 길이·완료 상태·최초 실행 스냅샷과 취소 후 replay를 보존한다.

```text
호출 모듈 application → 제공 모듈 application API
호출 모듈 port ← 제공 모듈 adapter

금지: 호출 모듈 → 타 모듈 Repository/DB table
```

#### 유스케이스와 트랜잭션

- Controller는 HTTP 변환과 validation 진입만 담당하고 application service가 유스케이스와 트랜잭션을 소유한다.
- Work 구조 변경·포트·보정 입력과 실행·계보 조회값도 application 계약이며 Farm이 Work의 HTTP DTO를 직접 소비하지 않는다. 기존 타입을 이식하고 JSON·OpenAPI 이름을 유지한다.
- 수동 입금처럼 HTTP 입력과 유스케이스 입력의 의미·필드가 같으면 application 명령을 그대로 바인딩하고 표준 validation도 해당 명령에 둔다. 값만 복사하는 Request·변환 메서드는 두지 않는다. 기존 OpenAPI 이름은 명시적으로 유지하며, 입력 의미나 변환이 달라지는 경우에만 HTTP DTO를 분리한다.
- 쓰기 유스케이스는 하나의 public application method를 원자 경계로 삼는다. 중간 service 호출이 별도 트랜잭션을 암묵적으로 만들거나 self invocation에 의존하지 않게 한다.
- 구조 변경 즉시 기록은 계획·시작·실행의 내부 결과를 같은 Work 모듈과 트랜잭션 안에서 전달하고, 최종 상세 응답만 조립한다. 내부 쓰기는 호출 트랜잭션을 필수로 요구한다. 상태 전이·전체 대상 완료 확인·잠금 후 재검증·효과와 Mutation의 snapshot 저장은 실행 단계에 유지하며, 단독 계획/시작/실행 API는 기존 상세 응답을 반환한다. Receipt의 ID 기반 replay와 입력 순서를 보존하고 최종 응답 조립 실패도 전체 기록과 함께 rollback한다.
- 입고 포트의 즉시 실행·기록도 내부 계획/진행/완료 결과로 작업 ID를 모으고 최종 상세를 한 번 조립한다. 전체 완료는 공통 진행 서비스의 대상 종료 정책으로 판단하며 응답 진행률을 다시 읽어 완료하지 않는다. 연결 입고 선잠금, 새 계획 생성 후 실행 대상 재조회, 효과 적용 직전 현재 입고의 snapshot 갱신과 같은 key의 구형 효과 검증은 유지한다. 활성 계획의 일부 대상만 완료하면 기존 작업을 진행 중으로 남기고, 일시정지 계획은 재개 후 처리한다. 외부의 단독 계획·진행·대상 완료 API는 기존 상세 응답을 유지한다.
- 독립 폐기는 품종별 계획과 대상을 일괄 조회한 뒤 내부 시작·대상 완료 경로로 처리하고 최종 상세만 조립한다. 이동 연계 폐기는 이동 실행에 작업 ID만 전달하며 연결과 제목은 생성된 대상의 품종 snapshot으로 확정한다. 품종별 일반 계획도 전체 생성 후 상세를 한 번 조립한다. 전체 대상 선잠금·대상 완료 직전 재검증·효과 저장·수량 배분·부모와 함께 취소하는 정책은 유지하며, 최종 응답 실패도 전체 쓰기와 함께 rollback한다.
- 품종·자재 코드는 각 저장소의 PostgreSQL sequence에서 원자적으로 발급한다. 엔티티 ID와 코드의 sequence는 분리하며, 입고 신규 품종도 같은 품종 발급 경로를 사용한다. 코드는 유일한 식별값이며 삭제·rollback에 따른 번호 공백을 허용한다.
- PostgreSQL 엔티티 ID는 테이블별 sequence와 `allocationSize = 50`을 사용한다. Hibernate JDBC batch와 insert 정렬을 활성화하며, 대량 저장은 같은 트랜잭션에서 동일 엔티티를 연속 저장해 JDBC batch가 유지되게 한다.
- Entity는 자기 상태의 불변식과 전이를 지키고 application service는 aggregate 조회, 순서 제어, 모듈 간 조율을 담당한다. 여러 Service에서 같은 상태 조건을 검사하면 Domain Policy 또는 상태 전이 메서드로 모은다.
- 책임 분리가 기존 구현·중복 정책을 대체하는지 확인한다. 단일 호출을 전달하는 wrapper나 한 필드만 감싼 반환형은 별도 의미가 없으면 만들지 않는다. 같은 유스케이스의 private method로 충분하면 클래스를 추가하지 않으며, 계약 이식이 끝나면 이전 API를 함께 제거한다.
- 배치 수용 프로필은 HTTP DTO를 domain 값으로 변환한 뒤 순수 정책에서 정규화·중복·수용량을 검사하고, 전체 검증이 끝나야 기존 규칙을 교체한다. 모드 강도는 enum 선언 순서와 분리하며 검증·응답·감사 정렬이 같은 강도 기준을 사용한다.
- 네트워크·파일·사용자 대기처럼 실패와 지연을 통제하기 어려운 작업은 DB 트랜잭션 안에서 수행하지 않는다.
- 여러 행을 잠글 때는 ID 오름차순처럼 잠금 순서를 고정한다. 재고, 잔액, 순번, 상태 변경에는 도메인 검사와 함께 version, 비관적 잠금, UNIQUE/CHECK 또는 원자 갱신 중 필요한 DB 보호를 둔다.
- 난 묶음 단건·일괄 상세 수정은 전체 대상 묶음 ID 순 → 잠금 후 확인한 현재 구역 ID 순으로 먼저 잠근다. 각 집합은 전체 정렬 후 500개씩 취득하며, 감사·응답용 연관 상세도 잠금 뒤 일괄 로딩한다. 변경 여부와 감사의 변경 전 값은 잠금 후 상태로 판단하고, 실제 수정·응답은 요청 순서를 유지한다. 구역별로 잠근 뒤 다음 묶음을 잠그는 입력별 실행은 허용하지 않는다.
- 신규 구조 변경 기록은 Receipt → 전체 원본 묶음 → 현재·결과 구역 → 생성하는 Work·실행 root 순이다. 이 Work root들은 해당 트랜잭션에서 처음 생성한 미확정 ID다. 기존 계획의 진행·실행·취소는 아래의 연결 입고·기존 Work root 선잠금 규칙을 따른다. 완료 요청의 replay는 Receipt 결과를 조회하며 새 원본·구역 잠금을 취득하지 않는다.
- 작업 일괄 취소의 최상위 트랜잭션은 Work application에 둔다. 작업 ID 오름차순으로 root 행을 잠근 뒤 Farm port에서 영향 난 묶음을 ID 순으로 잠그고 외부 참조를 재검증한다. 생성 취소로 지정한 원본만 복원 위치 검사를 생략하며 기존 일괄 보상 Engine을 사용한다. 추가 선택이 없는 보상 요청은 기존 요청 지문과 호환된다.
- 작업 대상 등록은 Farm resolver port에서 대상 난 묶음을 ID 순으로 잠그고 활성 여부를 다시 검증한 뒤 저장한다. 단건 구조 변경·포트 취소도 같은 난 묶음 root 잠금 뒤 외부 참조를 재검증하여, 취소된 결과에 새 계획 작업이 연결되는 경쟁 조건을 막는다. 잠금은 최상위 쓰기 트랜잭션이 끝날 때까지 유지한다.
- 일반 일괄 계획과 이를 사용하는 폐기 기록은 제외 대상 필터 적용 후 전체 대상 묶음을 먼저 잠근 뒤 품종별 aggregate를 생성한다. resolver는 전체 ID를 중복 제거·정렬한 다음 500개씩 모두 잠그고 활성 여부를 분할 재검증한다. 기존 품종·대상 순서는 유지한다. 잠금 전 해석한 대상의 version이 대기 중 변경되면 기존 `WORK_TARGET_CHANGED`로 전체를 거절하며 오래된 snapshot으로 진행하지 않는다.
- 키가 있는 일반 단건·일괄 계획과 일반 완료 기록은 경로별 독립 Work Receipt를 대상 해석·묶음 잠금 전에 claim하고 잠근다. 최초 응답 snapshot·결과 ID·생성 membership과 전체 작업/대상/실행/효과/기존 감사를 최상위 생성 트랜잭션에 확정한다. 완료 접수는 현재 Entity를 다시 로딩하지 않는다. 기존 Work 실행·구조 변경·포트의 ID 기반 replay와 생성 membership 의미는 유지하며 과거 snapshot을 추정하지 않는다. 키 없는 internal 계획 조합은 기존 계약을 유지한다.
- 작업 진행·실행·취소는 연결 입고 ID 오름차순 → 작업 ID 오름차순 → 대상 실행 ID 오름차순 → 난 묶음 순으로 잠근다. 포트 실행은 같은 계획의 형제 입고까지 먼저 일괄 잠그고 계획 변경 여부를 재검증한다. 취소의 Farm 검사는 쓰기용 잠금 조회와 읽기 전용 가능 여부 조회를 분리하여 잠금 전 엔티티를 재사용하지 않는다. 입고의 포트 실행·취소 접수는 기존 Work 접수 저장소를 쓰되 신규 작업 등록 membership을 만들지 않는다. 같은 기존 계획을 처리하는 여러 입고별 접수가 함께 등록 관계를 덮어쓰지 않게 한다.
- 입고 쓰기 조회는 collection fetch의 후속 잠금에 의존하지 않는다. root 행을 먼저 직접 잠근 뒤 연관 정보를 일괄 로딩한다. 입고 취소·포트 취소는 연결 작업들의 형제 입고까지 기존 Work 잠금 API로 먼저 잠그며, 단순 입고 수정은 root 잠금 안에서 최신 수정 가능 상태를 검증한다. 목록의 포트 취소 action 판정은 Work application의 일괄 조회를 사용한다.
- 키가 있는 입고 생성은 Farm 소유 접수 PK를 원자 claim한 뒤 해당 root를 잠근다. 품종·배치·완료 Work 생성 전에 완료 접수를 재조회하며, 최초 응답을 현재 Entity로 재조립하지 않는다. 신규 품종·입고·즉시 배치 Mutation/묶음·Work/효과·감사·접수는 최상위 입고 생성 트랜잭션에 참여한다. Work 실행/취소 접수와 membership은 공유하지 않으며 키 없는 호환 생성은 접수를 만들지 않는다. 영속 입력 지문과 응답 schema 변경은 과거 replay 호환 검증을 포함한다.
- Partner 잠금 API는 호출자의 트랜잭션을 필수로 요구하고 거래처 ID 오름차순으로 잠근다. 잠금 획득만 하는 호출이 독립 트랜잭션을 열고 즉시 반환하는 방식은 허용하지 않는다. 잔액 생성·갱신은 거래처 잠금 후 잔액 행 잠금 순서를 유지한다.
- 판매 예약·해제·출고·복구의 수량 불변식은 Farm Entity가 적용한다. Farm Mutation Engine은 typed command를 받고 호출자의 트랜잭션을 필수로 요구하며 실제 재고 변경을 소유한다. Sales는 유스케이스 순서와 배분별 재고 이동·Mutation 연결을 저장한다. 판매 수정은 ORM 버전과 별도의 변경 식별자를 사용해 해제·재예약을 같은 변경에 연결하며, Mutation replay 때 이동 이력을 중복 저장하지 않는다. Legacy 직접 writer는 제거된 상태다.
- Sales의 키가 있는 생성은 Sales 소유 접수 PK의 원자 claim → 접수 root 잠금 → 기존 거래처·일별 번호·난 묶음 순으로 처리한다. 최초 응답을 보존하고 완료 접수는 최신 상태 조회나 Mutation을 재실행하지 않는다. 접수·전표·예약·출고/출하·snapshot·잔액·감사는 최상위 생성 application 트랜잭션에서 함께 확정/rollback한다. 키 없는 기존 생성은 이 접수를 만들지 않는다. Work의 접수·membership을 공유하지 않으며, 영속 요청 지문·응답 schema 변경은 과거 replay 호환을 검토한다.
- 판매 수정은 전표 → 이전·신규 거래처 ID 순 → 기존·신규 allocation 묶음 합집합 ID 순으로 잠근 뒤 기존 예약을 해제한다. Farm application의 잠금 API가 전체 ID를 중복 제거·정렬한 다음 500개씩 취득하고 호출자의 트랜잭션 종료까지 유지한다. 사전 잠금에서 DTO·연관 상태 snapshot을 만들지 않으며 새 allocation의 `CREATION` snapshot은 기존 예약 해제 후, 새 예약 전 값으로 저장한다. 후속 Mutation이 자기 대상만 정렬하는 것으로 유스케이스 전체 잠금 순서를 대체하지 않는다.
- 신규 판매 예약은 난 묶음 잠금 안에서 Farm 상태 정책을 검증한다. 판매 선택 조회와 집계는 그 정책의 상태 목록을 DB 조건으로 전달하고, Work·자동 그룹 조회는 같은 정책의 비활성 목록을 사용한다. 경고 상태와 전량 예약을 Work 대상으로 유지하며 판매 제한을 작업 제한으로 복제하지 않는다. 기존 예약 해제·출고·복구와 이미 적용한 Mutation replay는 신규 예약 자격 검증과 구분한다.
- 경매 전표 취소는 Auction의 실제 시도·처리 이력과 수량을 기준으로 판정하며 현재 lot 상태만으로 과거 기록의 존재를 대체하지 않는다. Auction은 출하의 lot를 ID 순서로 잠근 뒤 삭제 가능 여부를 재검증한다. 결과·반환·보정·상태 writer도 shipment fetch graph 없이 lot root를 먼저 잠그며, 이력이 없는 출하의 삭제·Sales 연결 해제·재고 복구·취소 감사는 Sales의 최상위 트랜잭션에서 함께 반영하거나 rollback한다.
- Auction의 결과·반환 접수는 lot root 잠금 후 같은 lot·업무·요청 키의 receipt를 먼저 확인한다. 최초 요청은 시도·결과·상태 이력을 flush해 ID를 확정하고 최초 응답·입력 지문과 함께 같은 트랜잭션에 저장한다. replay는 현재 상태 전이를 다시 실행하지 않는다. 요청/응답 schema 변경 시 영속 지문과 기존 응답 snapshot 호환을 함께 검토하며 receipt를 임의 만료시키지 않는다.
- Auction의 상태 이력은 수량 전후값도 보존하며 같은 상태의 수량 변경에도 생성한다. 최초 응답의 이력 ID는 flush 후 확정한다. 직접 수량 보정의 자격은 lot domain이 경매 시도·반환 확인 사실로 판단하고, 목록은 이미 일괄 조회한 시도를 정책에 전달해 lazy collection 조회를 추가하지 않는다. V37 이전 이력·Receipt에 없는 수량·capability는 추정하지 않는다.
- 구조 변환의 application 실행기는 원본 잠금 후 변경 전 속성과 결과 목적을 계획하고 상태 변경을 Mutation Engine에 요청한다. 결과·계보 조립의 기존 의미와 입력 경로별 정규화는 유지하되 Legacy writer나 writer 선택 분기는 사용하지 않는다. Engine은 잠금·상태 변경·revision을, 내부 recorder는 header·쓰기 context·Entry·Relation 기록을 담당한다. 새 그룹은 header와 트랜잭션 context 설정 후 저장하며, 잠금 전후의 재실행 확인과 최상위 트랜잭션은 유지한다.
- Mutation 명령은 닫힌 타입 집합으로 선언하고 fingerprint 계산은 모든 명령을 다루는 switch로 검사한다. 명령 추가 시 지문 처리가 누락되면 컴파일에 실패한다. 기존 payload와 저장 지문은 호환 fixture로 검사한다. Transform의 제외 ID 집합은 정렬하여 JVM의 집합 순회 순서와 무관한 지문을 만든다. 이번 전환은 Mutation 원장이 없는 V20 백업에서 원장을 재구성한다. 이전 실험용 Engine 원장의 지문을 수정하거나 그대로 재사용하지 않는다.
- Mutation 지문의 기존 v1 입력은 중첩 값까지 전용 projection으로 고정한다. 현재 application record에 필드를 추가해도 기존 지문에 자동 편입하지 않는다. 명령·중첩 값·snapshot의 필드 변경은 저장 계약 fixture가 검출하며, 의미 있는 새 값을 v1에서 무시한 채 fixture만 갱신하지 않는다. 새 의미는 명시적인 version/판별 계약과 구형 replay 시험을 마련한 뒤 반영한다. 여기서 v1은 현재 V20 cutover 이후 형식이며 이전 실험 원장의 호환 보증이 아니다.
- Sales 생성의 명령·품목·배분과 일반 Work 단건/일괄 계획·완료 기록의 요청은 기존 v1 지문 전용 값으로 변환한다. DB claim·완료 응답·membership·경로별 key 범위는 각 모듈에서 유지한다. Sales의 날짜 문자열/숫자 표현과 Work의 날짜 배열/숫자 정규화를 공유 mapper로 통합하지 않는다. Work 자유 details는 전체를 비교하며 고정 필드만 추출해 버리지 않는다. 필드 추가는 schema guard와 구형 지문 replay 시험으로 검토한다.
- Snapshot에서 누락과 명시적 null은 미상 값으로 보존하고 0·false·현재 Entity 값으로 채우지 않는다. canonical 위치는 소수 둘째 자리까지 정확히 표현하며 초과 정밀도를 반올림하지 않는다. Farm의 기존 scale 기반 지문과 Work의 숫자 정규화 지문은 서로 다른 영속 계약이다. 공통 JSON 설정으로 임의 통합하지 않는다. 필드 확장 배포·구버전 writer·rollback 정책은 `07-deployment.md`의 저장 지문·스냅샷 형식 변경 기준을 따른다.
- 정산 설정의 최초 조회도 기본값 생성이 가능한 쓰기 유스케이스다. 거래처를 먼저 잠그고 설정을 다시 조회해 동시 최초 조회의 중복 생성을 막는다. 설정 변경도 같은 거래처 잠금 안에서 변경 전후 감사 값을 저장한다.
- 수동 입금 원장 API는 거래처 ID와 application 명령을 받고 입금 이벤트 식별자만 반환한다. 원장·잔액 Entity는 Settlement 안에서 관리한다. 원장 처리는 호출 트랜잭션을 필수로 요구해 대상 입금 상태·입금/연결 이벤트·잔액·감사가 함께 반영되거나 rollback되게 한다.
- 일반 판매의 입금 대상 조건은 전표 도메인이 소유하며 실제 입금과 `CONFIRM_PAYMENT` 판단이 이를 공유한다. application은 대상 검증 후 기존 입금 키를 확인하고 새 입금에만 잔액 검사를 적용해 완납 후 재요청도 재처리 없이 응답한다.
- 경매 정산 행은 결과·lot ID와 최초 반영 시점의 수량·단가·금액을 보관한다. Auction application의 결과 값을 받아 생성하며, 이미 반영한 결과의 금액은 재구성이나 응답 조립 때 원본 값으로 덮어쓰지 않는다. 예상 입금일의 달력일·영업일 계산은 정산 설정 Entity가 소유하고 application은 설정의 단건·일괄 조회만 조율한다.

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
- index 변경은 실제 Repository SQL과 다른 건수·선택도의 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`로 근거를 남긴다. query count·Entity 적재 상한과 DB scan·상관 subquery의 반복 횟수는 별도로 검증한다. 작은 fixture의 순차 scan을 실패로 취급하거나 planner의 특정 node·index 이름·실행 시간을 정답으로 고정하지 않는다. 조회 개선과 함께 쓰기/WAL·저장 공간·migration 잠금 비용을 확인한다.
- 거래처 이름·대표자·연락처 검색은 Partner가 scalar ID를 500건씩 keyset 조회하고 Sales·Auction은 식별자 조건을 자기 검색에 결합한다. 경매의 이름 전체/각 공백 경계/경매장 일치 조건은 중복 제거한 32개 조건씩 묶어 조회하며 조건별 DB 일치 값을 반환한다. 전체 matching ID를 사용해 최종 페이지와 전체 건수를 계산하고 과거 검색의 비활성 거래처·문구/공백·LIKE escape 의미를 유지한다. 검색어와 경매장 조건이 없으면 추가 거래처 검색은 하지 않는다. Sales·Auction의 500개 초과 ID 조건과 Work history의 범위 ID 조건은 소유 Repository 안에서 Long 배열 하나를 바인딩해 `= ANY`로 처리한다. 다른 모듈 테이블을 SQL로 join하지 않는다. 전체 ID 집합과 배열의 전송/메모리 크기, 검색어의 경계 조건 수는 여전히 증가하며 임의 상한으로 결과를 자르지 않는다. 분할 검색은 단일 DB snapshot을 보장하지 않는다.
- Partner 전체 표시 값, Farm의 읽기용 계보/collection 상세 참조와 Work 최신 날짜 입력은 ID 중복을 제거하고 500개씩 조회한다. Work history는 전체 범위에 대해 root page/count를 계산한 뒤 페이지 작업의 대상/효과를 읽는다. 범위 ID를 여러 개의 독립 페이지로 나눠 total이나 정렬을 합성하지 않는다. Farm 쓰기의 상세 로딩·잠금 경로는 읽기용 분할 helper와 구분한다.
- 출하 선택지는 Auction이 최신 후보 ID를 페이지로 제공하고 Sales가 자기 전표에 연결된 ID를 제외한다. 미사용 200건을 채우거나 후보가 끝날 때까지 확인한 뒤 선택된 출하·lot만 일괄 조회한다. 다른 모듈의 Entity를 JPQL 하위 쿼리에 직접 넣지 않는다.
- Farm 구조 조회는 동·다이·구역을 읽은 뒤 다이 ID로 난 묶음과 참조를 일괄 조회해 조립한다. 맵은 필요한 값만 JPQL projection으로 읽고 전체 난 묶음 상세 DTO나 Entity graph를 만들지 않는다. 저장소 projection은 Farm application 안에서 응답으로 변환하며 외부 계약으로 노출하지 않는다.
- DTO mapper가 lazy association을 순회하지 않게 조회 범위를 명시한다. mapper 호출 전 필요한 연관 데이터가 이미 로딩됐는지 확인한다.
- 경매 정산 페이지는 정산 root와 Partner application API의 일괄 이름 조회만 사용한다. 정산 행과 Auction 결과는 상세 조회에서만 조립한다. 같은 조회 조건의 전체 금액은 DB 집계로 구하고 페이지 크기나 호환 목록 상한을 적용하지 않는다. 호환 목록은 최신 500개의 root를 선택한 뒤 상세를 일괄 조회한다.
- 입금 이벤트의 거래처 이름도 Partner application API로 일괄 조회한다. 정산 상세 행의 출하일·품종·등급은 Auction application의 결과 값으로 일괄 조립한다. 거래처 이름은 현재 기준 정보이며, 원장의 금액·입금자·날짜나 기존 정산 행의 보존된 값은 다시 계산하지 않는다.
- 입금 이벤트는 유형 필터를 적용한 뒤 서버 페이지로 조회한다. 원본 입금의 식별자만 필요한 연결 이벤트 응답에서는 원본 Entity 전체를 fetch하지 않는다. 호환 전체 목록은 모든 유형을 포함해 최신 500개 이벤트로 제한한다.
- 정산 초기 재구성은 시작 시 Auction 양수 결과의 최대 ID를 고정하고 500개씩 자기 연결 ID와 대조한다. 후보의 scalar 값에서 정산 key를 찾은 뒤 각 writer가 같은 상한 안의 경매장·경매일 결과를 잠금 아래 다시 읽는다. 기존 정산은 정확히 해당 key의 line만 적재하며 광범위한 날짜 구간을 읽지 않는다. 전체 결과/aggregate를 한 persistence context에 누적하지 않는다. 처리할 새 결과가 없으면 aggregate를 읽거나 수정하지 않으며, 상한 조회 1회와 후보 ID/link 대조만 수행한다. 한 정산의 line 전체와 영향 key 중복 제거는 유지한다. 모듈 간 역방향 의존이나 별도 동기화 상태 테이블은 추가하지 않는다.
- 목록·옵션·분석 조회에는 pagination, 날짜 범위 또는 명시적 최대 건수 중 하나를 둔다. 장기 누적 테이블의 무제한 `findAll`을 API 경로에 사용하지 않는다.
- 거래처 관리 목록과 선택지는 같은 검색 조건·정렬·페이지 조회를 사용하되 선택지 응답에는 식별자·이름·활성 여부만 전달한다. 현재 선택은 검색 결과와 별도로 단건 조회해 페이지 밖이나 비활성 거래처도 표시한다. 호환 활성 목록은 이름 검색과 기존 응답을 유지하고 500건으로 제한한다.
- 작업·재고 분석은 Work·Farm의 기존 읽기 application API가 집계 값과 제한된 최근 기록을 제공한다. Analytics는 기간 검증과 HTTP 응답 조립을 담당하며 두 모듈의 Q 타입이나 Repository projection을 참조하지 않는다. 작업의 완료 조건과 재고의 판매 가능·주의 상태는 소유 모듈에서 적용한다.
- 판매 분석도 Sales의 완료 전표 집계와 최근·미수 전표 값만 소비한다. Partner는 현재 이름·유형을 500개 ID씩 scalar 조회하고, Settlement는 0이 아닌 현재 잔액을 읽기 전용 값으로 제공한다. Analytics는 전표·품목·거래처 Entity를 적재하거나 타 모듈 테이블을 직접 join하지 않는다.
- 분석의 최상위 application 트랜잭션은 읽기 전용 `REPEATABLE_READ`를 사용한다. 여러 소유 모듈을 조회하는 동안 매출·현재 이름·잔액이 서로 다른 시점으로 섞이지 않게 한다. 쓰기 유스케이스의 트랜잭션·잠금 순서는 바꾸지 않는다.
- 입금 상태별 판매 분석은 Sales가 저장된 자유 문자열의 호환 분류를 소유하고 분류별 합계만 제공한다. Analytics는 원문 상태를 해석하거나 실제 입금액으로 분류를 다시 만들지 않는다. 이 분류는 기존 보고서의 호환 값이며 입금 가능 여부·원장 상태 판단에 사용하지 않는다. 저장 상태의 표준화는 기존 데이터와 지표 변경을 함께 검토할 별도 정책 작업이다.
- 조회 종료월과 직전 월 비교 범위는 기존 분석 기간 값에서 계산한다. 직전 월에 없는 일자는 양 끝을 각각 말일로 보정한다. 6개월 차트의 표시 순서·빈 값, 입금 분류의 표시 이름, 미수 안내 문구·색상·링크는 조회 의존성이 없는 응답 assembler가 담당한다. 별도 Spring service나 중간 조회 DTO를 추가하지 않는다.
- 판매 화면의 거래처 순위는 기간 내 모든 거래처별 DB 합계를 현재 이름으로 합산한 뒤 상위 10개를 선택한다. 거래처 분석은 ID별 기간 합계와 양수 잔액이 있는 거래처를 합친 뒤 정렬한다. 독립 페이지의 일부 결과로 전체 순위를 만들지 않는다. 이 조합은 원본 전표 수 대신 거래처 집계 수에 비례하는 메모리를 사용하며, 거래처 수가 커질 때는 측정 후 별도 보고용 projection을 검토한다.
- 같은 조건의 전체 합계와 분포를 함께 반환할 때는 DB에서 그룹별로 집계한 값을 재사용한다. 작업 전체 건수·유형별 건수·최근 작업일은 이름·템플릿별 집계에서, 판매 가능 재고 합계는 품종별 집계에서 구한다. 최근 기록이나 일부 순위에 적용한 상한을 전체 합계에 적용하지 않는다.
- DB 집계로 표현 가능한 값을 전체 Entity 조회 후 Java에서 다시 집계하지 않는다. 다만 데이터량이 작고 규칙 표현이 더 명확한 경우에는 측정 근거를 남기고 단순 구현을 유지할 수 있다.
- 원장 대사의 현재 그룹은 ID 순서의 500행 scalar 조회로 읽고 Entity graph를 적재하지 않는다. 현재/삭제 그룹의 revision chain은 그룹·revision 순 scalar cursor로 읽어 앞뒤 Entry와 chain 시작/말단만 유지한다. fetch size는 500이며 전체 이력을 List나 persistence context에 누적하지 않는다. baseline/current fingerprint의 기존 입력·ID 순서와 누락/불연속/삭제 tombstone 판정은 유지한다. 전역 배치/업무 참조 검사를 위한 현재 그룹 값·baseline·오류 전체와 기존 fingerprint 직렬화는 여전히 크기에 비례하므로 완전한 상수 메모리 대사로 취급하지 않는다.
- 원장 대사의 Work 보정 참조도 ID 순서의 500행 scalar 조회로 읽고, Work 안에서 저장 결과 reader를 통해 application 참조 값으로 변환한다. Farm은 참조 500건씩 중복/null Mutation ID를 정리해 필요한 출처·correlation 값만 조회하며 Entity나 전체 Mutation map을 누적하지 않는다. 보정 결과의 absent/null/date/수량 수지 해석은 상세 응답과 같은 reader를 사용한다. 기존 오류 순서·fingerprint와 호출 transaction의 snapshot을 유지한다. Work의 참조 목록과 그룹 ID 전체가 메모리에 남으므로 이 경로 역시 상수 메모리 계약은 아니다.

#### 이력과 스냅샷

- 작업 효과, 출하, 전표, 정산처럼 과거 사실은 현재 Entity 상태와 분리해 보존한다. 과거 응답을 현재 난 묶음·거래처 값으로 다시 계산하지 않는다.
- 스냅샷은 이력이 확정되는 생성·완료 경계에서 같은 트랜잭션으로 저장한다. 스냅샷 생성이 필요한 후속 기능은 이 경계를 확장하고 여러 Controller 또는 mapper에서 임의로 복제하지 않는다.
- 판매 생성은 예약 반영 전, 출고·출하는 수량 차감 전 난 묶음을 ID 오름차순으로 잠그고 Farm 상태 값을 받는다. Sales가 해당 시점 값을 자기 스냅샷에 저장하며, 이후 응답의 현재 위치·가용 수량은 새로 조회하되 과거 스냅샷은 다시 만들지 않는다.
- 이력 데이터는 물리 삭제보다 상태 변경, 취소, 보정 레코드를 우선한다. 보정은 원본과 변경 전후 값을 추적할 수 있어야 한다.

#### 시간, migration, 검증

- DB 시점은 UTC로 저장하고 농장 업무일 계산은 `Asia/Seoul` 기준 `TimeConfig`와 주입된 `Clock`을 사용한다. 공통 Entity 생성·수정 시각은 같은 Clock을 읽는 Spring Data JPA auditing provider가 기록한다. 상태 이력과 그룹 가입·탈퇴 시각은 application이 UTC 값을 domain에 전달한다.
- 난 묶음 응답의 나이 계산은 application에서 한 번 구한 업무일을 전달받는다. 목록 조립 도중 날짜가 바뀌거나 DTO가 시스템 시계를 직접 읽지 않게 한다.
- 경매 정산의 결과 수신·입금 확인 시각은 application service가 `Clock`에서 UTC 값으로 정해 domain에 전달한다. 일괄 재구성은 같은 처리 시각을 사용하고, 정산에 이미 연결된 결과는 다시 재구성하지 않는다.
- Flyway migration은 `nullable 추가 → backfill → 제약 적용`처럼 기존 운영 데이터가 통과할 수 있는 순서를 사용한다. 대용량 table 변경은 lock 범위와 운영 적용 시간을 별도로 검토한다.
- `NOT VALID` CHECK의 신규/갱신 행 보호와 기존 행 검증 완료를 구분한다. 설치된 CHECK 식·DB 검증 상태·기존 위반을 같은 read-only snapshot에서 확인하고, 이력을 보존한 복구·재대사 후 제약별 validation을 수행한다. 운영 절차는 [배포 문서](07-deployment.md#기존-데이터의-check-제약-대사와-검증)를 따른다. 위반 0건이나 Flyway 성공을 원장·예약·금액의 모든 교차 불변식 검증으로 취급하지 않는다.
- 수량·금액·정산·migration 변경은 정상 흐름뿐 아니라 rollback과 중복 요청을 검증한다. 동시성 보강은 병렬 실행 테스트, N+1 보강은 query count 상한 테스트를 둔다.
- 원장 대사의 독립 read-only 호출은 `REPEATABLE_READ`로 그룹·Entry·count·업무 참조를 같은 DB snapshot에서 읽는다. cutover 등 기존 쓰기 transaction에서 호출하면 그 transaction에 참여하며 isolation을 별도로 올리거나 미확정 변경을 clear/commit하지 않는다. 일관된 read snapshot이 writer 중지·cutover 확인 절차를 대체하지 않는다.
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
- 판매·입고·일반 Work 생성의 미확인 요청 키는 local transport state로 두고, 같은 탭 재진입/새로고침에는 업무별 sessionStorage namespace로 보존한다. 일반 Work의 계획과 완료 기록도 별도 범위를 사용한다. 공통 키 수명 코드가 서로 독립적인 업무의 저장 키를 공유하게 만들지 않는다. 성공 응답 확인 직후 키를 해제하며, 확인 전에는 cache 갱신 실패·폼 닫기·입력 오류만으로 새 키를 발급하지 않는다. 서버 응답·폼 내용은 이 저장소에 복제하지 않는다.

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

- 구현 반복 중에는 변경 코드와 직접 관련된 단위·통합 테스트만 실행한다. 작은 표현 수정마다 전체 `npm run check`나 브라우저 E2E를 반복하지 않는다.
- URL parser·writer, 날짜·payload 변환, selection coordinator처럼 React와 분리 가능한 규칙은 pure function unit test로 검증한다.
- 서버 capability와 상태 전이는 백엔드 단위·통합 테스트를 기준으로 검증한다. 프론트 테스트에서 같은 전이 규칙을 다시 구현하지 않는다.
- mutation과 cache 갱신, back/forward, dialog focus, 지도 연속 선택처럼 경계를 넘는 흐름은 회귀 위험에 따라 integration 또는 E2E 테스트를 추가한다.
- API contract 변경은 Controller·DTO·테스트 수정 후 OpenAPI와 생성 타입을 갱신한다.
- 기능 단위 변경 완료 전 `cd frontend && npm run check`를 한 번 실행한다. 실행하지 못한 검증과 기존 경고는 결과에 남긴다.

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

전체 백엔드 검증은 기본 검사·패키징과 실제 PostgreSQL 회귀·벤치마크로 나눈다. Gradle 작업 이름에는 `work`가 남아 있지만 여러 도메인을 포함하며 브라우저 E2E와는 별도다.

로컬 검증은 다음 단계로 실행한다.

1. 구현 반복: `./gradlew test --tests '변경과 직접 관련된 테스트'`
2. 기능 단위 완료: `./gradlew test`
3. PostgreSQL 경계 변경: 관련 기능이 모인 체크포인트에서 `./gradlew workE2eTest`

`workE2eTest`는 PostgreSQL 전용 SQL·Flyway·constraint, 트랜잭션 경계, 멱등 처리, lock·동시성, 수량·금액·정산 변경에 사용한다. 문서·포맷·import·표현 전용 UI 수정에는 실행하지 않는다. CI는 아래 전체 검증을 계속 수행한다.

```bash
cd backend
./gradlew check bootJar
./gradlew workE2eTest
./gradlew workBenchmark
./gradlew workBenchmark -PworkBenchmarkEnforce=true
```

- `workE2eTest`: RANDOM_PORT의 실제 HTTP 요청으로 대상 미리보기, 일반·즉시 완료 작업,
  작업과 대상 상태 전이, 분갈이 수량·계보, 계획형 구조 변경, 요청 키 멱등성,
  작업 상세·분갈이 결과·난 묶음 통합 이력을 검증한다. 같은 PostgreSQL 환경에서 판매일별 전표 번호의 동시 원자 증가도 검증한다.
- 쓰기 rollback 회귀는 테스트 외부 transaction 없이 DI application proxy 또는 HTTP를 호출하고, 별도 read-only transaction에서 변경 전후 저장 상태를 비교한다. 늦은 실패를 주입할 때는 의도한 CHECK 이름·SQLSTATE 또는 정확한 실패 원인을 검증한 뒤 원장·업무 이력·접수·감사의 atomicity와 재시도를 확인한다. 호출자 transaction 참여 시험은 별도로 유지하며 최상위 transaction 검증을 대신하지 않는다.
- 잠금 경쟁 회귀는 실제 application transaction 안에서 worker DB PID를 기록하고 `pg_blocking_pids`의 직접·대기열 차단 관계가 지정한 잠금 보유자까지 이어지는지 확인한다. 시간 경과·DB 전체 대기자 수만으로 요청의 충돌을 추정하지 않는다. worker 진입과 잠금 관측·결과 대기에는 상한을 두고, 잠금 해제 뒤 양쪽 실행 순서의 상태·업무 오류·이력 불변식을 확인한다. 테스트용 관측은 최상위 쓰기 transaction을 대신 만들지 않는다.
- 실제 HTTP 회귀는 연결 10초·응답 60초 상한과 요청 method/path 진단을 공유한다. request body는 전송 실패 로그에 넣지 않는다. PostgreSQL 테스트 메서드는 기본 5분, 장시간 benchmark는 명시적으로 15분 상한을 사용하며 DB 번호 발급의 병렬 future도 60초 안에 완료해야 한다. `workE2eTest`의 JUnit timeout 시 thread dump를 남긴다. 이 상한은 운영 API SLA나 서버 JDBC 취소 보장이 아니다.
- `workBenchmark`: 작업 100건을 고정하고 작업당 대상 1·20·100건 및 한 난 묶음의 이력 1·100건으로 하위 fan-out을 늘린다. 작업 목록·상세·이력의 SQL 수와 목록 target Entity/반환 행 상한을 검사한다. API별 3회 워밍업 후 20회 측정한 median/p95는
  `backend/build/work-benchmark/results.json`에 기록한다.
- 테스트 전용 DataSource 계측은 Hibernate와 JdbcTemplate의 JDBC execute 호출·batch 호출·`ResultSet.next()`로 소비한 행·실패·명시 commit/rollback을 함께 센다. Work 보고서는 Hibernate Entity/flush와 응답 JSON의 UTF-8 byte 수도 보존하고, 검색 보고서는 호출 스레드의 할당 바이트를 함께 기록한다. JSON byte 수는 재직렬화한 body 기준이며 HTTP header·압축 bytes가 아니다. 측정 창은 별도 테스트 context에서 순차 요청 하나를 완전히 끝낸 뒤 닫으며 fixture 준비와 워밍업을 제외한다.
- `WorkQueryMeasurementPostgresE2ETest`는 root 4건 고정·대상 1·40·100건에서 SQL/반환 행이 증가하지 않고 target Entity가 적재되지 않는지 검사한다. JdbcTemplate만 사용한 조회가 Hibernate 통계 0건이어도 계측되는지 검증하며, 독립 Work 기록의 flush·commit 이후 응답과 replay 비용을 분리해 `build/test-measurements/work-write.json`에 남긴다. 기존 Auction 쓰기·계보·profile·품종·정산·placement 경로별 PostgreSQL 회귀는 그대로 유지한다.
- JDBC 실행 시간에는 driver/DB/잠금 대기가 섞이고 executeBatch 한 호출의 driver 내부 round trip은 구별하지 못한다. 소비 행은 DB scan 행 수가 아니며, 계측 밖의 다른 DataSource·raw unwrap 접근과 autocommit 경계도 포함하지 않는다. Hibernate flush 횟수·commit 시간과 lock 보유 시간·peak heap/GC·프로세스 전체 할당량은 별도 지표다. 절대 시간·할당량에 환경 의존적인 CI 실패 기준을 추가하지 않는다.
- 같은 `workBenchmark`의 거래처 검색 실험은 501개·5,001개·70,001개의 일치 거래처와 경매 문구의 공백 1/20개에서 판매·경매 검색의 전체 건수와 마지막 페이지를 검증한다. `partner-search.json`에 SQL 수·최대 바인딩 인자 수·응답 시간·호출 스레드의 할당 바이트를 기록한다. 별도의 `partner-search-plan-*.json`은 Sales 소유 배열 ID 조건의 EXPLAIN ANALYZE/BUFFERS이며 전체 API 쿼리 plan은 아니다. 할당량은 프로세스 전체나 최대 상주 메모리 측정값이 아니다. 500개 단위 식별자 조회 횟수와 검색 문구 경계의 반복 조회 방어를 항상 검사한다.
- 기능 결과와 DB 불변식은 자동 실패 조건으로 사용한다. 응답 시간은 실행 환경 영향을 받으므로
  전후 결과를 수동 비교하고 CI의 강한 실패 조건으로 사용하지 않는다. 기본 벤치마크는
  리팩터링 전 기준값도 남길 수 있도록 쿼리 상한을 기록만 하며, `-PworkBenchmarkEnforce=true`를
  지정한 경우에만 상한 초과로 실패한다.
- 전후 비교가 필요하면 각 대상 커밋에서 `clean workE2eTest workBenchmark`를 실행하고 생성된
  `results.json`을 각각 `before.json`, `after.json`으로 별도 보관한다.
- `FarmQueryPostgresE2ETest`는 다이 1·10·50개와 다이별 복수 구역에서 전체 구조·맵 SQL 3회, 다이·구역 목록 SQL 2회와 맵의 난 묶음·품종 Entity 로딩 0건을 검증한다. Work 정형 상세는 보정 0·1·10·50건에서 SQL 4회로 고정한다.
- 거래처 검색 경계를 거치는 판매 검색은 1·10·50행에서 SQL 4회, 품종과 경매장 이름에 걸친 경매 문구 검색은 6회 이내다. 기존 3회·5회에 scalar 검색이 추가되며 경매의 다중 검색은 일괄 처리해 행별 반복 조회를 피한다. 검색 없는 경매 페이지의 기존 5회 상한은 유지한다.
- `CoreQueryRegressionTest`는 기본 테스트에서 농장 viewport 3회, 경매 lot 페이지 5회 이내를 검증한다. 일반 판매 전표 상세는 서로 다른 난 묶음 배분 1·10·50개에서 SQL 5회로 고정되며, 배분·스냅샷·현재 Farm 값·거래처·서버 판정 액션을 일괄 조회한다.
- 사용자 그룹 목록은 1·10·50개에서 SQL 3회, 난 묶음별 소속 그룹 조회는 5회 이내인지 검증한다. 보관·탈퇴 제외와 소속 순서도 함께 확인한다.
- CI의 기본 job은 `check`와 `bootJar`, `backend-postgres` job은 Docker 확인 후 `workE2eTest`와 `workBenchmark -PworkBenchmarkEnforce=true`를 각각 실행한다. Docker가 없으면 PostgreSQL 검사는 실패하며 조용히 건너뛰지 않는다. 검사별 결과는 Actions Summary에 기록하고 테스트·벤치마크 보고서는 14일간 artifact로 보관한다. 기본 architecture 검사도 테스트 비활성화와 모듈 내부·직접 시간 조회 예외의 재도입을 막는다.
- Java의 최종 포맷 기준은 Spotless의 [Google Java Format](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md#google-java-format)이다. `backend`에서 `./gradlew format`으로 적용하고 `./gradlew spotlessCheck`로 검사한다. CI의 `check`에도 `spotlessCheck`가 연결되며 검사 중 소스를 수정하지 않는다. Spring Java Format, Eclipse formatter XML, `formatAll`은 사용하지 않는다.
- `backend/.editorconfig`는 Java 블록 들여쓰기를 2 spaces로 정의한다. Kotlin Gradle 스크립트는 기존 tab 4, YAML은 2 spaces를 유지하며 Google Java Format의 적용 대상은 Java 소스뿐이다. FQCN 축약 → import 정렬 → 미사용 import 제거 → Google Java Format 순으로 적용한다. import는 static 먼저, 각 그룹에서 세미콜론을 제외한 이름의 사전순으로 정렬한다.
- VS Code의 Java 저장 포맷은 [Spotless Gradle 확장](https://github.com/badsyntax/vscode-spotless-gradle)으로 같은 Gradle 설정을 사용한다. `.vscode/extensions.json`의 Spotless Gradle·Gradle for Java 권장 확장을 설치하고 창을 다시 로드한다. `.vscode/settings.json`은 Red Hat Java 포맷을 끄고 Java 기본 포맷터와 저장 포맷을 Spotless로 지정하며, 중첩 Gradle 프로젝트 `backend`를 탐색 대상으로 둔다. 별도의 Eclipse XML이나 Google formatter 설정은 유지하지 않는다. 기능 변경과 전체 포맷 적용은 별도 커밋으로 나눈다.

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
