"use client";

import type { FarmZoomLevel } from "@/entities/farm/types";
import { zoomLabel } from "../../lib/farmStatusView";

export function MapControls({
  zoomLevel,
  onReset,
  onZoomIn,
  onZoomOut,
}: {
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}) {
  return (
    <div className="absolute bottom-16 left-4 z-20 flex flex-col gap-2">
      <button aria-label="확대" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow" onClick={onZoomIn} type="button">
        +
      </button>
      <button aria-label="축소" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow" onClick={onZoomOut} type="button">
        -
      </button>
      <button aria-label="전체 보기" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-sm font-semibold text-[#2b3a2f] shadow" onClick={onReset} type="button">
        ⌖
      </button>
      <span className="rounded-md bg-white px-2 py-1 text-center text-xs font-semibold text-[#405246] shadow">{zoomLabel(zoomLevel)}</span>
    </div>
  );
}

export function Legend() {
  return (
    <div className="absolute bottom-4 left-16 z-20 flex flex-wrap gap-3 rounded-md bg-white/95 px-3 py-2 text-xs shadow">
      <LegendItem color="bg-[#20a64d]" label="정상" />
      <LegendItem color="bg-[#f59e0b]" label="주의" />
      <LegendItem color="bg-[#ef4444]" label="이상" />
      <LegendItem color="bg-[#1976f3]" label="선택" />
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`inline-block h-2.5 w-2.5 rounded-full ${color}`} />
      {label}
    </span>
  );
}

export function MapBackdrop() {
  return (
    <>
      <div className="absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.22),rgba(255,255,255,0)_38%)]" />
      <div className="absolute -left-6 right-[-20px] top-24 h-10 rotate-[-4deg] bg-[#d7aa5d]" />
      <div className="absolute -left-6 right-[-20px] top-27 h-4 rotate-[-4deg] bg-[#f0d59a]" />
      <div className="absolute left-8 top-8 h-16 w-32 rotate-[-12deg] rounded-md border border-[#cfd6cf] bg-[#ecefe9] shadow-md">
        <div className="grid h-full grid-cols-4 gap-px p-2 opacity-70">
          {Array.from({ length: 12 }, (_, index) => (
            <span key={index} className="bg-white" />
          ))}
        </div>
      </div>
      <MapTree className="left-8 bottom-10" />
      <MapTree className="right-12 top-10" />
      <MapTree className="right-16 bottom-8" />
      <MapTree className="left-60 bottom-6" />
    </>
  );
}

function MapTree({ className }: { className: string }) {
  return (
    <div className={`absolute z-0 flex gap-1 ${className}`}>
      <span className="h-8 w-8 rounded-full bg-[#6b9f45] opacity-80" />
      <span className="mt-3 h-6 w-6 rounded-full bg-[#4f8538] opacity-80" />
      <span className="mt-1 h-7 w-7 rounded-full bg-[#7aaa4f] opacity-80" />
    </div>
  );
}
