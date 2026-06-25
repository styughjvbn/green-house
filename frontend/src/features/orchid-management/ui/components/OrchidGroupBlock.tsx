"use client";

import type { DragEvent, MouseEvent, PointerEvent } from "react";
import type { OrchidGroup } from "@/entities/farm/types";

export default function OrchidGroupBlock({
  draggable,
  orchidGroup,
  selected,
  onDragEnd,
  onDragStart,
  onSelect,
}: {
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
      className={`min-h-16 touch-manipulation rounded-md border p-2 shadow-sm transition ${
        selected
          ? "border-[#246df2] bg-[#dcecff] ring-2 ring-[#246df2]"
          : "border-[#82c886] bg-[#bfe2b8] hover:border-[#159447]"
      } ${draggable ? "cursor-grab active:cursor-grabbing" : "cursor-pointer"}`}
      draggable={draggable}
      onClick={handleClick}
      onDragEnd={onDragEnd}
      onDragStart={handleDragStart}
      onPointerUp={handlePointerUp}
      role="button"
      tabIndex={0}
      title={draggable ? "드래그해서 다른 구역으로 이동" : undefined}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-semibold">{orchidGroup.varietyName}</p>
        <span className={warning ? "text-[#f59e0b]" : "text-[#159447]"}>●</span>
      </div>
      <p className="mt-0.5 text-xs font-semibold">{orchidGroup.quantity}분</p>
      <p className="mt-0.5 text-xs text-[#435047]">
        {[
          orchidGroup.potSize,
          orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null,
        ]
          .filter(Boolean)
          .join(" · ")}
      </p>
    </div>
  );
}
