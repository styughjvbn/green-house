# 운영 사진 저장소와 Drive 백업

## 저장 구성

| 위치 | 용도 |
| --- | --- |
| `/opt/green-house/data/slip-photos` | 운영 사진 원본, 정적 hostPath PV |
| `/var/lib/greenhouse/slip-photos` | backend 컨테이너 내부 mount 경로 |
| `/opt/green-house/backups/local/database` | 로컬 DB dump, 기존 파일 유지 |
| `/opt/green-house/backups/local/manifests` | 회차별 파일 목록·크기·SHA-256, 사진 사본 아님 |
| `/opt/green-house/backups/local/completed` | 원격 전송·검증까지 성공한 회차 기록 |
| 설정할 rclone remote의 `photos/` | 사진 원격 백업, 과거 사진도 누적 보존 |
| remote의 `database/`, `manifests/`, `completed/` | dump·검증 목록·완료 기록 |

사진의 로컬 백업 사본은 만들지 않는다. 기존 DB 백업 파일·사진 백업 사본이 이미 있으면
스크립트가 삭제하거나 이동하지 않는다. 이번 전환은 운영에 저장된 사진이 없다는 전제다.
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

## Drive 백업 실행

필요 도구는 `pg_dump`, Python 3, rclone, `realpath`, `flock`, `cmp`다. `pg_dump`는
서버와 같은 major를 권장하며 더 오래된 major를 사용하지 않는다. 실행 계정은 `sjw`다.
rclone 설정과 토큰 갱신 파일은 `sjw`가 읽고 쓸 수 있어야 하며 다른 사용자에게 공개하지 않는다.
DB 인증은 기존 libpq service와 mode 600 `.pgpass` 등을 사용한다.

remote 이름·폴더는 아직 미정이므로 [cron 예시](../../deploy/cron/green-house-backup.cron.example)의
`RCLONE_BACKUP_REMOTE`를 비워 두었다. 빈 값은 dump·업로드 전에 실패한다.
애플리케이션 `.env`나 ConfigMap에 Drive 인증을 넣지 않는다.

remote 설정 후 실제 값을 사용해 **수동 실행·원격 복원 검사부터** 수행한다.

```bash
SALES_PHOTO_STORAGE_ROOT=/opt/green-house/data/slip-photos \
RCLONE_BACKUP_REMOTE='<configured-remote>:green-house' \
RCLONE_CONFIG=/home/sjw/.config/rclone/rclone.conf \
PGSERVICE=greenhouse PGPASSFILE=/home/sjw/.pgpass \
bash scripts/backup-with-photos.sh /opt/green-house/backups/local
```

스크립트 동작:

1. 이미지 배포·demo refresh와 같은 operation lock을 잡고 DB dump를 만든다.
2. 성공한 dump는 로컬에 유지한다. 이후 원격 전송 실패로 이 파일을 버리지 않는다.
3. dump 이후 사진 목록과 SHA-256을 고정한다. 완료 전 `.upload-*.tmp`, 심볼릭 링크와
   특수 파일은 백업 대상으로 다루지 않는다. 임시 업로드만 제외하며 나머지 비정상 경로는 실패한다.
4. 고정 목록의 사진을 `rclone copy --checksum --immutable`로 전송한다. 기존 원격
   파일을 삭제하지 않고 동일 key의 내용이 다르면 덮어쓰지 않고 실패한다.
5. `rclone check --download`로 사진을 스트리밍 비교한 뒤 dump·목록도 전송·검증한다.
   이 검증은 다운로드 통신량을 사용하지만 사진 사본을 디스크에 저장하지 않는다.
6. 원본 사진이 목록 생성 후 변경되지 않았는지 다시 확인한다.
7. 모든 단계가 성공한 경우에만 `completed/<backup-id>.json`을 마지막으로 업로드하고
   다시 읽어 비교한다. 원격 완료 기록이 없는 회차는 복원 후보로 사용하지 않는다.

사진은 DB commit 전에 발행된 불변 파일이므로 dump 이후 고정한 목록으로 해당 snapshot을
보호한다. 목록 고정 이후 새로 등록된 사진은 다음 백업 대상이다. 백업 중에는 원본 사진
정리·수동 삭제를 실행하지 않는다. 배포·리프레시도 같은 lock을 사용하는 계정에서 실행한다.
미확인 원격 손상이나 동일 key의 변경을 `sync`, 삭제, 덮어쓰기 옵션으로 우회하지 않는다.

수동 검증 후 cron 예시를 운영 값에 맞춰 설치한다. 예시는 호스트 cron의 매일 03:00이며
`timedatectl`로 호스트 시간대를 먼저 확인한다. 기존 DB dump cron과 중복 등록하지 않고
실제 계정·스크립트 경로·DB service·PATH·로그 쓰기 권한을 확인해 교체한다. 자동 설치는 하지 않는다.
로그의 비정상 종료를 운영 알림 대상으로 삼고, `Backup complete`와 원격 완료 기록을 확인한다.

로컬 DB 보존 기간은 기존 운영 정책을 유지하되, 기존 정리 작업이 새 `database/` 경로와
파일 이름도 대상으로 하는지 확인한다. 원격 `photos/`는 과거 dump도 참조하므로
날짜만 보고 지우지 않는다. 원격 dump·manifest·완료 기록 정리도 검증된 별도 보존 정책으로
수행하며 현재 스크립트는 자동 삭제를 하지 않는다.

## 복원

1. 원격 `completed/`에서 회차를 선택한다. 해당 JSON과 `manifests/<backup-id>/`를
   별도 복구 작업 폴더에 내려받아 JSON이 일치하는지 확인한다.
2. 같은 회차의 `database/<backup-id>.dump`를 내려받고 작업 폴더에서
   `sha256sum -c manifests/database.sha256`로 검사한다.
3. `manifests/files.txt`를 `--files-from-raw`로 사용해 remote의 `photos/`를 복구 작업
   폴더로 내려받는다. 원격의 과거 전체 사진을 모두 내려받을 필요는 없다.

```bash
# restore_dir는 준비한 별도 복구 작업 폴더이며, 운영 원본 경로로 지정하지 않는다.
rclone copy "$remote/photos" "$restore_dir/photos" \
  --files-from-raw "$restore_dir/manifests/files.txt"
python3 scripts/photo_backup_manifest.py verify \
  "$restore_dir/photos" "$restore_dir/manifests"
```

4. 우선 격리 PostgreSQL DB에 `pg_restore --exit-on-error`로 복원해 schema·사진 참조를
   검증한다. 운영 DB를 자동으로 덮어쓰거나 Flyway repair를 수행하지 않는다.
5. 실제 복원 시 backend/writer를 중지한다. DB와 같은 회차의 검증된 사진을 hostPath에
   배치하고 관리자가 UID/GID `10001:10001`, 디렉터리 `2750`, 사진 `640`을 복구한다.
   일반 계정만으로 복원하려면 앱 소유 디렉터리와 그룹 쓰기 권한을 사전에 준비해야 한다.
6. 기존 사진 무결성 CLI, 앱 조회·신규 저장·Pod 교체 후 재조회를 확인하고 정상 운영한다.

이 문서의 복구 작업 폴더는 복원 시의 임시 공간이며 일상 백업용 사진 사본이 아니다.

## 회귀 검증

```bash
GREENHOUSE_BACKUP_POSTGRES_TEST=1 \
python3 -m unittest discover -s scripts -p 'test_backup_with_photos.py' -v
```

테스트에는 실제 rclone과 Docker가 필요하다. `RCLONE_TEST_BIN`으로 별도 rclone 경로를
지정할 수 있다. 임시 local-alias remote와 격리 PostgreSQL을 사용하며 실제 Drive에는
접속하지 않는다. 업로드·검증 실패, 원본 손상, 동시 실행, 업로드 중 새 파일, 빈 사진 저장소,
부분 dump 정리, 원격 사진 보존과 실제 PostgreSQL dump·복원을 검사한다.

2026-10-30에 rclone 1.75.2 local-alias와 실제 PostgreSQL 18로 회귀 12건을 통과했다.
운영 base·demo·dev overlay의 렌더링과 격리도 확인했다. 백엔드 전체 검사(캐시 재사용)와
프론트 전체 검사를 통과했다. 이후 운영 진입점을 base로 유지하도록 매니페스트·문서를
정리하고 base·demo·dev 볼륨 격리를 다시 검증했다. 앱 코드·백업 스크립트는 변경하지
않았으며 실제 운영 적용·Drive 전송·cron 등록은 미실행이다. remote 설정은 요청에 따라 비어 있다.
