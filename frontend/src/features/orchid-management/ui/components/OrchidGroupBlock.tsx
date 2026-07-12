"use client";

import type { DragEvent, MouseEvent, PointerEvent } from "react";
import { getOrchidVarietyColor } from "@/entities/farm/orchidColors";
import type { OrchidGroup } from "@/entities/farm/types";

export default function OrchidGroupBlock({
  distinguishVarietyColors,
  draggable,
  heightPx,
  muted,
  orchidGroup,
  positionLabel,
  selected,
  onDragEnd,
  onDragStart,
  onSelect,
}: {
  distinguishVarietyColors: boolean;
  draggable: boolean;
  heightPx: number;
  muted: boolean;
  orchidGroup: OrchidGroup;
  positionLabel: string | null;
  selected: boolean;
  onDragEnd: () => void;
  onDragStart: () => void;
  onSelect: () => void;
}) {
  const warning =
    orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";
  const density = resolveDensity(heightPx);
  const varietyColor =
    distinguishVarietyColors && !muted && !selected
      ? getOrchidVarietyColor(orchidGroup)
      : null;
  const titleTextClass = varietyColor ? "text-white" : "text-[#1e2b21]";
  const detailTextClass = varietyColor ? "text-white/85" : "text-[#435047]";
  const detailText = [
    orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null,
    orchidGroup.potSize,
    positionLabel,
  ]
    .filter(Boolean)
    .join(" / ");

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    event.stopPropagation();
    if (muted) {
      return;
    }
    onSelect();
  }

  function handleDragStart(event: DragEvent<HTMLDivElement>) {
    if (muted) {
      event.preventDefault();
      return;
    }
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
    if (muted) {
      return;
    }
    onSelect();
  }

  return (
    <div
      className={`h-full touch-manipulation border transition ${
        varietyColor
          ? "text-white hover:brightness-95"
          : muted
            ? "border-[#d6d8d4] bg-[#ebeeea] text-[#8a928a] opacity-80"
            : selected
              ? "border-[#246df2] bg-[#dcecff]"
              : "border-[#c8ddc2] bg-[#e4f2d8] hover:border-[#159447]"
      } ${selected ? "ring-1 ring-[#246df2]/20" : ""} ${
        muted
          ? "cursor-default"
          : draggable
            ? "cursor-grab active:cursor-grabbing"
            : "cursor-pointer"
      }`}
      draggable={draggable}
      onClick={handleClick}
      onDragEnd={onDragEnd}
      onDragStart={handleDragStart}
      onPointerUp={handlePointerUp}
      role="button"
      style={
        varietyColor
          ? {
              backgroundColor: varietyColor.fill,
              borderColor: varietyColor.border,
            }
          : undefined
      }
      tabIndex={0}
      title={draggable ? "드래그해 다른 구역으로 이동" : undefined}
    >
      <div className={`flex h-full flex-col ${paddingClass(density)}`}>
        {density === "dot" ? (
          <div className="flex h-full items-center justify-end px-1">
            {warning || muted ? (
              <span
                className={`inline-block h-2 w-2 rounded-full ${
                  muted ? "bg-[#9aa39a]" : "bg-[#f59e0b]"
                }`}
              />
            ) : null}
          </div>
        ) : density === "tiny" ? (
          <div className="flex h-full items-center gap-2">
            <p
              className={`min-w-0 flex-1 truncate text-sm font-bold ${titleTextClass}`}
            >
              {orchidGroup.varietyName}
            </p>
            <p className={`shrink-0 text-xs font-bold ${titleTextClass}`}>
              {orchidGroup.quantity}분
            </p>
            {warning || muted ? (
              <span
                className={`inline-block h-2 w-2 rounded-full ${
                  muted ? "bg-[#9aa39a]" : "bg-[#f59e0b]"
                }`}
              />
            ) : null}
          </div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-2">
              <p className={`truncate text-sm font-bold ${titleTextClass}`}>
                {orchidGroup.varietyName}
              </p>
              {warning || muted ? (
                <span
                  className={`mt-0.5 inline-block h-2 w-2 rounded-full ${
                    muted ? "bg-[#9aa39a]" : "bg-[#f59e0b]"
                  }`}
                />
              ) : null}
            </div>
            <div className="mt-auto">
              <p className={`text-xs font-bold ${titleTextClass}`}>
                {orchidGroup.quantity}분
              </p>
              {detailText ? (
                <p className={`mt-1 text-xs ${detailTextClass}`}>
                  {detailText}
                </p>
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
