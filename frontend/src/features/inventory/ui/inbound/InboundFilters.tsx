"use client";

import type { InboundFilterState } from "../../model/types";
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
} from "../../lib/inboundUi";
import { inputClass } from "../common/InventoryPrimitives";

export function InboundFilters({
  filters,
  onChange,
  onSearch,
  onReset,
}: {
  filters: InboundFilterState;
  onChange: <K extends keyof InboundFilterState>(
    field: K,
    value: InboundFilterState[K],
  ) => void;
  onSearch: () => void;
  onReset: () => void;
}) {
  return (
    <form
      className="shrink-0"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch();
      }}
    >
      <FilterPanel>
        <FilterGrid className="md:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto_auto] lg:items-end">
          <FilterField label="입고 유형">
            <select
              className={inputClass}
              value={filters.inboundType}
              name="inboundType"
              onChange={(event) =>
                onChange(
                  "inboundType",
                  event.target.value as InboundFilterState["inboundType"],
                )
              }
            >
              <option value="">전체</option>
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
              value={filters.status}
              name="inboundStatus"
              onChange={(event) =>
                onChange(
                  "status",
                  event.target.value as InboundFilterState["status"],
                )
              }
            >
              <option value="">전체</option>
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
              value={filters.keyword}
              name="inboundKeyword"
              placeholder="품종명, 위치"
              onChange={(event) => onChange("keyword", event.target.value)}
            />
          </FilterField>
          <FilterResetButton className="h-9 lg:mt-5" onClick={onReset} />
          <FilterSearchButton className="h-9 lg:mt-5" />
        </FilterGrid>
      </FilterPanel>
    </form>
  );
}
