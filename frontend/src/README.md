# Frontend src Guide

`frontend/src`는 Next.js App Router 위에서 기능 중심 구조로 관리한다.

## 구조

```text
src/
  app/        라우트 진입점
  features/   기능 단위 구현
  entities/   여러 기능이 공유하는 도메인 타입
  shared/     전역 공통 기반 코드
  widgets/    앱 쉘처럼 큰 공통 UI
```

이전 구조인 `components/`, `lib/`, `types/`는 사용하지 않는다.

## app

`app/*/page.tsx`는 얇게 유지한다.

담당 범위:

- route params/search params 해석
- 초기 데이터 조회
- feature page 컴포넌트 호출
- `dynamic`, metadata 같은 Next.js 라우트 설정

예시:

```ts
import { getCustomers, getSalesSlips, SalesPage } from "@/features/sales";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [customers, salesSlips] = await Promise.all([
    getCustomers(),
    getSalesSlips(),
  ]);

  return <SalesPage customers={customers} salesSlips={salesSlips} />;
}
```

## features

기능별 구현은 `features/{feature}` 아래에 둔다.

```text
features/{feature}/
  api/       API 호출 함수
  lib/       순수 유틸, 폼 변환, 표시 계산
  model/     타입, 상태 hook
  ui/        화면 컴포넌트
  index.ts   외부 공개 export
```

현재 feature:

- `farm-status`: 농장 현황 맵
- `orchid-management`: 난 묶음 관리 상세맵
- `work-record`: 작업 이력
- `sales`: 판매 전표/거래처
- `print`: A5 출력

`app`에서는 feature 내부 경로보다 `index.ts`를 통해 import한다.

```ts
import { OrchidManagementPage, getHouse } from "@/features/orchid-management";
```

## shared and entities

공유 도메인 타입은 `entities`에서 가져온다.

```ts
import type { House, OrchidGroup, SalesSlip } from "@/entities/farm/types";
```

공통 API 클라이언트는 `shared`에 둔다.

```ts
import { API_BASE_URL, fetchApi } from "@/shared/api/client";
```

조회 API는 가능하면 `fetchApi<T>()`를 사용한다. 생성/수정/삭제처럼 세밀한 에러 처리가 필요한 경우 feature의 `api` 파일에 별도 request helper를 둔다.

## widgets

`widgets/app-shell/AppShell.tsx`는 사이드바, 모바일 헤더, 페이지 공통 레이아웃을 담당한다. 개별 업무 화면은 feature에 둔다.

## 새 화면 추가

1. `app/{route}/page.tsx`를 만든다.
2. `features/{feature}/api`에 `get...` 조회 함수를 만든다.
3. `features/{feature}/ui/{FeaturePage}.tsx`에 화면을 둔다.
4. 상태가 복잡하면 `model/use...` hook으로 분리한다.
5. 외부에서 쓰는 항목은 `features/{feature}/index.ts`에서 export한다.

## 검증

```bash
npm run lint
npm run build
```
