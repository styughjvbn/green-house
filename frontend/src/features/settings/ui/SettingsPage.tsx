"use client";

import {
  FONT_SCALE_OPTIONS,
  formatFontScale,
  MAX_FONT_SCALE,
  MIN_FONT_SCALE,
} from "../lib/fontScale";
import { useFontScaleSetting } from "../model/useFontScaleSetting";

export function SettingsPage() {
  const { fontScale, resetFontScale, updateFontScale } = useFontScaleSetting();

  return (
    <main className="space-y-5">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">설정</p>
        <h1 className="mt-1 text-2xl font-semibold">시스템 설정</h1>
        <p className="mt-1 text-sm text-[#5c6a60]">
          서비스 전체 글자 크기를 조정합니다.
        </p>
      </section>

      <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold">글자 크기</h2>
            <p className="mt-1 text-sm text-[#5c6a60]">
              변경 즉시 전체 화면에 적용되고 브라우저에 저장됩니다.
            </p>
          </div>
          <div className="rounded-md bg-[#eef7ec] px-3 py-2 text-sm font-semibold text-[#246b38]">
            현재 {formatFontScale(fontScale)}
          </div>
        </div>

        <div className="mt-5 space-y-4">
          <input
            aria-label="글자 크기 배율"
            className="w-full accent-[#159447]"
            max={MAX_FONT_SCALE}
            min={MIN_FONT_SCALE}
            step={0.05}
            type="range"
            value={fontScale}
            onChange={(event) => updateFontScale(Number(event.target.value))}
          />

          <div className="grid gap-2 sm:grid-cols-4">
            {FONT_SCALE_OPTIONS.map((option) => (
              <button
                key={option.value}
                className={`rounded-md border px-4 py-2 text-sm font-semibold ${
                  Math.abs(fontScale - option.value) < 0.01
                    ? "border-[#159447] bg-[#eef7ec] text-[#246b38]"
                    : "border-[#d7ddd4] bg-white text-[#435047]"
                }`}
                type="button"
                onClick={() => updateFontScale(option.value)}
              >
                {option.label} {formatFontScale(option.value)}
              </button>
            ))}
          </div>

          <div className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-4">
            <p className="text-sm font-semibold text-[#435047]">미리보기</p>
            <p className="mt-2 text-base text-[#1f2a24]">
              난 묶음, 작업 이력, 판매 전표 화면의 글자 크기가 같은 비율로
              변경됩니다.
            </p>
          </div>

          <button
            className="rounded-md border border-[#cfd8cc] px-4 py-2 text-sm font-semibold text-[#435047]"
            type="button"
            onClick={resetFontScale}
          >
            기본값으로 되돌리기
          </button>
        </div>
      </section>
    </main>
  );
}
