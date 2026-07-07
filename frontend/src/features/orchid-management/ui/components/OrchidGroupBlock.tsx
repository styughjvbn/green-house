"use client";

import type { DragEvent, MouseEvent, PointerEvent } from "react";
import type { OrchidGroup } from "@/entities/farm/types";

export default function OrchidGroupBlock({
  compact = false,
  draggable,
  orchidGroup,
  selected,
  onDragEnd,
  onDragStart,
  onSelect,
}: {
  compact?: boolean;
  draggable: boolean;
  orchidGroup: OrchidGroup;
  selected: boolean;
  onDragEnd: () => void;
  onDragStart: () => void;
  onSelect: () => void;
}) {
  const warning =
    orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";

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
      className={`${compact ? "min-h-11 px-5 py-4" : "min-h-16 p-3"} touch-manipulation border transition ${
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
      <div className="flex items-start justify-between gap-2">
        <p
          className={`${compact ? "text-[15px]" : "text-sm"} font-bold text-[#1e2b21]`}
        >
          {orchidGroup.varietyName}
        </p>
        <span
          className={`mt-0.5 inline-block h-4 w-4 rounded-full ${
            warning ? "bg-[#f59e0b]" : "bg-[#16a34a]"
          }`}
        />
      </div>
      <p className="mt-7 text-[15px] font-bold text-[#1e2b21]">
        {orchidGroup.quantity}분
      </p>
      {!compact ? (
        <p className="mt-1 text-xs text-[#435047]">
          {[
            orchidGroup.potSize,
            orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null,
          ]
            .filter(Boolean)
            .join(" / ")}
        </p>
      ) : null}
    </div>
  );
}
