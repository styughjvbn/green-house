"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import type { BedZone, BedZoneSide, OrchidGroup } from "@/entities/farm/types";

type ZoneNode = {
  id: number;
  name: string;
  side: BedZoneSide;
  groups: OrchidGroup[];
};

type BedNode = {
  number: number;
  groups: OrchidGroup[];
  zones: ZoneNode[];
};

type HouseNode = {
  id: number;
  number: number;
  groups: OrchidGroup[];
  beds: BedNode[];
};

export function WorkTargetSelectionDialog({
  bedZones,
  groups,
  initialSelectedIds,
  onClose,
  onConfirm,
}: {
  bedZones: BedZone[];
  groups: OrchidGroup[];
  initialSelectedIds: Set<number>;
  onClose: () => void;
  onConfirm: (selectedIds: Set<number>) => void;
}) {
  const [selectedIds, setSelectedIds] = useState(
    () => new Set(initialSelectedIds),
  );
  const [keyword, setKeyword] = useState("");
  const tree = useMemo(
    () => buildTargetTree(groups, bedZones),
    [bedZones, groups],
  );
  const visibleTree = useMemo(
    () => filterTargetTree(tree, keyword),
    [keyword, tree],
  );
  const [focusedHouseId, setFocusedHouseId] = useState<number | null>(
    () => tree[0]?.id ?? null,
  );
  const focusedHouse =
    visibleTree.find((house) => house.id === focusedHouseId) ??
    visibleTree[0] ??
    null;
  const selectedGroups = useMemo(
    () => groups.filter((group) => selectedIds.has(group.id)),
    [groups, selectedIds],
  );
  const selectedQuantity = selectedGroups.reduce(
    (sum, group) => sum + group.quantity,
    0,
  );
  const selectedZoneCount = new Set(
    selectedGroups.map((group) => group.bedZoneId),
  ).size;

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  function toggleGroups(targetGroups: OrchidGroup[]) {
    const targetIds = targetGroups.map((group) => group.id);
    const shouldSelect = targetIds.some((id) => !selectedIds.has(id));
    setSelectedIds((current) => {
      const next = new Set(current);
      targetIds.forEach((id) => {
        if (shouldSelect) next.add(id);
        else next.delete(id);
      });
      return next;
    });
  }

  return (
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/45 p-3 sm:p-6"
      role="presentation"
      onMouseDown={(event) => {
        event.stopPropagation();
        onClose();
      }}
    >
      <section
        className="flex max-h-[calc(100dvh-1.5rem)] w-full max-w-4xl flex-col overflow-hidden rounded-lg bg-[#f7faf6] shadow-2xl sm:max-h-[calc(100dvh-3rem)]"
        role="dialog"
        aria-modal="true"
        aria-label="작업 대상 선택"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-[#dbe5da] bg-white px-5 py-4">
          <div>
            <h3 className="text-lg font-bold text-[#17251b]">작업 대상 선택</h3>
            <p className="mt-1 text-sm text-[#617067]">
              동과 다이를 왼쪽에서 오른쪽으로 확인하며 복수 범위를 선택할 수
              있습니다.
            </p>
          </div>
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
            type="button"
            aria-label="닫기"
            onClick={onClose}
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </header>

        <div className="shrink-0 border-b border-[#dbe5da] bg-white px-5 py-3">
          <label className="relative block">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#718077]"
              aria-hidden="true"
            />
            <input
              autoFocus
              className="w-full rounded-md border border-[#cfd8cc] bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-[#159447] focus:ring-2 focus:ring-[#159447]/15"
              placeholder="품종 또는 위치 검색"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5">
          {focusedHouse ? (
            <div className="space-y-4">
              <section>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-sm font-bold text-[#344138]">동 선택</p>
                  <p className="text-xs text-[#718077]">
                    {visibleTree[0].number}동 →{" "}
                    {visibleTree[visibleTree.length - 1].number}동
                  </p>
                </div>
                <div className="overflow-x-auto pb-1">
                  <div className="flex min-w-max gap-2">
                    {visibleTree.map((house) => {
                      const active = house.id === focusedHouse.id;
                      return (
                        <div
                          className={`flex min-w-24 items-center gap-2 rounded-md border p-2 ${
                            active
                              ? "border-[#159447] bg-[#edf8ef]"
                              : "border-[#d7dfd5] bg-white"
                          }`}
                          key={house.id}
                        >
                          <SelectionCheckbox
                            label={`${house.number}동 전체`}
                            {...selectionState(house.groups, selectedIds)}
                            disabled={house.groups.length === 0}
                            onChange={() => toggleGroups(house.groups)}
                          />
                          <button
                            className="flex flex-1 flex-col items-start"
                            type="button"
                            onClick={() => setFocusedHouseId(house.id)}
                          >
                            <span className="text-sm font-bold text-[#26352b]">
                              {house.number}동
                            </span>
                            <span className="text-[11px] text-[#718077]">
                              {house.groups.length}묶음
                            </span>
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </section>

              <section>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-sm font-bold text-[#344138]">
                    {focusedHouse.number}동 배치
                  </p>
                  <p className="text-xs text-[#718077]">
                    다이 전체 또는 좌·우 구역을 선택하세요.
                  </p>
                </div>
                <div className="grid gap-3 lg:grid-cols-3">
                  {focusedHouse.beds.map((bed) => (
                    <div
                      className="overflow-hidden rounded-md border border-[#cfd9cd] bg-white"
                      key={`${focusedHouse.id}-${bed.number}`}
                    >
                      <div className="flex items-center gap-2 border-b border-[#dfe6dd] bg-[#f1f6f0] px-3 py-2.5">
                        <SelectionCheckbox
                          label={`${focusedHouse.number}동 ${bed.number}다이 전체`}
                          {...selectionState(bed.groups, selectedIds)}
                          disabled={bed.groups.length === 0}
                          onChange={() => toggleGroups(bed.groups)}
                        />
                        <span className="text-sm font-bold text-[#26352b]">
                          {bed.number}다이
                        </span>
                        <span className="ml-auto text-xs text-[#718077]">
                          {bed.groups.length}묶음
                        </span>
                      </div>
                      <div className="grid min-h-44 grid-cols-2 gap-px bg-[#dce4da]">
                        {(["RIGHT", "LEFT"] as const).map((side) => {
                          const zone = bed.zones.find(
                            (candidate) => candidate.side === side,
                          );
                          return zone ? (
                            <BedSideZone
                              key={side}
                              bedNumber={bed.number}
                              houseNumber={focusedHouse.number}
                              selectedIds={selectedIds}
                              side={side}
                              zone={zone}
                              onToggle={toggleGroups}
                            />
                          ) : (
                            <div
                              className="flex min-h-44 flex-col items-center justify-center bg-[#edf0ed] px-2 text-center text-[#9aa39c]"
                              key={side}
                              aria-disabled="true"
                            >
                              <span className="text-xs font-bold">
                                {sideLabel(side)}
                              </span>
                              <span className="mt-1 text-[11px]">
                                비어 있음
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            </div>
          ) : (
            <div className="rounded-md border border-[#d7dfd5] bg-white px-4 py-12 text-center text-sm text-[#6a766e]">
              검색 결과가 없습니다.
            </div>
          )}
        </div>

        <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-[#dbe5da] bg-white px-5 py-4">
          <div>
            <p className="text-sm font-bold text-[#26352b]">
              {selectedGroups.length}묶음 · {selectedQuantity}분
            </p>
            <p className="mt-0.5 text-xs text-[#6a766e]">
              {selectedZoneCount}개 구역에서 선택
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              className="rounded-md border border-[#cfd8cc] bg-white px-4 py-2 text-sm font-semibold text-[#435047] disabled:opacity-40"
              disabled={selectedIds.size === 0}
              type="button"
              onClick={() => setSelectedIds(new Set())}
            >
              선택 초기화
            </button>
            <button
              className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
              disabled={selectedGroups.length === 0}
              type="button"
              onClick={() => onConfirm(new Set(selectedIds))}
            >
              대상 확정
            </button>
          </div>
        </footer>
      </section>
    </div>
  );
}

function BedSideZone({
  bedNumber,
  houseNumber,
  selectedIds,
  side,
  zone,
  onToggle,
}: {
  bedNumber: number;
  houseNumber: number;
  selectedIds: Set<number>;
  side: "LEFT" | "RIGHT";
  zone: ZoneNode;
  onToggle: (groups: OrchidGroup[]) => void;
}) {
  if (zone.groups.length === 0) {
    return (
      <div
        className="flex min-h-44 flex-col items-center justify-center bg-[#edf0ed] px-2 text-center text-[#9aa39c]"
        aria-disabled="true"
      >
        <span className="text-xs font-bold">{sideLabel(side)}</span>
        <span className="mt-1 text-[11px]">난 묶음 없음</span>
      </div>
    );
  }

  return (
    <div className="min-h-44 bg-white p-2">
      <div className="flex items-center gap-2 border-b border-[#edf0ec] px-1 pb-2">
        <SelectionCheckbox
          label={`${houseNumber}동 ${bedNumber}다이 ${sideLabel(side)} 전체`}
          {...selectionState(zone.groups, selectedIds)}
          onChange={() => onToggle(zone.groups)}
        />
        <div className="min-w-0">
          <p className="text-xs font-bold text-[#435047]">{sideLabel(side)}</p>
          <p className="truncate text-[10px] text-[#7a867e]">{zone.name}</p>
        </div>
      </div>
      <div className="mt-1 space-y-0.5">
        {zone.groups.map((group) => (
          <label
            className="flex cursor-pointer items-center gap-1.5 rounded px-1 py-1.5 text-xs hover:bg-[#f4f8f3]"
            key={group.id}
          >
            <SelectionCheckbox
              label={`${group.varietyName} ${group.quantity}분`}
              checked={selectedIds.has(group.id)}
              indeterminate={false}
              onChange={() => onToggle([group])}
            />
            <span className="min-w-0 flex-1 truncate text-[#344138]">
              {group.varietyName}
            </span>
            <span className="shrink-0 text-[10px] text-[#6a766e]">
              {group.quantity}분
            </span>
          </label>
        ))}
      </div>
    </div>
  );
}

function SelectionCheckbox({
  checked,
  disabled = false,
  indeterminate,
  label,
  onChange,
}: {
  checked: boolean;
  disabled?: boolean;
  indeterminate: boolean;
  label: string;
  onChange: () => void;
}) {
  const ref = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.indeterminate = indeterminate;
  }, [indeterminate]);

  return (
    <input
      ref={ref}
      aria-label={label}
      checked={checked}
      className="h-4 w-4 shrink-0 accent-[#159447] disabled:cursor-not-allowed disabled:opacity-35"
      disabled={disabled}
      type="checkbox"
      onClick={(event) => event.stopPropagation()}
      onChange={onChange}
    />
  );
}

function sideLabel(side: "LEFT" | "RIGHT") {
  return side === "RIGHT" ? "우측" : "좌측";
}

function selectionState(groups: OrchidGroup[], selectedIds: Set<number>) {
  const selectedCount = groups.reduce(
    (count, group) => count + Number(selectedIds.has(group.id)),
    0,
  );
  return {
    checked: groups.length > 0 && selectedCount === groups.length,
    indeterminate: selectedCount > 0 && selectedCount < groups.length,
  };
}

function buildTargetTree(
  groups: OrchidGroup[],
  bedZones: BedZone[],
): HouseNode[] {
  const groupsByZone = new Map<number, OrchidGroup[]>();
  groups.forEach((group) => {
    const zoneGroups = groupsByZone.get(group.bedZoneId) ?? [];
    zoneGroups.push(group);
    groupsByZone.set(group.bedZoneId, zoneGroups);
  });

  const zonesByHouse = new Map<number, BedZone[]>();
  bedZones.forEach((zone) => {
    const houseZones = zonesByHouse.get(zone.houseId) ?? [];
    houseZones.push(zone);
    zonesByHouse.set(zone.houseId, houseZones);
  });

  return [...zonesByHouse.entries()]
    .map(([houseId, houseZones]) => {
      const beds = [1, 2, 3].map((bedNumber) => {
        const zones = houseZones
          .filter((zone) => zone.physicalBedNumber === bedNumber)
          .map((zone) => ({
            id: zone.id,
            name: zone.name,
            side: zone.side,
            groups: [...(groupsByZone.get(zone.id) ?? [])].sort(compareGroups),
          }))
          .sort(compareZones);
        return {
          number: bedNumber,
          groups: zones.flatMap((zone) => zone.groups),
          zones,
        };
      });
      return {
        id: houseId,
        number: houseZones[0].houseNumber,
        groups: beds.flatMap((bed) => bed.groups),
        beds,
      };
    })
    .sort((a, b) => a.number - b.number);
}

function filterTargetTree(tree: HouseNode[], keyword: string): HouseNode[] {
  const normalizedKeyword = keyword.trim().toLocaleLowerCase("ko");
  if (!normalizedKeyword) return tree;

  return tree.flatMap((house) => {
    const beds = house.beds.map((bed) => {
      const zones = bed.zones.flatMap((zone) => {
        const matchingGroups = zone.groups.filter((group) =>
          `${group.varietyName} ${group.houseNumber}동 ${group.physicalBedNumber}다이 ${group.bedZoneName}`
            .toLocaleLowerCase("ko")
            .includes(normalizedKeyword),
        );
        return matchingGroups.length > 0
          ? [{ ...zone, groups: matchingGroups }]
          : [];
      });
      return {
        ...bed,
        groups: zones.flatMap((zone) => zone.groups),
        zones,
      };
    });
    return beds.some((bed) => bed.groups.length > 0)
      ? [
          {
            ...house,
            groups: beds.flatMap((bed) => bed.groups),
            beds,
          },
        ]
      : [];
  });
}

function compareGroups(a: OrchidGroup, b: OrchidGroup) {
  return a.sortOrder - b.sortOrder || a.id - b.id;
}

function compareZones(a: ZoneNode, b: ZoneNode) {
  const order: Record<BedZoneSide, number> = {
    RIGHT: 0,
    LEFT: 1,
    CUSTOM: 2,
    HANGING: 3,
  };
  return order[a.side] - order[b.side] || a.name.localeCompare(b.name, "ko");
}
