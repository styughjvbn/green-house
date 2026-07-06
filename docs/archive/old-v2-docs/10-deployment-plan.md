# 배포 계획서 v2

## 1. 개발 환경

로컬 개발은 Docker Compose 기반으로 구성한다.

구성 요소:

```text
frontend: Next.js
backend: Spring Boot
database: PostgreSQL
```

---

## 2. Docker Compose 구성

초기 구성:

```text
frontend
backend
postgres
```

추후 사진 저장 기능이 추가되면 다음을 추가할 수 있다.

```text
minio
```

---

## 3. 환경 변수

### Backend

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_PROFILES_ACTIVE
JWT_SECRET 또는 SESSION_SECRET
FILE_STORAGE_PATH
```

### Frontend

```text
NEXT_PUBLIC_API_BASE_URL
```

---

## 4. 운영 서버

초기 운영은 농장 내부 미니 PC 또는 개인 서버를 고려한다.

권장 방식:

```text
Docker Compose로 frontend/backend/postgres 실행
내부망 접속 우선
필요 시 외부 도메인과 HTTPS 연결
```

농장맵 기반 조작은 PC/태블릿 환경에서 원활해야 하므로, 운영 전 내부망 태블릿 접속 테스트를 수행한다.

---

## 5. 백업 방침

PostgreSQL 데이터는 정기 백업이 필요하다.

권장 백업:

- 매일 1회 DB dump
- 최근 7일 백업 보관
- 월 1회 장기 백업 보관
- 외장 디스크 또는 다른 PC에 백업 복사

예시:

```bash
pg_dump -U farm_user farm_db > backup_$(date +%Y%m%d).sql
```

---

## 6. 장애 대응 기본 방침

- DB 백업 파일이 존재하는지 주기적으로 확인한다.
- 배포 전 DB 마이그레이션 내용을 확인한다.
- 운영 데이터가 들어간 이후에는 직접 DB 수정은 최소화한다.
- 전표와 작업 이력은 삭제보다 상태 변경 방식을 우선한다.
- 난 묶음 이동 이력은 삭제하지 않는다.
- 판매 수량 차감 기능이 추가된 이후에는 전표 취소/출고 취소 복구 절차를 별도로 검증한다.

---

## 7. 운영 전 체크리스트

- [ ] 15개 동이 생성되어 있는가
- [ ] 45개 물리 배드가 생성되어 있는가
- [ ] 90개 기본 논리 구역이 생성되어 있는가
- [ ] 농장맵에서 논리 구역 선택이 가능한가
- [ ] 난 묶음 등록이 가능한가
- [ ] 난 묶음 이동이 가능한가
- [ ] 난 묶음 이동 시 위치 이동 이력이 생성되는가
- [ ] 작업 이력 목록과 상세 조회가 가능한가
- [ ] 작업 이력 등록이 가능한가
- [ ] 판매 전표 등록이 가능한가
- [ ] 판매 전표 목록과 상세 조회가 가능한가
- [ ] 전표 A5 출력이 가능한가
- [ ] DB 백업 명령이 정상 동작하는가
- [ ] 서버 재시작 후 데이터가 유지되는가
- [ ] 외부 접속이 필요한 경우 HTTPS가 적용되어 있는가
