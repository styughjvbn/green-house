"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Layers3, LoaderCircle, Search, Users, X } from "lucide-react";
import type { BedZone, BedZoneSide, OrchidGroup } from "@/entities/farm/types";
import {
  getDerivedWorkTargetMembers,
  getWorkTargetGroupOptions,
} from "../../api/workRecordApi";
import type {
  WorkCollectionOption,
  WorkDerivedGroupOption,
} from "../../model/types";

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

export type WorkTargetSelectionScope =
  | {
      type: "DERIVED_GROUP";
      scopeKey: string;
      label: string;
      memberIds: number[];
    }
  | {
      type: "USER_COLLECTION";
      collectionId: number;
      label: string;
      memberIds: number[];
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
  onConfirm: (
    selectedIds: Set<number>,
    scope: WorkTargetSelectionScope | null,
  ) => void;
}) {
  const [selectedIds, setSelectedIds] = useState(
    () => new Set(initialSelectedIds),
  );
  const [keyword, setKeyword] = useState("");
  const [derivedGroups, setDerivedGroups] = useState<WorkDerivedGroupOption[]>(
    [],
  );
  const [collections, setCollections] = useState<WorkCollectionOption[]>([]);
  const [derivedMemberIds, setDerivedMemberIds] = useState<
    Map<string, number[]>
  >(() => new Map());
  const [loadingGroups, setLoadingGroups] = useState(true);
  const [loadingGroupKey, setLoadingGroupKey] = useState<string | null>(null);
  const [groupError, setGroupError] = useState<string | null>(null);
  const [selectedScope, setSelectedScope] =
    useState<WorkTargetSelectionScope | null>(null);
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
  const selectableIds = useMemo(
    () => new Set(groups.map((group) => group.id)),
    [groups],
  );
  const normalizedKeyword = keyword.trim().toLocaleLowerCase("ko");
  const visibleDerivedGroups = normalizedKeyword
    ? derivedGroups
        .filter((group) =>
          `${group.varietyName} ${group.ageYear ?? ""} ${group.potSize ?? ""}`
            .toLocaleLowerCase("ko")
            .includes(normalizedKeyword),
        )
        .slice(0, 8)
    : derivedGroups
        .filter((group) => derivedMemberIds.has(group.groupKey))
        .slice(0, 8);
  const visibleCollections = normalizedKeyword
    ? collections
        .filter((collection) =>
          collection.name.toLocaleLowerCase("ko").includes(normalizedKeyword),
        )
        .slice(0, 8)
    : [];

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    void getWorkTargetGroupOptions()
      .then((options) => {
        if (cancelled) return;
        setDerivedGroups(options.derivedGroups);
        setCollections(options.collections);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setGroupError(
            error instanceof Error
              ? error.message
              : "그룹 목록을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingGroups(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function toggleIds(targetIds: number[]) {
    const availableIds = targetIds.filter((id) => selectableIds.has(id));
    const shouldSelect = availableIds.some((id) => !selectedIds.has(id));
    setSelectedIds((current) => {
      const next = new Set(current);
      availableIds.forEach((id) => {
        if (shouldSelect) next.add(id);
        else next.delete(id);
      });
      return next;
    });
  }

  function toggleGroups(targetGroups: OrchidGroup[]) {
    setSelectedScope(null);
    toggleIds(targetGroups.map((group) => group.id));
  }

  async function toggleDerivedGroup(group: WorkDerivedGroupOption) {
    if (loadingGroupKey) return;
    const cachedIds = derivedMemberIds.get(group.groupKey);
    if (cachedIds) {
      const availableIds = cachedIds.filter((id) => selectableIds.has(id));
      const selecting = availableIds.some((id) => !selectedIds.has(id));
      toggleIds(cachedIds);
      setSelectedScope(
        selecting
          ? {
              type: "DERIVED_GROUP",
              scopeKey: group.groupKey,
              label: group.varietyName,
              memberIds: availableIds,
            }
          : null,
      );
      setKeyword("");
      return;
    }
    setLoadingGroupKey(group.groupKey);
    setGroupError(null);
    try {
      const members = await getDerivedWorkTargetMembers(group.groupKey);
      const memberIds = members.map((member) => member.id);
      setDerivedMemberIds((current) =>
        new Map(current).set(group.groupKey, memberIds),
      );
      toggleIds(memberIds);
      setSelectedScope({
        type: "DERIVED_GROUP",
        scopeKey: group.groupKey,
        label: group.varietyName,
        memberIds: memberIds.filter((id) => selectableIds.has(id)),
      });
      setKeyword("");
    } catch (error) {
      setGroupError(
        error instanceof Error
          ? error.message
          : "자동 그룹 대상을 불러오지 못했습니다.",
      );
    } finally {
      setLoadingGroupKey(null);
    }
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
              위치, 자동 그룹, 사용자 그룹을 함께 사용해 대상을 선택할 수
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

        <div className="shrink-0 space-y-3 border-b border-[#dbe5da] bg-white px-5 py-3">
          <label className="relative block">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#718077]"
              aria-hidden="true"
            />
            <input
              autoFocus
              className="w-full rounded-md border border-[#cfd8cc] bg-white py-2 pr-3 pl-9 text-sm outline-none focus:border-[#159447] focus:ring-2 focus:ring-[#159447]/15"
              placeholder="위치, 품종 또는 그룹 이름 검색"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>

          {loadingGroups ||
          visibleCollections.length > 0 ||
          visibleDerivedGroups.length > 0 ? (
            <div>
              <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-[#58705e]">
                <span>{normalizedKeyword ? "검색된 그룹" : "빠른 선택"}</span>
                {loadingGroups ? (
                  <LoaderCircle className="h-3.5 w-3.5 animate-spin text-[#159447]" />
                ) : null}
              </div>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {visibleCollections.map((collection) => {
                  const memberIds = collection.members
                    .map((member) => member.orchidGroupId)
                    .filter((id) => selectableIds.has(id));
                  return (
                    <label
                      className="flex min-w-52 cursor-pointer items-center gap-2 rounded-md border border-[#d7dfd5] bg-[#f8faf7] px-3 py-2 hover:border-[#159447]"
                      key={`collection-${collection.id}`}
                    >
                      <SelectionCheckbox
                        label={`${collection.name} 사용자 그룹`}
                        {...selectionStateByIds(memberIds, selectedIds)}
                        disabled={memberIds.length === 0}
                        onChange={() => {
                          const selecting = memberIds.some(
                            (id) => !selectedIds.has(id),
                          );
                          toggleIds(memberIds);
                          setSelectedScope(
                            selecting
                              ? {
                                  type: "USER_COLLECTION",
                                  collectionId: collection.id,
                                  label: collection.name,
                                  memberIds,
                                }
                              : null,
                          );
                          setKeyword("");
                        }}
                      />
                      <Users
                        className="h-4 w-4 shrink-0 text-[#3d6f91]"
                        aria-hidden="true"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-xs font-semibold text-[#26352b]">
                          {collection.name}
                        </span>
                        <span className="block text-[11px] text-[#718077]">
                          사용자 그룹 · {memberIds.length}묶음
                        </span>
                      </span>
                    </label>
                  );
                })}
                {visibleDerivedGroups.map((group) => {
                  const memberIds = derivedMemberIds.get(group.groupKey);
                  const state = memberIds
                    ? selectionStateByIds(
                        memberIds.filter((id) => selectableIds.has(id)),
                        selectedIds,
                      )
                    : { checked: false, indeterminate: false };
                  return (
                    <label
                      className="flex min-w-56 cursor-pointer items-center gap-2 rounded-md border border-[#d7dfd5] bg-[#f8faf7] px-3 py-2 hover:border-[#159447]"
                      key={`derived-${group.groupKey}`}
                    >
                      {loadingGroupKey === group.groupKey ? (
                        <LoaderCircle className="h-4 w-4 shrink-0 animate-spin text-[#159447]" />
                      ) : (
                        <SelectionCheckbox
                          label={`${group.varietyName} 자동 그룹`}
                          {...state}
                          disabled={loadingGroupKey !== null}
                          onChange={() => void toggleDerivedGroup(group)}
                        />
                      )}
                      <Layers3
                        className="h-4 w-4 shrink-0 text-[#159447]"
                        aria-hidden="true"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-xs font-semibold text-[#26352b]">
                          {group.varietyName}
                        </span>
                        <span className="block truncate text-[11px] text-[#718077]">
                          자동 그룹 · {group.ageYear ?? "년생 미지정"}
                          {group.ageYear == null ? "" : "년생"} ·{" "}
                          {group.potSize ?? "화분 미지정"}
                        </span>
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>
          ) : null}

          {groupError ? (
            <p className="rounded-md bg-[#fff1ec] px-3 py-2 text-xs text-[#8f2f19]">
              {groupError}
            </p>
          ) : null}
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
                      const originalHouse =
                        tree.find((item) => item.id === house.id) ?? house;
                      const selectedCount = countSelectedGroups(
                        originalHouse.groups,
                        selectedIds,
                      );
                      const searching = normalizedKeyword.length > 0;
                      return (
                        <div
                          className={`flex min-w-32 items-center gap-2 rounded-md border p-2 ${
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
                            {selectedCount > 0 ? (
                              <span className="text-[11px] font-semibold text-[#3d6f91]">
                                선택 {selectedCount}개
                              </span>
                            ) : null}
                            <span className="text-[11px] text-[#718077]">
                              {searching
                                ? `검색 ${house.groups.length}개 · `
                                : ""}
                              전체 {originalHouse.groups.length}개
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
                        {(["LEFT", "RIGHT"] as const).map((side) => {
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
              onClick={() =>
                onConfirm(
                  new Set(selectedIds),
                  selectedScope &&
                    sameIds(selectedIds, new Set(selectedScope.memberIds))
                    ? selectedScope
                    : null,
                )
              }
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
        {[...zone.groups].reverse().map((group) => (
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
  return selectionStateByIds(
    groups.map((group) => group.id),
    selectedIds,
  );
}

function countSelectedGroups(groups: OrchidGroup[], selectedIds: Set<number>) {
  return groups.reduce(
    (count, group) => count + Number(selectedIds.has(group.id)),
    0,
  );
}

function selectionStateByIds(targetIds: number[], selectedIds: Set<number>) {
  const selectedCount = targetIds.reduce(
    (count, id) => count + Number(selectedIds.has(id)),
    0,
  );
  return {
    checked: targetIds.length > 0 && selectedCount === targetIds.length,
    indeterminate: selectedCount > 0 && selectedCount < targetIds.length,
  };
}

function sameIds(left: Set<number>, right: Set<number>) {
  return left.size === right.size && [...left].every((id) => right.has(id));
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
    LEFT: 0,
    RIGHT: 1,
    CUSTOM: 2,
    HANGING: 3,
  };
  return order[a.side] - order[b.side] || a.name.localeCompare(b.name, "ko");
}
