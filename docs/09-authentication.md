# Authentication

## 방식

- JWT가 아닌 서버 세션 방식.
- 로그인 성공 시 백엔드가 `JSESSIONID` 쿠키를 발급.
- 프론트엔드는 API 요청에 쿠키를 포함해 인증 상태 유지.
- 기본 권한은 `ADMIN`, `WORKER`.
- 세션 유지 시간은 기본 7일.
- 로그인된 요청이 계속 들어오면 서버 세션과 쿠키 만료 시간이 다시 7일로 연장.
- 세션이 만료되어 API가 `401` 또는 `403`을 반환하면 프론트엔드는 로그인 화면으로 이동.

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
SESSION_TIMEOUT=7d
SESSION_COOKIE_MAX_AGE=7d
FRONTEND_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*
```

테스트 프로필은 기존 API 테스트 보호를 위해 `AUTH_ENABLED=false`.

## 권한

- `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`: 인증 API
- `/api/work-types/**`: `ADMIN` 전용
- 그 외 `/api/**`: 로그인 필요
- `/actuator/health`, Swagger/OpenAPI 문서: 공개
