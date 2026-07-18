"use client";

import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
} from "@/entities/farm/types";
import { MapPin, X } from "lucide-react";
import { useMemo, useState } from "react";

type ResolvedZone = {
  house: House;
  bed: PhysicalBed;
  zone: BedZone;
};

export type FarmPlacementSelection = {
  bedZoneId: number;
  startCell: number;
  endCell: number;
  startPosition: number;
  endPosition: number;
  label: string;
};

export type FarmPlacementReference = FarmPlacementSelection & {
  kind: "SOURCE" | "RESULT" | "SAVED_RESULT";
};

export function FarmPlacementField({
  buttonPlaceholder = "칸 선택",
  dialogDescription = "구역을 고른 뒤 차지할 시작 칸과 끝 칸을 지정하세요.",
  dialogTitle = "배치 칸 선택",
  excludeOrchidGroupId = null,
  excludeOrchidGroupIds = [],
  fieldLabel = "배치 칸",
  houses,
  referencePlacements = [],
  submitLabel = "선택 완료",
  value,
  onChange,
}: {
  buttonPlaceholder?: string;
  dialogDescription?: string;
  dialogTitle?: string;
  excludeOrchidGroupId?: number | null;
  excludeOrchidGroupIds?: number[];
  fieldLabel?: string;
  houses: House[];
  referencePlacements?: FarmPlacementReference[];
  submitLabel?: string;
  value: FarmPlacementSelection | null;
  onChange: (value: FarmPlacementSelection) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <label className="space-y-1 text-xs font-semibold text-[#425047]">
        <span>{fieldLabel}</span>
        <button
          className="flex h-10 w-full items-center justify-between gap-2 rounded-md border border-[#d7ddd8] bg-white px-3 text-left text-sm font-semibold text-[#17251b] transition outline-none hover:border-[#159447] focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
          type="button"
          onClick={() => setOpen(true)}
        >
          <span className={value ? "" : "text-[#7d887f]"}>
            {value?.label ?? buttonPlaceholder}
          </span>
          <MapPin className="h-4 w-4 shrink-0 text-[#159447]" />
        </button>
      </label>

      {open ? (
        <FarmPlacementPickerDialog
          dialogDescription={dialogDescription}
          dialogTitle={dialogTitle}
          excludeOrchidGroupId={excludeOrchidGroupId}
          excludeOrchidGroupIds={excludeOrchidGroupIds}
          houses={houses}
          initialValue={value}
          referencePlacements={referencePlacements}
          submitLabel={submitLabel}
          onClose={() => setOpen(false)}
          onSelect={(nextValue) => {
            onChange(nextValue);
            setOpen(false);
          }}
        />
      ) : null}
    </>
  );
}

export function FarmPlacementPickerDialog({
  dialogDescription = "구역을 고른 뒤 차지할 시작 칸과 끝 칸을 지정하세요.",
  dialogTitle = "배치 칸 선택",
  excludeOrchidGroupId = null,
  excludeOrchidGroupIds = [],
  houses,
  initialValue,
  referencePlacements = [],
  submitDisabled = false,
  submitLabel = "선택 완료",
  onClose,
  onSelect,
}: {
  dialogDescription?: string;
  dialogTitle?: string;
  excludeOrchidGroupId?: number | null;
  excludeOrchidGroupIds?: number[];
  houses: House[];
  initialValue: FarmPlacementSelection | null;
  referencePlacements?: FarmPlacementReference[];
  submitDisabled?: boolean;
  submitLabel?: string;
  onClose: () => void;
  onSelect: (value: FarmPlacementSelection) => void;
}) {
  const zones = useMemo(() => flattenResolvedZones(houses), [houses]);
  const fallbackZoneId = zones[0]?.zone.id ?? 0;
  const [bedZoneId, setBedZoneId] = useState(
    initialValue?.bedZoneId ?? fallbackZoneId,
  );
  const selected = zones.find((item) => item.zone.id === bedZoneId) ?? zones[0];
  const [houseId, setHouseId] = useState(selected?.house.id ?? houses[0]?.id);
  const selectedHouse =
    houses.find((house) => house.id === houseId) ??
    selected?.house ??
    houses[0];
  const maxCell = Math.max(
    1,
    Math.floor(selected?.bed.positionUnitCount ?? 28),
  );
  const [startCell, setStartCell] = useState(initialValue?.startCell ?? 1);
  const [endCell, setEndCell] = useState(initialValue?.endCell ?? 1);

  const normalizedStart = clamp(Math.min(startCell, endCell), 1, maxCell);
  const normalizedEnd = clamp(Math.max(startCell, endCell), 1, maxCell);
  const selectedCells = buildCellSet(normalizedStart, normalizedEnd);
  const referenceOrchidGroupIds = useMemo(
    () =>
      new Set([
        ...excludeOrchidGroupIds,
        ...(excludeOrchidGroupId == null ? [] : [excludeOrchidGroupId]),
      ]),
    [excludeOrchidGroupId, excludeOrchidGroupIds],
  );
  const occupiedCells = useMemo(
    () =>
      buildOccupiedCells(
        selected?.zone.orchidGroups ?? [],
        referenceOrchidGroupIds,
        referencePlacements,
        selected?.zone.id ?? null,
      ),
    [referenceOrchidGroupIds, referencePlacements, selected],
  );
  const hasOverlap = [...selectedCells].some((cell) => occupiedCells.has(cell));
  const selectedLabel = selected
    ? formatSelectionLabel(selected, normalizedStart, normalizedEnd)
    : "";

  function updateZone(nextBedZoneId: number) {
    const next = zones.find((item) => item.zone.id === nextBedZoneId);
    const nextMaxCell = Math.max(
      1,
      Math.floor(next?.bed.positionUnitCount ?? 28),
    );
    setBedZoneId(nextBedZoneId);
    if (next) {
      setHouseId(next.house.id);
    }
    setStartCell(1);
    setEndCell(Math.min(1, nextMaxCell));
  }

  function updateHouse(nextHouseId: number) {
    const house = houses.find((item) => item.id === nextHouseId);
    const nextZone = house?.physicalBeds[0]?.bedZones[0];
    setHouseId(nextHouseId);
    if (nextZone) {
      updateZone(nextZone.id);
    }
  }

  function updateSpan(nextSpan: number) {
    const span = clamp(nextSpan, 1, maxCell);
    setEndCell(clamp(normalizedStart + span - 1, 1, maxCell));
  }

  function selectCell(zoneId: number, cell: number) {
    if (zoneId !== bedZoneId) {
      setBedZoneId(zoneId);
      setStartCell(cell);
      setEndCell(cell);
      return;
    }
    if (startCell !== endCell) {
      setStartCell(cell);
      setEndCell(cell);
      return;
    }
    if (cell === startCell) {
      return;
    }
    setStartCell(Math.min(startCell, cell));
    setEndCell(Math.max(startCell, cell));
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[90vh] w-full max-w-7xl flex-col overflow-hidden rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={dialogTitle}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-[#e1e7df] px-4 py-3">
          <div>
            <h2 className="text-base font-bold text-[#17251b]">
              {dialogTitle}
            </h2>
            <p className="mt-0.5 text-xs text-[#66736a]">{dialogDescription}</p>
          </div>
          <button
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7ddd4]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        <div className="grid min-h-0 flex-1 lg:grid-cols-[220px_minmax(0,1fr)]">
          <aside className="space-y-4 border-b border-[#e1e7df] bg-[#f8faf7] p-4 lg:border-r lg:border-b-0">
            <label className="block space-y-1">
              <span className="text-xs font-semibold text-[#425047]">동</span>
              <select
                className={inputClass}
                value={selectedHouse?.id ?? ""}
                onChange={(event) => updateHouse(Number(event.target.value))}
              >
                {houses.map((house) => (
                  <option key={house.id} value={house.id}>
                    {house.number}동
                  </option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-3 gap-2">
              <NumberField
                label="시작 칸"
                max={maxCell}
                min={1}
                value={normalizedStart}
                onChange={setStartCell}
              />
              <NumberField
                label="끝 칸"
                max={maxCell}
                min={1}
                value={normalizedEnd}
                onChange={setEndCell}
              />
              <NumberField
                label="칸수"
                max={maxCell}
                min={1}
                value={normalizedEnd - normalizedStart + 1}
                onChange={updateSpan}
              />
            </div>

            <div className="rounded-md border border-[#dde5db] bg-white p-3 text-xs text-[#425047]">
              <p className="font-bold">
                {selectedLabel || "선택 가능한 구역 없음"}
              </p>
              <p className="mt-1 text-[#66736a]">
                초록색은 선택 범위, 노란색은 원본 난 묶음 위치, 흰색은 빈 칸,
                회색은 이미 사용 중인 칸입니다.
              </p>
              {hasOverlap ? (
                <p className="mt-2 font-bold text-[#b42318]">
                  선택 범위가 기존 난 묶음과 겹칩니다.
                </p>
              ) : null}
            </div>
          </aside>

          <div className="min-h-0 overflow-y-auto p-4">
            {selectedHouse ? (
              <HousePreview
                excludeOrchidGroupId={excludeOrchidGroupId}
                excludeOrchidGroupIds={excludeOrchidGroupIds}
                referencePlacements={referencePlacements}
                selectedBedZoneId={selected?.zone.id ?? null}
                selectedCells={selectedCells}
                house={selectedHouse}
                onSelectCell={selectCell}
              />
            ) : (
              <div className="rounded-md border border-dashed border-[#d7ddd4] p-8 text-center text-sm text-[#66736a]">
                선택 가능한 배치 구역이 없습니다.
              </div>
            )}
          </div>
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-[#e1e7df] px-4 py-3">
          <button
            className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
            disabled={!selected || hasOverlap || submitDisabled}
            type="button"
            onClick={() => {
              if (!selected || hasOverlap || submitDisabled) return;
              onSelect({
                bedZoneId: selected.zone.id,
                startCell: normalizedStart,
                endCell: normalizedEnd,
                startPosition: normalizedStart - 1,
                endPosition: normalizedEnd,
                label: selectedLabel,
              });
            }}
          >
            {submitLabel}
          </button>
        </footer>
      </section>
    </div>
  );
}

function HousePreview({
  excludeOrchidGroupId,
  excludeOrchidGroupIds,
  house,
  referencePlacements,
  selectedBedZoneId,
  selectedCells,
  onSelectCell,
}: {
  excludeOrchidGroupId: number | null;
  excludeOrchidGroupIds: number[];
  house: House;
  referencePlacements: FarmPlacementReference[];
  selectedBedZoneId: number | null;
  selectedCells: Set<number>;
  onSelectCell: (bedZoneId: number, cell: number) => void;
}) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-[#17251b]">{house.number}동</h3>
        <div className="flex items-center gap-3 text-xs font-semibold text-[#66736a]">
          <Legend color="bg-[#159447]" label="선택" />
          {excludeOrchidGroupId != null ||
          excludeOrchidGroupIds.length > 0 ||
          referencePlacements.some((item) => item.kind === "SOURCE") ? (
            <Legend color="bg-[#fff1b8]" label="원본 위치" />
          ) : null}
          {referencePlacements.some((item) => item.kind === "RESULT") ? (
            <Legend color="bg-[#dcecff]" label="다른 결과" />
          ) : null}
          {referencePlacements.some((item) => item.kind === "SAVED_RESULT") ? (
            <Legend color="bg-[#e8f3df]" label="다른 회차 결과" />
          ) : null}
          <Legend color="bg-[#eef1ed]" label="사용 중" />
          <Legend color="bg-white" label="빈 칸" />
        </div>
      </div>

      <div
        className="grid gap-4"
        style={{
          gridTemplateColumns: `repeat(${house.physicalBeds.length}, minmax(0, 1fr))`,
        }}
      >
        {house.physicalBeds.map((bed) => (
          <section
            key={bed.id}
            className="min-w-0 rounded-md border border-[#dce5da] bg-[#fbfcfb] p-3"
          >
            <div className="mb-3 flex items-center justify-between">
              <h4 className="text-xs font-bold text-[#2d4a35]">
                {bed.number}다이
              </h4>
              <span className="text-[11px] font-semibold text-[#7b867f]">
                {Math.floor(bed.positionUnitCount ?? 28)}칸 기준
              </span>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {bed.bedZones.map((zone) => (
                <ZonePreview
                  key={zone.id}
                  excludeOrchidGroupId={excludeOrchidGroupId}
                  excludeOrchidGroupIds={excludeOrchidGroupIds}
                  maxCell={Math.max(1, Math.floor(bed.positionUnitCount ?? 28))}
                  referencePlacements={referencePlacements}
                  selected={selectedBedZoneId === zone.id}
                  selectedCells={selectedCells}
                  zone={zone}
                  onSelectCell={(cell) => onSelectCell(zone.id, cell)}
                />
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

function ZonePreview({
  excludeOrchidGroupId,
  excludeOrchidGroupIds,
  maxCell,
  referencePlacements,
  selected,
  selectedCells,
  zone,
  onSelectCell,
}: {
  excludeOrchidGroupId: number | null;
  excludeOrchidGroupIds: number[];
  maxCell: number;
  referencePlacements: FarmPlacementReference[];
  selected: boolean;
  selectedCells: Set<number>;
  zone: BedZone;
  onSelectCell: (cell: number) => void;
}) {
  const cells = Array.from({ length: maxCell }, (_, index) => maxCell - index);
  const occupiedCells = buildOccupiedCells(
    zone.orchidGroups,
    new Set([
      ...excludeOrchidGroupIds,
      ...(excludeOrchidGroupId == null ? [] : [excludeOrchidGroupId]),
    ]),
    referencePlacements,
    zone.id,
  );
  const referenceOrchidGroupIds = new Set([
    ...excludeOrchidGroupIds,
    ...(excludeOrchidGroupId == null ? [] : [excludeOrchidGroupId]),
  ]);
  const referenceCells = buildReferenceCells(
    zone.orchidGroups,
    referenceOrchidGroupIds,
    referencePlacements,
    zone.id,
  );

  return (
    <div
      className={`overflow-hidden rounded-md border bg-white ${
        selected
          ? "border-[#159447] ring-2 ring-[#159447]/20"
          : "border-[#dbe3d9]"
      }`}
    >
      <div className="flex items-center justify-between border-b border-[#e7ece5] px-2 py-1.5">
        <p className="truncate text-xs font-bold text-[#17251b]">{zone.name}</p>
        <span className="text-[10px] font-semibold text-[#7b867f]">
          {zone.orchidGroups.length}묶음
        </span>
      </div>
      <div className="grid grid-cols-[34px_minmax(0,1fr)]">
        <div className="border-r border-[#edf1ec]">
          {cells.map((cell) => (
            <div
              key={cell}
              className="flex h-5 items-center justify-end pr-1.5 text-[10px] font-bold text-[#2d5a3b]"
            >
              {cell % 5 === 0 || cell === maxCell ? cell : ""}
            </div>
          ))}
        </div>
        <div className="min-w-0">
          {cells.map((cell) => {
            const occupied = occupiedCells.has(cell);
            const reference = referenceCells.get(cell);
            const cellSelected = selected && selectedCells.has(cell);

            return (
              <button
                key={cell}
                className={`flex h-5 w-full items-center justify-between border-b border-[#edf1ec] px-2 text-left text-[10px] transition last:border-b-0 ${
                  cellSelected
                    ? "bg-[#159447] text-white"
                    : reference
                      ? reference.kind === "RESULT"
                        ? "bg-[#dcecff] text-[#20518f] hover:bg-[#c7e0ff]"
                        : reference.kind === "SAVED_RESULT"
                          ? "bg-[#e8f3df] text-[#2f6334] hover:bg-[#d9ebca]"
                          : "bg-[#fff1b8] text-[#6f5700] hover:bg-[#ffe99a]"
                      : occupied
                        ? "bg-[#eef1ed] text-[#77817a]"
                        : "bg-white text-[#425047] hover:bg-[#eef8ef]"
                }`}
                type="button"
                onClick={() => onSelectCell(cell)}
              >
                <span className="truncate pl-2 font-semibold">
                  {reference
                    ? `${referencePrefix(reference.kind)} · ${reference.label}`
                    : occupied
                      ? findGroupLabel(zone.orchidGroups, cell)
                      : "빈 칸"}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span
        className={`h-2.5 w-2.5 rounded-sm border border-[#d9e1d8] ${color}`}
      />
      {label}
    </span>
  );
}

function referencePrefix(kind: FarmPlacementReference["kind"]) {
  if (kind === "SOURCE") return "원본";
  if (kind === "SAVED_RESULT") return "다른 회차";
  return "결과";
}

function NumberField({
  label,
  max,
  min,
  value,
  onChange,
}: {
  label: string;
  max: number;
  min: number;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-semibold text-[#425047]">{label}</span>
      <input
        className={inputClass}
        max={max}
        min={min}
        type="number"
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      />
    </label>
  );
}

function flattenResolvedZones(houses: House[]): ResolvedZone[] {
  return houses.flatMap((house) =>
    house.physicalBeds.flatMap((bed) =>
      bed.bedZones.map((zone) => ({
        house,
        bed,
        zone,
      })),
    ),
  );
}

function buildCellSet(startCell: number, endCell: number) {
  const cells = new Set<number>();
  for (let cell = startCell; cell <= endCell; cell += 1) {
    cells.add(cell);
  }
  return cells;
}

function buildOccupiedCells(
  orchidGroups: OrchidGroup[],
  excludeOrchidGroupIds: Set<number>,
  referencePlacements: FarmPlacementReference[] = [],
  bedZoneId: number | null = null,
) {
  const cells = new Set<number>();
  orchidGroups.forEach((group) => {
    if (excludeOrchidGroupIds.has(group.id)) {
      return;
    }
    if (group.startPosition == null || group.endPosition == null) {
      return;
    }
    const startCell = Math.floor(group.startPosition) + 1;
    const endCell = Math.ceil(group.endPosition);
    for (let cell = startCell; cell <= endCell; cell += 1) {
      cells.add(cell);
    }
  });
  referencePlacements
    .filter(
      (reference) =>
        (reference.kind === "RESULT" || reference.kind === "SAVED_RESULT") &&
        (bedZoneId == null || reference.bedZoneId === bedZoneId),
    )
    .forEach((reference) => {
      for (
        let cell = reference.startCell;
        cell <= reference.endCell;
        cell += 1
      ) {
        cells.add(cell);
      }
    });
  return cells;
}

function buildReferenceCells(
  orchidGroups: OrchidGroup[],
  referenceOrchidGroupIds: Set<number>,
  referencePlacements: FarmPlacementReference[] = [],
  bedZoneId: number | null = null,
) {
  const cells = new Map<number, FarmPlacementReference>();
  orchidGroups.forEach((group) => {
    if (
      !referenceOrchidGroupIds.has(group.id) ||
      group.startPosition == null ||
      group.endPosition == null
    ) {
      return;
    }
    const startCell = Math.floor(group.startPosition) + 1;
    const endCell = Math.ceil(group.endPosition);
    const label = `${group.varietyName} ${group.quantity.toLocaleString()}분`;
    for (let cell = startCell; cell <= endCell; cell += 1) {
      cells.set(cell, {
        bedZoneId: group.bedZoneId,
        startCell,
        endCell,
        startPosition: group.startPosition,
        endPosition: group.endPosition,
        kind: "SOURCE",
        label,
      });
    }
  });
  referencePlacements.forEach((reference) => {
    if (bedZoneId != null && reference.bedZoneId !== bedZoneId) return;
    for (let cell = reference.startCell; cell <= reference.endCell; cell += 1) {
      cells.set(cell, reference);
    }
  });
  return cells;
}

function findGroupLabel(
  orchidGroups: OrchidGroup[],
  cell: number,
  groupIds?: Set<number>,
) {
  const group = orchidGroups.find((item) => {
    if (groupIds && !groupIds.has(item.id)) {
      return false;
    }
    if (item.startPosition == null || item.endPosition == null) {
      return false;
    }
    return item.startPosition < cell && item.endPosition > cell - 1;
  });
  return group
    ? `${group.varietyName} ${group.quantity.toLocaleString()}분`
    : "";
}

function formatSelectionLabel(
  selected: ResolvedZone,
  startCell: number,
  endCell: number,
) {
  return `${selected.house.number}동 ${selected.bed.number}다이 ${selected.zone.name} ${startCell}-${endCell}칸`;
}

function clamp(value: number, min: number, max: number) {
  if (Number.isNaN(value)) return min;
  return Math.min(Math.max(value, min), max);
}

const inputClass =
  "h-10 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]";
