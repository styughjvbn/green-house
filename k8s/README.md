# k3s 배포 매니페스트

대상 환경:

- mini-pc k3s
- k3s 내장 Traefik Ingress
- mini-pc host PostgreSQL
- 도메인: `https://green-house.sjw-project.site/`

## 파일 구조

```text
k8s/
  base/
    namespace.yaml
    configmap.yaml
    secret.yaml
    postgres-host-service.yaml
    ai-runtime-deployment.yaml
    ai-runtime-service.yaml
    ai-codex-pvc.yaml
    backend-deployment.yaml
    backend-service.yaml
    frontend-deployment.yaml
    frontend-service.yaml
    ingress.yaml
    kustomization.yaml
  overlays/
    demo/
      kustomization.yaml
      configmap-patch.yaml
      secret-patch.yaml
      postgres-endpoints-patch.yaml
      network-policy.yaml
```

## 적용 전 수정

- `base/secret.yaml`: 운영 DB 비밀번호와 운영 로그인 비밀번호 변경
- `base/postgres-host-service.yaml`: mini-pc host PostgreSQL IP 확인
- `base/backend-deployment.yaml`: GHCR에 실제 발행된 backend `sha-<commit>` 태그로 변경하고 `docker manifest inspect`로 존재 확인
- `base/configmap.yaml`: `ACTIVE`로 전환한 DB와 coverage 최소 버전 이상인 `ORCHID_LEDGER_WRITER_VERSION` 확인 (기본 2.0.0, Legacy 모드 제거)
- `base/frontend-deployment.yaml`: GHCR frontend 이미지 태그 변경
- `base/ai-runtime-deployment.yaml`: GHCR ai-runtime 이미지 태그 변경
- `base/ingress.yaml`: TLS secret 이름이 기존 Traefik 설정과 다르면 변경

GHCR private 이미지를 쓰는 경우 같은 namespace에 pull secret을 만든다.

```bash
kubectl -n green-house create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<github-user> \
  --docker-password=<github-token> \
  --docker-email=<email>
```

## 운영 AI 설정

base에서 AI runtime Deployment·ClusterIP Service·Codex 인증용 PVC를 함께 배포한다. backend는 `http://green-house-ai-runtime:8091`, runtime은 `http://green-house-backend:8080`으로 통신한다. 개발 PC나 host Node 프로세스에 연결하지 않는다.

기존 `base/configmap.yaml`에서 실행기·600초 제한·도구 300회·기본 경매장 ID 3을 관리한다. 공유 키는 `green-house-ai-secret`의 `AI_RUNTIME_SECRET`으로 사전 생성한다. backend는 이 키만, runtime은 필요 시 같은 Secret의 `OPENAI_API_KEY`도 읽는다. frontend에는 인증키를 전달하지 않는다. base의 DB·로그인 Secret 템플릿은 실제 값으로 설정한 뒤 적용한다.

`ai-runtime/Dockerfile`로 발행한 이미지 tag를 `base/ai-runtime-deployment.yaml`에 지정한다. Codex 로그인과 설정은 `/var/lib/greenhouse-codex`의 PVC에 보존한다. 최초 Secret·로그인·재배포 절차는 `docs/07-deployment.md`의 `운영 AI 설정`을 따른다. 데모 overlay에서는 runtime Deployment·Service·PVC·AI 설정·인증키 참조를 모두 제거한다.

## 적용

ConfigMap의 환경 변수 변경은 실행 중인 Pod에 반영되지 않는다. 난 묶음 Engine cutover처럼
writer mode를 바꾸는 배포는 `docs/07-deployment.md`의 운영 primary cutover runbook에 따라
backend를 먼저 0개로 내리고, 검증된 이미지 SHA와 ConfigMap을 함께 적용해 새 Pod를
기동한다. repository에 남아 있는 과거 고정 이미지 태그를 배포 후보로 간주하지 않는다.

```bash
kubectl apply -k k8s/base
```

데모 환경:

```bash
kubectl kustomize k8s/overlays/demo
kubectl apply -k k8s/overlays/demo
```

데모 적용 전 도메인, PostgreSQL host IP, DB Secret을 반드시 설정한다. 상세 운영 PC
절차는 `docs/features/demo-operations.md`를 따른다.

## 라우팅

- `/api` -> backend
- `/` -> frontend

운영 k3s 설정에서는 `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`로 Swagger/OpenAPI 공개를 막는다.
