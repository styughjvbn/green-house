"use client";

import { useState, type ReactNode } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
  WorkType,
} from "@/entities/farm/types";
import {
  FarmPlacementPickerDialog,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";
import { Copy, Edit2, Trash2, Clipboard, Move, Sprout } from "lucide-react";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type {
  MutationMode,
  MapCellRangePick,
  MutationPayload,
  OrchidFormDraft,
  OrchidListSelection,
  OrchidSelection,
  PreciseMovePayload,
  WorkRecordQuickFormState,
} from "../../model/types";
import ActionButton from "./ActionButton";
import DerivedGroupPanel from "./DerivedGroupPanel";
import OrchidGroupForm from "./OrchidGroupForm";
import OrchidWorkRecordForm from "./OrchidWorkRecordForm";
import UserCollectionPanel from "./UserCollectionPanel";

export default function OrchidSelectionPanel({
  copiedOrchidGroup,
  errorMessage,
  filteredOrchidGroupIds,
  hasActiveSearch,
  house,
  placementHouses,
  listSelection,
  mutationMode,
  pasteSourceOrchidGroup,
  resolvedZone,
  saving,
  selectedBedZone,
  selectedOrchidGroupIds,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
  workRecordForm,
  workTypes,
  mapCellRangePick,
  onCancelMutation,
  onClearCopiedOrchidGroup,
  onClearSelectedOrchidGroups,
  onCopyOrchidGroup,
  onCreate,
  onDelete,
  onEdit,
  onMove,
  onOpenEdit,
  onOpenMove,
  onOpenPaste,
  onOpenRepot,
  onOpenWorkRecord,
  onSelectOrchidGroup,
  onSelectOrchidGroups,
  onStartMapCellRangePick,
  onSyncMapCellRangePick,
  onToggleSelectedOrchidGroup,
  onUpdateWorkRecordForm,
  onWorkRecordCreate,
}: {
  copiedOrchidGroup: OrchidGroup | null;
  errorMessage: string | null;
  filteredOrchidGroupIds: Set<number>;
  hasActiveSearch: boolean;
  house: House;
  placementHouses: House[];
  listSelection: OrchidListSelection;
  mutationMode: MutationMode;
  pasteSourceOrchidGroup: OrchidGroup | null;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedBedZone: BedZone | null;
  selectedOrchidGroupIds: Set<number>;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: PhysicalBed | null;
  selection: OrchidSelection | null;
  workRecordForm: WorkRecordQuickFormState;
  workTypes: WorkType[];
  mapCellRangePick: MapCellRangePick;
  onCancelMutation: () => void;
  onClearCopiedOrchidGroup: () => void;
  onClearSelectedOrchidGroups: () => void;
  onCopyOrchidGroup: (orchidGroupId: number) => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onDelete: () => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onMove: (payload: PreciseMovePayload) => Promise<void>;
  onOpenEdit: () => void;
  onOpenMove: () => void;
  onOpenPaste: () => void;
  onOpenRepot: () => void;
  onOpenWorkRecord: () => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
  onSelectOrchidGroups: (orchidGroupIds: number[]) => void;
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
  onToggleSelectedOrchidGroup: (orchidGroupId: number) => void;
  onUpdateWorkRecordForm: <K extends keyof WorkRecordQuickFormState>(
    field: K,
    value: WorkRecordQuickFormState[K],
  ) => void;
  onWorkRecordCreate: () => Promise<void>;
}) {
  const [viewMode, setViewMode] = useState<
    "LOCATION" | "DERIVED" | "COLLECTION"
  >("LOCATION");
  const [createDraft, setCreateDraft] = useState<OrchidFormDraft | null>(null);
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
  const allHouseOrchidGroups = house.physicalBeds.flatMap((bed) =>
    bed.bedZones.flatMap((bedZone) => bedZone.orchidGroups),
  );
  const selectedOrchidGroupOutsideViewport =
    selectedOrchidGroup != null &&
    !allHouseOrchidGroups.some(
      (orchidGroup) => orchidGroup.id === selectedOrchidGroup.id,
    );
  const batchSelectedGroups = allHouseOrchidGroups.filter((orchidGroup) =>
    selectedOrchidGroupIds.has(orchidGroup.id),
  );
  const collectionTargets =
    batchSelectedGroups.length > 0
      ? batchSelectedGroups
      : selectedOrchidGroup
        ? [selectedOrchidGroup]
        : [];
  const matchedCount = orchidGroups.filter((orchidGroup) =>
    filteredOrchidGroupIds.has(orchidGroup.id),
  ).length;
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

  return (
    <aside className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
      {/* {!hideList ? (
        <div className="grid shrink-0 grid-cols-3 gap-1 rounded-md border border-[#d7ddd4] bg-white p-1 shadow-sm">
          {(
            [
              ["LOCATION", "위치별"],
              ["DERIVED", "자동 그룹"],
              ["COLLECTION", "사용자 그룹"],
            ] as const
          ).map(([mode, label]) => (
            <button
              className={`rounded-md px-2 py-2 text-xs font-bold transition ${
                viewMode === mode
                  ? "bg-[#2f8f4e] text-white"
                  : "text-[#526057] hover:bg-[#eef5ed]"
              }`}
              key={mode}
              type="button"
              onClick={() => setViewMode(mode)}
            >
              {label}
            </button>
          ))}
        </div>
      ) : null}  TODO: 난 묶음 관리 페이지 맵 정리 후 활성화 */}

      {selectedOrchidGroupOutsideViewport ? (
        <p className="rounded-md border border-[#f0d58a] bg-[#fff9e8] px-3 py-2 text-xs font-semibold text-[#7a5b08]">
          선택한 난 묶음은 현재 화면 밖에 있습니다.
        </p>
      ) : null}

      {!hideList && viewMode === "LOCATION" ? (
        <section className="flex min-h-0 flex-1 flex-col rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
          <div className="flex shrink-0 items-center justify-between gap-3">
            <p className="text-sm font-semibold text-[#17251b]">
              {selectedHouse
                ? `${visibleRangeLabel} 난 묶음 목록`
                : "난 묶음 목록"}{" "}
              (
              {hasActiveSearch
                ? `${matchedCount}/${orchidGroups.length}`
                : orchidGroups.length}
              개)
            </p>
            {/* {selectedOrchidGroupIds.size > 0 ? (
              <button
                className="text-xs font-semibold text-[#159447]"
                onClick={onClearSelectedOrchidGroups}
                type="button"
              >
                {selectedOrchidGroupIds.size}개 선택 해제
              </button>
            ) : null}  TODO: 난 묶음 관리 페이지 맵 정리 후 활성화 */}
          </div>
          {copiedOrchidGroup ? (
            <div className="mt-3 flex items-center justify-between gap-2 rounded-md border border-[#dbe8d8] bg-[#f5faf3] px-3 py-2 text-xs">
              <span className="min-w-0 truncate font-semibold text-[#34503b]">
                복사됨: {copiedOrchidGroup.varietyName} /{" "}
                {copiedOrchidGroup.quantity}분
              </span>
              <div className="flex shrink-0 items-center gap-1.5">
                <button
                  className="rounded-md bg-[#159447] px-2.5 py-1.5 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={!resolvedZone}
                  onClick={onOpenPaste}
                  type="button"
                >
                  붙여넣기
                </button>
                <button
                  className="rounded-md border border-[#cfd8cc] bg-white px-2.5 py-1.5 font-semibold text-[#435047]"
                  onClick={onClearCopiedOrchidGroup}
                  type="button"
                >
                  Clear
                </button>
              </div>
            </div>
          ) : null}

          {hasListTarget ? (
            <div className="mt-3 flex min-h-0 flex-1 flex-col">
              <div
                className={`space-y-2 overflow-y-auto pr-1 ${
                  compactList ? "max-h-28 shrink-0" : "min-h-0 flex-1"
                }`}
              >
                {orchidGroups.map((orchidGroup) => {
                  const selected = orchidGroup.id === selectedOrchidGroup?.id;
                  const matched = filteredOrchidGroupIds.has(orchidGroup.id);

                  return (
                    <div
                      key={orchidGroup.id}
                      className={`rounded-md border p-3 transition ${
                        matched ? "cursor-pointer hover:border-[#159447]" : ""
                      } ${
                        !matched
                          ? "border-[#e5e8e4] bg-[#f2f4f1] opacity-75"
                          : selected
                            ? "border-[#b9d0ff] bg-[#f5f8ff]"
                            : "border-[#e1e6df] bg-white"
                      } ${selected ? "ring-1 ring-[#b9d0ff]/40" : ""}`}
                      onClick={() =>
                        matched
                          ? onSelectOrchidGroup(orchidGroup.id)
                          : undefined
                      }
                    >
                      <div className="flex items-start justify-between gap-3">
                        {/* TODO: 난 묶음 관리 페이지 맵 정리 후 체크박스 활성화 시 label과 클릭 전파 차단 복원 */}
                        <div className="flex min-w-0 flex-1 cursor-pointer items-start gap-2">
                          {/* TODO: 난 묶음 관리 페이지 맵 정리 후 활성화 */}
                          {/* <input
                            aria-label={`${orchidGroup.varietyName} 다중 선택`}
                            checked={selectedOrchidGroupIds.has(orchidGroup.id)}
                            className="mt-0.5 h-4 w-4 accent-[#159447]"
                            disabled
                            onChange={() =>
                              onToggleSelectedOrchidGroup(orchidGroup.id)
                            }
                            type="checkbox"
                          /> */}
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span
                                className={`h-2.5 w-2.5 shrink-0 rounded-full ${
                                  matched
                                    ? getStatusDotClass(orchidGroup.status)
                                    : "bg-[#a6ada6]"
                                }`}
                              />
                              <p className="truncate text-sm font-bold text-[#17251b]">
                                {orchidGroup.varietyName}
                              </p>
                            </div>
                            <p className="mt-1 text-xs font-semibold text-[#344138]">
                              {orchidGroup.quantity}분
                            </p>
                            <p className="mt-0.5 text-[11px] text-[#6a766e]">
                              {formatOrchidMeta(orchidGroup)}
                            </p>
                          </div>
                        </div>

                        <div className="flex shrink-0 items-center gap-1.5">
                          <StatusBadge
                            muted={!matched}
                            value={orchidGroup.status}
                          />
                          <IconAction
                            label="복사"
                            onClick={() => onCopyOrchidGroup(orchidGroup.id)}
                            disabled={!matched}
                          >
                            <Copy
                              className="h-4 w-4"
                              strokeWidth={1.8}
                              aria-hidden="true"
                            />
                          </IconAction>
                          <IconAction
                            label="수정"
                            onClick={onOpenEdit}
                            disabled={!matched || !selected}
                          >
                            <Edit2
                              className="h-4 w-4"
                              strokeWidth={1.8}
                              aria-hidden="true"
                            />
                          </IconAction>
                          <IconAction
                            label="삭제"
                            onClick={onDelete}
                            disabled={!matched || !selected || saving}
                          >
                            <Trash2
                              className="h-4 w-4"
                              strokeWidth={1.8}
                              aria-hidden="true"
                            />
                          </IconAction>
                        </div>
                      </div>
                    </div>
                  );
                })}
                {orchidGroups.length === 0 ? (
                  <p className="rounded-md bg-[#f5f7f3] p-3 text-sm text-[#5c6a60]">
                    {listTargetLabel}에 등록된 난 묶음이 없습니다.
                  </p>
                ) : hasActiveSearch && matchedCount === 0 ? (
                  <p className="rounded-md border border-[#e4e8e2] bg-[#f6f8f5] p-3 text-sm text-[#5c6a60]">
                    필터에 맞는 난 묶음이 없습니다. 회색 항목은 필터 제외
                    상태입니다.
                  </p>
                ) : null}
              </div>

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
                {/* <ActionButton
                  icon={<Sprout className="h-4 w-4" />}
                  label="분갈이"
                  onClick={onOpenRepot}
                  disabled={!selectedOrchidGroup}
                />  TODO: 난 묶음 관리 페이지 맵 정리 후 활성화 */}
              </div>
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

      {!hideList && viewMode === "DERIVED" ? (
        <DerivedGroupPanel
          key={house.id}
          houseId={house.id}
          onSelectMembers={(members) => {
            onSelectOrchidGroups(members.map((member) => member.id));
            if (members[0]) onSelectOrchidGroup(members[0].id);
          }}
        />
      ) : null}

      {!hideList && viewMode === "COLLECTION" ? (
        <UserCollectionPanel
          key={`user-groups-${house.id}`}
          houseNumber={house.number}
          orchidGroups={collectionTargets}
          onSelectMembers={(orchidGroupIds) => {
            onSelectOrchidGroups(orchidGroupIds);
            if (orchidGroupIds[0]) onSelectOrchidGroup(orchidGroupIds[0]);
          }}
        />
      ) : null}

      {mutationMode === "CREATE" || mutationMode === "EDIT" ? (
        <OrchidGroupForm
          key={
            mutationMode === "EDIT"
              ? `edit-${selectedOrchidGroup?.id ?? "none"}`
              : `create-${resolvedZone?.id ?? "none"}-${pasteSourceOrchidGroup?.id ?? "empty"}`
          }
          draft={mutationMode === "CREATE" ? createDraft : null}
          house={house}
          initialValue={
            mutationMode === "EDIT"
              ? selectedOrchidGroup
              : pasteSourceOrchidGroup
          }
          mode={mutationMode}
          saving={saving}
          mapCellRangePick={mapCellRangePick}
          targetZone={resolvedZone}
          onCancel={onCancelMutation}
          onDraftChange={mutationMode === "CREATE" ? setCreateDraft : undefined}
          onStartMapCellRangePick={onStartMapCellRangePick}
          onSyncMapCellRangePick={onSyncMapCellRangePick}
          onSubmit={
            mutationMode === "EDIT"
              ? onEdit
              : async (payload) => {
                  await onCreate(payload);
                  setCreateDraft(null);
                }
          }
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

function IconAction({
  children,
  disabled = false,
  label,
  onClick,
}: {
  children: ReactNode;
  disabled?: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-label={label}
      className="flex h-8 w-8 items-center justify-center rounded-md border border-[#dfe5dc] text-[#435047] disabled:opacity-40"
      disabled={disabled}
      type="button"
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
    >
      {children}
    </button>
  );
}

function StatusBadge({
  muted = false,
  value,
}: {
  muted?: boolean;
  value: string;
}) {
  const className = muted
    ? "bg-[#e9ece8] text-[#7d857d]"
    : value === "정상" || value === "판매 가능"
      ? "bg-[#e6f7e8] text-[#159447]"
      : value.includes("주의")
        ? "bg-[#fff1d6] text-[#d88400]"
        : "bg-[#ffe7e7] text-[#d72d2d]";

  return (
    <span className={`rounded-md px-2 py-1 text-[11px] font-bold ${className}`}>
      {value}
    </span>
  );
}

function formatOrchidMeta(orchidGroup: OrchidGroup) {
  return [
    orchidGroup.potSize,
    orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function getStatusDotClass(status: string) {
  if (status === "정상" || status === "판매 가능") {
    return "bg-[#159447]";
  }
  if (status.includes("주의")) {
    return "bg-[#f59e0b]";
  }
  return "bg-[#e52d2d]";
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
