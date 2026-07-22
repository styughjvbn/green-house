# 난 묶음 관리 맵 성능 E2E

난 묶음 관리 맵 리팩터링 전후를 같은 데이터와 Chromium 환경에서 비교한다.

## 환경

- Next.js production build
- 실제 Spring Boot jar
- PostgreSQL 전용 DB `greenhouse_map_e2e`
- Playwright Chromium
- workers 1, viewport 1440×1000
- 기본 워밍업 5회, 측정 20회

전용 DB 이름은 안전을 위해 반드시 `_map_e2e`로 끝나야 한다. prepare와 baseline
명령은 해당 DB를 삭제 후 다시 생성하므로 운영·개발 DB 이름을 지정하지 않는다.

## 실행

최초 한 번 Chromium을 설치한다.

```bash
cd frontend
npm run e2e:map:install
```

데이터만 다시 준비한다.

```bash
npm run e2e:map:prepare
```

DB 초기화, seed, 백엔드 실행, production build, 프론트 실행, Playwright 측정을
한 번에 수행한다.

```bash
npm run e2e:map:baseline
```

기존 E2E DB 상태를 유지한 채 브라우저와 Playwright Inspector를 열어 디버깅한다.
DB reset, DB 생성, Flyway migration, seed는 실행하지 않는다. 백엔드·프론트엔드는
현재 코드로 다시 build한 뒤 기존 `greenhouse_map_e2e` DB에 연결한다.

```bash
npm run e2e:map:debug
```

디버그 명령은 워밍업 0회, 측정 1회, 연속 이동 1회로 축소하며 결과를
`frontend/e2e-results/map-performance-debug.json`에 저장한다. 기존 DB에 seed가
없거나 스키마가 현재 코드와 호환되지 않으면 검증 또는 백엔드 시작 단계에서 실패한다.

실행 스크립트 옵션을 직접 조합할 수도 있다.

```bash
bash ../scripts/e2e/run-map-performance-baseline --reuse-db --headed
bash ../scripts/e2e/run-map-performance-baseline --reuse-db --headed --debug
```

- `--reuse-db`: 초기화·마이그레이션·seed 생략
- `--headed`: Chromium 화면 표시
- `--debug`: Playwright Inspector 활성화

결과는 `frontend/e2e-results/map-performance-before.json`에 저장한다. 실패 시
trace, 영상, 스크린샷은 `frontend/test-results/map-performance`에 남는다.

리팩터링 후에는 결과 경로만 바꿔 같은 테스트를 실행한다.

```bash
MAP_E2E_RESULT_FILE=e2e-results/map-performance-after.json \
npm run e2e:map:baseline
```

짧은 코드 검증이 필요하면 반복 수를 줄일 수 있다. 비교 기준값은 반드시 기본값으로
다시 실행한다.

```bash
MAP_E2E_WARMUP=1 \
MAP_E2E_ITERATIONS=1 \
MAP_E2E_CONTINUOUS_STEPS=1 \
npm run e2e:map:baseline
```

## 환경 변수

| 변수                       |                                    기본값 | 역할                           |
| -------------------------- | ----------------------------------------: | ------------------------------ |
| `MAP_E2E_WARMUP`           |                                       `5` | 시나리오별 워밍업 횟수(0 허용) |
| `MAP_E2E_ITERATIONS`       |                                      `20` | 시나리오별 측정 횟수           |
| `MAP_E2E_CONTINUOUS_STEPS` |                                      `10` | 연속 이동의 전진·후진 횟수     |
| `MAP_E2E_DB_NAME`          |                      `greenhouse_map_e2e` | E2E 전용 DB 이름               |
| `MAP_E2E_DB_HOST`          |                               `127.0.0.1` | PostgreSQL 호스트              |
| `MAP_E2E_DB_PORT`          |                                    `5432` | PostgreSQL 포트                |
| `MAP_E2E_DB_USER`          |                              `greenhouse` | PostgreSQL 사용자              |
| `MAP_E2E_DB_PASSWORD`      |                              `greenhouse` | PostgreSQL 비밀번호            |
| `MAP_E2E_BACKEND_PORT`     |                                   `18080` | 측정용 백엔드 포트             |
| `MAP_E2E_FRONTEND_PORT`    |                                   `13000` | 측정용 프론트 포트             |
| `MAP_E2E_RESULT_FILE`      | `e2e-results/map-performance-before.json` | 결과 JSON 경로                 |

## seed 규칙

- 15동, 동별 3다이, 다이별 좌·우 구역
- 각 구역에서 실제 `positionUnitCount`의 모든 칸에 난 묶음 1개
- 난 묶음당 직접 60건, 구역 20건, 다이 10건, 동 10건
- 난 묶음별 최종 통합 이력 100건
- 고정된 `E2E-DIRECT`, `E2E-ZONE`, `E2E-BED`, `E2E-HOUSE` 작업명

seed 완료 시 난 묶음 수와 이력 최소·최대 건수를 출력한다.

## 결과 해석

각 시나리오는 클릭부터 이력 렌더 완료까지의 p50, p95, 최댓값과 API 요청 수,
API 응답 시간, payload 크기를 기록한다. 정확성 결과에는 직접·전파 범위별 건수,
통합 이력 중복, 범위 밖 데이터, 최근 작업 표본, 요청 중복을 포함한다.

제품 동작에는 계측 분기를 추가하지 않는다. 선택과 이력 확인에는 리팩터링 후에도
유지할 테스트 계약인 `map-root`, `physical-bed-{id}`, `bed-zone-{id}`,
`orchid-group-{id}`, `selected-history`, `history-item`을 사용한다. 응답 완료 후 두
animation frame이 지난 시점을 렌더 완료로 측정한다.

선택 범위 불일치, 범위 밖·누락 작업, 최신 작업 미표시, HTTP·콘솔 오류와 최종 선택
범위 불일치는 실패 조건이다. API 요청·중복 요청 수, 중복 응답 수, payload 크기,
렌더 시간, 마운트 수와 DOM 증감은 개선 여부를 비교하는 JSON 지표이며 실패 조건으로
사용하지 않는다.

맵은 현재 전체 다이 슬라이드를 DOM에 유지한다. `withinVisibleBedLimit`와 `domGrowth`는
가상화 전후를 비교하기 위한 관측값이다. 최종 선택 데이터 준비 여부와 실제 드래그의
선택 보존 여부는 계속 검증한다.
