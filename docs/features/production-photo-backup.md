# 운영 사진 저장소와 Drive 백업

## 저장 구성

| 위치 | 용도 |
| --- | --- |
| `/opt/green-house/data/slip-photos` | 운영 사진 원본, 정적 hostPath PV |
| `/var/lib/greenhouse/slip-photos` | backend 컨테이너 내부 mount 경로 |
| `/opt/green-house/backups/local/*.dump.gz` | 기존 로컬 DB 백업 형식·위치 유지 |
| `/opt/green-house/backups/local/manifests` | 회차별 파일 목록·크기·정밀 검사 시 SHA-256 |
| `/opt/green-house/backups/local/completed` | 원격 전송·검증까지 성공한 회차 기록 |
| 설정할 rclone remote의 `photos/` | 사진 원격 백업, 과거 사진도 누적 보존 |
| remote의 `db/` | 기존 `green-house-backups/db/`에 `.dump.gz` 누적 보관 |
| remote의 `replaced/`, `manifests/` | 교체 전 사진과 회차별 검증 목록 |

사진의 로컬 백업 사본은 만들지 않는다. 로컬 DB만 성공한 백업 후 기존 `-mtime +7`
조건으로 정리한다. 원격 DB·사진·교체 이력은 자동 삭제하지 않는다. 이번 전환은 운영에
저장된 사진이 없다는 전제다.
사진이 발견되면 적용을 중단하고 이관·보존 범위를 먼저 확인한다. DB는 초기화하지 않는다.

## 운영 hostPath 적용

`k8s/base` 자체가 운영 배포용이며 운영 사진 PV/PVC를 포함한다. demo·dev overlay는
운영 정적 PV를 제외하고 각 namespace의 기존 동적 PVC로 덮어쓴다. PV는 `Retain`, `hostPath.type=Directory`, 기본 StorageClass를 사용하지 않는
명시적 PVC binding과 node affinity를 사용한다. 10Gi는 hostPath의 실제 디스크 quota가
아니므로 호스트 디스크 사용량을 별도로 확인한다.

1. `kubectl get nodes -L kubernetes.io/hostname`으로 실제 노드 label을 확인한다.
   `sales-photos-pv.yaml`의 `mini-pc`와 다르면 첫 적용 전에 수정한다.
2. GID 10001의 기존 그룹을 확인하고 없을 때만 만들며 `sjw`를 가입시킨다.
   [일반 사용자 백업 권한](../07-deployment.md#일반-사용자-계정의-사진-백업-권한)을 따른다.
3. 호스트에서 사진 루트를 준비한다. `fsGroup`만으로 hostPath의 소유자가 자동으로
   바뀐다고 가정하지 않는다.

```bash
sudo install -d -o 10001 -g 10001 -m 2750 /opt/green-house/data/slip-photos
sudo install -d -o sjw -g "$(id -gn sjw)" -m 700 /opt/green-house/backups/local
namei -l /opt/green-house/data/slip-photos
findmnt -T /opt/green-house/data/slip-photos
df -h /opt/green-house/data/slip-photos
```

4. 상위 `/opt/green-house`·`data` 경로에 `sjw`의 탐색 권한이 있는지 확인한다.
   저장소에는 atomic rename과 파일·디렉터리 fsync가 필요하다.
5. backend는 새 사진을 POSIX `640`으로 저장하는 권한 보완이 포함된 릴리스로 지정한다.
   base의 과거 고정 이미지 tag나 Secret 예시를 그대로 운영 후보로 적용하지 않는다.
   실제 DB·로그인 Secret과 AI Secret은 기존 운영 배포 절차에 맞게 관리한다.
6. backend를 중지하고 기존 사진용 PVC·파일과 DB에 보존할 사진이 없는지 확인한다.
   manifest를 검토한 뒤 운영 base를 적용한다.

```bash
kubectl kustomize k8s/base
kubectl -n green-house scale deployment/green-house-backend --replicas=0
kubectl -n green-house wait --for=delete pod \
  -l app.kubernetes.io/component=backend --timeout=180s
kubectl apply -k k8s/base
kubectl -n green-house get pvc green-house-sales-photos-host
kubectl -n green-house rollout status deployment/green-house-backend --timeout=300s
```

base는 기존 `green-house-sales-photos` 동적 PVC를 원하는 리소스 목록에서 제외하지만
실제 PVC를 삭제하지 않는다. `--prune`을 사용하지 않는다. 새 PVC는
`green-house-sales-photos-host`이며 기존 bound PVC의 immutable 필드를 바꾸지 않는다.
기존 PVC 정리는 대상·내용·복귀 필요성을 확인한 별도 작업이다.

7. 앱에서 새 사진을 등록한 뒤 새 `sjw` 프로세스로 파일 읽기와 SHA-256을 확인한다.
   Pod 재생성 후 조회·신규 저장도 확인한다. 컨테이너 경로와 ConfigMap의
   `SALES_PHOTO_STORAGE_ROOT`는 기존 `/var/lib/greenhouse/slip-photos`를 유지한다.

## 운영 설정: backup.env

`backup.env`는 백업 전용 운영 설정이다. DB 주소·계정과 `.pgpass` 위치, 원본 사진 경로,
로컬 DB 백업 경로, rclone 설정·Drive 경로를 전달한다. 애플리케이션 `.env`나 Kubernetes
ConfigMap으로 전달하지 않는다.

- [deploy/backup.env.example](../../deploy/backup.env.example): Git에 포함하는 설정 예시
- `deploy/backup.env`: 실제 운영 값, Git 추적 제외·권한 600
- export된 환경변수 > 설정 파일 순으로 적용한다. 쉘로 source하거나 eval하지 않는다.
  `$HOME` 같은 변수나 명령은 확장하지 않으므로 실제 절대 경로를 입력한다.

```bash
# sjw 계정, 프로젝트 루트에서 최초 한 번만 준비
if [ ! -e deploy/backup.env ]; then
  install -m 600 deploy/backup.env.example deploy/backup.env
fi
chmod 600 deploy/backup.env
```

생성한 파일의 DB 값은 기존 운영 백업 스크립트와 대조한다. 예시 기본값을 운영의
확인된 값으로 간주하지 않는다. remote는 요청에 따라 비워 두었으며 실제 remote 이름을
확정한 뒤 `<remote>:green-house-backups`로 지정한다. 스크립트가 `db/`를 덧붙이므로
기존 Drive의 `green-house-backups/db/` 경로를 유지한다. 빈 remote는 dump·업로드 전에 실패한다.

`.pgpass`와 rclone 설정은 `sjw`가 사용할 수 있어야 한다. `pg_dump`는 서버와 같은 major를
권장하며 서버보다 오래된 major를 사용하지 않는다. 인증이 없으면 대화형 비밀번호
입력을 기다리지 않고 실패한다. 필요 도구는 `pg_dump`, gzip, Python 3, rclone, realpath,
flock, find다. 설정값·비밀번호를 로그로 출력하지 않는다.

## 기존 cron에 연결

별도 사진 cron은 만들지 않는다. 현재 매일 03:00에 실행하는
`/opt/green-house/scripts/backup-greenhouse-db.sh`의 진입점을 그대로 유지한다.
[호환 진입 스크립트](../../deploy/backup-greenhouse-db.sh)는 기본 저장소
`/home/sjw/projects/green-house/scripts/backup-with-photos.sh`를 호출하고
그 저장소의 `deploy/backup.env`를 읽는다. 저장소 위치가 다르면
`GREENHOUSE_BACKUP_REPO`를 지정하거나 진입 스크립트의 경로를 맞춘다.

먼저 새 스크립트를 `sjw`로 수동 실행하고 결과·복원을 확인한다.

```bash
# 일일 모드: 원격에 없는 사진만 업로드
bash scripts/backup-with-photos.sh
# 다른 설정 파일로 수동 실행할 때
bash scripts/backup-with-photos.sh --env-file /absolute/path/backup.env
# 전체 체크섬 비교와 교체 전 사진 보존: 주기는 아직 미정, 수동 실행
bash scripts/backup-with-photos.sh --verify-photos
```

검증 후에만 기존 운영 스크립트의 보관본을 만들고 wrapper를 설치한다. 기존 스크립트의
DB 접속값을 설정 파일로 옮겼는지 먼저 확인한다. 보관본에는 기존 하드코딩 비밀값이
있을 수 있으므로 관리자만 읽을 수 있게 보관한다.

```bash
sudo install -m 600 /opt/green-house/scripts/backup-greenhouse-db.sh \
  "/opt/green-house/scripts/backup-greenhouse-db.sh.before-photos-$(date -u +%Y%m%dT%H%M%SZ)"
sudo install -m 755 deploy/backup-greenhouse-db.sh \
  /opt/green-house/scripts/backup-greenhouse-db.sh
```

기존 cron 줄과 실행 계정·로그 경로·호스트 시간대는 유지·확인한다. 새로운 중복 cron은
등록하지 않는다. cron은 `sjw`의 새 프로세스에서 실행돼야 그룹 권한이 반영된다.

## 백업 정책과 실행 순서

| 항목 | 정책 |
| --- | --- |
| DB 형식 | custom dump를 gzip으로 감싼 `.dump.gz` |
| 로컬 DB | 기존 폴더 직하에 저장, 성공 후 `find -mtime +7`로 정리 |
| Drive DB | 기존 `green-house-backups/db/`, 누적 보관, 삭제 정책 미정 |
| 일일 사진 | `--ignore-existing`, 없는 파일만 복사·크기 확인 |
| 정밀 검사 | `--verify-photos`, 전체 체크섬 비교·다운로드 검증 |
| 교체 이력 | 정밀 검사에서 교체 전 원격 파일을 `replaced/<backup-id>/`에 보존 |
| 업로드 순서 | 사진·교체 이력 → manifest → DB 마지막 |

1. 배포·demo refresh와 공유하는 operation lock을 잡는다.
2. DB dump를 gzip으로 생성·검사한 뒤 로컬 `.dump.gz`를 완성한다. 이후 Drive 전송이
   실패해도 완성한 로컬 DB 파일은 남긴다.
3. dump 이후 원본 사진 목록을 고정한다. `.upload-*.tmp`는 제외하고, 심볼릭 링크나
   특수 파일·읽을 수 없는 파일이 발견되면 실패한다.
4. 일일 모드는 기존 사진 내용을 해시하거나 다운로드하지 않고 없는 파일만 복사한다.
   원격 기존 사진의 크기가 다르면 덮어쓰지 않고 실패하므로 원인을 조사한 뒤 정밀 검사를 결정한다.
5. 정밀 모드는 원본 SHA-256을 기록하고 `--checksum --backup-dir`로 전송한다. 교체 전
   원격 바이트는 먼저 `replaced/`로 보존하며 사진·이력 전송이 끝난 후 전체 파일을 검증한다.
6. 회차별 manifest를 올리고 검증한다. 사진 목록 고정 후 원본이 변경되지 않았는지
   일일 모드는 크기, 정밀 모드는 크기·SHA-256으로 다시 확인한다.
7. DB `.dump.gz`를 **마지막 업로드 객체**로 보내고 내려받기 비교로 검사한다. 성공한 뒤
   로컬 `completed/`에 기록하고 로컬 DB만 `-mtime +7` 조건으로 정리한다.

사진은 DB commit 전에 발행되므로 dump 이후 고정한 사진 목록에 그 DB가 참조하는 파일이
포함된다. 이후 새로 등록된 사진은 다음 백업 대상이다. 단순히 같은 시각에 두 cron을
실행하는 방식이 아니며, 한 작업 안에서 순서를 보장한다. 백업 중 사진 정리·수동 삭제는
금지한다. photos·replaced·원격 DB에는 날짜 기반 삭제나 `sync`를 적용하지 않는다.

일일 모드는 기존 파일의 동일 크기 손상을 검출하지 않는다. 그 검사는 `--verify-photos`에서
수행한다. 정밀 검사 시 원본 자체가 잘못됐을 수도 있으므로 교체 이력을 보존하고, 오류를
이력 삭제로 우회하지 않는다. 과거 dump가 참조하는 사진은 현재 photos와 바이트가 다를
수 있으므로 복원 검사에서 불일치가 나면 해당 replaced 이력에서 올바른 바이트를 찾는다.

일일 manifest의 사진 SHA-256은 비어 있고 파일 목록·크기만 기록한다. 정밀 manifest에는
전체 SHA-256과 이력 경로를 기록한다. 원격 DB가 없거나 manifest의 DB 해시가 일치하지
않는 회차는 복원 후보로 사용하지 않는다. 원격 완료 JSON을 DB 뒤에 추가 업로드하지 않는다.
실패한 회차의 선행 사진·manifest는 남을 수 있지만 성공한 백업으로 취급하지 않는다.

## 복원

1. 기존 Drive `green-house-backups/db/`에서 `.dump.gz`를 선택한다. 새 스크립트가 만든
   회차라면 같은 backup ID의 `manifests/`도 별도 복구 작업 폴더로 내려받는다.
2. 작업 폴더에서 `sha256sum -c manifests/database.sha256`와 `gzip -t`로 dump를 검사한다.
   기존 스크립트가 만든 과거 dump에는 새 manifest가 없으므로 별도로 사진 보유 여부를 확인한다.
3. `manifests/files.txt`로 해당 회차 사진을 내려받는다.

```bash
rclone copy "$remote/photos" "$restore_dir/photos" \
  --files-from-raw "$restore_dir/manifests/files.txt"
python3 scripts/photo_backup_manifest.py verify \
  "$restore_dir/photos" "$restore_dir/manifests"
```

일일 manifest 검사는 크기만, 정밀 manifest 검사는 SHA-256까지 확인한다. DB에 저장된
사진 크기·해시는 기존 사진 무결성 CLI로 **항상** 다시 검사한다. 불일치하면 덮어쓰거나
DB 메타데이터를 고치지 않고 replaced 이력과 원본을 확인한다.

4. 먼저 빈 격리 DB에서 gzip을 해제해 `pg_restore --exit-on-error`로 검증한다.
   gzip 해제 프로세스와 restore 모두 성공해야 한다. 운영 DB를 자동 덮어쓰거나
   Flyway repair를 수행하지 않는다.
5. 실제 복원에서는 backend/writer를 중지하고 같은 회차 DB·사진을 사용한다. 관리자가
   UID/GID `10001:10001`, 디렉터리 `2750`, 사진 `640`을 복구한다. 일반 계정만으로
   복원하려면 앱 소유 디렉터리와 그룹 쓰기 권한을 사전에 준비해야 한다.
6. 사진 무결성 CLI, 앱 조회·신규 저장·Pod 교체 후 재조회를 확인한 뒤 운영을 재개한다.

복구 작업 폴더는 복원 시 임시 공간이며 일상 백업용 사진 사본이 아니다.

## 검증

```bash
GREENHOUSE_BACKUP_POSTGRES_TEST=1 \
python3 -m unittest discover -s scripts -p 'test_backup_with_photos.py' -v
```

실제 rclone·Docker가 필요하며 `RCLONE_TEST_BIN`으로 rclone 경로를 지정할 수 있다.
테스트는 임시 local-alias와 격리 PostgreSQL을 사용한다. 실제 Drive에는 접속하지 않는다.
기존 운영 스크립트 교체·cron 변경·Drive 업로드는 자동 수행하지 않는다.

2026-10-30에 환경 파일·기존 cron wrapper·일일/정밀 모드·교체 이력·로컬 `-mtime +7`·
실제 PostgreSQL gzip dump 복원 등 18건을 통과했다. 백엔드 전체 검사(캐시 일부 재사용)와
프론트 전체 검사도 통과했다. 이후 변경은 이 검증 기록뿐이며 실제 운영 적용은 미실행이다.
