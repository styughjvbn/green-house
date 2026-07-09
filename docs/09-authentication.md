# Authentication

## 방식

- JWT가 아닌 서버 세션 방식.
- 로그인 성공 시 백엔드가 `JSESSIONID` 쿠키를 발급.
- 프론트엔드는 API 요청에 쿠키를 포함해 인증 상태 유지.
- 기본 권한은 `ADMIN`, `WORKER`.

## 기본 계정

운영 배포 시 아래 환경변수로 반드시 변경.

```text
ADMIN_USERNAME
ADMIN_PASSWORD
WORKER_USERNAME
WORKER_PASSWORD
```

로컬 기본값:

```text
admin / admin
worker / worker
```

## 환경변수

```text
AUTH_ENABLED=true
FRONTEND_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*
```

테스트 프로필은 기존 API 테스트 보호를 위해 `AUTH_ENABLED=false`.

## 권한

- `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`: 인증 API
- `/api/work-types/**`: `ADMIN` 전용
- 그 외 `/api/**`: 로그인 필요
- `/actuator/health`, Swagger/OpenAPI 문서: 공개
