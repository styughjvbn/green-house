"use client";

import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";

export function SalesSlipPhotoProgress({
  operation,
  readingCount,
  preparationMode = "automatic",
}: {
  operation: "upload" | "prepare" | "read" | "save" | "cancel";
  readingCount: number;
  preparationMode?: "automatic" | "manual";
}) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const started = performance.now();
    const timer = window.setInterval(() => {
      setSeconds(Math.floor((performance.now() - started) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);
  const title =
    operation === "prepare"
      ? preparationMode === "manual"
        ? "문서 영역 적용 중"
        : "문서 영역 자동 지정 중"
      : operation === "cancel"
        ? "자동 입력 중지 중"
        : operation === "read"
          ? `사진 ${readingCount}장 자동 입력 중`
          : operation === "upload"
            ? "사진 준비 중"
            : "전표 저장 중";
  const description =
    operation === "prepare"
      ? preparationMode === "manual"
        ? "지정한 영역으로 문서 사진을 만들고 있습니다."
        : "사진에서 자동 입력에 사용할 문서 영역을 찾고 있습니다."
      : operation === "cancel"
        ? "처리 종료를 확인하고 있습니다. 완료되면 다시 요청할 수 있습니다."
        : operation === "read"
          ? "사진을 분석해 입력값을 가져오고 있습니다. 여러 장은 시간이 더 걸릴 수 있습니다."
          : operation === "upload"
            ? "선택한 사진을 확인하고 등록 대화를 준비하고 있습니다."
            : "저장 결과를 확인하고 있습니다. 잠시 기다려주세요.";
  return (
    <div className="flex shrink-0 items-center gap-3 border-b border-[#cfe0d2] bg-[#eaf6ed] px-4 py-3 text-[#234b31]">
      <LoaderCircle
        className="h-5 w-5 shrink-0 animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1" role="status" aria-atomic="true">
        <p className="text-sm font-semibold">{title}</p>
        <p className="mt-0.5 text-xs text-[#526158]">{description}</p>
      </div>
      <span
        aria-hidden="true"
        className="shrink-0 text-xs text-[#526158] tabular-nums"
      >
        {seconds < 60
          ? `${seconds}초`
          : `${Math.floor(seconds / 60)}분 ${seconds % 60}초`}{" "}
        경과
      </span>
    </div>
  );
}
