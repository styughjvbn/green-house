# 데모 환경 운영 PC 작업

이 문서는 운영 PC에서 관리자가 직접 수행해야 하는 데모 인프라 작업만 다룬다.
애플리케이션 구현과 Kubernetes 매니페스트의 기준은 저장소 코드를 따른다.

## 1. 최종 구성

```text
PostgreSQL instance
├─ greenhouse
│  └─ 기존 운영 계정
├─ greenhouse_demo
│  └─ greenhouse_demo (DB owner, Flyway, API)
└─ greenhouse_demo_template
   └─ 빈 후보 DB 생성 전용 template

k3s
├─ green-house
└─ green-house-demo
```

운영과 데모는 같은 PostgreSQL 프로세스를 사용하지만 DB, 쓰기 role, 비밀번호,
Kubernetes Secret을 공유하지 않는다. 운영 `greenhouse` role에는 조사 목적의 demo 읽기
권한만 별도로 준다. 이 구성은 권한을 논리적으로 격리하지만
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
  CONNECTION LIMIT 20;

CREATE ROLE greenhouse_demo_refresh
  LOGIN NOSUPERUSER CREATEDB NOCREATEROLE NOREPLICATION INHERIT
  CONNECTION LIMIT 1;

GRANT greenhouse_demo TO greenhouse_demo_refresh;
GRANT pg_signal_backend TO greenhouse_demo_refresh;

-- superuser 전용 설정이므로 관리자가 role 전체에 최초 1회 적용한다.
ALTER ROLE greenhouse_demo SET temp_file_limit = '128MB';

CREATE DATABASE greenhouse_demo OWNER greenhouse_demo;

-- 다음 명령은 CREATE DATABASE와 분리해서 실행한다.
CREATE DATABASE greenhouse_demo_template
  WITH TEMPLATE template0 OWNER greenhouse_demo;

REVOKE ALL ON DATABASE greenhouse_demo FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse_demo;
GRANT CONNECT ON DATABASE greenhouse_demo TO greenhouse;

REVOKE ALL ON DATABASE greenhouse_demo_template FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse_demo_template TO greenhouse_demo_refresh;

REVOKE CONNECT ON DATABASE greenhouse FROM PUBLIC;
GRANT CONNECT ON DATABASE greenhouse TO greenhouse;
```

`greenhouse_demo_refresh`는 DB 생성·이름 교체와 demo connection 종료만 담당하는 자동화
role이다. PostgreSQL `postgres` role에 LOGIN이나 비밀번호를 추가하지 않는다. 비밀번호는
SQL 파일이나 shell history에 기록하지 않고 `psql`에서 설정한다.

PostgreSQL 14에서는 새 DB의 `public` schema가 `postgres` 소유로 복제될 수 있다. 후보 DB를
최소권한 role로 안전하게 초기화할 수 있도록 `greenhouse_demo_template`에 접속해 다음을
한 번 적용한다. 이 DB에는 업무 table이나 데이터를 만들지 않는다.

```sql
\connect greenhouse_demo_template

ALTER SCHEMA public OWNER TO greenhouse_demo;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO greenhouse_demo;
```

```text
\password greenhouse_demo
\password greenhouse_demo_refresh
```

운영 계정 `sjw`의 `/home/sjw/.pgpass`에는 아래 네 접속을 등록하고 mode를 `0600`으로 둔다.
비밀번호에 `:` 또는 `\`가 있으면 `.pgpass` 형식에 맞게 `\`로 escape한다.

```text
127.0.0.1:5432:greenhouse:greenhouse:<production-password>
127.0.0.1:5432:postgres:greenhouse_demo_refresh:<refresh-password>
127.0.0.1:5432:greenhouse_demo:greenhouse_demo:<demo-password>
127.0.0.1:5432:greenhouse_demo_next:greenhouse_demo:<demo-password>
```

```bash
chmod 600 /home/sjw/.pgpass
```

데모 DB에 접속해 schema 권한을 설정한다. `greenhouse_demo`가 DB owner이므로
Flyway와 API 읽기·쓰기에 같은 계정을 사용한다.

```sql
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO greenhouse_demo;
GRANT USAGE ON SCHEMA public TO greenhouse;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO greenhouse;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO greenhouse;
ALTER DEFAULT PRIVILEGES FOR ROLE greenhouse_demo IN SCHEMA public
  GRANT SELECT ON TABLES TO greenhouse;
ALTER DEFAULT PRIVILEGES FOR ROLE greenhouse_demo IN SCHEMA public
  GRANT SELECT ON SEQUENCES TO greenhouse;
```

## 4. 데모 DB 자원 제한

```sql
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET statement_timeout = '15s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET lock_timeout = '3s';
ALTER ROLE greenhouse_demo IN DATABASE greenhouse_demo
  SET idle_in_transaction_session_timeout = '60s';
```

`temp_file_limit`은 일반 role이 변경할 수 없는 PostgreSQL superuser 설정이다. 위의 role 전체
설정을 최초 1회 관리자가 적용하며, 리프레시 스크립트는 값을 변경하지 않고 `128MB`인지
검증한다. 따라서 새로 만드는 `greenhouse_demo_next`에도 같은 제한이 자동 상속된다.

데모 백엔드는 Pod당 Hikari 연결을 최대 5개만 사용한다. 다만 RollingUpdate 중에는
구버전·신버전 Pod와 Flyway 연결이 잠시 겹치므로 role 연결 제한은 20개로 둔다.

설정 확인:

```sql
SELECT datname, datacl
FROM pg_database
WHERE datname IN ('greenhouse', 'greenhouse_demo', 'greenhouse_demo_template');

SELECT rolname, rolconnlimit, rolsuper, rolcreatedb, rolcreaterole
FROM pg_roles
WHERE rolname LIKE 'greenhouse_%';
```

운영 role로 `greenhouse_demo`를 읽을 수 있지만 쓰기는 실패하고, 데모 role로
`greenhouse` 접속은 실패하는지 확인한다. `REVOKE CONNECT`는 이미 열린 연결을 종료하지 않는다.

## 5. pg_hba.conf 제한

일반적인 광범위 허용 규칙보다 위에 DB와 사용자 조합 제한을 둔다. `<k3s-pod-cidr>`는
실제 Pod CIDR로 교체한다.

```text
host  greenhouse       greenhouse_demo  <k3s-pod-cidr>  reject
host  greenhouse       greenhouse       <k3s-pod-cidr>  scram-sha-256
host  greenhouse_demo  greenhouse       <k3s-pod-cidr>  reject
host  greenhouse_demo  greenhouse_demo  <k3s-pod-cidr>  scram-sha-256
```

운영 `greenhouse` role의 demo 조회는 운영 PC가 속한 별도 관리망 HBA 규칙을 통해서만
허용한다. 운영 backend Pod가 demo DB에 연결할 이유는 없으므로 Pod CIDR에서는 차단한다.
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

데모 overlay에는 이미지 tag를 고정하지 않는다. overlay로 namespace·ConfigMap·네트워크를
적용한 뒤 이미지 교체는 항상 공통 배포 스크립트에 실제 발행된 tag를 전달한다.

```bash
NAMESPACE=green-house-demo \
APP_URL=https://green-house-demo.sjw-project.site \
./scripts/deploy/deploy.sh sha-xxxx
```

## 7. 정기 데이터 리프레시

공개 데모는 운영 배포 사전 검증 환경이 아니다. 운영 DB에 현재 릴리스의 Flyway migration이
완료된 뒤에만 dump를 만들며, dump 안의 `flyway_schema_history`를 그대로 보존한다. 따라서
비식별 DB에서 V27 같은 운영 데이터 의존 migration을 다시 실행하지 않는다. 코드의 migration
파일과 checksum이 다르거나 최신 버전이 아니면 `flyway validate` 단계에서 중단한다.

전체 흐름은 다음과 같다.

```text
greenhouse dump → 격리 PostgreSQL 복원 → Flyway/schema 확인 → 전체 이력 비식별화
→ dump/SHA-256 → 새 격리 DB 재복원 검증 → greenhouse_demo_next 복원·검증
→ backend 정지 → greenhouse_demo→greenhouse_demo_prev
→ greenhouse_demo_next→greenhouse_demo → 권한 → backend·smoke
```

비식별화는 행을 삭제하거나 축약하지 않는다. Work operation/target/execution/effect/receipt,
이동·lineage, 판매 slip/item/allocation/inventory movement/snapshot, 경매 shipment/lot/attempt/
result/status history, 정산/payment/balance, audit event와 OrchidGroup mutation/entry/relation/
coverage의 ID·FK·행 순서·revision chain·mutation/correlation 연결을 보존한다. 작업자·입금자·
거래처·연락처·자유문자열은 제거 또는 결정적 데모 값으로 바꾸며 날짜는 같은 일수만큼,
연결된 수량·금액과 JSON snapshot은 같은 배율로 바꾼다. opaque fingerprint와 idempotency key는
참조 안정성을 위해 보존하고 ACTIVE baseline fingerprint는 변환된 snapshot으로 다시 계산한다.
작업 타입의 code·name과 `work_records.work_type`은 개인정보가 아닌 업무 분류 기준이므로
사용자 정의 타입을 포함해 원문을 보존한다.

비식별화 key와 날짜 이동값은 실행마다 바꾸지 않는다. 32자 이상의 key, 0이 아닌 날짜 이동,
수량·가격 배율 2~9를 `/etc/green-house/demo-refresh.env` 같은 root 관리 파일에 고정한다.
원본 dump는 mode 0700 임시 디렉터리에만 만들고 성공·실패와 관계없이 즉시 제거한다.

수동 전체 실행:

```bash
sudo install -d -o root -g sjw -m 750 /etc/green-house
sudo install -d -o sjw -g sjw -m 700 /opt/green-house/backups/demo-sanitized
sudo -u sjw install -d -m 700 /home/sjw/green-house-demo-refresh-staging
sudo install -o root -g sjw -m 640 deploy/systemd/demo-refresh.env.example \
  /etc/green-house/demo-refresh.env
sudoedit /etc/green-house/demo-refresh.env

set -a
. /etc/green-house/demo-refresh.env
set +a
./scripts/demo/scheduled-demo-refresh.sh
```

`/opt/green-house/backups/local`의 운영 원본 backup과 비식별 dump를 섞지 않는다. 정기
리프레시 산출물은 `/opt/green-house/backups/demo-sanitized`에만 저장한다. snap Docker는
`/opt`를 bind mount할 수 없으므로 원본 dump와 Docker 출력은
`DEMO_DOCKER_STAGING_DIR=/home/sjw/green-house-demo-refresh-staging`에서 임시 처리한다.
SHA-256 검증을 마친 비식별 dump만 호스트에서 `/opt`로 게시하며 staging 파일은 성공·실패와
관계없이 제거한다.

생성 단계만 실행하려면 `create-sanitized-demo-dump.sh <output.dump>`, 이미 검증된 dump를
승격하려면 `refresh-demo-db.sh <dump>`를 사용한다. 후보 복원과 모든 검증은 현재 데모가
실행되는 동안 수행하며 실제 DB 이름 교체 구간에만 backend를 중지한다. 세 DB 이름과
`DEMO_REFRESH_CONFIRM=greenhouse_demo:greenhouse_demo_next:greenhouse_demo_prev`가 정확히
일치해야 파괴적 작업을 수행한다. 활성 connection을 종료하고 교체하며, 교체 또는 재기동·
smoke 실패 시 `greenhouse_demo_prev` 복귀와 backend 재기동을 시도한다.

DB owner와 `public`/`demo_internal` schema owner는 `greenhouse_demo`로 복원한다. 이 role에는
전체 객체 권한을, 운영 `greenhouse` role에는 demo DB의 `CONNECT`, 두 schema의 `USAGE`,
table/sequence `SELECT`와 동일 default privileges를 적용한다. `PUBLIC`의 DB 권한과 schema
`CREATE`는 회수한다.

`/tmp/green-house-operation.lock`은 이미지 배포와 리프레시가 동시에 실행되는 것을 막는다.
한 운영 PC에서만 실행한다는 전제이며, 여러 호스트에서 실행한다면 Kubernetes Lease 같은
분산 lock이 추가로 필요하다. 비식별 dump는 `DEMO_SANITIZED_DUMP_KEEP=2` 또는 `3`으로 최근
산출물만 보존한다. 데모 사용자가 생성한 데이터는 다음 정기 리프레시 때 사라진다.

## 8. systemd timer

운영 PC에는 Docker Compose v2, 운영 PostgreSQL과 호환되는 `pg_dump`/`pg_restore`, `psql`,
`kubectl`, `curl`, `flock`이 필요하다. Flyway checksum 검증은 고정된
`redgate/flyway:11` Docker 이미지로 실행하므로 host Flyway CLI는 설치하지 않는다. 실제 운영
계정 `sjw`가 Kubernetes kubeconfig와 Docker에 접근하며, 저장소는
`/home/sjw/projects/green-house`에 있다.

libpq 명령은 `PGPASSFILE=/home/sjw/.pgpass`를 사용한다. Flyway Docker 컨테이너는 host
`.pgpass`를 읽지 않으므로 같은 demo 비밀번호를 `DEMO_DB_PASSWORD`로 EnvironmentFile에 둔다.

```bash
sudo install -m 644 deploy/systemd/green-house-demo-refresh.service /etc/systemd/system/
sudo install -m 644 deploy/systemd/green-house-demo-refresh.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now green-house-demo-refresh.timer
sudo systemctl start green-house-demo-refresh.service
systemctl status green-house-demo-refresh.timer
journalctl -u green-house-demo-refresh.service -n 200 --no-pager
```

service는 `User=sjw`, `WorkingDirectory=/home/sjw/projects/green-house`, snap Docker를 포함한
명시적 `PATH`, `EnvironmentFile`, 2시간 timeout, `UMask=0077`을 사용한다. timer는 매주 일요일
04:00(Asia/Seoul)에 실행해 매일 03:00 운영 DB backup과 겹치지 않게 하고, 실패로 놓친 실행을
`Persistent=true`로 보완한다. 이미지 배포도
같은 `sjw` 계정으로 실행해야 공유 `/tmp/green-house-operation.lock`이 정상 동작한다.

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
- 운영 `greenhouse` role의 데모 DB 조회 성공, 쓰기 실패
- 실제 개인정보·계좌·연락처·자유 메모가 남아 있지 않음
- 리프레시 후 난 묶음, 작업, 판매·경매·정산 전체 이력과 관계 정상
- Engine reconciliation이 `ACTIVE`, `ready=true`, `issues=[]`
- startup guard 활성 상태로 backend 기동 성공
