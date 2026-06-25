# Frontend src Directory Guide

이 문서는 `frontend/src`의 현재 디렉터리 구조와 파일 배치 기준을 한눈에 보기 위한 안내서다.

## 전체 구조

```text
src/
  app/                    Next.js App Router 라우트 진입점
  entities/               여러 feature가 공유하는 도메인 타입
  features/               기능 단위 화면, 상태, API, 유틸
  shared/                 앱 전역 공통 기반 코드
  widgets/                여러 페이지를 감싸는 큰 UI 조각
```

현재 실제 코드는 위 5개 축을 기준으로 구성한다. `components/`, `lib/`, `types/` 같은 이전 구조 디렉터리는 사용하지 않는다.

## app

`app/`은 URL 라우팅 진입점이다. 페이지 파일은 가능한 얇게 유지한다.

주요 역할:

- route params/search params 해석
- 서버 컴포넌트에서 필요한 초기 데이터 조회
- feature page 컴포넌트 호출
- `dynamic`, metadata 같은 Next.js 라우트 설정

예시:

```text
app/
  page.tsx
  layout.tsx
  globals.css
  farm-status/page.tsx
  orchid-groups/page.tsx
  work-records/page.tsx
  sales/page.tsx
  print/page.tsx
  print/sales-slips/[salesSlipId]/page.tsx
```

`app/*/page.tsx` 안에 긴 UI 구현이나 직접 fetch 로직을 늘리지 않는다. 필요한 API는 feature의 `get...` 함수로 감싼 뒤 사용한다.

## features

`features/`는 화면/기능 단위 구현의 중심이다.

공통 하위 구조:

```text
features/{feature-name}/
  api/        백엔드 API 호출 함수
  lib/        순수 유틸, 폼 변환, 표시용 계산
  model/      타입, 상태 hook, 화면 상태 관리
  ui/         page/view 컴포넌트와 하위 UI 컴포넌트
  index.ts    외부에서 사용할 공개 export
```

현재 feature:

```text
features/
  farm-status/          농장 현황 맵
  orchid-management/    난 묶음 관리 상세맵
  work-record/          작업 이력 등록/조회
  sales/                판매 전표/거래처 관리
  print/                A5 출력 화면
```

### feature import 원칙

`app`에서는 가급적 feature의 `index.ts`를 통해 가져온다.

```ts
import { getCustomers, getSalesSlips, SalesPage } from "@/features/sales";
```

feature 내부에서는 필요한 경우 같은 feature의 `api`, `lib`, `model`, `ui/components`를 상대 경로로 가져온다.

## entities

`entities/`는 여러 feature가 공유하는 도메인 타입을 둔다.

```text
entities/
  farm/
    types.ts
```

예시 타입:

- `House`
- `PhysicalBed`
- `BedZone`
- `OrchidGroup`
- `WorkRecord`
- `Customer`
- `SalesSlip`

새 feature에서 공통 도메인 타입이 필요하면 `@/entities/farm/types`에서 import한다.

## shared

`shared/`는 특정 feature에 속하지 않는 전역 기반 코드다.

```text
shared/
  api/
    client.ts
```

현재 `client.ts`는 다음을 제공한다.

- `API_BASE_URL`
- `fetchApi<T>()`
- 공통 API 성공/에러 응답 타입

feature의 조회 API는 가능하면 `fetchApi<T>()`를 사용하고, mutation처럼 세밀한 에러 처리가 필요하면 feature `api` 파일에서 별도 request helper를 둔다.

## widgets

`widgets/`는 여러 페이지를 감싸는 큰 UI 조각이다.

```text
widgets/
  app-shell/
    AppShell.tsx
```

`AppShell`은 사이드바, 모바일 헤더, 페이지 공통 레이아웃을 담당한다. 개별 feature의 업무 UI는 여기 넣지 않는다.

## feature별 현재 구조

### farm-status

```text
features/farm-status/
  api/farmStatusApi.ts
  lib/farmStatusView.ts
  model/types.ts
  model/useFarmStatusMap.ts
  ui/FarmStatusPage.tsx
  ui/FarmStatusMap.tsx
  ui/components/
```

농장 현황 맵의 조회 API, 맵 상태, 화면 렌더링을 분리한다.

### orchid-management

```text
features/orchid-management/
  api/orchidManagementApi.ts
  lib/orchidManagementUtils.ts
  model/types.ts
  model/useOrchidManagementMap.ts
  ui/OrchidManagementPage.tsx
  ui/OrchidManagementMap.tsx
  ui/components/
```

난 묶음 관리 상세맵, 선택 상태, 생성/수정/삭제/이동 UI와 API를 담당한다.

### work-record

```text
features/work-record/
  api/workRecordApi.ts
  lib/workRecordForm.ts
  model/types.ts
  model/useWorkRecordManager.ts
  ui/WorkRecordManager.tsx
  ui/components/
```

작업 이력 등록 폼과 이력 목록을 담당한다.

### sales

```text
features/sales/
  api/salesApi.ts
  lib/salesForm.ts
  model/types.ts
  model/useSalesManager.ts
  ui/SalesPage.tsx
  ui/SalesManager.tsx
  ui/components/
```

거래처 등록, 판매 전표 생성, 판매 전표 목록을 담당한다.

### print

```text
features/print/
  api/printApi.ts
  ui/PrintPage.tsx
  ui/SalesSlipPrintView.tsx
```

출력 목록과 판매 전표 A5 출력 화면을 담당한다.

## 새 화면 추가 기준

1. `app/{route}/page.tsx`를 만든다.
2. `features/{feature}/api`에 서버 데이터 조회 함수를 만든다.
3. `features/{feature}/ui/{FeaturePage}.tsx`에 화면 UI를 둔다.
4. 상태가 복잡하면 `model/use...` hook으로 분리한다.
5. 외부에서 쓰는 항목은 `features/{feature}/index.ts`에서 export한다.

## 검증

프론트 구조 변경 후에는 다음을 실행한다.

```bash
npm run lint
npm run build
```
