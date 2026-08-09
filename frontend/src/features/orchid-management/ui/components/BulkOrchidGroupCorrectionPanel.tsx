"use client";

import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import type { OrchidGroup, VarietyOption } from "@/entities/farm/types";
import { isStandardPotSize, POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import type { OrchidGroupBatchUpdateItem } from "../../model/types";
import TextField from "./TextField";
import VarietySearchSelect from "./VarietySearchSelect";
import BulkCorrectionPreviewDialog from "./BulkCorrectionPreviewDialog";

type BulkField =
  | "varietyId"
  | "quantity"
  | "potSize"
  | "ageYear"
  | "status"
  | "placementType"
  | "memo";

export default function BulkOrchidGroupCorrectionPanel({
  errorMessage,
  orchidGroups,
  saving,
  onCancel,
  onSubmit,
}: {
  errorMessage: string | null;
  orchidGroups: OrchidGroup[];
  saving: boolean;
  onCancel: () => void;
  onSubmit: (items: OrchidGroupBatchUpdateItem[]) => Promise<boolean>;
}) {
  const first = orchidGroups[0];
  const initialKeep = useMemo(
    () =>
      new Set<BulkField>(
        (
          [
            "varietyId",
            "quantity",
            "potSize",
            "ageYear",
            "status",
            "placementType",
            "memo",
          ] as BulkField[]
        ).filter((field) => !allEqual(orchidGroups, field)),
      ),
    [orchidGroups],
  );
  const [keepFields, setKeepFields] = useState(initialKeep);
  const [selectedVariety, setSelectedVariety] = useState<VarietyOption | null>(
    first?.varietyId
      ? {
          id: first.varietyId,
          genus: first.genus ?? "",
          name: first.varietyName,
          defaultPotSize: null,
          active: true,
        }
      : null,
  );
  const [values, setValues] = useState(() => ({
    quantity: String(first?.quantity ?? 1),
    potSize: first?.potSize ?? "",
    ageYear: first?.ageYear == null ? "" : String(first.ageYear),
    status: first?.status ?? "정상",
    placementType: first?.placementType ?? "",
    memo: first?.memo ?? "",
  }));
  const [warningVisible, setWarningVisible] = useState(false);
  const [previewItems, setPreviewItems] = useState<
    OrchidGroupBatchUpdateItem[] | null
  >(null);

  function toggleKeep(field: BulkField) {
    setKeepFields((current) => {
      const next = new Set(current);
      if (next.has(field)) next.delete(field);
      else next.add(field);
      return next;
    });
    setWarningVisible(false);
  }

  function updateValue(field: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setWarningVisible(false);
  }

  function buildItems(): OrchidGroupBatchUpdateItem[] {
    return orchidGroups.map((orchidGroup) => ({
      orchidGroupId: orchidGroup.id,
      update: {
        varietyId: keepFields.has("varietyId")
          ? requireVarietyId(orchidGroup)
          : (selectedVariety?.id ?? requireVarietyId(orchidGroup)),
        quantity: keepFields.has("quantity")
          ? orchidGroup.quantity
          : Number(values.quantity),
        potSize: keepFields.has("potSize")
          ? orchidGroup.potSize
          : values.potSize || null,
        ageYear: keepFields.has("ageYear")
          ? orchidGroup.ageYear
          : values.ageYear
            ? Number(values.ageYear)
            : null,
        status: keepFields.has("status") ? orchidGroup.status : values.status,
        placementType: keepFields.has("placementType")
          ? orchidGroup.placementType
          : values.placementType || null,
        trayCount: orchidGroup.trayCount,
        splitPlacementAllowed: orchidGroup.splitPlacementAllowed,
        startPosition: orchidGroup.startPosition,
        endPosition: orchidGroup.endPosition,
        memo: keepFields.has("memo")
          ? orchidGroup.memo
          : values.memo.trim() || null,
      },
    }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!warningVisible) {
      setWarningVisible(true);
      return;
    }
    setPreviewItems(buildItems());
  }

  if (!first) return null;

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">일괄 보정</p>
          <h3 className="mt-1 text-base font-semibold">
            선택한 난 묶음 {orchidGroups.length}개
          </h3>
          <p className="mt-1 text-xs font-semibold text-[#5c6a60]">
            개별값 유지를 선택한 항목은 각 난 묶음의 기존 값을 유지합니다.
          </p>
        </div>
        <button
          className="rounded-md border border-[#d7ddd4] px-2 py-1.5 text-xs font-semibold"
          onClick={onCancel}
          type="button"
        >
          닫기
        </button>
      </div>

      <form className="mt-3 space-y-2" onSubmit={handleSubmit}>
        <KeepableField
          keeping={keepFields.has("varietyId")}
          label="품종"
          onToggle={() => toggleKeep("varietyId")}
        >
          <VarietySearchSelect
            disabled={saving || keepFields.has("varietyId")}
            selectedVariety={selectedVariety}
            onSelect={(option) => {
              setSelectedVariety(option);
              setWarningVisible(false);
            }}
          />
        </KeepableField>

        <div className="grid grid-cols-2 gap-2">
          <KeepableField
            keeping={keepFields.has("quantity")}
            label="수량"
            onToggle={() => toggleKeep("quantity")}
          >
            <TextField
              disabled={keepFields.has("quantity")}
              label="수량"
              min={1}
              required
              type="number"
              value={values.quantity}
              onChange={(value) => updateValue("quantity", value)}
            />
          </KeepableField>
          <KeepableField
            keeping={keepFields.has("ageYear")}
            label="초기 년생"
            onToggle={() => toggleKeep("ageYear")}
          >
            <TextField
              disabled={keepFields.has("ageYear")}
              label="초기 년생"
              min={0}
              type="number"
              value={values.ageYear}
              onChange={(value) => updateValue("ageYear", value)}
            />
          </KeepableField>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <KeepableSelect
            keeping={keepFields.has("potSize")}
            label="화분 크기"
            value={values.potSize}
            onChange={(value) => updateValue("potSize", value)}
            onToggle={() => toggleKeep("potSize")}
          >
            {!isStandardPotSize(values.potSize) ? (
              <option disabled value={values.potSize}>
                검수 필요: {values.potSize}
              </option>
            ) : null}
            {POT_SIZE_OPTIONS.map((option) => (
              <option key={option.value || "unspecified"} value={option.value}>
                {option.label}
              </option>
            ))}
          </KeepableSelect>
          <KeepableSelect
            keeping={keepFields.has("status")}
            label="상태"
            value={values.status}
            onChange={(value) => updateValue("status", value)}
            onToggle={() => toggleKeep("status")}
          >
            <option value="정상">정상</option>
            <option value="주의">주의</option>
            <option value="이상">이상</option>
            <option value="판매 가능">판매 가능</option>
          </KeepableSelect>
        </div>

        <KeepableSelect
          keeping={keepFields.has("placementType")}
          label="배치 규격"
          value={values.placementType}
          selectValue={resolvePlacementSelectValue(values.placementType)}
          onChange={(value) =>
            updateValue("placementType", value === "CUSTOM" ? "CUSTOM:" : value)
          }
          onToggle={() => toggleKeep("placementType")}
        >
          <option value="">선택</option>
          <option value="TRAY_12">12구 트레이</option>
          <option value="TRAY_15">15구 트레이</option>
          <option value="TRAY_20">20구 트레이</option>
          <option value="TRAY_24">24구 트레이</option>
          <option value="SINGLE_POT">단독 화분</option>
          <option value="HANGING">행잉</option>
          <option value="CUSTOM">기타</option>
        </KeepableSelect>

        {!keepFields.has("placementType") &&
        resolvePlacementSelectValue(values.placementType) === "CUSTOM" ? (
          <TextField
            label="기타 배치 규격명"
            required
            value={values.placementType.slice(7)}
            onChange={(value) =>
              updateValue("placementType", `CUSTOM:${value}`)
            }
          />
        ) : null}

        <KeepableField
          keeping={keepFields.has("memo")}
          label="메모"
          onToggle={() => toggleKeep("memo")}
        >
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">메모</span>
            <textarea
              className="mt-1 min-h-16 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              disabled={keepFields.has("memo")}
              value={values.memo}
              onChange={(event) => updateValue("memo", event.target.value)}
            />
          </label>
        </KeepableField>

        <p className="rounded-md border border-[#d9e4f7] bg-[#f5f8ff] px-3 py-2 text-xs font-semibold text-[#526b91]">
          위치와 칸 배치는 난 묶음별 기존 값을 유지합니다.
        </p>
        {warningVisible ? (
          <p className="rounded-md border border-[#f0d299] bg-[#fff8e8] px-3 py-2 text-xs font-semibold text-[#96650f]">
            보정은 작업 이력이 남지 않아 추후 문제가 될 수 있습니다. 그래도
            보정하시겠다면 다시 한번 저장을 눌러주세요.
          </p>
        ) : null}
        <button
          className="w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={
            saving || (!keepFields.has("varietyId") && !selectedVariety)
          }
          type="submit"
        >
          보정 저장
        </button>
      </form>

      {previewItems ? (
        <BulkCorrectionPreviewDialog
          items={previewItems}
          orchidGroups={orchidGroups}
          selectedVariety={selectedVariety}
          saving={saving}
          errorMessage={errorMessage}
          onCancel={() => setPreviewItems(null)}
          onConfirm={() => onSubmit(previewItems)}
        />
      ) : null}
    </section>
  );
}

function KeepableField({
  children,
  keeping,
  label,
  onToggle,
}: {
  children: ReactNode;
  keeping: boolean;
  label: string;
  onToggle: () => void;
}) {
  return (
    <div className={keeping ? "opacity-65" : ""}>
      <label className="mb-1 flex items-center gap-1.5 text-xs font-semibold text-[#526057]">
        <input checked={keeping} onChange={onToggle} type="checkbox" />
        {label} 개별값 유지
      </label>
      <div className={keeping ? "pointer-events-none" : ""}>{children}</div>
    </div>
  );
}

function KeepableSelect({
  children,
  keeping,
  label,
  onChange,
  onToggle,
  selectValue,
  value,
}: {
  children: ReactNode;
  keeping: boolean;
  label: string;
  onChange: (value: string) => void;
  onToggle: () => void;
  selectValue?: string;
  value: string;
}) {
  return (
    <label className="block">
      <span className="flex items-center justify-between gap-2 text-sm font-semibold text-[#435047]">
        {label}
        <span className="flex items-center gap-1 text-xs text-[#526057]">
          <input checked={keeping} onChange={onToggle} type="checkbox" />
          개별값 유지
        </span>
      </span>
      <select
        className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-1.5 text-sm disabled:bg-[#f2f4f1] disabled:text-[#879087]"
        disabled={keeping}
        value={selectValue ?? value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}

function resolvePlacementSelectValue(value: string) {
  return value.startsWith("CUSTOM:") ? "CUSTOM" : value;
}

function allEqual(groups: OrchidGroup[], field: BulkField) {
  if (groups.length < 2) return true;
  return groups.every(
    (group) => valueFor(group, field) === valueFor(groups[0], field),
  );
}

function requireVarietyId(group: OrchidGroup) {
  if (group.varietyId == null) {
    throw new Error(`#${group.id} 난 묶음의 품종을 먼저 지정해주세요.`);
  }
  return group.varietyId;
}

function valueFor(group: OrchidGroup, field: BulkField) {
  return group[field];
}
