"use client";

import useEmblaCarousel from "embla-carousel-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Layers3,
  LoaderCircle,
  Search,
  Users,
  X,
} from "lucide-react";
import type { BedZoneSide, House, OrchidGroup } from "@/entities/farm/types";
import { useFarmBedViewportCache } from "@/entities/farm/model/useFarmBedViewportCache";
import {
  getDerivedWorkTargetMembers,
  getWorkTargetGroupOptions,
} from "../../api/workRecordApi";
import type {
  WorkCollectionOption,
  WorkDerivedGroupOption,
  WorkTargetSelectionScope,
} from "../../model/types";

type ZoneNode = {
  id: number;
  name: string;
  side: BedZoneSide;
  groups: OrchidGroup[];
};

type BedNode = {
  id: number;
  number: number;
  positionUnitCount: number | null;
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
  groups,
  houses,
  initialSelectedIds,
  onClose,
  onConfirm,
}: {
  groups: OrchidGroup[];
  houses: House[];
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
  const [quickSelectionOpen, setQuickSelectionOpen] = useState(true);
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
  const tree = useMemo(() => buildTargetTree(groups, houses), [groups, houses]);
  const visibleTree = useMemo(
    () => filterTargetTree(tree, keyword),
    [keyword, tree],
  );
  const { bedOrder, bedsById, loadAround } = useFarmBedViewportCache(
    houses[0]?.physicalBeds[0]?.id ?? null,
  );
  const visibleGroupIds = useMemo(
    () =>
      new Set(
        visibleTree.flatMap((house) => house.groups.map((group) => group.id)),
      ),
    [visibleTree],
  );
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
        className="flex h-[calc(100dvh-1.5rem)] max-h-[calc(100dvh-1.5rem)] w-full max-w-6xl flex-col overflow-hidden rounded-lg bg-[#f7faf6] shadow-2xl sm:h-[calc(100dvh-3rem)] sm:max-h-[calc(100dvh-3rem)]"
        role="dialog"
        aria-modal="true"
        aria-label="작업 대상 선택"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex shrink-0 items-end justify-between gap-3 border-b border-[#dbe5da] bg-white px-5 py-1.5">
          <div className="flex gap-2">
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

        <div className="shrink-0 space-y-3 border-b border-[#dbe5da] bg-white px-3 py-2">
          <label className="relative block">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#718077]"
              aria-hidden="true"
            />
            <input
              autoFocus
              className="w-full rounded-md border border-[#cfd8cc] bg-white py-1.5 pr-3 pl-9 text-sm outline-none focus:border-[#159447] focus:ring-2 focus:ring-[#159447]/15"
              placeholder="위치, 품종 또는 그룹 이름 검색"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </label>

          {loadingGroups ||
          visibleCollections.length > 0 ||
          visibleDerivedGroups.length > 0 ? (
            <div>
              <div
                className={`flex items-center gap-2 text-xs font-semibold text-[#58705e] ${
                  quickSelectionOpen ? "mb-2" : ""
                }`}
              >
                {normalizedKeyword ? (
                  <span>검색된 그룹</span>
                ) : (
                  <div
                    aria-expanded={quickSelectionOpen}
                    className="inline-flex items-center gap-1 hover:text-[#159447]"
                    role="button"
                    tabIndex={0}
                    onClick={() => setQuickSelectionOpen((current) => !current)}
                  >
                    빠른 선택
                    <ChevronDown
                      className={`h-3.5 w-3.5 transition-transform ${
                        quickSelectionOpen ? "" : "-rotate-90"
                      }`}
                      aria-hidden="true"
                    />
                  </div>
                )}
                {loadingGroups ? (
                  <LoaderCircle className="h-3.5 w-3.5 animate-spin text-[#159447]" />
                ) : null}
              </div>
              {normalizedKeyword || quickSelectionOpen ? (
                <div className="flex gap-2 overflow-x-auto">
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
              ) : null}
            </div>
          ) : null}

          {groupError ? (
            <p className="rounded-md bg-[#fff1ec] px-3 py-2 text-xs text-[#8f2f19]">
              {groupError}
            </p>
          ) : null}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-3">
          {visibleTree.length > 0 ? (
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
                      const originalHouse =
                        tree.find((item) => item.id === house.id) ?? house;
                      const selectedCount = countSelectedGroups(
                        originalHouse.groups,
                        selectedIds,
                      );
                      const searching = normalizedKeyword.length > 0;
                      return (
                        <div
                          className="flex min-w-32 items-center gap-2 rounded-md border border-[#d7dfd5] bg-white p-2"
                          key={house.id}
                        >
                          <SelectionCheckbox
                            label={`${house.number}동 전체`}
                            {...selectionState(house.groups, selectedIds)}
                            disabled={house.groups.length === 0}
                            onChange={() => toggleGroups(house.groups)}
                          />
                          <div className="flex flex-1 flex-col items-start">
                            <span className="flex-end flex items-end gap-1 text-sm font-bold text-[#26352b]">
                              {house.number}동
                              {selectedCount > 0 ? (
                                <span className="text-[11px] font-semibold text-[#3d6f91]">
                                  {selectedCount}개 선택
                                </span>
                              ) : null}
                            </span>
                            <span className="text-[11px] text-[#718077]">
                              {searching
                                ? `검색 ${house.groups.length}개 · `
                                : ""}
                              전체 {originalHouse.groups.length}개
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </section>

              <section>
                <WorkTargetBedCarousel
                  bedOrder={bedOrder}
                  bedsById={bedsById}
                  selectedIds={selectedIds}
                  visibleGroupIds={visibleGroupIds}
                  onViewportIndexChange={loadAround}
                  onToggleGroups={toggleGroups}
                />
              </section>
            </div>
          ) : (
            <div className="rounded-md border border-[#d7dfd5] bg-white px-4 py-12 text-center text-sm text-[#6a766e]">
              검색 결과가 없습니다.
            </div>
          )}
        </div>

        <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-[#dbe5da] bg-white px-5 py-1.5">
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

function WorkTargetBedCarousel({
  bedOrder,
  bedsById,
  selectedIds,
  visibleGroupIds,
  onViewportIndexChange,
  onToggleGroups,
}: {
  bedOrder: Array<{
    id: number;
    houseId: number;
    houseNumber: number;
    number: number;
  }>;
  bedsById: Map<number, import("@/entities/farm/types").PhysicalBed>;
  selectedIds: Set<number>;
  visibleGroupIds: Set<number>;
  onViewportIndexChange: (index: number) => void;
  onToggleGroups: (groups: OrchidGroup[]) => void;
}) {
  const [emblaRef, emblaApi] = useEmblaCarousel({
    align: "start",
    containScroll: "trimSnaps",
    dragFree: false,
    loop: false,
    slidesToScroll: 1,
  });
  const [canScrollPrevious, setCanScrollPrevious] = useState(false);
  const [canScrollNext, setCanScrollNext] = useState(false);
  const [activeBedIndex, setActiveBedIndex] = useState(0);
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null);
  const pointerDraggedRef = useRef(false);
  const lastDragEndRef = useRef(0);
  const houses = useMemo(
    () =>
      bedOrder.filter(
        (bed, index) =>
          index === 0 || bed.houseId !== bedOrder[index - 1]?.houseId,
      ),
    [bedOrder],
  );

  const syncControls = useCallback(() => {
    if (!emblaApi) return;
    setCanScrollPrevious(emblaApi.canScrollPrev());
    setCanScrollNext(emblaApi.canScrollNext());
  }, [emblaApi]);

  useEffect(() => {
    if (!emblaApi) return;
    const handleSelect = () => {
      syncControls();
      const index = emblaApi.selectedScrollSnap();
      setActiveBedIndex(index);
      onViewportIndexChange(index);
    };
    emblaApi.on("reInit", syncControls);
    emblaApi.on("select", handleSelect);
    emblaApi.reInit();
    return () => {
      emblaApi.off("reInit", syncControls);
      emblaApi.off("select", handleSelect);
    };
  }, [emblaApi, onViewportIndexChange, syncControls]);

  useEffect(() => {
    onViewportIndexChange(0);
  }, [onViewportIndexChange]);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <div className="mb-2 flex items-center justify-between gap-2">
          <p className="text-sm font-bold text-[#344138]">전체 배치</p>
          <p className="text-xs text-[#718077]">
            다이 전체 또는 구역별로 선택하세요.
          </p>
        </div>
        <div className="flex items-center justify-end gap-2">
          <select
            aria-label="동으로 이동"
            className="h-8 rounded-md border border-[#d7dfd5] bg-white px-2 text-xs font-semibold text-[#344138]"
            value={bedOrder[activeBedIndex]?.houseId ?? ""}
            onChange={(event) => {
              const nextHouseId = Number(event.target.value);
              const index = bedOrder.findIndex(
                (bed) => bed.houseId === nextHouseId,
              );
              if (index >= 0) emblaApi?.scrollTo(index);
            }}
          >
            {houses.map((house) => (
              <option key={house.houseId} value={house.houseId}>
                {house.houseNumber}동으로 이동
              </option>
            ))}
          </select>
          <button
            aria-label="이전 다이"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7dfd5] bg-white disabled:cursor-not-allowed disabled:opacity-40"
            disabled={!canScrollPrevious}
            type="button"
            onClick={() => emblaApi?.scrollPrev()}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            aria-label="다음 다이"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7dfd5] bg-white disabled:cursor-not-allowed disabled:opacity-40"
            disabled={!canScrollNext}
            type="button"
            onClick={() => emblaApi?.scrollNext()}
          >
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div
        ref={emblaRef}
        className="overflow-hidden"
        data-testid="work-target-bed-carousel"
        onClickCapture={(event) => {
          if (Date.now() - lastDragEndRef.current < 400) {
            event.preventDefault();
            event.stopPropagation();
          }
        }}
        onPointerDownCapture={(event) => {
          pointerStartRef.current = { x: event.clientX, y: event.clientY };
          pointerDraggedRef.current = false;
        }}
        onPointerMoveCapture={(event) => {
          const start = pointerStartRef.current;
          if (
            start &&
            Math.hypot(event.clientX - start.x, event.clientY - start.y) > 6
          ) {
            pointerDraggedRef.current = true;
          }
        }}
        onPointerUpCapture={() => {
          if (pointerDraggedRef.current) lastDragEndRef.current = Date.now();
          pointerStartRef.current = null;
          pointerDraggedRef.current = false;
        }}
        onPointerCancelCapture={() => {
          pointerStartRef.current = null;
          pointerDraggedRef.current = false;
        }}
      >
        <div className="-ml-3 flex touch-pan-y">
          {bedOrder.map((bedOrderItem) => {
            const loadedBed = bedsById.get(bedOrderItem.id);
            const bed = loadedBed
              ? workTargetBedNode(loadedBed, visibleGroupIds)
              : null;
            return (
              <div
                className="min-w-0 shrink-0 basis-full pl-3 md:basis-1/2 lg:basis-1/3"
                key={bedOrderItem.id}
              >
                <WorkTargetBedCard
                  bed={bed}
                  houseNumber={bedOrderItem.houseNumber}
                  bedNumber={bedOrderItem.number}
                  selectedIds={selectedIds}
                  onToggleGroups={onToggleGroups}
                />
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function WorkTargetBedCard({
  bed,
  bedNumber,
  houseNumber,
  selectedIds,
  onToggleGroups,
}: {
  bed: BedNode | null;
  bedNumber: number;
  houseNumber: number;
  selectedIds: Set<number>;
  onToggleGroups: (groups: OrchidGroup[]) => void;
}) {
  return (
    <div className="overflow-hidden rounded-md border border-[#cfd9cd] bg-white">
      <div className="flex items-center gap-2 border-b border-[#dfe6dd] bg-[#f1f6f0] px-3 py-1.5">
        <SelectionCheckbox
          label={`${houseNumber}동 ${bedNumber}다이 전체`}
          {...selectionState(bed?.groups ?? [], selectedIds)}
          disabled={!bed || bed.groups.length === 0}
          onChange={() => onToggleGroups(bed?.groups ?? [])}
        />
        <span className="text-sm font-bold text-[#26352b]">
          {houseNumber}동 {bedNumber}다이
        </span>
        <span className="ml-auto text-xs text-[#718077]">
          {bed ? `${bed.groups.length}묶음` : "불러오는 중"}
        </span>
      </div>
      <div className="grid min-h-44 grid-cols-2 gap-px bg-[#dce4da]">
        {bed ? (
          bedDisplayZones(bed).map(({ side, zone }, index) =>
            zone ? (
              <BedSideZone
                key={zone.id}
                bedNumber={bed.number}
                houseNumber={houseNumber}
                maxCell={Math.max(1, Math.floor(bed.positionUnitCount ?? 28))}
                selectedIds={selectedIds}
                side={side}
                zone={zone}
                onToggle={onToggleGroups}
              />
            ) : (
              <div
                className="flex min-h-44 flex-col items-center justify-center bg-[#edf0ed] px-2 text-center text-[#9aa39c]"
                key={`${side}-${index}`}
                aria-disabled="true"
              >
                <span className="text-xs font-bold">{sideLabel(side)}</span>
                <span className="mt-1 text-[11px]">비어 있음</span>
              </div>
            ),
          )
        ) : (
          <div className="col-span-2 min-h-44 bg-[#edf0ed]" />
        )}
      </div>
    </div>
  );
}

function BedSideZone({
  bedNumber,
  houseNumber,
  maxCell,
  selectedIds,
  side,
  zone,
  onToggle,
}: {
  bedNumber: number;
  houseNumber: number;
  maxCell: number;
  selectedIds: Set<number>;
  side: BedZoneSide;
  zone: ZoneNode;
  onToggle: (groups: OrchidGroup[]) => void;
}) {
  return (
    <div className="min-h-44 bg-white p-2">
      <div className="flex items-center gap-2 px-1 pb-1">
        <SelectionCheckbox
          label={`${houseNumber}동 ${bedNumber}다이 ${sideLabel(side)} 전체`}
          {...selectionState(zone.groups, selectedIds)}
          onChange={() => onToggle(zone.groups)}
        />
        <div className="min-w-0">
          <p className="text-xs font-bold text-[#435047]">{sideLabel(side)}</p>
        </div>
      </div>
      <div
        className="mt-1 grid grid-cols-[22px_minmax(0,1fr)] overflow-hidden rounded border border-[#e4e8e4] bg-white"
        style={{ gridTemplateRows: `repeat(${maxCell}, minmax(0, 1fr))` }}
      >
        {Array.from({ length: maxCell }, (_, index) => maxCell - index).map(
          (cell) => {
            const group = zone.groups.find((candidate) =>
              isGroupInCell(candidate, cell),
            );
            const startsGroup = group?.endPosition === cell;
            const selected = group != null && selectedIds.has(group.id);
            const lastCell = cell === 1;

            return (
              <div className="contents" key={cell}>
                <div
                  className={`flex min-h-5 items-center justify-end border-r border-[#e4e8e4] pr-1 text-[9px] font-semibold text-[#829087] ${
                    lastCell ? "" : "border-b border-[#edf1ec]"
                  }`}
                >
                  {cell % 5 === 0 || cell === maxCell ? cell : ""}
                </div>
                {group ? (
                  <div
                    aria-label={`${zone.name} ${cell}칸 ${group.varietyName} ${group.quantity}분`}
                    className={`flex min-h-5 min-w-0 cursor-pointer items-center px-1 text-left text-[10px] font-semibold transition ${startsGroup ? "border-t" : "border-t-0"} ${
                      selected
                        ? "border-[#0c7b38] bg-[#159447] text-white"
                        : "border-[#cfd8cc] bg-[#f7faf6] text-[#344138] hover:bg-[#eef4ed]"
                    }`}
                    onClick={() => onToggle([group])}
                  >
                    {startsGroup ? (
                      <span className="min-w-0 truncate px-1">
                        {group.varietyName} · {group.quantity}분
                      </span>
                    ) : null}
                  </div>
                ) : (
                  <div
                    aria-label={`${zone.name} ${cell}칸 빈 칸`}
                    className={`flex min-h-5 items-center bg-white px-1 ${
                      lastCell ? "" : "border-b border-[#edf1ec]"
                    }`}
                  />
                )}
              </div>
            );
          },
        )}
      </div>
    </div>
  );
}

function isGroupInCell(group: OrchidGroup, cell: number) {
  if (group.startPosition == null || group.endPosition == null) return false;
  return cell >= group.startPosition + 1 && cell <= group.endPosition;
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

function sideLabel(side: BedZoneSide) {
  switch (side) {
    case "LEFT":
      return "좌측";
    case "RIGHT":
      return "우측";
    case "CUSTOM":
      return "사용자 구역";
    case "HANGING":
      return "행잉";
  }
}

function bedDisplayZones(bed: BedNode) {
  const standardSides: BedZoneSide[] = ["LEFT", "RIGHT"];
  return [
    ...standardSides.map((side) => ({
      side,
      zone: bed.zones.find((zone) => zone.side === side) ?? null,
    })),
    ...bed.zones
      .filter((zone) => zone.side === "CUSTOM" || zone.side === "HANGING")
      .map((zone) => ({ side: zone.side, zone })),
  ];
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

function buildTargetTree(groups: OrchidGroup[], houses: House[]): HouseNode[] {
  const groupsByZone = new Map<number, OrchidGroup[]>();
  groups.forEach((group) => {
    const zoneGroups = groupsByZone.get(group.bedZoneId) ?? [];
    zoneGroups.push(group);
    groupsByZone.set(group.bedZoneId, zoneGroups);
  });

  return houses
    .map((house) => {
      const beds = house.physicalBeds
        .map((bed) => {
          const zones = bed.bedZones
            .filter((zone) => zone.active)
            .map((zone) => ({
              id: zone.id,
              name: zone.name,
              side: zone.side,
              groups: [...(groupsByZone.get(zone.id) ?? [])].sort(
                compareGroups,
              ),
            }))
            .sort(compareZones);
          return {
            id: bed.id,
            number: bed.number,
            positionUnitCount: bed.positionUnitCount,
            groups: zones.flatMap((zone) => zone.groups),
            zones,
          };
        })
        .filter((bed) => bed.zones.length > 0)
        .sort((left, right) => left.number - right.number);
      return {
        id: house.id,
        number: house.number,
        groups: beds.flatMap((bed) => bed.groups),
        beds,
      };
    })
    .filter((house) => house.beds.length > 0)
    .sort((a, b) => a.number - b.number);
}

function workTargetBedNode(
  bed: import("@/entities/farm/types").PhysicalBed,
  visibleGroupIds: Set<number>,
): BedNode {
  const zones = bed.bedZones
    .filter((zone) => zone.active)
    .map((zone) => ({
      id: zone.id,
      name: zone.name,
      side: zone.side,
      groups: zone.orchidGroups
        .filter((group) => visibleGroupIds.has(group.id))
        .sort(compareGroups),
    }))
    .sort(compareZones);
  return {
    id: bed.id,
    number: bed.number,
    positionUnitCount: bed.positionUnitCount,
    groups: zones.flatMap((zone) => zone.groups),
    zones,
  };
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
