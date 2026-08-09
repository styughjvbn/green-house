import type { OrchidGroup } from "@/entities/farm/types";
import { ListChecks, X } from "lucide-react";

export default function SelectedOrchidGroupsInfo({
  orchidGroups,
  onClear,
  onRemove,
}: {
  orchidGroups: OrchidGroup[];
  onClear: () => void;
  onRemove: (orchidGroupId: number) => void;
}) {
  const totalQuantity = orchidGroups.reduce(
    (sum, orchidGroup) => sum + orchidGroup.quantity,
    0,
  );

  return (
    <section
      className="flex h-[70px] items-center gap-3 overflow-hidden rounded-md border border-[#b8d8bf] bg-[#f7fbf7] px-2.5 py-2 shadow-sm"
      data-testid="multi-selection-info"
    >
      <div className="flex shrink-0 items-center gap-2">
        <ListChecks
          aria-hidden
          className="h-4 w-4 shrink-0 text-[#159447]"
          strokeWidth={1.8}
        />
        <div>
          <p className="text-sm font-bold whitespace-nowrap text-[#17251b]">
            선택한 난 묶음 {orchidGroups.length}개
          </p>
          <span className="text-xs font-semibold text-[#526057]">
            총 {totalQuantity}분
          </span>
        </div>
      </div>

      {orchidGroups.length > 0 ? (
        <div className="flex min-w-0 flex-1 gap-1.5 overflow-x-auto pb-0.5">
          {orchidGroups.map((orchidGroup) => (
            <div
              className="flex shrink-0 items-center gap-2 rounded-md border border-[#d8e5d8] bg-white px-2.5 py-1"
              key={orchidGroup.id}
            >
              <div>
                <p className="text-xs font-bold text-[#26352b]">
                  {orchidGroup.varietyName} · {orchidGroup.quantity}분
                </p>
                <p className="text-[10px] text-[#6a766e]">
                  {orchidGroup.houseNumber}동 {orchidGroup.physicalBedNumber}
                  다이 {orchidGroup.bedZoneName}
                </p>
              </div>
              <button
                aria-label={`${orchidGroup.varietyName} 선택 해제`}
                className="rounded p-0.5 text-[#77857c] hover:bg-[#eef3ed] hover:text-[#26352b]"
                onClick={() => onRemove(orchidGroup.id)}
                type="button"
              >
                <X aria-hidden className="h-3.5 w-3.5" strokeWidth={2} />
              </button>
            </div>
          ))}
        </div>
      ) : (
        <p className="min-w-0 flex-1 truncate text-xs text-[#667169]">
          목록이나 지도에서 난 묶음을 선택하세요.
        </p>
      )}

      <button
        className="shrink-0 rounded-md border border-[#cfd8cc] bg-white px-2.5 py-1 text-xs font-semibold text-[#435047] disabled:cursor-not-allowed disabled:opacity-40"
        disabled={orchidGroups.length === 0}
        onClick={onClear}
        type="button"
      >
        전체 해제
      </button>
    </section>
  );
}
