"use client";

import { useEffect, useId, useRef, useState } from "react";
import Image from "next/image";
import Panzoom from "@panzoom/panzoom";
import { Expand, Maximize, Minus, Plus, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";

export function SalesSlipPhotoViewer({
  src,
  alt,
  className,
  width = 900,
  height = 1200,
  onLoad,
}: {
  src: string;
  alt: string;
  className?: string;
  width?: number;
  height?: number;
  onLoad?: (width: number, height: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const opener = useRef<HTMLButtonElement>(null);
  return (
    <>
      <button
        ref={opener}
        type="button"
        aria-label={`${alt} 크게 보기`}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
        className="group relative block w-full cursor-zoom-in rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447]"
      >
        <Image
          src={src}
          alt={alt}
          width={width}
          height={height}
          unoptimized
          onLoad={(event) =>
            onLoad?.(
              event.currentTarget.naturalWidth,
              event.currentTarget.naturalHeight,
            )
          }
          className={className}
        />
        <span className="pointer-events-none absolute right-2 bottom-2 inline-flex items-center gap-1 rounded bg-black/65 px-2 py-1 text-xs font-semibold text-white">
          <Expand className="h-3.5 w-3.5" aria-hidden="true" /> 크게 보기
        </span>
      </button>
      {open ? (
        <PhotoZoomDialog
          key={src}
          src={src}
          alt={alt}
          onClose={() => setOpen(false)}
          onReturnFocus={() => opener.current?.focus()}
        />
      ) : null}
    </>
  );
}

function PhotoZoomDialog({
  src,
  alt,
  onClose,
  onReturnFocus,
}: {
  src: string;
  alt: string;
  onClose: () => void;
  onReturnFocus: () => void;
}) {
  const viewport = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLDivElement>(null);
  const controls = useRef<ReturnType<typeof Panzoom> | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);
  const [scale, setScale] = useState(1);
  const helpId = useId();
  useEffect(() => {
    const element = canvas.current;
    const area = viewport.current;
    if (!loaded || !element || !area) return;
    const instance = Panzoom(element, {
      minScale: 1,
      maxScale: 6,
      panOnlyWhenZoomed: true,
      canvas: true,
    });
    controls.current = instance;
    const updateScale = () => setScale(instance.getScale());
    const wheel = (event: WheelEvent) => instance.zoomWithWheel(event);
    element.addEventListener("panzoomchange", updateScale);
    area.addEventListener("wheel", wheel, { passive: false });
    return () => {
      area.removeEventListener("wheel", wheel);
      element.removeEventListener("panzoomchange", updateScale);
      instance.destroy();
      instance.resetStyle();
      controls.current = null;
    };
  }, [loaded]);
  const buttonClass =
    "flex h-10 min-w-10 items-center justify-center gap-1 rounded-md border border-white/25 px-2 text-sm text-white hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white disabled:opacity-40";
  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={helpId}
        overlayClassName="z-[1100] bg-black/80"
        className="z-[1101] flex h-[calc(100dvh-1rem)] w-[calc(100%-1rem)] max-w-none flex-col overflow-hidden rounded-lg bg-[#17211b] text-white"
        onEscapeKeyDown={(event) => event.stopPropagation()}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          onReturnFocus();
        }}
        onInteractOutside={(event) => event.preventDefault()}
      >
        <header className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-white/15 px-3 py-2">
          <DialogTitle className="text-base font-semibold">
            전표 사진
          </DialogTitle>
          <div
            role="group"
            aria-label="사진 확대·축소"
            className="flex items-center gap-2"
          >
            <button
              type="button"
              className={buttonClass}
              aria-label="축소"
              disabled={!loaded || scale <= 1.01}
              onClick={() => controls.current?.zoomOut()}
            >
              <Minus className="h-4 w-4" aria-hidden="true" />
            </button>
            <span
              className="w-12 text-center text-sm tabular-nums"
              aria-label="화면 맞춤 대비 확대 비율"
            >
              {Math.round(scale * 100)}%
            </span>
            <button
              type="button"
              className={buttonClass}
              aria-label="확대"
              disabled={!loaded || scale >= 5.99}
              onClick={() => controls.current?.zoomIn()}
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
            </button>
            <button
              type="button"
              className={buttonClass}
              disabled={!loaded}
              onClick={() => controls.current?.reset()}
            >
              <Maximize className="h-4 w-4" aria-hidden="true" />
              화면 맞춤
            </button>
            <button
              type="button"
              className={buttonClass}
              aria-label="사진 크게 보기 닫기"
              onClick={onClose}
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
        </header>
        <div
          ref={viewport}
          tabIndex={0}
          role="region"
          aria-label="확대 가능한 전표 사진"
          aria-describedby={helpId}
          className="relative min-h-0 flex-1 overflow-hidden overscroll-contain focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-white"
          onDoubleClick={() => {
            if (scale > 1) controls.current?.reset();
            else controls.current?.zoom(2);
          }}
          onKeyDown={(event) => {
            const instance = controls.current;
            if (!instance) return;
            if (
              [
                "+",
                "=",
                "-",
                "0",
                "ArrowLeft",
                "ArrowRight",
                "ArrowUp",
                "ArrowDown",
              ].includes(event.key)
            )
              event.preventDefault();
            if (event.key === "+" || event.key === "=") instance.zoomIn();
            if (event.key === "-") instance.zoomOut();
            if (event.key === "0") instance.reset();
            const pan = instance.getPan();
            const step = 60 / instance.getScale();
            if (event.key === "ArrowLeft") instance.pan(pan.x + step, pan.y);
            if (event.key === "ArrowRight") instance.pan(pan.x - step, pan.y);
            if (event.key === "ArrowUp") instance.pan(pan.x, pan.y + step);
            if (event.key === "ArrowDown") instance.pan(pan.x, pan.y - step);
          }}
        >
          <div
            ref={canvas}
            className="flex h-full w-full items-center justify-center"
          >
            <Image
              src={src}
              alt={alt}
              width={1600}
              height={1600}
              unoptimized
              draggable={false}
              onLoad={() => setLoaded(true)}
              onError={() => setFailed(true)}
              className="h-full w-full object-contain select-none"
            />
          </div>
          {!loaded ? (
            <p
              role={failed ? "alert" : "status"}
              className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm"
            >
              {failed
                ? "사진을 불러오지 못했습니다. 창을 닫고 다시 열어주세요."
                : "사진을 불러오는 중입니다."}
            </p>
          ) : null}
        </div>
        <DialogDescription
          id={helpId}
          className="shrink-0 border-t border-white/15 px-3 py-2 text-center text-xs text-white/75"
        >
          두 손가락이나 휠로 확대·축소 · 드래그로 이동 · 두 번 누르면 확대·화면
          맞춤
          <span className="sr-only">
            키보드: + 확대, - 축소, 0 화면 맞춤, 방향키 이동, Escape 닫기.
          </span>
        </DialogDescription>
      </DialogContent>
    </Dialog>
  );
}
