"use client";

import type { MouseEvent } from "react";
import type { OrchidGroup } from "@/types/farm";

export default function OrchidGroupBlock({
  orchidGroup,
  selected,
  onSelect,
}: {
  orchidGroup: OrchidGroup;
  selected: boolean;
  onSelect: (event: MouseEvent<HTMLDivElement>) => void;
}) {
  const warning = orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";

  return (
    <div
      className={`min-h-16 cursor-pointer rounded-md border p-2 shadow-sm transition ${
        selected ? "border-[#246df2] bg-[#dcecff] ring-2 ring-[#246df2]" : "border-[#82c886] bg-[#bfe2b8] hover:border-[#159447]"
      }`}
      onClick={onSelect}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-semibold">{orchidGroup.varietyName}</p>
        <span className={warning ? "text-[#f59e0b]" : "text-[#159447]"}>●</span>
      </div>
      <p className="mt-0.5 text-xs font-semibold">{orchidGroup.quantity}분</p>
      <p className="mt-0.5 text-xs text-[#435047]">
        {[orchidGroup.potSize, orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null].filter(Boolean).join(" · ")}
      </p>
    </div>
  );
}
