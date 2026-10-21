# 농장 관리 프론트엔드

Next.js 기반 프론트엔드입니다.

```bash
npm run dev
npm run lint
npm run build
```

`frontend/.env` 또는 `.env.local`에 다음 API 주소를 설정합니다.

```dotenv
BACKEND_API_URL=http://localhost:8080/api
NEXT_PUBLIC_API_BASE_URL=/api
```

브라우저 요청은 같은 호스트의 `/api`를 거쳐 Next.js가 백엔드로 전달합니다.
서버 조회에는 `BACKEND_API_URL`을 사용합니다. 다른 PC에서 접속할 때 브라우저 API
주소를 `localhost:8080`으로 지정하면 접속한 PC를 가리키므로 사용하지 않습니다.
환경변수 변경 후 개발 서버를 재시작합니다.
