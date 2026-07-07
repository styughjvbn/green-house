"use client";

import type { DragEvent, MouseEvent, PointerEvent } from "react";
import type { OrchidGroup } from "@/entities/farm/types";

export default function OrchidGroupBlock({
  draggable,
  heightPx,
  orchidGroup,
  selected,
  onDragEnd,
  onDragStart,
  onSelect,
}: {
  draggable: boolean;
  heightPx: number;
  orchidGroup: OrchidGroup;
  selected: boolean;
  onDragEnd: () => void;
  onDragStart: () => void;
  onSelect: () => void;
}) {
  const warning =
    orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";
  const density = resolveDensity(heightPx);
  const detailText = [
    orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null,
    orchidGroup.potSize,
  ]
    .filter(Boolean)
    .join(" / ");

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    event.stopPropagation();
    onSelect();
  }

  function handleDragStart(event: DragEvent<HTMLDivElement>) {
    event.stopPropagation();
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", String(orchidGroup.id));
    onDragStart();
  }

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType === "mouse") {
      return;
    }
    event.stopPropagation();
    onSelect();
  }

  return (
    <div
      className={`h-full touch-manipulation border transition ${
        selected
          ? "border-[#246df2] bg-[#dcecff]"
          : "border-[#c8ddc2] bg-[#e4f2d8] hover:border-[#159447]"
      } ${draggable ? "cursor-grab active:cursor-grabbing" : "cursor-pointer"}`}
      draggable={draggable}
      onClick={handleClick}
      onDragEnd={onDragEnd}
      onDragStart={handleDragStart}
      onPointerUp={handlePointerUp}
      role="button"
      tabIndex={0}
      title={draggable ? "드래그해 다른 구역으로 이동" : undefined}
    >
      <div className={`flex h-full flex-col ${paddingClass(density)}`}>
        {density === "dot" ? (
          <div className="flex h-full items-center justify-end px-1">
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                warning ? "bg-[#f59e0b]" : "bg-[#16a34a]"
              }`}
            />
          </div>
        ) : density === "tiny" ? (
          <div className="flex h-full items-center gap-2">
            <p className="min-w-0 flex-1 truncate text-sm font-bold text-[#1e2b21]">
              {orchidGroup.varietyName}
            </p>
            <p className="shrink-0 text-xs font-bold text-[#1e2b21]">
              {orchidGroup.quantity}분
            </p>
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                warning ? "bg-[#f59e0b]" : "bg-[#16a34a]"
              }`}
            />
          </div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-2">
              <p className="truncate text-sm font-bold text-[#1e2b21]">
                {orchidGroup.varietyName}
              </p>
              <span
                className={`mt-0.5 inline-block h-2 w-2 rounded-full ${
                  warning ? "bg-[#f59e0b]" : "bg-[#16a34a]"
                }`}
              />
            </div>
            <div className="mt-auto">
              <p className="text-xs font-bold text-[#1e2b21]">
                {orchidGroup.quantity}분
              </p>
              {detailText ? (
                <p className="mt-1 text-xs text-[#435047]">{detailText}</p>
              ) : null}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function resolveDensity(heightPx: number) {
  if (heightPx < 16) {
    return "dot";
  }
  if (heightPx < 70) {
    return "tiny";
  }
  return "full";
}

function paddingClass(density: string) {
  switch (density) {
    case "dot":
      return "px-1 py-0";
    case "tiny":
      return "px-2 py-1";
    default:
      return "px-3 py-2";
  }
}
