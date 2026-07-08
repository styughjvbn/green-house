"use client";

import type { InboundStatus, InboundType } from "../../../model/types";
import {
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
} from "../../../lib/inboundUi";
import { Field, inputClass } from "../InventoryPrimitives";

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
      className="grid gap-3 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm md:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_auto_auto] xl:items-end"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(new FormData(event.currentTarget));
      }}
    >
      <Field label="입고 유형">
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
      </Field>
      <Field label="상태">
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
      </Field>
      <Field label="품종명">
        <input
          className={inputClass}
          defaultValue={keyword}
          name="inboundKeyword"
          placeholder="품종명, 위치"
        />
      </Field>
      <button
        className="h-9 rounded-md border border-[#d7ddd8] px-4 text-sm font-semibold"
        type="button"
        onClick={onReset}
      >
        초기화
      </button>
      <button
        className="h-9 rounded-md bg-[#159447] px-6 text-sm font-semibold text-white"
        type="submit"
      >
        검색
      </button>
    </form>
  );
}
