# 난 농장 관리 시스템 MVP

`농장 관리 시스템 개발 계획.md`를 기준으로 구현한 비닐하우스 난 농장 관리 MVP입니다.

## 구현 범위

- 15개 동 / 45개 배드 기본 구조
- 위에서 내려다보는 도식형 농장 맵 UI
- 동/배드 선택 및 우측 배드 상세 패널
- 배드별 난 묶음 등록, 수정, 삭제
- 농약, 비료, 분갈이, 상태 기록, 일반 메모 작업 이력 등록
- 작업 이력 기반 최근 농약/비료/분갈이 일자 계산
- 판매 전표 등록 및 품목 금액/총액 자동 계산
- 배드 관리표와 전표 브라우저 출력 지원
- Prisma 데이터 모델과 시드 스크립트 초안

## 기술 스택

- Next.js
- TypeScript
- Tailwind CSS
- Prisma
- SQLite 개발 DB 예시 (`DATABASE_URL="file:./dev.db"`)

## 실행 방법

```bash
npm install
cp .env.example .env
npx prisma generate
npm run dev
```

## 빌드

```bash
npm run build
```

## 시드 데이터

```bash
cp .env.example .env
npx prisma db push
npm run prisma:seed
```
