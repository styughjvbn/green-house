"use client";

import useEmblaCarousel from "embla-carousel-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Search, X } from "lucide-react";
import type { BedZoneSide, House, OrchidGroup } from "@/entities/farm/types";
import { useFarmBedViewportCache } from "@/entities/farm/model/useFarmBedViewportCache";
import type { ReactNode } from "react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "@/shared/ui/primitives/dialog";

export type FarmSelectionContext = {
  keyword: string;
  setKeyword: (value: string) => void;
  selectedIds: Set<number>;
  selectableIds: Set<number>;
  toggleIds: (ids: number[]) => void;
};

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

export function FarmGroupSelectionDialog({
  groups,
  houses,
  initialSelectedIds,
  initialKeyword = "",
  onClose,
  onConfirm,
  title,
  description,
  searchPlaceholder = "위치, 품종 또는 그룹 이름 검색",
  renderQuickSelection,
  onManualSelection,
  loading = false,
  error,
  onRetry,
}: {
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  groups: OrchidGroup[];
  houses: House[];
  initialSelectedIds: Set<number>;
  initialKeyword?: string;
  onClose: () => void;
  onConfirm: (selectedIds: Set<number>) => void;
  title: string;
  description: string;
  searchPlaceholder?: string;
  renderQuickSelection?: (context: FarmSelectionContext) => ReactNode;
  onManualSelection?: () => void;
}) {
  const [selectedIds, setSelectedIds] = useState(
    () => new Set(initialSelectedIds),
  );
  const [keyword, setKeyword] = useState(initialKeyword);
  const [houseNavigation, setHouseNavigation] = useState<{
    houseId: number;
  } | null>(null);
  const [returnFocus] = useState(() =>
    typeof document === "undefined"
      ? null
      : (document.activeElement as HTMLElement | null),
  );
  const selectedGroups = groups.filter((group) => selectedIds.has(group.id));
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
  const firstSearchHouseId = normalizedKeyword ? visibleTree[0]?.id : null;
  const targetHouseNavigation = useMemo(
    () =>
      houseNavigation ??
      (firstSearchHouseId != null ? { houseId: firstSearchHouseId } : null),
    [firstSearchHouseId, houseNavigation, normalizedKeyword],
  );

  function updateKeyword(value: string) {
    setHouseNavigation(null);
    setKeyword(value);
  }

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
    onManualSelection?.();
    toggleIds(targetGroups.map((group) => group.id));
  }

  return (
    <div onMouseDown={(event) => event.stopPropagation()}>
      <Dialog
        open
        onOpenChange={(open) => {
          if (!open) onClose();
        }}
      >
        <DialogContent
          onEscapeKeyDown={(event) => event.stopPropagation()}
          showCloseButton={false}
          overlayClassName="z-[1200]"
          className="z-[1201] flex h-[calc(100dvh-3rem)] max-h-[calc(100dvh-3rem)] w-[calc(100%-2rem)] max-w-6xl flex-col overflow-hidden rounded-lg bg-[#f7faf6]"
          onMouseDown={(event) => event.stopPropagation()}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            if (returnFocus?.isConnected) returnFocus.focus();
          }}
        >
          <header className="flex shrink-0 items-end justify-between gap-3 border-b border-[#dbe5da] bg-white px-5 py-1.5">
            <div className="flex min-w-0 flex-col gap-0 sm:flex-row sm:gap-2">
              <DialogTitle className="text-lg font-bold text-[#17251b]">
                {title}
              </DialogTitle>
              <DialogDescription className="mt-1 text-sm text-[#617067]">
                {description}
              </DialogDescription>
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
                placeholder={searchPlaceholder}
                aria-label="난 묶음 검색"
                onKeyDown={(event) => {
                  if (event.key === "Enter") event.preventDefault();
                }}
                value={keyword}
                onChange={(event) => updateKeyword(event.target.value)}
              />
            </label>

            {renderQuickSelection?.({
              keyword,
              setKeyword: updateKeyword,
              selectedIds,
              selectableIds,
              toggleIds,
            })}
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            {error ? (
              <p role="alert">
                {error}{" "}
                {onRetry ? (
                  <button type="button" className="underline" onClick={onRetry}>
                    다시 조회
                  </button>
                ) : null}
              </p>
            ) : loading ? (
              <p role="status">난 묶음 확인 중</p>
            ) : visibleTree.length > 0 ? (
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
                              onChange={() => {
                                toggleGroups(house.groups);
                                setHouseNavigation({ houseId: house.id });
                              }}
                            />
                            <button
                              type="button"
                              aria-label={`${house.number}동 배치 보기`}
                              className="flex flex-1 flex-col items-start rounded text-left focus-visible:outline-2 focus-visible:outline-[#159447]"
                              onClick={() =>
                                setHouseNavigation({ houseId: house.id })
                              }
                            >
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
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </section>

                <section>
                  <FarmSelectionBedCarousel
                    bedOrder={bedOrder}
                    bedsById={bedsById}
                    selectedIds={selectedIds}
                    visibleGroupIds={visibleGroupIds}
                    groupsById={
                      new Map(groups.map((group) => [group.id, group]))
                    }
                    houseNavigation={targetHouseNavigation}
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
                onClick={() => {
                  onManualSelection?.();
                  setSelectedIds(new Set());
                }}
              >
                선택 초기화
              </button>
              <button
                className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
                disabled={loading || !!error || selectedGroups.length === 0}
                type="button"
                onClick={() =>
                  onConfirm(new Set(selectedGroups.map((group) => group.id)))
                }
              >
                대상 확정
              </button>
            </div>
          </footer>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function FarmSelectionBedCarousel({
  bedOrder,
  bedsById,
  selectedIds,
  visibleGroupIds,
  groupsById,
  houseNavigation,
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
  groupsById: Map<number, OrchidGroup>;
  houseNavigation: { houseId: number } | null;
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
  const activeHouseIndex = houses.findIndex(
    (house) => house.houseId === bedOrder[activeBedIndex]?.houseId,
  );

  function scrollToHouse(houseIndex: number) {
    const houseId = houses[houseIndex]?.houseId;
    if (houseId == null) return;
    const bedIndex = bedOrder.findIndex((bed) => bed.houseId === houseId);
    if (bedIndex >= 0) emblaApi?.scrollTo(bedIndex);
  }

  useEffect(() => {
    if (!emblaApi) return;
    const handleSelect = () => {
      const index = emblaApi.selectedScrollSnap();
      setActiveBedIndex(index);
      onViewportIndexChange(index);
    };
    emblaApi.on("reInit", handleSelect);
    emblaApi.on("select", handleSelect);
    emblaApi.reInit();
    return () => {
      emblaApi.off("reInit", handleSelect);
      emblaApi.off("select", handleSelect);
    };
  }, [bedOrder, emblaApi, onViewportIndexChange]);

  useEffect(() => {
    if (!emblaApi || houseNavigation == null) return;
    const bedIndex = bedOrder.findIndex(
      (bed) => bed.houseId === houseNavigation.houseId,
    );
    if (bedIndex >= 0) emblaApi.scrollTo(bedIndex);
  }, [bedOrder, emblaApi, houseNavigation]);

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
            aria-label="동 이동"
            className="h-8 rounded-md border border-[#d7dfd5] bg-white px-2 text-xs font-semibold text-[#344138]"
            value={bedOrder[activeBedIndex]?.houseId ?? ""}
            onChange={(event) => {
              const nextHouseId = Number(event.target.value);
              scrollToHouse(
                houses.findIndex((house) => house.houseId === nextHouseId),
              );
            }}
          >
            {houses.map((house) => (
              <option key={house.houseId} value={house.houseId}>
                {house.houseNumber}동
              </option>
            ))}
          </select>
          <button
            aria-label="이전 동"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7dfd5] bg-white disabled:cursor-not-allowed disabled:opacity-40"
            disabled={activeHouseIndex <= 0}
            type="button"
            onClick={() => scrollToHouse(activeHouseIndex - 1)}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            aria-label="다음 동"
            className="flex h-8 w-8 items-center justify-center rounded-md border border-[#d7dfd5] bg-white disabled:cursor-not-allowed disabled:opacity-40"
            disabled={
              activeHouseIndex < 0 || activeHouseIndex >= houses.length - 1
            }
            type="button"
            onClick={() => scrollToHouse(activeHouseIndex + 1)}
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
              ? farmSelectionBedNode(loadedBed, visibleGroupIds, groupsById)
              : null;
            return (
              <div
                className="min-w-0 shrink-0 basis-full pl-3 md:basis-1/2 lg:basis-1/3"
                key={bedOrderItem.id}
              >
                <FarmSelectionBedCard
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

function FarmSelectionBedCard({
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
                  <button
                    type="button"
                    aria-pressed={selected}
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
                  </button>
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

export function SelectionCheckbox({
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

export function selectionStateByIds(
  targetIds: number[],
  selectedIds: Set<number>,
) {
  const selectedCount = targetIds.reduce(
    (count, id) => count + Number(selectedIds.has(id)),
    0,
  );
  return {
    checked: targetIds.length > 0 && selectedCount === targetIds.length,
    indeterminate: selectedCount > 0 && selectedCount < targetIds.length,
  };
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

function farmSelectionBedNode(
  bed: import("@/entities/farm/types").PhysicalBed,
  visibleGroupIds: Set<number>,
  groupsById: Map<number, OrchidGroup>,
): BedNode {
  const zones = bed.bedZones
    .filter((zone) => zone.active)
    .map((zone) => ({
      id: zone.id,
      name: zone.name,
      side: zone.side,
      groups: zone.orchidGroups
        .filter((group) => visibleGroupIds.has(group.id))
        .map((group) => groupsById.get(group.id) ?? group)
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
          `${group.genus ?? ""} ${group.varietyName} ${group.houseNumber}동 ${group.physicalBedNumber}다이 ${group.bedZoneName}`
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
