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
    backend-deployment.yaml
    backend-service.yaml
    frontend-deployment.yaml
    frontend-service.yaml
    ingress.yaml
    kustomization.yaml
```

## 적용 전 수정

- `base/secret.yaml`: DB 비밀번호와 운영 계정 비밀번호 변경
- `base/postgres-host-service.yaml`: mini-pc host PostgreSQL IP 확인
- `base/backend-deployment.yaml`: GHCR backend 이미지 태그 변경
- `base/frontend-deployment.yaml`: GHCR frontend 이미지 태그 변경
- `base/ingress.yaml`: TLS secret 이름이 기존 Traefik 설정과 다르면 변경

GHCR private 이미지를 쓰는 경우 같은 namespace에 pull secret을 만든다.

```bash
kubectl -n green-house create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<github-user> \
  --docker-password=<github-token> \
  --docker-email=<email>
```

## 적용

```bash
kubectl apply -k k8s/base
```

## 라우팅

- `/api` -> backend
- `/` -> frontend

운영 k3s 설정에서는 `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`로 Swagger/OpenAPI 공개를 막는다.
