# 배포 및 운영

## 1. 로컬 실행

필수 도구:

- Docker / Docker Compose
- JDK 21
- Node.js / npm

실행 흐름:

```bash
# DB 실행
docker compose up -d db

# backend 실행
cd backend
./gradlew bootRun

# frontend 실행
cd frontend
npm install
npm run dev
```

## 2. 환경 변수

### Backend

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_PROFILES_ACTIVE
FILE_STORAGE_PATH
```

인증을 적용하는 경우 다음 값을 별도로 관리한다.

```text
JWT_SECRET 또는 SESSION_SECRET
```

### Frontend

```text
NEXT_PUBLIC_API_BASE_URL
```

## 3. 초기 데이터

초기 seed 기준:

- 15개 동
- 45개 물리 배드
- 90개 기본 논리 구역
- 기본 작업 유형
- 기본 거래처/테스트 데이터

운영 데이터가 들어간 이후에는 seed 재실행으로 기존 데이터가 훼손되지 않도록 주의한다.

## 4. 운영 배포

초기 운영 후보:

- 농장 내부 미니 PC
- 개인 서버
- 소형 클라우드 VM

권장 구성:

```text
Nginx
 ├─ frontend
 └─ backend

PostgreSQL
```

외부 접속이 필요하면 HTTPS를 적용한다. 내부망 전용으로 쓸 경우에도 DB 백업은 반드시 분리 보관한다.

## 5. 백업

PostgreSQL 백업 예시:

```bash
pg_dump -U greenhouse greenhouse > backup_$(date +%Y%m%d).sql
```

권장 정책:

- 매일 1회 백업
- 최근 7일 보관
- 월 1회 장기 보관
- 다른 PC 또는 외장 디스크에 복사
- 복구 테스트 주기적으로 수행

## 6. 운영 전 체크리스트

- [ ] DB 마이그레이션 정상 실행
- [ ] 15개 동/45개 배드/90개 구역 생성 확인
- [ ] 난 묶음 등록/수정/삭제 확인
- [ ] 난 묶음 이동 시 작업 이력 생성 확인
- [ ] 작업 이력 등록/조회 확인
- [ ] 판매 전표 생성/출력 확인
- [ ] 경매 lot 조회/상태 변경 확인
- [ ] 경매 정산 생성/입금 확인 확인
- [ ] 거래처 잔액 조회 확인
- [ ] 백업 파일 생성 확인
- [ ] 서버 재시작 후 데이터 유지 확인

## 7. 장애 대응 원칙

- 운영 DB 직접 수정은 최소화한다.
- 수정 전 백업을 만든다.
- 전표, 작업 이력, 입금 이벤트, 경매 상태 이력은 삭제하지 않는다.
- 잘못된 데이터는 취소/보정 이력으로 처리한다.
