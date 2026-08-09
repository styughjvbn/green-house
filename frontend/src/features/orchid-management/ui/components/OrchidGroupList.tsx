import type { ReactNode } from "react";
import type { OrchidGroup } from "@/entities/farm/types";
import { Copy, Edit2, Trash2 } from "lucide-react";

export default function OrchidGroupList({
  compact,
  displayedOrchidGroups,
  displayingFarmResults,
  hasActiveSearch,
  listOrchidGroupCount,
  listRef,
  listTargetLabel,
  multiSelectEnabled,
  searchLoading,
  searchScope,
  selectedOrchidGroupId,
  selectedOrchidGroupIds,
  selectedSearchGroup,
  saving,
  onCopy,
  onDelete,
  onEdit,
  onSelect,
  onSelectSearchResult,
  onToggleSelected,
}: {
  compact: boolean;
  displayedOrchidGroups: OrchidGroup[];
  displayingFarmResults: boolean;
  hasActiveSearch: boolean;
  listOrchidGroupCount: number;
  listRef: React.RefObject<HTMLDivElement | null>;
  listTargetLabel: string;
  multiSelectEnabled: boolean;
  searchLoading: boolean;
  searchScope: "CURRENT_LIST" | "FARM";
  selectedOrchidGroupId: number | null;
  selectedOrchidGroupIds: Set<number>;
  selectedSearchGroup: boolean;
  saving: boolean;
  onCopy: (orchidGroupId: number) => void;
  onDelete: (orchidGroupId: number) => void;
  onEdit: (orchidGroupId: number) => void;
  onSelect: (orchidGroupId: number) => void;
  onSelectSearchResult: (orchidGroup: OrchidGroup) => void;
  onToggleSelected: (orchidGroupId: number) => void;
}) {
  const resultVisible =
    !hasActiveSearch ||
    selectedSearchGroup ||
    searchScope === "CURRENT_LIST" ||
    !searchLoading;

  return (
    <div
      ref={listRef}
      className={`space-y-2 overflow-y-auto pr-1 ${
        compact ? "max-h-28 shrink-0" : "min-h-0 flex-1"
      }`}
    >
      {!selectedSearchGroup &&
      searchScope === "FARM" &&
      hasActiveSearch &&
      searchLoading ? (
        <p className="rounded-md bg-[#f5f7f3] p-3 text-sm text-[#5c6a60]">
          전체 농장에서 검색 중입니다.
        </p>
      ) : null}

      {resultVisible
        ? displayedOrchidGroups.map((orchidGroup, index) => {
            const selected = multiSelectEnabled
              ? selectedOrchidGroupIds.has(orchidGroup.id)
              : orchidGroup.id === selectedOrchidGroupId;

            return (
              <div
                key={orchidGroup.id}
                data-orchid-group-id={orchidGroup.id}
                className={`cursor-pointer rounded-md border p-3 transition hover:border-[#159447] ${
                  selected
                    ? "border-[#b9d0ff] bg-[#f5f8ff] ring-1 ring-[#b9d0ff]/40"
                    : "border-[#e1e6df] bg-white"
                }`}
                onClick={() =>
                  multiSelectEnabled
                    ? onToggleSelected(orchidGroup.id)
                    : displayingFarmResults && hasActiveSearch
                      ? onSelectSearchResult(orchidGroup)
                      : onSelect(orchidGroup.id)
                }
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 flex-1 cursor-pointer items-start gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span
                          className={`h-2.5 w-2.5 shrink-0 rounded-full ${getStatusDotClass(
                            orchidGroup.status,
                          )}`}
                        />
                        <p className="truncate text-sm font-bold text-[#17251b]">
                          {orchidGroup.varietyName}
                        </p>
                      </div>
                      <p className="mt-1 text-xs font-semibold text-[#344138]">
                        {orchidGroup.quantity}분
                      </p>
                      <p className="mt-0.5 truncate text-[11px] text-[#6a766e]">
                        {formatOrchidMeta(orchidGroup) || "-"}
                      </p>
                      {displayingFarmResults && hasActiveSearch ? (
                        <p className="mt-0.5 truncate text-[11px] text-[#6a766e]">
                          {orchidGroup.houseNumber}동{" "}
                          {orchidGroup.physicalBedNumber}
                          다이 {orchidGroup.bedZoneName}
                        </p>
                      ) : null}
                    </div>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-2.5">
                    <div className="flex items-center gap-1.5">
                      <StatusBadge value={orchidGroup.status} />
                      <IconAction
                        label="복사"
                        disabled={multiSelectEnabled}
                        onClick={() => onCopy(orchidGroup.id)}
                      >
                        <Copy
                          className="h-4 w-4"
                          strokeWidth={1.8}
                          aria-hidden
                        />
                      </IconAction>
                      <IconAction
                        label="보정"
                        disabled={multiSelectEnabled}
                        onClick={() => onEdit(orchidGroup.id)}
                      >
                        <Edit2
                          className="h-4 w-4"
                          strokeWidth={1.8}
                          aria-hidden
                        />
                      </IconAction>
                      <IconAction
                        label="삭제"
                        disabled={multiSelectEnabled || saving}
                        onClick={() => onDelete(orchidGroup.id)}
                      >
                        <Trash2
                          className="h-4 w-4"
                          strokeWidth={1.8}
                          aria-hidden
                        />
                      </IconAction>
                    </div>
                    <span className="text-[10px] font-semibold text-[#9aa49e]">
                      #{index + 1}
                    </span>
                  </div>
                </div>
              </div>
            );
          })
        : null}

      {!hasActiveSearch && listOrchidGroupCount === 0 ? (
        <p className="rounded-md bg-[#f5f7f3] p-3 text-sm text-[#5c6a60]">
          {listTargetLabel}에 등록된 난 묶음이 없습니다.
        </p>
      ) : hasActiveSearch &&
        resultVisible &&
        displayedOrchidGroups.length === 0 ? (
        <p className="rounded-md border border-[#e4e8e2] bg-[#f6f8f5] p-3 text-sm text-[#5c6a60]">
          {selectedSearchGroup
            ? "선택한 그룹에 포함된 난 묶음이 없습니다."
            : searchScope === "CURRENT_LIST"
              ? "현재 목록에 검색 결과가 없습니다."
              : "전체 농장에 검색 결과가 없습니다."}
        </p>
      ) : null}
    </div>
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

function StatusBadge({ value }: { value: string }) {
  const className =
    value === "정상" || value === "판매 가능"
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
  if (status === "정상" || status === "판매 가능") return "bg-[#159447]";
  if (status.includes("주의")) return "bg-[#f59e0b]";
  return "bg-[#e52d2d]";
}
