"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { LoaderCircle, X } from "lucide-react";
import { API_BASE_URL } from "@/shared/api/client";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import {
  prepareSalesSlipPhoto,
  removeSalesSlipImagePreview,
} from "../../api/salesApi";
import type { SalesSlipPhotoInput } from "../../api/types";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";

const buttonClass =
  "inline-flex h-9 items-center justify-center gap-2 rounded-md border border-[#cdd9cd] bg-white px-3 text-sm font-semibold text-[#24422e] hover:bg-[#f2f7f1] disabled:opacity-50";
const labels = {
  CORRECTED: "전체 보정",
  DOCUMENT: "분리된 문서",
  TABLE: "빈 행을 제거한 표",
};

export function SalesSlipImagePreparationPreview({
  photo,
  disabled,
}: {
  photo: SalesSlipPhotoInput;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [rotation, setRotation] = useState(-1);
  const opener = useRef<HTMLButtonElement>(null);
  const controller = useRef<AbortController | null>(null);
  const previewId = useRef<string | null>(null);
  const generation = useRef(0);
  function release() {
    generation.current += 1;
    controller.current?.abort();
    controller.current = null;
    if (previewId.current)
      void removeSalesSlipImagePreview(previewId.current).catch(() => {});
    previewId.current = null;
  }
  useEffect(() => () => release(), []);
  const mutation = useMutation({
    mutationFn: async (degrees: number) => {
      release();
      const version = generation.current;
      const abort = new AbortController();
      controller.current = abort;
      const result = await prepareSalesSlipPhoto(photo, degrees, abort.signal);
      if (generation.current !== version) {
        if (result.id)
          void removeSalesSlipImagePreview(result.id).catch(() => {});
        throw new DOMException("미리보기 닫힘", "AbortError");
      }
      previewId.current = result.id ?? null;
      return result;
    },
  });
  const result = mutation.data;
  function close() {
    release();
    mutation.reset();
    setOpen(false);
  }
  return (
    <>
      <button
        ref={opener}
        type="button"
        className={buttonClass}
        disabled={disabled}
        onClick={() => {
          setRotation(-1);
          setOpen(true);
          mutation.mutate(-1);
        }}
      >
        전처리 미리보기
      </button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (!next) close();
        }}
      >
        <DialogContent
          showCloseButton={false}
          overlayClassName="z-[1050]"
          className="z-[1051] flex max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-5xl flex-col overflow-hidden bg-white"
          onEscapeKeyDown={(event) => event.stopPropagation()}
          onInteractOutside={(event) => event.preventDefault()}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            opener.current?.focus();
          }}
        >
          <header className="flex items-center justify-between gap-3 border-b border-[#dce5dc] p-4">
            <div>
              <DialogTitle className="text-lg font-bold">
                전표 사진 전처리
              </DialogTitle>
              <DialogDescription className="mt-1 text-sm text-[#5c6a60]">
                검출 결과를 확인해 주세요. 자동 입력과 저장에는 반영되지
                않습니다.
              </DialogDescription>
            </div>
            <button
              type="button"
              className={buttonClass}
              aria-label="전처리 미리보기 닫기"
              onClick={close}
            >
              <X className="h-4 w-4" />
            </button>
          </header>
          <div className="flex flex-wrap items-center gap-2 px-4 pt-3">
            <label
              htmlFor="slip-preview-rotation"
              className="text-sm font-semibold"
            >
              회전
            </label>
            <select
              id="slip-preview-rotation"
              value={rotation}
              disabled={mutation.isPending}
              onChange={(event) => setRotation(Number(event.target.value))}
              className="h-9 rounded-md border border-[#cdd9cd] px-2 text-sm"
            >
              <option value={-1}>자동</option>
              {[0, 90, 180, 270].map((degrees) => (
                <option key={degrees} value={degrees}>
                  {degrees}° 시계 방향
                </option>
              ))}
            </select>
            <button
              type="button"
              className={buttonClass}
              disabled={mutation.isPending}
              onClick={() => mutation.mutate(rotation)}
            >
              다시 처리
            </button>
            <span className="text-xs text-[#5c6a60]">
              원본 보존 · 미리보기 5분 유지
            </span>
          </div>
          <div className="min-h-0 overflow-y-auto p-4">
            {mutation.isPending ? (
              <div
                role="status"
                className="flex items-center gap-2 rounded-md bg-[#f2f7f1] p-4 text-sm"
              >
                <LoaderCircle className="h-5 w-5 animate-spin motion-reduce:animate-none" />
                문서 보정·분리와 표의 빈 행을 처리하고 있습니다.
              </div>
            ) : null}
            {mutation.isError ? (
              <p role="alert" className="mb-3 text-sm text-red-700">
                {mutation.error instanceof Error
                  ? mutation.error.message
                  : "사진을 처리하지 못했습니다."}
              </p>
            ) : null}
            {result ? (
              <div className="mb-4 rounded-md bg-[#f2f7f1] p-3 text-sm text-[#24422e]">
                <p>
                  문서 {result.documentCount}개 · 표 {result.tableCount}개 · 빈
                  행 {result.removedRows}개 제거
                </p>
                <p className="mt-1 text-xs">
                  {result.sourceWidth} × {result.sourceHeight} →{" "}
                  {result.workingWidth} × {result.workingHeight} px · 회전{" "}
                  {result.clockwiseDegrees}°
                </p>
                {result.reviewRequired ? (
                  <p className="mt-2 font-semibold">
                    문서 방향과 분리 영역을 확인해 주세요.
                  </p>
                ) : null}
                {result.warnings?.length ? (
                  <ul className="mt-2 list-disc pl-5">
                    {result.warnings.map((warning, index) => (
                      <li key={index}>{warning}</li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <PreviewCard
                title="원본"
                src={`data:${photo.contentType};base64,${photo.contentBase64}`}
              />
              {result?.images?.map((image, index) => (
                <PreviewCard
                  key={image.id}
                  title={`${labels[image.kind ?? "CORRECTED"]} ${index + 1}`}
                  src={`${API_BASE_URL}/sales-slips/image-previews/${result.id}/images/${image.id}`}
                />
              ))}
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function PreviewCard({ title, src }: { title: string; src: string }) {
  return (
    <section className="rounded-md border border-[#dce5dc] p-3">
      <h3 className="mb-2 text-sm font-semibold">{title}</h3>
      <SalesSlipPhotoViewer
        src={src}
        alt={title}
        className="h-80 w-full rounded bg-[#f7faf6] object-contain"
      />
    </section>
  );
}
