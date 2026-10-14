"use client";
import { useState } from "react";
import { useQuery, useQueries, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, Layers3, LoaderCircle, Users } from "lucide-react";
import type { House, OrchidGroup } from "@/entities/farm/types";
import {
  FarmGroupSelectionDialog,
  SelectionCheckbox,
  selectionStateByIds,
  type FarmSelectionContext,
} from "@/entities/farm/ui/FarmGroupSelectionDialog";
import {
  getDerivedWorkTargetMembers,
  getWorkTargetGroupOptions,
} from "../../api/workRecordApi";
import type {
  WorkDerivedGroupOption,
  WorkTargetGroupChoice,
} from "../../model/types";

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
  onConfirm: (ids: Set<number>, choice: WorkTargetGroupChoice | null) => void;
}) {
  const [choice, setChoice] = useState<WorkTargetGroupChoice | null>(null);
  return (
    <FarmGroupSelectionDialog
      groups={groups}
      houses={houses}
      initialSelectedIds={initialSelectedIds}
      title="작업 대상 선택"
      description="위치, 자동 그룹, 사용자 그룹을 함께 사용해 대상을 선택할 수 있습니다."
      onClose={onClose}
      onManualSelection={() => setChoice(null)}
      onConfirm={(ids) =>
        onConfirm(
          ids,
          choice &&
            ids.size === choice.memberIds.length &&
            choice.memberIds.every((id) => ids.has(id))
            ? choice
            : null,
        )
      }
      renderQuickSelection={(context) => (
        <WorkGroupSuggestions {...context} setSelectedGroupChoice={setChoice} />
      )}
    />
  );
}

function WorkGroupSuggestions({
  keyword,
  setKeyword,
  selectedIds,
  selectableIds,
  toggleIds,
  setSelectedGroupChoice,
}: FarmSelectionContext & {
  setSelectedGroupChoice: (choice: WorkTargetGroupChoice | null) => void;
}) {
  const [quickSelectionOpen, setQuickSelectionOpen] = useState(true);
  const cache = useQueryClient();
  const options = useQuery({
    queryKey: ["workRecords", "target-group-options"],
    queryFn: ({ signal }) => getWorkTargetGroupOptions(signal),
  });
  const derivedGroups = options.data?.derivedGroups ?? [];
  const collections = options.data?.collections ?? [];
  const loadingGroups = options.isPending;
  const [loadedKeys, setLoadedKeys] = useState<string[]>([]);
  const members = useQueries({
    queries: loadedKeys.map((key) => ({
      queryKey: ["workRecords", "derived-target-members", key],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        getDerivedWorkTargetMembers(key, signal),
    })),
  });
  const derivedMemberIds = new Map(
    loadedKeys.flatMap((key, index) => {
      const data = members[index]?.data;
      return data ? [[key, data.map((group) => group.id)] as const] : [];
    }),
  );
  const [loadingGroupKey, setLoadingGroupKey] = useState<string | null>(null);
  const [groupError, setGroupError] = useState<string | null>(null);
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

  async function toggleDerivedGroup(group: WorkDerivedGroupOption) {
    if (loadingGroupKey) return;
    const cachedIds = derivedMemberIds.get(group.groupKey);
    if (cachedIds) {
      const availableIds = cachedIds.filter((id) => selectableIds.has(id));
      const selecting = availableIds.some((id) => !selectedIds.has(id));
      toggleIds(cachedIds);
      setSelectedGroupChoice(
        selecting
          ? {
              type: "DERIVED_GROUP",
              derivedGroupKey: group.groupKey,
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
      const members = await cache.fetchQuery({
        queryKey: ["workRecords", "derived-target-members", group.groupKey],
        queryFn: ({ signal }) =>
          getDerivedWorkTargetMembers(group.groupKey, signal),
      });
      const memberIds = members.map((member) => member.id);
      setLoadedKeys((current) =>
        current.includes(group.groupKey)
          ? current
          : [...current, group.groupKey],
      );
      toggleIds(memberIds);
      setSelectedGroupChoice({
        type: "DERIVED_GROUP",
        derivedGroupKey: group.groupKey,
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
    <>
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
                        setSelectedGroupChoice(
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

      {groupError || options.isError ? (
        <p className="rounded-md bg-[#fff1ec] px-3 py-2 text-xs text-[#8f2f19]">
          {groupError ?? "그룹 목록을 불러오지 못했습니다."}
        </p>
      ) : null}
    </>
  );
}
