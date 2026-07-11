"use client";

import type { House } from "@/entities/farm/types";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import type { CSSObjectWithLabel, SingleValue } from "react-select";
import Select from "react-select";
import { useState } from "react";
import type {
  InboundPottingPayload,
  InboundRecord,
  InboundRecordPayload,
  InboundType,
  Variety,
} from "../../../model/types";
import {
  flattenZones,
  INBOUND_TYPE_LABELS,
  toNumber,
} from "../../../lib/inboundUi";
import { Field, inputClass } from "../InventoryPrimitives";

export function InboundCreateDialog({
  open,
  varieties,
  houses,
  onClose,
  onSubmit,
}: {
  open: boolean;
  varieties: Variety[];
  houses: House[];
  onClose: () => void;
  onSubmit: (payload: InboundRecordPayload) => Promise<void>;
}) {
  const [inboundType, setInboundType] = useState<InboundType>("FLASK_SEEDLING");
  const [inboundDate, setInboundDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [varietyMode, setVarietyMode] = useState<"existing" | "new">(
    "existing",
  );
  const [varietyId, setVarietyId] = useState(varieties[0]?.id ?? 0);
  const [newGenus, setNewGenus] = useState("");
  const [newName, setNewName] = useState("");
  const [newPotSize, setNewPotSize] = useState("");
  const [bottleCount, setBottleCount] = useState("");
  const [estimatedQuantity, setEstimatedQuantity] = useState("");
  const [actualQuantity, setActualQuantity] = useState("");
  const [tempLocation, setTempLocation] = useState("");
  const [pottingDueDate, setPottingDueDate] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [placementType, setPlacementType] = useState("");
  const [bedZoneId, setBedZoneId] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");

  if (!open) return null;

  const zoneOptions = flattenZones(houses);
  const flaskType = inboundType === "FLASK_SEEDLING";
  const selectedVariety =
    varieties.find((variety) => variety.id === varietyId) ??
    varieties[0] ??
    null;

  return (
    <DialogShell title="새 입고 등록" onClose={onClose}>
      <form
        className="mt-4 grid gap-3 md:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit({
            inboundDate,
            inboundType,
            varietyId:
              varietyMode === "existing" ? selectedVariety?.id : undefined,
            newVariety:
              varietyMode === "new"
                ? {
                    genus: newGenus,
                    name: newName,
                    defaultPotSize: newPotSize,
                    memo,
                  }
                : undefined,
            bottleCount: toNumber(bottleCount),
            estimatedQuantity: toNumber(estimatedQuantity),
            actualQuantity: toNumber(actualQuantity),
            tempLocation: tempLocation.trim() || undefined,
            pottingDueDate: pottingDueDate || undefined,
            potSize: potSize.trim() || undefined,
            ageYear: toNumber(ageYear),
            placementType: placementType.trim() || undefined,
            bedZoneId: toNumber(bedZoneId),
            worker: worker.trim() || undefined,
            memo: memo.trim() || undefined,
          });
        }}
      >
        <Field label="입고일">
          <input
            className={inputClass}
            required
            type="date"
            value={inboundDate}
            onChange={(event) => setInboundDate(event.target.value)}
          />
        </Field>
        <Field label="입고 유형">
          <select
            className={inputClass}
            value={inboundType}
            onChange={(event) =>
              setInboundType(event.target.value as InboundType)
            }
          >
            {Object.entries(INBOUND_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="품종 선택 방식">
          <select
            className={inputClass}
            value={varietyMode}
            onChange={(event) =>
              setVarietyMode(event.target.value as "existing" | "new")
            }
          >
            <option value="existing">기존 품종</option>
            <option value="new">새 품종</option>
          </select>
        </Field>
        {varietyMode === "existing" ? (
          <VarietySearchField
            selectedVariety={selectedVariety}
            varieties={varieties}
            onSelect={(variety) => setVarietyId(variety.id)}
          />
        ) : (
          <div className="grid gap-3 md:col-span-2 md:grid-cols-3">
            <Field label="새 속">
              <input
                className={inputClass}
                required
                value={newGenus}
                onChange={(event) => setNewGenus(event.target.value)}
              />
            </Field>
            <Field label="새 품종명">
              <input
                className={inputClass}
                required
                value={newName}
                onChange={(event) => setNewName(event.target.value)}
              />
            </Field>
            <Field label="기본 화분">
              <input
                className={inputClass}
                value={newPotSize}
                onChange={(event) => setNewPotSize(event.target.value)}
              />
            </Field>
          </div>
        )}
        {flaskType ? (
          <>
            <Field label="유리병 수">
              <input
                className={inputClass}
                required
                type="number"
                value={bottleCount}
                onChange={(event) => setBottleCount(event.target.value)}
              />
            </Field>
            <Field label="예상 수량">
              <input
                className={inputClass}
                required
                type="number"
                value={estimatedQuantity}
                onChange={(event) => setEstimatedQuantity(event.target.value)}
              />
            </Field>
            <Field label="임시 위치">
              <input
                className={inputClass}
                value={tempLocation}
                onChange={(event) => setTempLocation(event.target.value)}
              />
            </Field>
            <Field label="포트 작업 예정일">
              <input
                className={inputClass}
                type="date"
                value={pottingDueDate}
                onChange={(event) => setPottingDueDate(event.target.value)}
              />
            </Field>
          </>
        ) : (
          <>
            <Field label="실제 수량">
              <input
                className={inputClass}
                required
                type="number"
                value={actualQuantity}
                onChange={(event) => setActualQuantity(event.target.value)}
              />
            </Field>
            <Field label="화분 크기">
              <input
                className={inputClass}
                value={potSize}
                onChange={(event) => setPotSize(event.target.value)}
              />
            </Field>
            <Field label="초기 년생">
              <input
                className={inputClass}
                type="number"
                value={ageYear}
                onChange={(event) => setAgeYear(event.target.value)}
              />
            </Field>
            <Field label="배치 형태">
              <input
                className={inputClass}
                value={placementType}
                onChange={(event) => setPlacementType(event.target.value)}
              />
            </Field>
            <Field label="배치 위치">
              <select
                className={inputClass}
                required
                value={bedZoneId}
                onChange={(event) => setBedZoneId(event.target.value)}
              >
                <option value="">선택</option>
                {zoneOptions.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.label}
                  </option>
                ))}
              </select>
            </Field>
          </>
        )}
        <Field label="작업자">
          <input
            className={inputClass}
            value={worker}
            onChange={(event) => setWorker(event.target.value)}
          />
        </Field>
        <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
          <span>메모</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2 md:col-span-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            저장
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

type VarietySelectOption = {
  label: string;
  value: number;
  variety: Variety;
};

function VarietySearchField({
  selectedVariety,
  varieties,
  onSelect,
}: {
  selectedVariety: Variety | null;
  varieties: Variety[];
  onSelect: (variety: Variety) => void;
}) {
  const options = varieties.map(toVarietyOption);
  const selectedOption = selectedVariety
    ? toVarietyOption(selectedVariety)
    : null;

  function handleChange(option: SingleValue<VarietySelectOption>) {
    if (!option) {
      return;
    }
    onSelect(option.variety);
  }

  return (
    <label className="space-y-1 text-xs font-semibold text-[#425047]">
      <span>품종</span>
      <Select<VarietySelectOption, false>
        isClearable={false}
        noOptionsMessage={() => "검색 결과가 없습니다."}
        options={options}
        placeholder="품종명 또는 속명 검색"
        styles={selectStyles}
        value={selectedOption}
        onChange={handleChange}
      />
    </label>
  );
}

function toVarietyOption(variety: Variety): VarietySelectOption {
  return {
    label: `${variety.name} ${variety.genus}`,
    value: variety.id,
    variety,
  };
}

const selectStyles = {
  control: (base: CSSObjectWithLabel, state: { isFocused: boolean }) => ({
    ...base,
    minHeight: 38,
    borderRadius: 6,
    borderColor: state.isFocused ? "#159447" : "#d7ddd8",
    boxShadow: state.isFocused ? "0 0 0 1px #159447" : "none",
    "&:hover": {
      borderColor: state.isFocused ? "#159447" : "#d7ddd8",
    },
  }),
  valueContainer: (base: CSSObjectWithLabel) => ({
    ...base,
    padding: "0 10px",
  }),
  placeholder: (base: CSSObjectWithLabel) => ({
    ...base,
    color: "#7d887f",
    fontSize: 14,
  }),
  input: (base: CSSObjectWithLabel) => ({
    ...base,
    fontSize: 14,
  }),
  singleValue: (base: CSSObjectWithLabel) => ({
    ...base,
    color: "#17251b",
    fontSize: 14,
    fontWeight: 600,
  }),
  menu: (base: CSSObjectWithLabel) => ({
    ...base,
    borderRadius: 8,
    overflow: "hidden",
    zIndex: 40,
  }),
  option: (
    base: CSSObjectWithLabel,
    state: { isSelected: boolean; isFocused: boolean },
  ) => ({
    ...base,
    backgroundColor: state.isSelected
      ? "#eaf7eb"
      : state.isFocused
        ? "#f3f9f3"
        : "#ffffff",
    color: "#17251b",
    padding: "8px 12px",
  }),
} satisfies Record<string, unknown>;

export function InboundPottingDialog({
  open,
  record,
  houses,
  onClose,
  onSubmit,
}: {
  open: boolean;
  record: InboundRecord | null;
  houses: House[];
  onClose: () => void;
  onSubmit: (payload: InboundPottingPayload) => Promise<void>;
}) {
  const [pottingDate, setPottingDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [actualQuantity, setActualQuantity] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [placementType, setPlacementType] = useState("");
  const [bedZoneId, setBedZoneId] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");

  if (!open || !record) return null;

  const zoneOptions = flattenZones(houses);

  return (
    <DialogShell title="포트 작업 등록" onClose={onClose}>
      <form
        className="mt-4 grid gap-3 md:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit({
            pottingDate,
            actualQuantity: Number(actualQuantity),
            potSize: potSize.trim() || undefined,
            ageYear: toNumber(ageYear),
            placementType: placementType.trim() || undefined,
            bedZoneId: Number(bedZoneId),
            worker: worker.trim() || undefined,
            memo: memo.trim() || undefined,
          });
        }}
      >
        <Field label="입고 품종">
          <input className={inputClass} disabled value={record.varietyName} />
        </Field>
        <Field label="포트 작업일">
          <input
            className={inputClass}
            required
            type="date"
            value={pottingDate}
            onChange={(event) => setPottingDate(event.target.value)}
          />
        </Field>
        <Field label="실제 생성 수량">
          <input
            className={inputClass}
            required
            type="number"
            value={actualQuantity}
            onChange={(event) => setActualQuantity(event.target.value)}
          />
        </Field>
        <Field label="화분 크기">
          <input
            className={inputClass}
            value={potSize}
            onChange={(event) => setPotSize(event.target.value)}
          />
        </Field>
        <Field label="초기 년생">
          <input
            className={inputClass}
            type="number"
            value={ageYear}
            onChange={(event) => setAgeYear(event.target.value)}
          />
        </Field>
        <Field label="배치 형태">
          <input
            className={inputClass}
            value={placementType}
            onChange={(event) => setPlacementType(event.target.value)}
          />
        </Field>
        <Field label="배치 위치">
          <select
            className={inputClass}
            required
            value={bedZoneId}
            onChange={(event) => setBedZoneId(event.target.value)}
          >
            <option value="">선택</option>
            {zoneOptions.map((zone) => (
              <option key={zone.id} value={zone.id}>
                {zone.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="작업자">
          <input
            className={inputClass}
            value={worker}
            onChange={(event) => setWorker(event.target.value)}
          />
        </Field>
        <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
          <span>메모</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2 md:col-span-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            저장
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

export function CancelDialog({
  open,
  title,
  onClose,
  onSubmit,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: (memo: string) => Promise<void>;
}) {
  const [memo, setMemo] = useState("");

  if (!open) return null;

  return (
    <DialogShell title={title} onClose={onClose}>
      <form
        className="mt-4 space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          void onSubmit(memo);
        }}
      >
        <label className="space-y-1 text-xs font-semibold text-[#425047]">
          <span>사유</span>
          <textarea
            className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>
        <div className="flex justify-end gap-2">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
          <button
            className="rounded-md bg-[#a14545] px-4 py-2 text-sm font-semibold text-white"
            type="submit"
          >
            취소 확정
          </button>
        </div>
      </form>
    </DialogShell>
  );
}

function DialogShell({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-3xl rounded-md bg-white p-5 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold">{title}</h2>
          <button
            className="flex h-8 w-8 items-center justify-center rounded border border-[#d9dfda]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}
