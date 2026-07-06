# 시스템 아키텍처 문서 v2.2

## 1. 기술 스택

이 프로젝트는 단순한 화면 MVP가 아니라 실제 농장에서 장기간 사용할 수 있는 운영 시스템을 목표로 한다.

따라서 빠른 개발도 중요하지만, 판매 전표·작업 이력·난 묶음 위치 데이터가 장기간 안정적으로 쌓일 수 있도록 프론트엔드와 백엔드를 분리한 구조로 시작한다. v2.2에서는 농장 현황 조회용 전체맵과 난 묶음 관리용 상세맵의 역할을 분리한다.

### Frontend

- Next.js
- TypeScript
- Tailwind CSS
- lucide-react 아이콘
- HTML5 Drag and Drop API

현재 MVP 구현은 React Hook Form, Zod, dnd-kit을 사용하지 않고 React 상태와 브라우저 기본 드래그 앤 드롭으로 구성한다. 입력 검증은 백엔드 Bean Validation과 프론트의 기본 필수 입력을 조합한다.

### Backend

- Spring Boot
- Java 21
- Spring Data JPA
- Bean Validation
- PostgreSQL

현재 MVP 구현에서는 인증/권한을 적용하지 않는다. Spring Security 기반 로그인은 운영 전 별도 단계로 둔다.

### Infra

- Docker Compose
- PostgreSQL
- 로컬 파일 저장소
- 추후 MinIO 또는 S3 호환 스토리지 확장

---

## 2. 전체 구조

```text
farm-management/
 ├─ frontend/
 │   └─ Next.js
 ├─ backend/
 │   └─ Spring Boot
 ├─ docker-compose.yml
 └─ docs/
```

---

## 3. 백엔드 구조

MSA가 아니라 모듈러 모놀리스로 시작한다.

```text
backend
 ├─ farm          동/물리 배드/논리 구역/난 묶음 관리
 ├─ work          농약/비료/분갈이/정리/위치 이동 작업 이력
 ├─ sales         판매 전표/거래처
 ├─ print         출력용 데이터 조회
 └─ common        공통 응답, 예외, 유틸
```

### 계층 구조

```text
controller
application/service
domain/entity
repository
dto
```

권장 원칙:

- Controller는 요청/응답 처리만 담당한다.
- Service는 유스케이스와 트랜잭션을 담당한다.
- Entity는 DB 매핑과 기본 도메인 규칙을 가진다.
- Repository는 데이터 접근만 담당한다.

### 주요 유스케이스

```text
CreateOrchidGroupUseCase
MoveOrchidGroupUseCase
RegisterWorkRecordUseCase
CreateSalesSlipUseCase
PrintSalesSlipUseCase
```

`MoveOrchidGroupUseCase`는 다음을 하나의 트랜잭션으로 처리한다.

```text
난 묶음 위치 변경
대상 구역 내 sort_order 조정
위치 이동 작업 이력 생성
```

---

## 4. 프론트엔드 구조

[프론트엔드-구조 참고](../../green-house/frontend\src\README.md)

### 농장맵 상태 관리

농장 현황 화면과 난 묶음 관리 화면의 상태를 분리한다.

농장 현황 화면 상태:

```text
zoomLevel: FARM | HOUSE | PHYSICAL_BED | BED_ZONE
selectedTargetType
selectedTargetId
selectedHouseId
selectedPhysicalBedId
selectedBedZoneId
```

난 묶음 관리 화면 상태:

```text
selectedHouseId
selectedPhysicalBedId
selectedBedZoneId
selectedOrchidGroupId
editMode
viewMode: REAL_DIRECTION | ROTATED | BY_BED
draggingOrchidGroupId
moveDestinationPickerOpen
```

---

## 5. 화면별 맵 설계

### 농장 현황 맵

농장 현황 맵은 전체 조회용이다. 동, 물리 배드, 논리 구역을 선택하면 선택 범위에 포함된 난 묶음 목록을 조회한다. 이 화면에서는 난 묶음 생성, 수정, 이동을 직접 수행하지 않는다.

### 난 묶음 관리 맵

난 묶음 관리 맵은 작업용 상세맵이다. 선택한 동의 물리 배드 3개를 실제 농장 기준 좌→우로 표시하고, 난 묶음 생성·수정·삭제·이동을 수행한다.

## 6. 드래그 앤 드롭 설계

MVP에서는 난 묶음 전체 이동만 지원한다. 같은 동 내부 이동은 드래그 앤 드롭으로 처리하고, 다른 동 이동은 목적지 선택 방식으로 처리한다.

같은 동 내부 프론트엔드 동작:

```text
배치 수정 모드 확인
난 묶음 블록 드래그 시작
같은 동 안의 대상 논리 구역에 드롭
이동 확인 창 표시
사용자 확인
POST /api/orchid-groups/{id}/move 호출
난 묶음 관리 상세맵 데이터 갱신
```

다른 동 이동 프론트엔드 동작:

```text
난 묶음 블록 선택
[다른 위치로 이동] 클릭
목적 동 선택
목적 물리 배드 선택
목적 논리 구역 선택
이동 확인 창 표시
사용자 확인
POST /api/orchid-groups/{id}/move 호출
난 묶음 관리 상세맵 데이터 갱신
```

백엔드 동작:

```text
난 묶음 존재 확인
대상 논리 구역 존재 확인
기존 구역 기록
난 묶음 bed_zone_id 변경
대상 구역 sort_order 마지막으로 설정
위치 이동 work_record 생성
트랜잭션 커밋
```

---

## 7. 인증 방식

MVP에서는 복잡한 권한 관리를 제외한다.

초기 방식:

- 단일 관리자 계정
- 로그인 세션 기반 인증
- 내부망 또는 제한된 사용자 환경을 전제로 함

향후 확장:

- 사용자별 권한
- 작업자별 기록
- 읽기 전용 계정
- 관리자 계정

---

## 8. 파일 저장 방식

MVP에서는 실제 사진 업로드를 제외하거나 로컬 파일 저장소로 시작한다.

향후 사진 기록 기능이 추가되면 다음 중 하나를 사용한다.

- 로컬 파일 저장소
- MinIO
- S3 호환 스토리지

DB에는 파일 경로, 원본 파일명, MIME 타입, 연결 대상 정보를 저장한다.

---

## 9. 예외 처리

공통 에러 응답 형식을 사용한다.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": []
  }
}
```

예외 유형:

- Validation Error
- Not Found
- Conflict
- Unauthorized
- Internal Server Error

난 묶음 이동 관련 예외:

- 이동 대상 난 묶음 없음
- 이동 대상 논리 구역 없음
- 비활성 구역으로 이동 시도
- 수량이 0인 난 묶음 이동 시도
