# 프론트엔드 src 리팩터링 결과

업로드한 `src` 폴더를 기준으로 Next.js App Router에 맞는 기능 중심 구조로 재배치했다.

## 핵심 방향

- `app/`: 라우팅 진입점. 가능한 얇게 유지한다.
- `features/`: 화면/기능 단위 구현을 배치한다.
- `entities/`: 여러 feature에서 공유하는 도메인 타입을 둔다.
- `shared/`: API 클라이언트처럼 앱 전체에서 공유되는 기반 코드를 둔다.
- `widgets/`: 앱 쉘/사이드바처럼 여러 페이지를 감싸는 큰 UI 조각을 둔다.

## 주요 변경

```text
components/farm-map/farm-status-map.tsx
→ features/farm-status/ui/components/* 로 분리

components/orchid-group/orchid-management-map.tsx
→ features/orchid-management/ui/OrchidManagementMap.tsx

components/sales/sales-manager.tsx
→ features/sales/ui/SalesManager.tsx

components/work-record/work-record-manager.tsx
→ features/work-record/ui/WorkRecordManager.tsx

components/print/sales-slip-print-view.tsx
→ features/print/ui/SalesSlipPrintView.tsx

components/layout/app-shell.tsx
→ widgets/app-shell/AppShell.tsx

lib/api.ts
→ shared/api/client.ts

types/farm.ts
→ entities/farm/types.ts
```

## 농장 현황 기능 구조

```text
features/farm-status/
  api/
    farmStatusApi.ts
  lib/
    farmStatusView.ts
  model/
    types.ts
  ui/
    FarmStatusPage.tsx
    components/
      FarmStatusMap.tsx
      FarmMapCanvas.tsx
      FarmOverviewLayer.tsx
      HouseDetailLayer.tsx
      MapChrome.tsx
      StatusPanels.tsx
```

`FarmStatusMap.tsx`는 상태 관리와 이벤트 흐름만 담당하고, 실제 렌더링 요소는 `FarmMapCanvas`, `FarmOverviewLayer`, `HouseDetailLayer`, `StatusPanels`, `MapChrome`으로 분리했다.

## 적용 방법

기존 프로젝트의 `src` 폴더를 교체하기 전에 백업 또는 별도 브랜치를 만든다.

```bash
git checkout -b refactor/frontend-feature-structure
```

이 압축 파일의 내용을 기존 `src` 폴더에 덮어쓴 뒤 다음을 확인한다.

```bash
npm run lint
npm run build
```

프로젝트에 `tsconfig.json`의 `paths`가 다음처럼 잡혀 있어야 `@/...` import가 정상 동작한다.

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```
