"use client";

import type { InboundStatus, InboundType } from "../../../model/types";
import {
  FilterField,
  FilterGrid,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
} from "@/shared/ui/FilterControls";
import {
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
} from "../../../lib/inboundUi";
import { inputClass } from "../InventoryPrimitives";

export function InboundFilterCard({
  inboundType,
  status,
  keyword,
  onSubmit,
  onReset,
}: {
  inboundType: InboundType | "ALL";
  status: InboundStatus | "ALL";
  keyword: string;
  onSubmit: (formData: FormData) => void;
  onReset: () => void;
}) {
  return (
    <form
      className="shrink-0"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(new FormData(event.currentTarget));
      }}
    >
      <FilterPanel>
        <FilterGrid className="md:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto_auto] lg:items-end">
          <FilterField label="입고 유형">
            <select
              className={inputClass}
              defaultValue={inboundType}
              name="inboundType"
            >
              <option value="ALL">전체</option>
              {Object.entries(INBOUND_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </FilterField>
          <FilterField label="상태">
            <select
              className={inputClass}
              defaultValue={status}
              name="inboundStatus"
            >
              <option value="ALL">전체</option>
              {Object.entries(INBOUND_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </FilterField>
          <FilterField label="품종명">
            <input
              className={inputClass}
              defaultValue={keyword}
              name="inboundKeyword"
              placeholder="품종명, 위치"
            />
          </FilterField>
          <FilterResetButton className="h-9 lg:mt-5" onClick={onReset} />
          <FilterSearchButton className="h-9 lg:mt-5" />
        </FilterGrid>
      </FilterPanel>
    </form>
  );
}
