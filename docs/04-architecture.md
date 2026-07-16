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
- 입고 관리와 작업 관리가 공통으로 사용하는 포트 실행·농장 배치 UI는 `entities/farm/ui`에 두고 저장 API는 각 `features/*`에서 연결한다.

### Backend

- Spring Boot
- Java 21
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway Migration

### Infra

- Docker Compose
- PostgreSQL
- 운영 초기에는 미니 PC 또는 개인 서버 가능
- 외부 공개 시 Nginx/HTTPS 적용

## 3. 백엔드 구조

MSA가 아니라 **모듈러 모놀리스**로 관리한다.

권장 모듈 경계:

```text
common
farm
work
partner
sales
auction
payment
print
```

### common

- 공통 응답
- 예외 처리
- 공통 유틸
- 공통 검증

### farm

- 동
- 물리 배드
- 논리 구역
- 세그먼트
- 난 묶음
- 품종
- 입고 기록
- 자재
- 배드 정밀 설정
- 위치 이동

### work

- 작업 유형
- 작업 이력
- 이동 작업 이력
- 신규 작업 전체 단위와 상태 전이
- 실제 난 묶음 대상 해석과 스냅샷
- 작업 유형별 효과 handler와 효과 적용 감사 기록
- 자리 이동·분갈이·입고 포트 계획의 대상별 전용 실행 handler
- 원본 대상 없는 작업 효과 실행 facade와 생성 결과 ID 연결
- 기존·신규 난 묶음 이력 통합 조회
- 입고 포트 계획은 `work`의 대상 조회 인터페이스를 `farm`이 구현해 입고 저장소를 작업 모듈에서 직접 참조하지 않는다.

### partner

- 거래처
- 거래처 정산 설정

### sales

- 판매 전표
- 판매 품목
- A5 출력 데이터

### auction

- 경매 lot
- 경매 시도
- 경매 결과 행
- 반환 확인
- 수량 보정
- 경매 정산

### payment

- 수동 입금 확인
- 부분입금
- 거래처 잔액
- 입금 이벤트

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
- 외부로 노출되는 구조는 DTO로 제한한다.

## 5. 프론트엔드 구조

기능 중심 구조를 유지한다.

```text
src/
 ├─ app/        라우트 진입점
 ├─ components/ shadcn/ui 공통 primitives
 ├─ features/   기능 단위 화면/상태/API
 ├─ entities/   도메인 타입
 ├─ lib/        shadcn/ui 공통 유틸
 ├─ shared/     공통 UI/API 유틸
 └─ widgets/    큰 공통 레이아웃
```

원칙:

- `app/*/page.tsx`는 얇게 유지한다.
- 실제 UI와 상태 로직은 `features/*`에 둔다.
- API 타입은 OpenAPI 또는 `entities` 타입과 맞춘다.
- 화면별 복잡한 상태는 페이지 내부에 몰아넣지 않는다.
- AppShell의 버튼, 툴팁, 접이식 메뉴, 모바일 시트는 `components/ui`의 shadcn primitives를 사용한다.

## 6. 데이터 보존 원칙

운영 데이터는 삭제보다 이력 보존을 우선한다.

보존 우선 데이터:

- 작업 이력
- 위치 이동 이력
- 판매 전표
- 경매 lot 상태 이력
- 입금 이벤트
- 원본 가져오기 데이터

삭제가 필요한 경우에도 물리 삭제보다 상태 변경 또는 비활성화를 우선 검토한다.
