# 데모 환경 운영 PC 작업

이 문서는 운영 PC에서 관리자가 직접 수행해야 하는 데모 인프라 작업만 다룬다.
애플리케이션 구현과 Kubernetes 매니페스트의 기준은 저장소 코드를 따른다.

## 1. 최종 구성

```text
PostgreSQL instance
├─ greenhouse
│  └─ 기존 운영 계정
└─ greenhouse_demo
   ├─ greenhouse_demo_app
   └─ greenhouse_demo_migrator

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
CREATE ROLE greenhouse_demo_app
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT
  CONNECTION LIMIT 10;

CREATE ROLE greenhouse_demo_migrator
  LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT
  CONNECTION LIMIT 2;

CREATE DATABASE greenhouse_demo OWNER greenhouse_demo_migrator;

REVOKE ALL ON DATABASE greenhouse_demo FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse_demo_app;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse_demo_migrator;

REVOKE CONNECT ON DATABASE greenhouse FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse TO greenhouse;
```

비밀번호는 SQL 파일이나 shell history에 기록하지 않고 `psql`에서 설정한다.

```text
\password greenhouse_demo_app
\password greenhouse_demo_migrator
```

데모 DB에 접속해 schema 권한과 기본 권한을 설정한다.

```sql
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO greenhouse_demo_app;
GRANT USAGE, CREATE ON SCHEMA public TO greenhouse_demo_migrator;

ALTER DEFAULT PRIVILEGES FOR ROLE greenhouse_demo_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO greenhouse_demo_app;

ALTER DEFAULT PRIVILEGES FOR ROLE greenhouse_demo_migrator IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO greenhouse_demo_app;
```

## 4. 데모 DB 자원 제한

```sql
ALTER ROLE greenhouse_demo_app IN DATABASE greenhouse_demo
  SET statement_timeout = '15s';
ALTER ROLE greenhouse_demo_app IN DATABASE greenhouse_demo
  SET lock_timeout = '3s';
ALTER ROLE greenhouse_demo_app IN DATABASE greenhouse_demo
  SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE greenhouse_demo_app IN DATABASE greenhouse_demo
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
host  greenhouse_demo  greenhouse_demo_app       <k3s-pod-cidr>  scram-sha-256
host  greenhouse_demo  greenhouse_demo_migrator  <k3s-pod-cidr>  scram-sha-256
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
read -rs DEMO_MIGRATOR_PASSWORD

kubectl -n green-house-demo create secret generic green-house-secret \
  --from-literal=DATABASE_PASSWORD="${DEMO_APP_PASSWORD}" \
  --from-literal=FLYWAY_PASSWORD="${DEMO_MIGRATOR_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

unset DEMO_APP_PASSWORD DEMO_MIGRATOR_PASSWORD
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

## 7. 초기 데이터 복구

초기화 스크립트는 DB 이름이 정확히 `greenhouse_demo`일 때만 실행된다. 백엔드를
중지하고 DB를 다시 만든 뒤 dump 복구, 권한 적용, Flyway 시작, health check를
수행한다.

```bash
export DEMO_DB_ADMIN_URL='postgresql://<admin>@127.0.0.1:5432/postgres'
export DEMO_DB_TARGET_URL='postgresql://greenhouse_demo_migrator@127.0.0.1:5432/greenhouse_demo'
export DEMO_RESET_CONFIRM='greenhouse_demo'
export DEMO_SANITIZATION_VERIFIED='true'

./scripts/demo/reset-demo-db.sh /secure/demo/greenhouse_demo_sanitized.dump
```

비밀번호는 `.pgpass` 또는 운영 PC의 안전한 비밀 저장소로 공급한다. 스크립트와 운영
배포는 기본적으로 `/tmp/green-house-operation.lock`을 공유해 동시에 실행되지 않는다.
운영 PC가 여러 대라면 로컬 파일 잠금만으로 부족하므로 Kubernetes Lease 잠금을
추가해야 한다.

Cron 등록 전 동일 명령을 수동 실행하고 복구·health check를 확인한다. 기본 주기는
하루 1회이며 저사용 시간에 실행한다.

## 8. 모니터링

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

## 9. 배포 후 검증

- 데모 URL이 로그인 없이 열림
- `/api/auth/me`가 `demo`, `DEMO`를 반환
- 운영 URL은 로그인 없이 열리지 않음
- 데모 작업 유형·정산 설정 변경 API가 `403` 반환
- 데모에서 입력한 작업자명이 `demo`로 저장
- 데모 role의 운영 DB 접속 실패
- 운영 role의 데모 DB 접속 실패
- 실제 개인정보·계좌·연락처·자유 메모가 남아 있지 않음
- 초기화 후 난 묶음, 작업, 판매·경매·정산 관계 정상
