"use client";

import { FormEvent, useMemo, useState } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  VarietyOption,
} from "@/entities/farm/types";
import { isStandardPotSize, POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import {
  buildOccupiedCells,
  findFirstAvailableSingleSlot,
  findBedZone,
  endCellToPosition,
  normalizeCellRange,
  nullableNumber,
  nullableText,
  positionToEndCell,
  rangeHasOccupiedCell,
  positionToStartCell,
  resolveMaxCell,
  startCellToPosition,
} from "../../lib/orchidManagementUtils";
import type {
  MapCellRangePick,
  OrchidFormDraft,
  MutationPayload,
  OrchidFormState,
} from "../../model/types";
import TextField from "./TextField";
import VarietySearchSelect from "./VarietySearchSelect";

export default function OrchidGroupForm({
  initialValue,
  house,
  mode,
  saving,
  targetZone,
  mapCellRangePick,
  draft,
  onCancel,
  onDraftChange,
  onStartMapCellRangePick,
  onSyncMapCellRangePick,
  onSubmit,
}: {
  initialValue: OrchidGroup | null;
  house: House;
  mode: "CREATE" | "EDIT";
  saving: boolean;
  targetZone: BedZone | null;
  mapCellRangePick: MapCellRangePick;
  draft?: OrchidFormDraft | null;
  onCancel: () => void;
  onDraftChange?: (draft: OrchidFormDraft) => void;
  onStartMapCellRangePick: (options: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number | null;
  }) => void;
  onSyncMapCellRangePick: (options: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number;
  }) => void;
  onSubmit: (payload: MutationPayload) => Promise<void>;
}) {
  const initialVariety = useMemo<VarietyOption | null>(
    () =>
      initialValue?.varietyId != null
        ? {
            id: initialValue.varietyId,
            genus: initialValue.genus ?? "",
            name: initialValue.varietyName,
            defaultPotSize: null,
            active: true,
          }
        : null,
    [initialValue],
  );

  const mapTargetZone =
    mode === "CREATE" && mapCellRangePick.targetBedZoneId != null
      ? (findBedZone(house, mapCellRangePick.targetBedZoneId)?.zone ?? null)
      : null;
  const activeZone = mapTargetZone ?? targetZone;

  const defaultPlacement = useMemo(
    () =>
      mode === "CREATE" && activeZone
        ? findFirstAvailableSingleSlot(house, activeZone.id)
        : null,
    [activeZone, house, mode],
  );

  const [form, setForm] = useState<OrchidFormState>(
    () =>
      draft?.form ??
      createInitialFormState(mode, initialValue, defaultPlacement),
  );
  const [selectedVariety, setSelectedVariety] = useState<VarietyOption | null>(
    draft?.selectedVariety ?? initialVariety,
  );
  const maxCell = useMemo(
    () => resolveMaxCell(house, activeZone?.id ?? null),
    [activeZone, house],
  );
  const excludedOrchidGroupId =
    mode === "EDIT" ? (initialValue?.id ?? null) : null;
  const occupiedCells = useMemo(
    () =>
      buildOccupiedCells(
        activeZone?.orchidGroups ?? [],
        excludedOrchidGroupId,
        maxCell,
      ),
    [activeZone, excludedOrchidGroupId, maxCell],
  );
  const [ignoredMapPickVersion, setIgnoredMapPickVersion] = useState(0);
  const rangePickActive =
    mapCellRangePick.active &&
    (mode === "CREATE"
      ? mapCellRangePick.targetBedZoneId == null ||
        mapCellRangePick.targetBedZoneId === activeZone?.id
      : mapCellRangePick.targetBedZoneId === activeZone?.id);
  const rangePickButtonLabel = rangePickActive
    ? "맵에서 지정 끄기"
    : "맵에서 지정 켜기";
  const mapPickedRange =
    activeZone != null &&
    mapCellRangePick.targetBedZoneId === activeZone.id &&
    mapCellRangePick.startCell != null &&
    mapCellRangePick.endCell != null &&
    mapCellRangePick.version > ignoredMapPickVersion
      ? {
          startPosition: String(mapCellRangePick.startCell),
          endPosition: String(mapCellRangePick.endCell),
        }
      : null;
  const startPositionValue =
    mapPickedRange?.startPosition ?? form.startPosition;
  const endPositionValue = mapPickedRange?.endPosition ?? form.endPosition;
  const normalizedRange = normalizeCellRange(
    startPositionValue,
    endPositionValue,
    maxCell,
  );
  const rangeBlocked = rangeHasOccupiedCell({
    startCell: normalizedRange.startCell,
    endCell: normalizedRange.endCell,
    occupiedCells,
  });
  const submitBlocked = mode === "EDIT" && rangeBlocked;

  function saveFormDraft(next: OrchidFormState, nextVariety = selectedVariety) {
    setForm(next);
    onDraftChange?.({ form: next, selectedVariety: nextVariety });
  }

  function updateField<K extends keyof OrchidFormState>(
    field: K,
    value: OrchidFormState[K],
  ) {
    const next = { ...form, [field]: value };
    saveFormDraft(next);
  }

  function updateStartCell(value: string) {
    setIgnoredMapPickVersion(mapCellRangePick.version);
    updateField("startPosition", value);
  }

  function updateEndCell(value: string) {
    setIgnoredMapPickVersion(mapCellRangePick.version);
    updateField("endPosition", value);
  }

  function commitCellRange() {
    if (!activeZone) {
      return;
    }
    const range = normalizeCellRange(
      startPositionValue,
      endPositionValue,
      maxCell,
    );
    setIgnoredMapPickVersion(mapCellRangePick.version);
    const next = {
      ...form,
      startPosition: String(range.startCell),
      endPosition: String(range.endCell),
    };
    saveFormDraft(next);
    onSyncMapCellRangePick({
      targetBedZoneId: activeZone.id,
      excludeOrchidGroupId: excludedOrchidGroupId,
      maxCell,
      startCell: String(range.startCell),
      endCell: String(range.endCell),
    });
  }

  function handleSelectVariety(option: VarietyOption) {
    setSelectedVariety(option);
    const next = {
      ...form,
      varietyId: String(option.id),
      varietyQuery: option.name,
      genus: option.genus,
      varietyName: option.name,
      potSize: form.potSize || option.defaultPotSize || "",
    };
    saveFormDraft(next, option);
  }

  function handleToggleMapCellRangePick() {
    if (mode === "EDIT" && !activeZone) return;
    if (rangePickActive && mapPickedRange) {
      saveFormDraft({
        ...form,
        startPosition: mapPickedRange.startPosition,
        endPosition: mapPickedRange.endPosition,
      });
    }
    onStartMapCellRangePick({
      targetBedZoneId: mode === "CREATE" ? null : (activeZone?.id ?? null),
      excludeOrchidGroupId: mode === "EDIT" ? (initialValue?.id ?? null) : null,
      maxCell,
      startCell: startPositionValue,
      endCell: endPositionValue,
    });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.varietyId || !activeZone || submitBlocked) {
      return;
    }
    const range = normalizeCellRange(
      startPositionValue,
      endPositionValue,
      maxCell,
    );

    void onSubmit({
      bedZoneId: activeZone.id,
      varietyId: Number(form.varietyId),
      quantity: Number(form.quantity),
      potSize: nullableText(form.potSize),
      ageYear: nullableNumber(form.ageYear),
      status: form.status.trim(),
      placementType: nullableText(form.placementType),
      trayCount: null,
      splitPlacementAllowed: form.splitPlacementAllowed,
      startPosition: startCellToPosition(String(range.startCell)),
      endPosition: endCellToPosition(String(range.endCell)),
      memo: nullableText(form.memo),
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">
            {mode === "CREATE"
              ? initialValue
                ? "난 묶음 복사"
                : "난 묶음 추가"
              : "난 묶음 수정"}
          </p>
          <h3 className="mt-1 text-base font-semibold">
            {activeZone?.name ?? "맵에서 위치 지정"}
          </h3>
          {activeZone ? (
            <p className="mt-1 text-xs font-semibold text-[#5c6a60]">
              {activeZone.houseNumber}동 {activeZone.physicalBedNumber}배드
            </p>
          ) : mode === "CREATE" ? (
            <p className="mt-1 text-xs font-semibold text-[#5c6a60]">
              맵에서 지정 버튼을 누른 뒤 빈 칸을 선택하세요.
            </p>
          ) : null}
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
        <VarietySearchSelect
          disabled={saving}
          selectedVariety={selectedVariety}
          onSelect={handleSelectVariety}
        />
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="수량"
            required
            type="number"
            value={form.quantity}
            onChange={(value) => updateField("quantity", value)}
          />
          <TextField
            label="초기 년생"
            type="number"
            value={form.ageYear}
            onChange={(value) => updateField("ageYear", value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <PotSizeField
            value={form.potSize}
            onChange={(value) => updateField("potSize", value)}
          />
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">상태</span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={form.status}
              onChange={(event) => updateField("status", event.target.value)}
            >
              <option value="정상">정상</option>
              <option value="주의">주의</option>
              <option value="이상">이상</option>
              <option value="판매 가능">판매 가능</option>
            </select>
          </label>
        </div>
        <div className="grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)] items-end gap-2">
          <button
            className={`mb-px h-[34px] rounded-md border px-3 text-xs font-semibold transition ${
              rangePickActive
                ? "border-[#159447] bg-[#159447] text-white"
                : "border-[#cfd8cc] bg-white text-[#36513d] hover:bg-[#f3f8f2]"
            }`}
            disabled={saving || (mode === "EDIT" && !activeZone)}
            type="button"
            onClick={handleToggleMapCellRangePick}
          >
            {rangePickButtonLabel}
          </button>
          <TextField
            label="시작 칸"
            max={maxCell}
            min={1}
            step={1}
            type="number"
            value={startPositionValue}
            onBlur={commitCellRange}
            onChange={updateStartCell}
          />
          <TextField
            label="끝 칸"
            max={maxCell}
            min={1}
            step={1}
            type="number"
            value={endPositionValue}
            onBlur={commitCellRange}
            onChange={updateEndCell}
          />
        </div>
        {rangeBlocked ? (
          <p className="rounded-md border border-[#f0d299] bg-[#fff8e8] px-3 py-2 text-xs font-semibold text-[#96650f]">
            선택 범위 중간에 이미 배치된 난 묶음이 있습니다.
          </p>
        ) : null}
        <div>
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">
              배치 규격
            </span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={resolvePlacementSelectValue(form.placementType)}
              onChange={(event) =>
                updateField(
                  "placementType",
                  event.target.value === "CUSTOM"
                    ? "CUSTOM:"
                    : event.target.value,
                )
              }
            >
              <option value="">선택</option>
              <option value="TRAY_12">12구 트레이</option>
              <option value="TRAY_15">15구 트레이</option>
              <option value="TRAY_20">20구 트레이</option>
              <option value="TRAY_24">24구 트레이</option>
              <option value="SINGLE_POT">단독 화분</option>
              <option value="HANGING">행잉</option>
              <option value="CUSTOM">기타</option>
            </select>
          </label>
        </div>
        {form.placementType.startsWith("CUSTOM:") ? (
          <TextField
            label="기타 배치 규격명"
            required
            value={form.placementType.slice(7)}
            onChange={(value) =>
              updateField("placementType", `CUSTOM:${value}`)
            }
          />
        ) : null}
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">메모</span>
          <textarea
            className="mt-1 min-h-16 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
            value={form.memo}
            onChange={(event) => updateField("memo", event.target.value)}
          />
        </label>
        <button
          className="w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={saving || !activeZone || !form.varietyId || submitBlocked}
          type="submit"
        >
          {saving ? "저장 중..." : "저장"}
        </button>
      </form>
    </section>
  );
}

function resolvePlacementSelectValue(value: string) {
  return value.startsWith("CUSTOM:") ? "CUSTOM" : value;
}

function createInitialFormState(
  mode: "CREATE" | "EDIT",
  initialValue: OrchidGroup | null,
  defaultPlacement: { startPosition: number; endPosition: number } | null,
): OrchidFormState {
  return {
    varietyId: initialValue?.varietyId ? String(initialValue.varietyId) : "",
    varietyQuery: "",
    genus: initialValue?.genus ?? "",
    varietyName: initialValue?.varietyName ?? "",
    quantity: initialValue ? String(initialValue.quantity) : "1",
    potSize: initialValue?.potSize ?? "",
    ageYear: initialValue?.ageYear ? String(initialValue.ageYear) : "",
    status: initialValue?.status ?? "정상",
    placementType: initialValue?.placementType ?? "",
    trayCount: initialValue?.trayCount ? String(initialValue.trayCount) : "",
    splitPlacementAllowed: initialValue?.splitPlacementAllowed ?? false,
    startPosition:
      mode === "EDIT" && initialValue?.startPosition != null
        ? positionToStartCell(initialValue.startPosition)
        : defaultPlacement != null
          ? String(defaultPlacement.startPosition)
          : "",
    endPosition:
      mode === "EDIT" && initialValue?.endPosition != null
        ? positionToEndCell(initialValue.endPosition)
        : defaultPlacement != null
          ? String(defaultPlacement.endPosition)
          : "",
    memo: initialValue?.memo ?? "",
  };
}

function PotSizeField({
  onChange,
  value,
}: {
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">화분 크기</span>
      <select
        className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-1.5 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {!isStandardPotSize(value) ? (
          <option disabled value={value}>
            검수 필요: {value}
          </option>
        ) : null}
        {POT_SIZE_OPTIONS.map((option) => (
          <option key={option.value || "unspecified"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
