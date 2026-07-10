"use client";

import { useState } from "react";
import type {
  FarmStatusOrchidGroupItem,
  FarmStatusOrchidGroupList,
  HouseStatusSummary,
} from "@/entities/farm/types";
import { hasHouseWarning, selectionTitle } from "../../lib/farmStatusView";
import type { SelectedFarmStatusOrchidGroup } from "../../model/types";

export function SelectionSummaryPanel({
  selection,
  selectedOrchidGroup,
  selectedHouse,
  onSelectOrchidGroup,
}: {
  selection: FarmStatusOrchidGroupList | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedHouse: HouseStatusSummary | null;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
}) {
  const items = selection?.items ?? [];
  const varietyCount = new Set(items.map((item) => item.varietyName)).size;
  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);
  const [showRecentWork, setShowRecentWork] = useState(false);
  const statusLabel = hasHouseWarning(selectedHouse) ? "주의" : "정상";
  const managementHref = createManagementHref(selection, selectedHouse);

  return (
    <aside className="min-h-0 flex-1 overflow-auto rounded-xl border border-[#d9e2d5] bg-white shadow-sm">
      <div className="flex items-start justify-between gap-3 border-b border-[#edf1ea] p-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-[#256ff0] px-3 py-1.5 text-sm font-semibold text-white">
              {selection?.targetName ?? selectedHouse?.houseName ?? "선택 없음"}
            </span>
            <span
              className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusLabel === "정상" ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff3da] text-[#b76600]"}`}
            >
              {statusLabel}
            </span>
          </div>
        </div>
        <a
          className="rounded-md border border-[#d2dcd0] bg-[#f8faf7] px-3 py-2 text-xs font-semibold text-[#34503b]"
          href={managementHref}
        >
          관리에서 수정
        </a>
      </div>

      <div className="grid grid-cols-4 border-b border-[#edf1ea] text-center">
        <PanelMetric label="난 묶음" value={`${items.length}개`} compact />
        <PanelMetric label="품종" value={`${varietyCount}개`} compact />
        <PanelMetric label="총 분" value={`${totalQuantity}분`} compact />
        <button
          className="border-r border-[#edf1ea] px-2 py-2 text-center last:border-r-0 hover:bg-[#f7faf6]"
          onClick={() => setShowRecentWork((current) => !current)}
          type="button"
        >
          <p className="text-[10px] font-semibold text-[#7a877e]">최근 작업</p>
          <p className="mt-0.5 text-xs font-semibold text-[#17251b]">
            {selectedHouse?.latestWorkDate ?? "없음"}
          </p>
        </button>
      </div>
      {showRecentWork ? (
        <div className="border-b border-[#edf1ea] px-3 py-2">
          <RecentWorkSummary compact />
        </div>
      ) : null}

      <div className="p-3">
        {selectedOrchidGroup ? (
          <SelectedOrchidDetail selectedOrchidGroup={selectedOrchidGroup} />
        ) : (
          <OrchidGroupList items={items} onSelect={onSelectOrchidGroup} />
        )}
      </div>
    </aside>
  );
}

function SelectedOrchidDetail({
  selectedOrchidGroup,
}: {
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup;
}) {
  return (
    <div className="rounded-lg border border-[#cfd9cc] bg-[#f8faf7] p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-[#718078]">선택한 난 묶음</p>
          <h3 className="mt-1 text-lg font-bold text-[#17251b]">
            {selectedOrchidGroup.varietyName}
          </h3>
          {selectedOrchidGroup.genus ? (
            <p className="mt-1 text-xs text-[#607067]">
              {selectedOrchidGroup.genus}
            </p>
          ) : null}
        </div>
        <StatusBadge status={selectedOrchidGroup.status} />
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
        <DetailMetric
          label="수량"
          value={`${selectedOrchidGroup.quantity}분`}
        />
        <DetailMetric
          label="동"
          value={`${selectedOrchidGroup.houseNumber}동`}
        />
        <DetailMetric
          label="배드"
          value={selectedOrchidGroup.physicalBedName}
        />
        <DetailMetric label="구역" value={selectedOrchidGroup.bedZoneName} />
        <DetailMetric
          label="초기 년생"
          value={formatOptionalNumber(selectedOrchidGroup.ageYear, "년생")}
        />
        <DetailMetric
          label="포트 크기"
          value={selectedOrchidGroup.potSize ?? "-"}
        />
        <DetailMetric
          label="배치 규격"
          value={selectedOrchidGroup.placementType ?? "-"}
        />
        <DetailMetric
          label="판 수"
          value={formatOptionalNumber(selectedOrchidGroup.trayCount, "판")}
        />
        <DetailMetric
          label="시작 위치"
          value={formatOptionalNumber(selectedOrchidGroup.startPosition, "")}
        />
        <DetailMetric
          label="종료 위치"
          value={formatOptionalNumber(selectedOrchidGroup.endPosition, "")}
        />
        <DetailMetric
          label="분할 배치"
          value={
            selectedOrchidGroup.splitPlacementAllowed == null
              ? "-"
              : selectedOrchidGroup.splitPlacementAllowed
                ? "허용"
                : "불가"
          }
        />
        <DetailMetric
          label="품종 ID"
          value={formatOptionalNumber(selectedOrchidGroup.varietyId, "")}
        />
      </div>
      {selectedOrchidGroup.memo ? (
        <div className="mt-2 rounded-md bg-white px-3 py-2 text-sm">
          <p className="text-[11px] font-semibold text-[#718078]">메모</p>
          <p className="mt-1 whitespace-pre-wrap text-[#26352c]">
            {selectedOrchidGroup.memo}
          </p>
        </div>
      ) : null}

      <a
        className="mt-4 inline-flex w-full justify-center rounded-md bg-[#1f8f48] px-3 py-2 text-sm font-semibold text-white"
        href={`/orchid-groups?houseId=${selectedOrchidGroup.houseId}&physicalBedId=${selectedOrchidGroup.physicalBedId}&bedZoneId=${selectedOrchidGroup.bedZoneId}`}
      >
        관리에서 확인
      </a>
    </div>
  );
}

function OrchidGroupList({
  items,
  onSelect,
}: {
  items: FarmStatusOrchidGroupItem[];
  onSelect: (group: SelectedFarmStatusOrchidGroup) => void;
}) {
  if (items.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-[#cfd9cc] bg-[#f8faf7] p-4 text-sm text-[#5f6d64]">
        <p className="font-semibold text-[#26352c]">난 묶음 목록</p>
        <p className="mt-2 text-xs leading-5">
          현재 선택한 범위에 배치된 난 묶음이 없습니다.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-[#cfd9cc] bg-[#f8faf7]">
      <div className="flex items-center justify-between border-b border-[#e4ebe1] px-3 py-2">
        <p className="text-sm font-semibold text-[#26352c]">난 묶음 목록</p>
        <span className="text-xs font-semibold text-[#718078]">
          {items.length}개
        </span>
      </div>
      <div className="max-h-[360px] overflow-y-auto bg-white">
        {items.map((item) => (
          <button
            key={item.orchidGroupId}
            className="block w-full border-b border-[#eef1ec] px-3 py-2 text-left last:border-b-0 hover:bg-[#f6faf5]"
            onClick={() => onSelect(item)}
            type="button"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-[#17251b]">
                  {item.varietyName}
                </p>
                <p className="mt-1 text-xs text-[#5e6a61]">
                  {item.houseNumber}동 {item.physicalBedName} {item.bedZoneName}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs font-semibold text-[#2d3a31]">
                  {item.quantity}분
                </p>
                <p className="mt-1 text-[11px] text-[#728076]">{item.status}</p>
              </div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

function DetailMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-white px-3 py-2">
      <p className="text-[11px] font-semibold text-[#718078]">{label}</p>
      <p className="mt-1 font-semibold text-[#26352c]">{value}</p>
    </div>
  );
}

function formatOptionalNumber(
  value: number | null | undefined,
  suffix: string,
) {
  if (value == null) {
    return "-";
  }

  return suffix ? `${value}${suffix}` : String(value);
}

function createManagementHref(
  selection: FarmStatusOrchidGroupList | null,
  selectedHouse: HouseStatusSummary | null,
) {
  const params = new URLSearchParams();
  const houseId = resolveHouseId(selection, selectedHouse);

  if (houseId != null) {
    params.set("houseId", String(houseId));
  }

  if (selection?.targetType === "PHYSICAL_BED") {
    params.set("physicalBedId", String(selection.targetId));
  }

  if (selection?.targetType === "BED_ZONE") {
    params.set("bedZoneId", String(selection.targetId));
  }

  const query = params.toString();
  return query ? `/orchid-groups?${query}` : "/orchid-groups";
}

function resolveHouseId(
  selection: FarmStatusOrchidGroupList | null,
  selectedHouse: HouseStatusSummary | null,
) {
  if (selection?.targetType === "HOUSE") {
    return selection.targetId;
  }

  return selection?.items[0]?.houseId ?? selectedHouse?.houseId ?? null;
}

export function RecentWorkSummary({ compact = false }: { compact?: boolean }) {
  const rows = [
    ["최근 농약", "기록 없음"],
    ["최근 비료", "기록 없음"],
    ["최근 분갈이", "기록 없음"],
    ["잎 정리", "기록 없음"],
    ["잡초 정리", "기록 없음"],
    ["단화 정리", "기록 없음"],
  ];

  return (
    <section
      className={
        compact
          ? ""
          : "rounded-xl border border-[#d9e2d5] bg-white p-4 shadow-sm"
      }
    >
      <h2
        className={
          compact
            ? "text-sm font-semibold text-[#17251b]"
            : "text-lg font-semibold text-[#17251b]"
        }
      >
        최근 작업 요약
      </h2>
      <div
        className={
          compact
            ? "mt-2 grid grid-cols-2 gap-1.5"
            : "mt-3 grid grid-cols-2 gap-2"
        }
      >
        {rows.map(([label, value]) => (
          <div
            key={label}
            className={
              compact
                ? "rounded-md bg-[#f7f9f5] px-2 py-1.5 text-xs"
                : "rounded-md bg-[#f7f9f5] px-3 py-2 text-sm"
            }
          >
            <p className="font-medium text-[#425348]">{label}</p>
            <p className="mt-0.5 text-[11px] text-[#68766d]">{value}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function PanelMetric({
  label,
  value,
  compact = false,
}: {
  label: string;
  value: string;
  compact?: boolean;
}) {
  return (
    <div className="border-r border-[#edf1ea] px-2 py-2 last:border-r-0">
      <p className="text-[10px] font-semibold text-[#7a877e]">{label}</p>
      <p
        className={`mt-0.5 font-semibold text-[#17251b] ${compact ? "text-xs" : "text-base"}`}
      >
        {value}
      </p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const isNormal = status === "정상";
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${isNormal ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff0df] text-[#c15b10]"}`}
    >
      {status}
    </span>
  );
}
