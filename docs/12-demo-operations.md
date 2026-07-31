# 데모 환경 운영 PC 작업

이 문서는 운영 PC에서 관리자가 직접 수행해야 하는 데모 인프라 작업만 다룬다.
애플리케이션 구현과 Kubernetes 매니페스트의 기준은 저장소 코드를 따른다.

## 1. 최종 구성

```text
PostgreSQL instance
├─ greenhouse
│  └─ 기존 운영 계정
└─ greenhouse_demo
   └─ greenhouse_demo (DB owner, Flyway, API)

k3s
├─ green-house
└─ green-house-demo
```

운영과 데모는 같은 PostgreSQL 프로세스를 사용하지만 DB, 로그인 role, 비밀번호,
Kubernetes Secret을 공유하지 않는다. 이 구성은 권한을 논리적으로 격리하지만
CPU, 메모리, 디스크 I/O 장애까지 격리하지는 않는다.

## 2. 사전 준비

- 데모 도메인과 TLS 준비
- PostgreSQL 관리자 접속 정보 준비
- k3s의 PostgreSQL host IP 확인
- 비식별화 검증이 완료된 custom-format dump 준비
- `kubectl`, `psql`, `pg_restore`, `createdb`, `dropdb`, `flock` 설치

운영 원본 백업은 `greenhouse_demo`에 직접 복구하지 않는다. 격리된 작업 환경에서
비식별화와 검증이 끝난 dump만 운영 PC로 반입한다.

## 3. PostgreSQL role과 DB 생성

관리자 계정으로 접속해 데모 전용 role과 DB만 추가한다. 기존 `greenhouse` DB와
운영 계정은 이름이나 소유권을 변경하지 않는다.

```sql
CREATE ROLE greenhouse_demo
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION INHERIT
  CONNECTION LIMIT 10;

CREATE DATABASE greenhouse_demo OWNER greenhouse_demo;

REVOKE ALL ON DATABASE greenhouse_demo FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse_demo;

REVOKE CONNECT ON DATABASE greenhouse FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse TO greenhouse;
```

비밀번호는 SQL 파일이나 shell history에 기록하지 않고 `psql`에서 설정한다.

```text
\password greenhouse_demo
```

데모 DB에 접속해 schema 권한을 설정한다. `greenhouse_demo`가 DB owner이므로
Flyway와 API 읽기·쓰기에 같은 계정을 사용한다.

```sql
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO greenhouse_demo;
```

## 4. 데모 DB 자원 제한

```sql
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET statement_timeout = '15s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET lock_timeout = '3s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET temp_file_limit = '128MB';
```

설정 확인:

```sql
SELECT datname, datacl
FROM pg_database
WHERE datname IN ('greenhouse', 'greenhouse_demo');

SELECT rolname, rolconnlimit, rolsuper, rolcreatedb, rolcreaterole
FROM pg_roles
WHERE rolname LIKE 'greenhouse_%';
```

운영 role로 `greenhouse_demo`, 데모 role로 `greenhouse` 접속이 모두 실패하는지
별도로 확인한다. `REVOKE CONNECT`는 이미 열린 연결을 종료하지 않는다.

## 5. pg_hba.conf 제한

일반적인 광범위 허용 규칙보다 위에 DB와 사용자 조합 제한을 둔다. `<k3s-pod-cidr>`는
실제 Pod CIDR로 교체한다.

```text
host  greenhouse       greenhouse                 <k3s-pod-cidr>  scram-sha-256
host  greenhouse_demo  greenhouse                 <k3s-pod-cidr>  scram-sha-256
```

설정 변경 후 reload하고 허용·거부 조합을 모두 접속 테스트한다.

## 6. Kubernetes demo overlay 설정

다음 파일의 환경별 값을 운영 PC 기준으로 확인한다.

- `k8s/overlays/demo/configmap-patch.yaml`: 데모 도메인과 DB 이름
- `k8s/overlays/demo/kustomization.yaml`: 데모 namespace와 도메인
- `k8s/overlays/demo/network-policy.yaml`: PostgreSQL host IP
- `k8s/overlays/demo/postgres-endpoints-patch.yaml`: PostgreSQL host IP

데모 namespace와 pull secret을 준비한다.

```bash
kubectl create namespace green-house-demo

kubectl -n green-house-demo create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<github-user> \
  --docker-password=<github-token> \
  --docker-email=<email>
```

데모 overlay는 저장소의 placeholder Secret을 배포 대상에서 제거한다. 실제 Secret을
별도로 만든다.

```bash
read -rs DEMO_APP_PASSWORD

kubectl -n green-house-demo create secret generic green-house-secret \
  --from-literal=DATABASE_PASSWORD="${DEMO_APP_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

unset DEMO_APP_PASSWORD
```

렌더링과 적용:

```bash
kubectl kustomize k8s/overlays/demo
kubectl apply -k k8s/overlays/demo
kubectl -n green-house-demo rollout status deployment/green-house-backend
kubectl -n green-house-demo rollout status deployment/green-house-frontend
```

운영 환경에서는 가능하면 비공개 overlay 또는 Secret 관리 도구로 같은 값을 관리한다.

운영에서 검증한 동일 이미지 SHA를 데모에 배포할 때는 공통 배포 스크립트의 namespace와
URL만 바꾼다.

```bash
NAMESPACE=green-house-demo \
APP_URL=https://green-house-demo.sjw-project.site \
./scripts/deploy/deploy.sh sha-<commit>
```

## 7. 운영 데이터 비식별화

운영 DB에는 비식별화 스크립트를 직접 실행하지 않는다. PostgreSQL 외부 접속이 차단된
격리 작업 PC 또는 격리 컨테이너에서 다음 순서로 처리한다. `psql`, `pg_dump`,
`pg_restore`, Flyway CLI는 운영 PostgreSQL과 같은 major 버전을 사용한다.
Python 3와 PostgreSQL 드라이버도 준비한다.

```bash
python3 -m pip install -r scripts/demo/requirements.txt
```

```text
운영 custom-format dump
→ greenhouse_demo_sanitize_tmp 복구
→ 최신 Flyway 적용
→ 스키마 허용 목록 확인
→ 비식별화
→ 민감정보·외래키·업무 불변식 검사
→ 허용된 테이블만 custom-format dump
→ SHA-256 생성
```

현재 허용 목록과 스키마 지문은 `scripts/demo/schema-allowlist.tsv`에 있다. 신규
테이블, 신규 컬럼, nullable/type 변경이 있으면 dump 생성이 실패한다. 검토 후
비식별화 Python 스크립트와 허용 목록을 함께 갱신한다.

격리 임시 DB 복구:

```bash
export SANITIZE_DB_ADMIN_URL='postgresql://<local-admin>@127.0.0.1:5432/postgres'
export SANITIZE_DB_URL='postgresql://<local-admin>@127.0.0.1:5432/greenhouse_demo_sanitize_tmp'
export SANITIZE_DB_NAME='greenhouse_demo_sanitize_tmp'
export SANITIZE_RESTORE_CONFIRM='greenhouse_demo_sanitize_tmp'

export SANITIZE_FLYWAY_URL='jdbc:postgresql://127.0.0.1:5432/greenhouse_demo_sanitize_tmp'
export SANITIZE_FLYWAY_USER='<local-admin>'
read -rs SANITIZE_FLYWAY_PASSWORD
export SANITIZE_FLYWAY_PASSWORD

./scripts/demo/restore-temp-db.sh /secure/source/greenhouse-production.dump
```

비식별화와 검증 후 dump 생성:

```bash
export SANITIZE_DUMP_CONFIRM='greenhouse_demo_sanitize_tmp'

read -rs DEMO_ANONYMIZATION_KEY
export DEMO_ANONYMIZATION_KEY
read -r DEMO_DATE_SHIFT_DAYS
export DEMO_DATE_SHIFT_DAYS
read -r DEMO_QUANTITY_FACTOR
export DEMO_QUANTITY_FACTOR
read -r DEMO_PRICE_FACTOR
export DEMO_PRICE_FACTOR

./scripts/demo/create-demo-dump.sh \
  /secure/demo/greenhouse_demo_sanitized.dump
```

생성 결과는 dump와 같은 디렉터리의 `.sha256` 파일이다. Python 변환이나 SQL 검증,
스키마·민감정보·외래키·수량·판매·정산 검증 중 하나라도 실패하면 dump가 생성되지
않는다.
비식별화가 시작된 임시 DB에는 내부 완료 마커가 남는다. 같은 임시 DB에 스크립트를
다시 실행하면 수량과 금액이 중복 변환되므로 실행 전에 차단된다. 실패 후 재시도할
때도 반드시 운영 원본 dump에서 `greenhouse_demo_sanitize_tmp`를 다시 복구한다.
비식별화 키는 32자 이상이어야 하며 수량·단가 배율은 각각 2~9 범위에서 선택한다.
날짜 이동값은 0이 아닌 정수다. 값은 저장소나 shell history에 남기지 않고 운영
비밀 저장소에서 공급한다. 같은 키를 사용하면 같은 원본 문자열이 같은 데모 값으로
변환된다. 정수 범위를 넘을 가능성이 있으면 Python 스크립트가 관계를 유지할 수 있는
범위까지 배율을 자동으로 낮추고 실제 적용한 배율을 출력한다.

변환 규칙:

- ID, 외래키, 구역 배치와 상태 이력은 유지
- 날짜와 audit timestamp는 모두 동일 일수만큼 이동
- 원본 작업 이력의 종료시각이 시작시각보다 이르면 데모에서는 시작시각으로 정규화
- 작업자와 입금자는 keyed HMAC 순서에 따른 결정적 데모명으로 치환
- 거래처는 유형별 ID 순서에 따라 소매·도매·경매장이 각각 `001`부터 시작하는
  데모명으로 치환하고, 자재명은 keyed HMAC 기반 데모명으로 치환
- `scripts/demo/item-varieties.json`의 실제 경매 속·품종 조합을 품종 마스터에
  중복 없이 결정적으로 배정
- 난 묶음, 작업 대상 스냅샷, 판매 품목, 경매 lot에도 같은 방식의 실제 속·품종
  조합을 반영
- 수량과 단가는 외부 주입 배율로 변환하고 연관 금액은 두 배율의 곱으로 변환
- 품목 금액과 판매·경매·정산 합계는 변환된 수량과 단가에서 다시 계산
- 전화번호는 고정값, 주소는 데모 주소, 자유 메모와 외부 UID는 제거
- JSON 구조와 숫자·불리언·허용 코드값은 유지하고 나머지 문자열은 `데모`로 치환

작업 성공 후 `greenhouse_demo_sanitize_tmp`를 삭제하고 운영 원본 dump를 보안 삭제한다.
삭제 전 데모 dump와 SHA-256을 별도 보관 위치에 복사하고 `pg_restore --list`로 다시
확인한다. 원본 dump 경로는 자동 삭제 대상이 아니므로 운영자가 정확한 파일을 확인한
뒤 삭제한다.

## 8. 초기 데이터 복구

초기화 스크립트는 DB 이름이 정확히 `greenhouse_demo`일 때만 실행된다. 백엔드를
중지하고 DB를 다시 만든 뒤 dump 복구, 권한 적용, Flyway 시작, health check를
수행한다.

```bash
export DEMO_DB_ADMIN_URL='postgresql://<admin>@127.0.0.1:5432/postgres'
export DEMO_DB_TARGET_URL='postgresql://greenhouse_demo@127.0.0.1:5432/greenhouse_demo'
export DEMO_RESET_CONFIRM='greenhouse_demo'

./scripts/demo/reset-demo-db.sh /secure/demo/greenhouse_demo_sanitized.dump
```

초기화 스크립트는 dump 옆의 `.sha256` 파일을 반드시 검증한다. 비밀번호는 `.pgpass`
또는 운영 PC의 안전한 비밀 저장소로 공급한다. 스크립트와 운영 배포는 기본적으로
`/tmp/green-house-operation.lock`을 공유해 동시에 실행되지 않는다. 운영 PC가 여러
대라면 로컬 파일 잠금만으로 부족하므로 Kubernetes Lease 잠금을 추가해야 한다.

Cron 등록 전 동일 명령을 수동 실행하고 복구·health check를 확인한다. 기본 주기는
하루 1회이며 저사용 시간에 실행한다.

## 9. 모니터링

필수 경고 항목:

- 전체 디스크 사용률 70%, 80%
- `pg_database_size('greenhouse_demo')`
- WAL과 임시 파일 증가량
- DB별 연결 수
- 15초에 가까운 장기 쿼리
- lock 대기와 idle transaction
- 운영 API 응답시간
- 데모 HTTP 429, 413 응답 수

데모 DB 복구 중 운영 API 지연이 커지면 복구를 중단하고 `pg_restore --jobs=1` 유지,
실행 시간 조정 또는 별도 PostgreSQL 인스턴스 전환을 검토한다.

## 10. 배포 후 검증

- 데모 URL이 로그인 없이 열림
- `/api/auth/me`가 `demo`, `DEMO`를 반환
- 운영 URL은 로그인 없이 열리지 않음
- 데모 작업 유형·정산 설정 변경 API가 `403` 반환
- 데모에서 입력한 작업자명이 `demo`로 저장
- 데모 role의 운영 DB 접속 실패
- 운영 role의 데모 DB 접속 실패
- 실제 개인정보·계좌·연락처·자유 메모가 남아 있지 않음
- 초기화 후 난 묶음, 작업, 판매·경매·정산 관계 정상
