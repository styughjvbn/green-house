"use client";

import type { MouseEvent, PointerEvent } from "react";
import { getOrchidVarietyColor } from "@/entities/farm/orchidColors";
import type { OrchidGroup } from "@/entities/farm/types";

export default function OrchidGroupBlock({
  distinguishVarietyColors,
  heightPx,
  muted,
  orchidGroup,
  positionLabel,
  selected,
  onSelect,
}: {
  distinguishVarietyColors: boolean;
  heightPx: number;
  muted: boolean;
  orchidGroup: OrchidGroup;
  positionLabel: string | null;
  selected: boolean;
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
      data-testid={`orchid-group-${orchidGroup.id}`}
      className={`h-full w-full min-w-0 touch-manipulation overflow-hidden border transition ${
        varietyColor
          ? "text-white hover:brightness-95"
          : selected
            ? "border-[#246df2] bg-[#dcecff]"
            : muted
              ? "border-[#d6d8d4] bg-[#ebeeea] text-[#8a928a] opacity-80"
              : "border-[#c8ddc2] bg-[#e4f2d8] hover:border-[#159447]"
      } ${selected ? "ring-1 ring-[#246df2]/20" : ""} ${
        muted ? "cursor-default" : "cursor-pointer"
      }`}
      onClick={handleClick}
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
    >
      <div className={`flex h-full min-w-0 flex-col ${paddingClass(density)}`}>
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
          <div className="flex h-full min-w-0 items-center gap-2">
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
            <div className="flex min-w-0 items-start justify-between gap-2">
              <p
                className={`min-w-0 truncate text-sm font-bold ${titleTextClass}`}
              >
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
