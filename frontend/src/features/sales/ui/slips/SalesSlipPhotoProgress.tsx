"use client";

import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import type { SalesSlipReadingProgress } from "../../api/types";

export function SalesSlipPhotoProgress({
  operation,
  readingProgress,
  readingProgressUnavailable,
  preparingReadingSession,
  activePhotos,
  onSelectPhoto,
  extractionProgress,
  regionProcessingMode = "automatic",
  regionProcessingProgress,
}: {
  operation: "upload" | "prepare" | "read" | "save" | "cancel";
  readingProgress?: SalesSlipReadingProgress;
  readingProgressUnavailable: boolean;
  preparingReadingSession: boolean;
  activePhotos: { id: string; fileName: string }[];
  onSelectPhoto?: (id: string) => void;
  extractionProgress?: {
    completed: number;
    total: number;
    batchIndex: number;
    batchCount: number;
  };
  regionProcessingMode?: "automatic" | "manual";
  regionProcessingProgress?: {
    completed: number;
    total: number;
    photoIds: string[];
  };
}) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const started = performance.now();
    const timer = window.setInterval(() => {
      setSeconds(Math.floor((performance.now() - started) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);
  const activity = readingProgress?.activity;
  const lookup = activity?.latestLookup;
  const readTitle = preparingReadingSession
    ? "AI 입력 준비 중"
    : readingProgress?.stage === "DONE"
      ? "입력 결과 받는 중"
      : readingProgress?.stage === "VALIDATING"
        ? "인식 결과 확인 중"
        : activity?.searching
          ? "거래처·품종 후보 검색 중"
          : readingProgress?.stage === "READING"
            ? "전표 내용 분석 중"
            : "전표 전송·응답 대기 중";
  const title =
    operation === "prepare"
      ? regionProcessingMode === "manual"
        ? "전표 사진 만드는 중"
        : `전표 찾는 중${regionProcessingProgress ? ` (${regionProcessingProgress.completed}/${regionProcessingProgress.total}장 완료)` : ""}`
      : operation === "cancel"
        ? "자동 입력 중지 중"
        : operation === "read"
          ? `${readTitle}${extractionProgress ? ` (${extractionProgress.completed}/${extractionProgress.total}개 완료${extractionProgress.batchCount > 1 ? ` · ${extractionProgress.batchIndex}/${extractionProgress.batchCount}묶음` : ""})` : ""}`
          : operation === "upload"
            ? "사진 준비 중"
            : "전표 저장 중";
  const description =
    operation === "prepare"
      ? regionProcessingMode === "manual"
        ? "선택한 영역을 잘라 전표 사진을 만들고 있습니다."
        : "사진에서 전표 영역을 찾고 있습니다. 자동 입력은 영역 찾기를 마치면 내용 읽기로 이어집니다."
      : operation === "cancel"
        ? "처리 종료를 확인하고 있습니다. 완료되면 다시 요청할 수 있습니다."
        : operation === "read"
          ? "표시된 전표를 한 번의 요청으로 읽고 있습니다. 아래 이름을 눌러 사진을 확인할 수 있습니다."
          : operation === "upload"
            ? "선택한 사진을 불러오고 있습니다."
            : "저장 결과를 확인하고 있습니다. 잠시 기다려주세요.";
  return (
    <div className="flex shrink-0 items-start gap-3 border-b border-[#cfe0d2] bg-[#eaf6ed] px-4 py-3 text-[#234b31]">
      <LoaderCircle
        className="mt-0.5 h-5 w-5 shrink-0 animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        <div role="status" aria-atomic="true">
          <p className="text-sm font-semibold">{title}</p>
          <p className="mt-0.5 text-xs text-[#526158]">{description}</p>
          {operation === "read" && lookup ? (
            <p className="mt-1 truncate text-xs" title={lookup.query ?? ""}>
              최근 검색: {lookup.tool === "search_partners" ? "거래처" : "품종"}
              {" · "}
              {lookup.query}
            </p>
          ) : null}
          {operation === "read" && activity?.summary ? (
            <p
              className="mt-1 line-clamp-3 text-xs break-words whitespace-pre-wrap text-[#526158]"
              title={activity.summary}
            >
              AI 진행 설명: {activity.summary}
            </p>
          ) : null}
          {operation === "read" && readingProgressUnavailable ? (
            <p className="mt-1 text-xs text-[#835719]">
              세부 진행 상태를 불러오지 못했습니다. 자동 입력 결과를 기다리고
              있습니다.
            </p>
          ) : null}
        </div>
        {activePhotos.length ? (
          <ul
            aria-label="현재 처리 중인 사진·전표"
            className="mt-2 flex max-h-20 flex-wrap gap-1.5 overflow-y-auto"
          >
            {activePhotos.map((photo) => (
              <li key={photo.id} className="max-w-full min-w-0">
                {onSelectPhoto ? (
                  <button
                    type="button"
                    onClick={() => onSelectPhoto(photo.id)}
                    title={photo.fileName}
                    className="block max-w-full truncate rounded border border-[#bedfc7] bg-white px-2 py-1 text-xs hover:bg-[#f7faf6] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447]"
                  >
                    {photo.fileName}
                  </button>
                ) : (
                  <span
                    title={photo.fileName}
                    className="block max-w-full truncate rounded border border-[#bedfc7] bg-white px-2 py-1 text-xs"
                  >
                    {photo.fileName}
                  </span>
                )}
              </li>
            ))}
          </ul>
        ) : null}
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
