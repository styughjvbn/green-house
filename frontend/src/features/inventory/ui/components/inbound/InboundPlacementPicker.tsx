"use client";

import type { House } from "@/entities/farm/types";
import {
  FarmPlacementField,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";

export type InboundPlacementSelection = FarmPlacementSelection;

export function InboundPlacementField({
  houses,
  value,
  onChange,
}: {
  houses: House[];
  value: InboundPlacementSelection | null;
  onChange: (value: InboundPlacementSelection) => void;
}) {
  return (
    <FarmPlacementField
      dialogDescription="구역을 고른 뒤 입고 난 묶음이 차지할 시작 칸과 끝 칸을 지정하세요."
      dialogTitle="입고 배치 위치 선택"
      houses={houses}
      value={value}
      onChange={onChange}
    />
  );
}
