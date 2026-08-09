"use client";

import { useEffect, useMemo, useRef, useState, type WheelEvent } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
  WorkType,
} from "@/entities/farm/types";
import { formatPotSize } from "@/entities/farm/potSizes";
import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import { FarmPlacementPickerDialog } from "@/entities/farm/ui/FarmPlacementPicker";
import {
  Clipboard,
  ListChecks,
  LoaderCircle,
  Move,
  Search,
} from "lucide-react";
import {
  getDerivedOrchidGroupMembers,
  getDerivedOrchidGroups,
  getOrchidGroupCollections,
  searchOrchidGroups,
} from "../../api/orchidManagementApi";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type {
  MutationMode,
  MapCellRangePick,
  MutationPayload,
  DerivedOrchidGroup,
  OrchidManagementSearchState,
  OrchidGroupCollection,
  OrchidListSelection,
  OrchidSelection,
  PreciseMovePayload,
  WorkRecordQuickFormState,
} from "../../model/types";
import ActionButton from "./ActionButton";
import CopiedOrchidGroupPanel from "./CopiedOrchidGroupPanel";
import OrchidGroupList from "./OrchidGroupList";
import OrchidGroupMutationPanel from "./OrchidGroupMutationPanel";
import OrchidWorkRecordForm from "./OrchidWorkRecordForm";

export default function OrchidSelectionPanel({
  copiedOrchidGroup,
  errorMessage,
  hasActiveSearch,
  house,
  placementHouses,
  listSelection,
  mutationMode,
  pasteSourceOrchidGroup,
  resolvedZone,
  saving,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
  searchFilters,
  searchLoading,
  searchResults,
  workRecordForm,
  workTypes,
  mapCellRangePick,
  multiSelectEnabled,
  selectedOrchidGroupIds,
  onCancelMutation,
  onClearCopiedOrchidGroup,
  onCopyOrchidGroup,
  onCreate,
  onDelete,
  onEdit,
  onMove,
  onOpenEdit,
  onOpenMove,
  onOpenPaste,
  onOpenWorkRecord,
  onSelectOrchidGroup,
  onSelectSearchResult,
  onSearchGroupSelectionChange,
  onStartMapCellRangePick,
  onSyncMapCellRangePick,
  onToggleMultiSelect,
  onUpdateSearchFilter,
  onUpdateWorkRecordForm,
  onWorkRecordCreate,
}: {
  copiedOrchidGroup: OrchidGroup | null;
  errorMessage: string | null;
  hasActiveSearch: boolean;
  house: House;
  placementHouses: House[];
  listSelection: OrchidListSelection;
  mutationMode: MutationMode;
  pasteSourceOrchidGroup: OrchidGroup | null;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: PhysicalBed | null;
  selection: OrchidSelection | null;
  searchFilters: OrchidManagementSearchState;
  searchLoading: boolean;
  searchResults: OrchidGroup[];
  workRecordForm: WorkRecordQuickFormState;
  workTypes: WorkType[];
  mapCellRangePick: MapCellRangePick;
  multiSelectEnabled: boolean;
  selectedOrchidGroupIds: Set<number>;
  onCancelMutation: () => void;
  onClearCopiedOrchidGroup: () => void;
  onCopyOrchidGroup: (orchidGroupId: number) => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onDelete: (orchidGroupId: number) => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onMove: (payload: PreciseMovePayload) => Promise<void>;
  onOpenEdit: (orchidGroupId: number) => void;
  onOpenMove: () => void;
  onOpenPaste: () => void;
  onOpenWorkRecord: () => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
  onSelectSearchResult: (orchidGroup: OrchidGroup) => void;
  onSearchGroupSelectionChange: (orchidGroupIds: number[] | null) => void;
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
  onToggleMultiSelect: () => void;
  onUpdateSearchFilter: <K extends keyof OrchidManagementSearchState>(
    field: K,
    value: OrchidManagementSearchState[K],
  ) => void;
  onUpdateWorkRecordForm: <K extends keyof WorkRecordQuickFormState>(
    field: K,
    value: WorkRecordQuickFormState[K],
  ) => void;
  onWorkRecordCreate: () => Promise<void>;
}) {
  const [searchScope, setSearchScope] = useState<"CURRENT_LIST" | "FARM">(
    "CURRENT_LIST",
  );
  const [derivedSearchGroups, setDerivedSearchGroups] = useState<
    DerivedOrchidGroup[]
  >([]);
  const [userSearchGroups, setUserSearchGroups] = useState<
    OrchidGroupCollection[]
  >([]);
  const [searchGroupsLoading, setSearchGroupsLoading] = useState(true);
  const [searchGroupLoadingKey, setSearchGroupLoadingKey] = useState<
    string | null
  >(null);
  const [selectedSearchGroup, setSelectedSearchGroup] = useState<{
    key: string;
    label: string;
    members: OrchidGroup[];
    type: "DERIVED" | "COLLECTION";
  } | null>(null);
  const [allFarmOrchidGroups, setAllFarmOrchidGroups] = useState<
    OrchidGroup[] | null
  >(null);
  const [searchGroupError, setSearchGroupError] = useState<string | null>(null);
  const orchidGroupListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    void Promise.all([getDerivedOrchidGroups(), getOrchidGroupCollections()])
      .then(([derivedGroups, collections]) => {
        if (cancelled) return;
        setDerivedSearchGroups(derivedGroups);
        setUserSearchGroups(collections);
      })
      .catch((error: unknown) => {
        if (!cancelled) setSearchGroupError(toSearchGroupMessage(error));
      })
      .finally(() => {
        if (!cancelled) setSearchGroupsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);
  const listZone =
    listSelection.type === "BED_ZONE"
      ? (findBedZone(house, listSelection.bedZoneId)?.zone ?? null)
      : null;
  const listPhysicalBed =
    listSelection.type === "PHYSICAL_BED"
      ? (house.physicalBeds.find(
          (bed) => bed.id === listSelection.physicalBedId,
        ) ?? null)
      : null;
  const selectedHouse = listSelection.type === "HOUSE";
  const orchidGroups = listZone
    ? listZone.orchidGroups
    : listPhysicalBed
      ? listPhysicalBed.bedZones.flatMap((bedZone) => bedZone.orchidGroups)
      : selectedHouse
        ? house.physicalBeds.flatMap((bed) =>
            bed.bedZones.flatMap((bedZone) => bedZone.orchidGroups),
          )
        : [];
  const sortedOrchidGroups = useMemo(
    () => sortOrchidGroupsByMapOrder(orchidGroups, house),
    [house, orchidGroups],
  );
  const allHouseOrchidGroups = house.physicalBeds.flatMap((bed) =>
    bed.bedZones.flatMap((bedZone) => bedZone.orchidGroups),
  );
  const selectedOrchidGroupOutsideViewport =
    selectedOrchidGroup != null &&
    !allHouseOrchidGroups.some(
      (orchidGroup) => orchidGroup.id === selectedOrchidGroup.id,
    );
  const currentListSearchResults = hasActiveSearch
    ? sortedOrchidGroups.filter((orchidGroup) =>
        matchesCurrentListSearch(orchidGroup, searchFilters),
      )
    : sortedOrchidGroups;
  const relatedDerivedGroups = useMemo(
    () =>
      hasActiveSearch
        ? derivedSearchGroups.filter((group) =>
            matchesSearchText(
              [
                group.varietyName,
                group.genus,
                group.ageYear == null ? "년생 미지정" : `${group.ageYear}년생`,
                formatPotSize(group.potSizeCode, group.potSize),
              ],
              searchFilters.keyword,
            ),
          )
        : [],
    [derivedSearchGroups, hasActiveSearch, searchFilters.keyword],
  );
  const relatedUserGroups = useMemo(
    () =>
      hasActiveSearch
        ? userSearchGroups.filter((group) =>
            matchesSearchText(
              [group.name, group.description, group.purpose],
              searchFilters.keyword,
            ),
          )
        : [],
    [hasActiveSearch, searchFilters.keyword, userSearchGroups],
  );
  const displayedOrchidGroups = selectedSearchGroup
    ? selectedSearchGroup.members
    : hasActiveSearch
      ? searchScope === "CURRENT_LIST"
        ? currentListSearchResults
        : searchResults
      : sortedOrchidGroups;
  const displayedOrchidGroupIds = displayedOrchidGroups
    .map((orchidGroup) => orchidGroup.id)
    .join(",");
  useEffect(() => {
    if (selectedOrchidGroup == null) return;
    const item = orchidGroupListRef.current?.querySelector<HTMLElement>(
      `[data-orchid-group-id="${selectedOrchidGroup.id}"]`,
    );
    item?.scrollIntoView({ block: "nearest" });
  }, [displayedOrchidGroupIds, selectedOrchidGroup]);
  const displayedResultCount = displayedOrchidGroups.length;
  const screenSearchResultCount = currentListSearchResults.length;
  const farmSearchResultCount = searchResults.length;
  const displayingFarmResults =
    selectedSearchGroup != null || searchScope === "FARM";
  const firstVisibleBed = house.physicalBeds[0];
  const lastVisibleBed = house.physicalBeds.at(-1);
  const visibleRangeLabel =
    firstVisibleBed && lastVisibleBed
      ? firstVisibleBed.houseId === lastVisibleBed.houseId
        ? `${firstVisibleBed.houseNumber}동`
        : `${firstVisibleBed.houseNumber}동 ${firstVisibleBed.number}다이 ~ ${lastVisibleBed.houseNumber}동 ${lastVisibleBed.number}다이`
      : "현재 화면";
  const listTargetLabel = listZone
    ? "이 구역"
    : listPhysicalBed
      ? "이 다이"
      : selectedHouse
        ? visibleRangeLabel
        : "선택 대상";
  const hasListTarget = Boolean(listZone || listPhysicalBed || selectedHouse);
  const compactList = mutationMode === "MOVE" && selectedOrchidGroup != null;
  const hideList = mutationMode === "CREATE" || mutationMode === "EDIT";

  async function selectDerivedSearchGroup(group: DerivedOrchidGroup) {
    const key = `DERIVED:${group.groupKey}`;
    if (searchGroupLoadingKey) return;
    setSearchGroupLoadingKey(key);
    setSearchGroupError(null);
    try {
      const members = await getDerivedOrchidGroupMembers(group.groupKey);
      setSelectedSearchGroup({
        key,
        label: formatDerivedSearchGroupLabel(group),
        members,
        type: "DERIVED",
      });
      onSearchGroupSelectionChange(members.map((member) => member.id));
      if (members[0]) {
        onSelectSearchResult(members[0]);
      }
    } catch (error) {
      setSearchGroupError(toSearchGroupMessage(error));
    } finally {
      setSearchGroupLoadingKey(null);
    }
  }

  async function selectUserSearchGroup(group: OrchidGroupCollection) {
    const key = `COLLECTION:${group.id}`;
    if (searchGroupLoadingKey) return;
    setSearchGroupLoadingKey(key);
    setSearchGroupError(null);
    try {
      const farmOrchidGroups =
        allFarmOrchidGroups ??
        (await searchOrchidGroups({ keyword: "", status: "" }));
      if (!allFarmOrchidGroups) {
        setAllFarmOrchidGroups(farmOrchidGroups);
      }
      const memberIds = new Set(
        group.members.map((member) => member.orchidGroupId),
      );
      const members = farmOrchidGroups.filter((orchidGroup) =>
        memberIds.has(orchidGroup.id),
      );
      setSelectedSearchGroup({
        key,
        label: group.name,
        members,
        type: "COLLECTION",
      });
      onSearchGroupSelectionChange(members.map((member) => member.id));
      if (members[0]) {
        onSelectSearchResult(members[0]);
      }
    } catch (error) {
      setSearchGroupError(toSearchGroupMessage(error));
    } finally {
      setSearchGroupLoadingKey(null);
    }
  }

  return (
    <aside className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
      {selectedOrchidGroupOutsideViewport ? (
        <p className="rounded-md border border-[#f0d58a] bg-[#fff9e8] px-3 py-2 text-xs font-semibold text-[#7a5b08]">
          선택한 난 묶음은 현재 화면 밖에 있습니다.
        </p>
      ) : null}

      {!hideList ? (
        <section className="flex min-h-0 flex-1 flex-col gap-2 rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
          <div className="flex shrink-0 items-center justify-between gap-3">
            <p className="text-sm font-semibold text-[#17251b]">
              {hasActiveSearch
                ? selectedSearchGroup
                  ? `${
                      selectedSearchGroup.type === "DERIVED"
                        ? "자동 그룹"
                        : "사용자 그룹"
                    } · ${selectedSearchGroup.label} (${displayedResultCount}개)`
                  : searchScope === "CURRENT_LIST"
                    ? `현재 보이는 화면 목록에서 검색 결과 (${displayedResultCount}개)`
                    : `농장 전체에서 검색 결과 (${displayedResultCount}개)`
                : `${
                    selectedHouse
                      ? `${visibleRangeLabel} 난 묶음 목록`
                      : "난 묶음 목록"
                  } (${displayedResultCount}개)`}
            </p>
            <button
              aria-pressed={multiSelectEnabled}
              className={`flex h-4 shrink-0 items-center gap-1.5 rounded-md border px-2.5 text-xs font-bold transition ${
                multiSelectEnabled
                  ? "border-[#159447] bg-[#eef8f0] text-[#176b37]"
                  : "border-[#dfe5dc] bg-white text-[#526057] hover:border-[#159447] hover:text-[#176b37]"
              }`}
              onClick={onToggleMultiSelect}
              type="button"
            >
              <ListChecks aria-hidden className="h-4 w-4" strokeWidth={1.8} />
              {multiSelectEnabled ? "다중 선택 중" : "다중 선택"}
            </button>
          </div>
          <div className="flex shrink-0 gap-1.5">
            <label className="relative min-w-0 flex-1">
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#77857c]"
                strokeWidth={1.8}
              />
              <input
                className="h-9 w-full rounded-md border border-[#dfe5dc] bg-white pr-3 pl-9 text-sm text-[#17251b] outline-none placeholder:text-[#98a29a] focus:border-[#159447]"
                placeholder="난 묶음 검색"
                type="search"
                value={searchFilters.keyword}
                onChange={(event) => {
                  setSelectedSearchGroup(null);
                  onSearchGroupSelectionChange(null);
                  onUpdateSearchFilter("keyword", event.target.value);
                }}
              />
            </label>
            {hasActiveSearch
              ? (
                  [
                    ["CURRENT_LIST", "화면", screenSearchResultCount],
                    ["FARM", "전체", farmSearchResultCount],
                  ] as const
                ).map(([scope, label, count]) => (
                  <button
                    className={`h-9 shrink-0 rounded-md border px-2.5 text-xs font-bold transition ${
                      !selectedSearchGroup && searchScope === scope
                        ? "border-[#159447] bg-[#eef8f0] text-[#176b37]"
                        : "border-[#dfe5dc] bg-white text-[#667169] hover:bg-[#f5f7f3]"
                    }`}
                    key={scope}
                    onClick={() => {
                      setSelectedSearchGroup(null);
                      onSearchGroupSelectionChange(null);
                      setSearchScope(scope);
                    }}
                    type="button"
                  >
                    {label} {count}
                  </button>
                ))
              : null}
          </div>
          {hasActiveSearch &&
          (searchGroupsLoading ||
            relatedDerivedGroups.length > 0 ||
            relatedUserGroups.length > 0) ? (
            <div
              className="flex shrink-0 gap-2 overflow-x-auto pb-1"
              onWheel={handleHorizontalWheel}
            >
              {searchGroupsLoading ? (
                <div className="flex h-[82px] min-w-28 items-center justify-center rounded-md border border-[#e1e6df] bg-white">
                  <LoaderCircle className="h-4 w-4 animate-spin text-[#159447]" />
                </div>
              ) : null}
              {relatedDerivedGroups.map((group) => {
                const key = `DERIVED:${group.groupKey}`;
                return (
                  <button
                    className={`min-w-40 rounded-md border px-3 py-2 text-left transition ${
                      selectedSearchGroup?.key === key
                        ? "border-[#246df2] bg-[#f4f8ff]"
                        : "border-[#e1e6df] bg-white hover:border-[#159447]"
                    }`}
                    disabled={searchGroupLoadingKey !== null}
                    key={key}
                    onClick={() => void selectDerivedSearchGroup(group)}
                    type="button"
                  >
                    <span className="block text-[10px] font-bold text-[#159447]">
                      자동 그룹
                    </span>
                    <span className="mt-1 block truncate text-xs font-bold text-[#26352b]">
                      {formatDerivedSearchGroupLabel(group)}
                    </span>
                    <span className="mt-1 block text-[11px] text-[#6a766e]">
                      {searchGroupLoadingKey === key ? (
                        <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        `${group.orchidGroupCount}묶음 ${group.totalQuantity}분`
                      )}
                    </span>
                  </button>
                );
              })}
              {relatedUserGroups.map((group) => {
                const key = `COLLECTION:${group.id}`;
                return (
                  <button
                    className={`min-w-40 rounded-md border px-3 py-2 text-left transition ${
                      selectedSearchGroup?.key === key
                        ? "border-[#246df2] bg-[#f4f8ff]"
                        : "border-[#e1e6df] bg-white hover:border-[#159447]"
                    }`}
                    disabled={searchGroupLoadingKey !== null}
                    key={key}
                    onClick={() => void selectUserSearchGroup(group)}
                    type="button"
                  >
                    <span className="block text-[10px] font-bold text-[#6b72c8]">
                      사용자 그룹
                    </span>
                    <span className="mt-1 block truncate text-xs font-bold text-[#26352b]">
                      {group.name}
                    </span>
                    <span className="mt-1 block text-[11px] text-[#6a766e]">
                      {searchGroupLoadingKey === key ? (
                        <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        `${group.orchidGroupCount}묶음 ${group.totalQuantity}분`
                      )}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : null}
          {hasActiveSearch && searchGroupError ? (
            <p className="mt-1.5 shrink-0 text-[11px] text-[#9b341e]">
              {searchGroupError}
            </p>
          ) : null}
          {copiedOrchidGroup ? (
            <CopiedOrchidGroupPanel
              copiedOrchidGroup={copiedOrchidGroup}
              resolvedZone={resolvedZone}
              onClear={onClearCopiedOrchidGroup}
              onPaste={onOpenPaste}
            />
          ) : null}

          {hasListTarget ? (
            <div className="flex min-h-0 flex-1 flex-col">
              <OrchidGroupList
                compact={compactList}
                displayedOrchidGroups={displayedOrchidGroups}
                displayingFarmResults={displayingFarmResults}
                hasActiveSearch={hasActiveSearch}
                listOrchidGroupCount={orchidGroups.length}
                listRef={orchidGroupListRef}
                listTargetLabel={listTargetLabel}
                multiSelectEnabled={multiSelectEnabled}
                searchLoading={searchLoading}
                searchScope={searchScope}
                selectedOrchidGroupId={selectedOrchidGroup?.id ?? null}
                selectedOrchidGroupIds={selectedOrchidGroupIds}
                selectedSearchGroup={selectedSearchGroup != null}
                saving={saving}
                onCopy={onCopyOrchidGroup}
                onDelete={(orchidGroupId) => void onDelete(orchidGroupId)}
                onEdit={onOpenEdit}
                onSelect={onSelectOrchidGroup}
                onSelectSearchResult={onSelectSearchResult}
                onToggleSelected={onSelectOrchidGroup}
              />

              {!multiSelectEnabled ? (
                <div className="mt-3 grid shrink-0 grid-cols-2 gap-2">
                  <ActionButton
                    icon={<Clipboard className="h-4 w-4" />}
                    label="작업 기록 추가"
                    onClick={onOpenWorkRecord}
                    active={mutationMode === "WORK_RECORD"}
                  />
                  <ActionButton
                    icon={<Move className="h-4 w-4" />}
                    label="자리 이동"
                    onClick={onOpenMove}
                    active={mutationMode === "MOVE"}
                    disabled={!selectedOrchidGroup}
                  />
                </div>
              ) : null}
            </div>
          ) : (
            <div className="mt-3 shrink-0">
              <p className="text-sm text-[#5c6a60]">
                동, 다이, 구역을 선택하면 해당 범위의 난 묶음 목록을 볼 수
                있습니다.
              </p>
            </div>
          )}
          {errorMessage ? (
            <p className="mt-3 shrink-0 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
              {errorMessage}
            </p>
          ) : null}
        </section>
      ) : null}

      {mutationMode === "CREATE" || mutationMode === "EDIT" ? (
        <OrchidGroupMutationPanel
          house={house}
          mode={mutationMode}
          mapCellRangePick={mapCellRangePick}
          pasteSourceOrchidGroup={pasteSourceOrchidGroup}
          resolvedZone={resolvedZone}
          saving={saving}
          selectedOrchidGroup={selectedOrchidGroup}
          onCancel={onCancelMutation}
          onCreate={onCreate}
          onEdit={onEdit}
          onStartMapCellRangePick={onStartMapCellRangePick}
          onSyncMapCellRangePick={onSyncMapCellRangePick}
        />
      ) : null}

      {mutationMode === "MOVE" && selectedOrchidGroup ? (
        <FarmPlacementPickerDialog
          dialogDescription="이동할 동과 구역을 고른 뒤 시작 칸과 끝 칸을 지정하세요."
          dialogTitle="난 묶음 위치 이동"
          excludeOrchidGroupId={selectedOrchidGroup.id}
          houses={placementHouses}
          initialValue={toPlacementSelection(selectedOrchidGroup)}
          submitDisabled={saving}
          submitLabel={saving ? "이동 중..." : "이동 저장"}
          onClose={onCancelMutation}
          onSelect={(value) => {
            void onMove({
              toBedZoneId: value.bedZoneId,
              startPosition: value.startPosition,
              endPosition: value.endPosition,
              memo: "",
            });
          }}
        />
      ) : null}

      {mutationMode === "WORK_RECORD" ? (
        <OrchidWorkRecordForm
          form={workRecordForm}
          house={house}
          resolvedZone={resolvedZone}
          saving={saving}
          selectedOrchidGroup={selectedOrchidGroup}
          selectedPhysicalBed={selectedPhysicalBed}
          selection={selection}
          workTypes={workTypes}
          onCancel={onCancelMutation}
          onChange={onUpdateWorkRecordForm}
          onSubmit={onWorkRecordCreate}
        />
      ) : null}
    </aside>
  );
}

function matchesCurrentListSearch(
  orchidGroup: OrchidGroup,
  filters: OrchidManagementSearchState,
) {
  const normalizedKeyword = filters.keyword.trim().toLocaleLowerCase("ko");
  const matchesStatus =
    !filters.status || orchidGroup.status === filters.status;
  if (!normalizedKeyword) return matchesStatus;

  return (
    matchesStatus &&
    [orchidGroup.varietyName, orchidGroup.genus, orchidGroup.memo].some(
      (value) => value?.toLocaleLowerCase("ko").includes(normalizedKeyword),
    )
  );
}

function handleHorizontalWheel(event: WheelEvent<HTMLDivElement>) {
  const container = event.currentTarget;
  const horizontalDelta =
    Math.abs(event.deltaX) > Math.abs(event.deltaY)
      ? event.deltaX
      : event.deltaY;
  const canScroll =
    horizontalDelta > 0
      ? container.scrollLeft + container.clientWidth < container.scrollWidth
      : container.scrollLeft > 0;

  if (!canScroll) return;

  event.preventDefault();
  container.scrollLeft += horizontalDelta;
}

function matchesSearchText(
  values: Array<string | null | undefined>,
  keyword: string,
) {
  const normalizedKeyword = keyword.trim().toLocaleLowerCase("ko");
  return values.some((value) =>
    value?.toLocaleLowerCase("ko").includes(normalizedKeyword),
  );
}

function formatDerivedSearchGroupLabel(group: DerivedOrchidGroup) {
  return [
    group.varietyName,
    group.ageYear == null ? "년생 미지정" : `${group.ageYear}년생`,
    formatPotSize(group.potSizeCode, group.potSize),
  ].join(" ");
}

function toSearchGroupMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "관련 그룹을 불러오지 못했습니다.";
}

function sortOrchidGroupsByMapOrder(orchidGroups: OrchidGroup[], house: House) {
  const zoneOrder = new Map<
    number,
    {
      bedDisplayOrder: number;
      bedNumber: number;
      zoneSideOrder: number;
      zoneSortOrder: number;
    }
  >();

  house.physicalBeds.forEach((bed) => {
    bed.bedZones.forEach((zone) => {
      zoneOrder.set(zone.id, {
        bedDisplayOrder: bed.displayOrder,
        bedNumber: bed.number,
        zoneSideOrder: sideOrder(zone.side),
        zoneSortOrder: zone.sortOrder,
      });
    });
  });

  return [...orchidGroups].sort((a, b) => {
    const aOrder = zoneOrder.get(a.bedZoneId);
    const bOrder = zoneOrder.get(b.bedZoneId);
    const bedCompare =
      compareNumber(aOrder?.bedDisplayOrder, bOrder?.bedDisplayOrder) ||
      compareNumber(aOrder?.bedNumber, bOrder?.bedNumber) ||
      a.physicalBedNumber - b.physicalBedNumber;
    if (bedCompare !== 0) return bedCompare;

    const zoneCompare =
      compareNumber(aOrder?.zoneSideOrder, bOrder?.zoneSideOrder) ||
      compareNumber(aOrder?.zoneSortOrder, bOrder?.zoneSortOrder) ||
      a.bedZoneName.localeCompare(b.bedZoneName, "ko");
    if (zoneCompare !== 0) return zoneCompare;

    const positionCompare =
      topCell(b) - topCell(a) || bottomCell(b) - bottomCell(a);
    if (positionCompare !== 0) return positionCompare;

    return a.sortOrder - b.sortOrder || a.id - b.id;
  });
}

function sideOrder(side: BedZone["side"]) {
  if (side === "LEFT") return 0;
  if (side === "RIGHT") return 1;
  if (side === "CUSTOM") return 2;
  return 3;
}

function topCell(orchidGroup: OrchidGroup) {
  return Math.ceil(orchidGroup.endPosition ?? orchidGroup.startPosition ?? 0);
}

function bottomCell(orchidGroup: OrchidGroup) {
  return Math.floor(orchidGroup.startPosition ?? orchidGroup.endPosition ?? 0);
}

function compareNumber(a: number | undefined, b: number | undefined) {
  return (a ?? Number.MAX_SAFE_INTEGER) - (b ?? Number.MAX_SAFE_INTEGER);
}

function toPlacementSelection(
  orchidGroup: OrchidGroup,
): FarmPlacementSelection {
  const startCell =
    orchidGroup.startPosition != null
      ? Math.floor(orchidGroup.startPosition) + 1
      : 1;
  const endCell =
    orchidGroup.endPosition != null
      ? Math.ceil(orchidGroup.endPosition)
      : startCell;

  return {
    bedZoneId: orchidGroup.bedZoneId,
    startCell,
    endCell,
    startPosition: startCell - 1,
    endPosition: endCell,
    label: `${orchidGroup.houseNumber}동 ${orchidGroup.physicalBedNumber}다이 ${orchidGroup.bedZoneName} ${startCell}-${endCell}칸`,
  };
}
