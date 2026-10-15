# Green-house AI Runtime

Spring Boot가 검증한 전표 이미지를 받아 입력 후보를 반환하는 별도 Node.js 프로세스다. 전표 저장·재고 변경 도구는 제공하지 않는다. 실행기는 `AI_PROVIDER=codex|openai`로 선택한다.

## 개발 실행

```sh
cd ai-runtime
npm ci
npm run dev
```

저장소 루트의 `.env`가 있으면 읽고, 이미 지정된 환경변수를 우선한다. 개발 모드(`NODE_ENV=development`)로 TypeScript를 빌드한 뒤 소스 변경을 감시해 재빌드·서버 재시작한다. `.env` 변경 후에는 명령을 다시 실행한다. Codex MCP 도구도 빌드된 파일을 사용한다. `Ctrl+C`로 감시 프로세스와 서버를 함께 종료한다.

시작하면 `server_started`에 주소·실행기·설정 모델을 출력한다. `curl http://127.0.0.1:8091/health`로 서버 응답을 확인한다. 분석 요청은 `request_received` → `inference_started` → 모델·도구 이벤트 → `result_validation_started` → `ai_run` 순서로 출력된다. 요청 ID로 Spring 로그와 연결할 수 있다. 실패는 `ai_run_failed`의 단계·오류 코드·HTTP 상태·네트워크 코드로 구분한다. `model_response`의 401/403은 인증·권한, 429는 사용 한도, `AI_TIMEOUT`은 실행 시간 초과, `ECONNREFUSED`는 연결 실패를 확인한다.

운영 모드도 시작·실패·실행 요약은 기록하고, 상세 진행 로그는 개발 모드에서만 출력한다. 모델의 원문 오류·SDK stderr는 사진 내용이나 인증값을 포함할 수 있어 출력하지 않는다. 시작 성공이나 health 응답은 모델 인증 성공을 보장하지 않는다.

## 운영 실행

Node.js 22 이상이 필요하다. 저장소 루트의 `.env.example`을 참고해 **서버 환경변수**를 설정한다. `.env` 파일은 자동으로 읽지 않는다. 프로세스 관리자에서 환경을 전달하거나 Node의 `--env-file`을 사용한다.

```sh
cd ai-runtime
npm ci
npm run build
node --env-file=../.env dist/server.js
```

| 환경변수 | 기본값 / 용도 |
| --- | --- |
| `AI_PROVIDER` | `codex`; `openai`로 변경 가능 |
| `AI_RUNTIME_SECRET` | 필수. 32자 이상의 무작위 비밀값. Spring과 동일한 값 |
| `AI_TOOLS_URL` | `http://127.0.0.1:8080`; Spring 내부 도구 API 주소 |
| `AI_HOST` / `AI_PORT` | `127.0.0.1` / `8091` |
| `AI_TIMEOUT_SECONDS` | `120`; 전체 실행 제한, Spring과 동일하게 설정 |
| `AI_MAX_TOOL_CALLS` | `20`; Spring과 동일하게 설정, 최대 100 |
| `AI_CODEX_HOME` | Codex 전용 계정의 별도 인증·설정 디렉터리. Codex 사용 시 필수 |
| `AI_CODEX_MODEL` | 비어 있으면 해당 Codex 계정의 기본 모델 |
| `OPENAI_API_KEY` | OpenAI API 실행기에서만 사용 |
| `AI_OPENAI_MODEL` | `gpt-4.1-mini` |

Codex는 위 SDK와 함께 설치된 CLI를 사용한다. 전용 OS 계정에서 전용 `CODEX_HOME`으로 로그인하고 파일 기반 인증을 구성한다. 개발자의 Codex 디렉터리나 프로젝트 디렉터리를 공유하지 않는다. 기기 인증이 계정에서 허용된 경우 SDK에 포함된 CLI로 로그인할 수 있다.

```sh
# ai-runtime 디렉터리, 전용 실행 계정에서 실행. 경로는 환경에 맞게 지정.
export CODEX_HOME=/absolute/path/to/greenhouse-codex
mkdir -p "$CODEX_HOME"
./node_modules/.bin/codex -c cli_auth_credentials_store='"file"' login --device-auth
# 같은 경로를 AI_CODEX_HOME으로 설정
```

개인 로그인은 구독 이용 범위·한도에 영향을 받으며 운영 전 실제 계정의 이미지 입력과 JSON 출력을 확인한다. 인증 파일은 비밀정보다. 저장소에 넣지 않는다. 이 전용 디렉터리에는 다른 MCP 서버·플러그인·업무 자격증명을 설정하지 않는다. Codex thread는 등록 대화의 최초 추출에서 만들고 이후 일괄·개별·피드백 추출에 같은 thread를 재개한다. read-only sandbox, shell 비활성화, web search 비활성화를 사용한다. 이미지는 요청별 임시 디렉터리에서 처리한 뒤 삭제한다. Codex 자체 인증·세션 저장 파일은 전용 계정의 보존 정책으로 관리한다.

OpenAI 실행기는 Responses API의 이미지 입력·structured output·function calling을 사용한다. 실행기 내부에서 도구 요청을 받아 동일한 Spring Tool API로 전달하고, 결과를 모델에 돌려준다. `store: false`를 사용한다. API 이용 요금은 구독과 별도다.

## 등록 대화

첫 사진 추가 시 Spring이 Runtime에 세션을 생성한다. `POST /sessions`는 공유 비밀값으로만 호출하며, 최초 추출에서 Codex thread를 열고 다음 요청은 `resumeThread`로 이어간다. OpenAI는 `store: false`를 유지하며 Node 메모리의 대화 이력을 이어 보낸다. 과거 사진 바이너리는 이력에서 제거하고 현재 요청의 사진만 전달한다.

`POST /infer/slip`의 대화 요청은 세션 ID와 최대 10개의 사진 ID·이미지·선택 피드백을 받는다. 한 번의 요청이 한 turn이며 미처리 사진은 일괄 처리하고 개별·피드백 요청은 해당 사진만 처리한다. 응답의 사진 ID 집합은 요청과 정확히 같아야 한다. 읽을 수 없는 사진은 개별 `result: null`로 표시한다. 매 turn 새로운 도구 토큰과 현재 deadline을 MCP 서버에 전달하고 후보 ID는 이번 실행에서 다시 조회한다.

대화는 30분 비활성·30회 추출·최대 100개 동시 보관으로 제한한다. OpenAI 대화 이력은 2MB 상한을 적용한다. 창을 닫으면 `DELETE /sessions/{sessionId}`로 폐기하며 서버 재시작·만료 시 창을 다시 연다. 추출 완료 뒤 사람이 검토·저장하는 기존 흐름은 유지한다. 일괄 요청은 사진별 8MB·전체 multipart 82MB, 내부 Base64 JSON 112MB까지 허용한다. 큰 묶음은 양쪽의 실행 시간·도구 호출 한도를 운영 상황에 맞춰 조정한다.

## 계약과 실행 제한

- `POST /infer/slip`: Spring 전용. 공유 비밀값으로 인증하며 공개 프론트에서 호출하지 않는다.
- Spring의 등록 대화 API는 사용자 소유권과 사진 형식·해상도·크기를 검증한다. 단건 `/api/ai/slips/extract`도 호환용으로 유지한다.
- Spring이 발급한 실행별 토큰만 `search_varieties`, `search_partners`에 접근할 수 있다. 토큰은 모델 프롬프트에 포함하지 않는다.
- Codex는 이 Node 프로세스가 제공하는 STDIO MCP 서버를 통해 검색하고, OpenAI는 function calling으로 동일한 도구 클라이언트를 호출한다. Codex의 `approval_policy=never`는 승인 질문을 띄우지 않는 정책이다. 무인 실행에 허용한 두 조회 도구에만 `tools.<tool>.approval_mode=approve`를 지정하고 MCP에 읽기 전용 annotation을 제공한다. 전체 MCP 서버나 변경 도구에 일괄 승인을 적용하지 않는다.
- 동시 실행은 프로세스당 1건. 실행 중 요청은 즉시 `429 AI_BUSY`. 별도 MQ나 대기열은 없다. Spring도 추출 실행을 1건으로 제한하며 도구 콜백에는 이 실행 잠금을 걸지 않는다.
- 전체 deadline과 호출 횟수 상한을 적용한다. 취소·타임아웃 후 provider가 종료되기 전 실행 슬롯을 해제하지 않는다.
- 결과는 `contracts/slip-extraction.schema.json`으로 검사하고 Spring에서 값·조회 후보 ID·날짜·금액을 다시 검증한다.
- 로그에는 시작 정보와 요청 ID·실행기·설정 모델·소요시간·성공 여부·실패 코드 및 단계별 처리 메타데이터를 기록한다. 프롬프트·사진·도구 검색어·토큰은 기록하지 않는다.
- 내부 HTTP는 전용 네트워크에서만 허용한다. 외부 네트워크를 지나면 TLS를 적용한다.

## 검증

```sh
npm run check
npm test
```

테스트는 로컬 mock HTTP와 가짜 Codex runner, 실제 STDIO MCP 연결을 사용한다. 외부 모델 호출과 개인 계정 인증은 수행하지 않는다.

참고: [Codex SDK](https://learn.chatgpt.com/docs/codex-sdk), [MCP](https://learn.chatgpt.com/docs/extend/mcp?surface=cli), [OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling).
