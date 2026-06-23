"use client";

import { useMemo, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import type {
  FarmStatusMapData,
  FarmStatusOrchidGroupItem,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
} from "@/types/farm";

type SelectedTarget = {
  type: FarmStatusTargetType;
  id: number;
};

type FarmStatusMapProps = {
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
};

export function FarmStatusMap({ mapData, initialSelection, initialZoom }: FarmStatusMapProps) {
  const [zoomLevel] = useState<FarmZoomLevel>("FARM");
  const [selectedTarget, setSelectedTarget] = useState<SelectedTarget | null>(
    initialSelection ? { type: initialSelection.targetType, id: initialSelection.targetId } : null,
  );
  const [selection, setSelection] = useState<FarmStatusOrchidGroupList | null>(initialSelection);
  const [zoomData] = useState<FarmStatusZoomData | null>(initialZoom);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedHouseId = useMemo(() => {
    if (selectedTarget?.type === "HOUSE") {
      return selectedTarget.id;
    }
    return zoomData?.houseId ?? mapData.houses.find((house) => house.orchidGroupCount > 0)?.houseId ?? mapData.houses[0]?.houseId ?? null;
  }, [mapData.houses, selectedTarget, zoomData?.houseId]);

  const selectedHouse = useMemo(
    () => mapData.houses.find((house) => house.houseId === selectedHouseId) ?? mapData.houses[0] ?? null,
    [mapData.houses, selectedHouseId],
  );

  async function loadSelection(type: FarmStatusTargetType, id: number) {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/farm-status/orchid-groups?targetType=${type}&targetId=${id}`, {
        cache: "no-store",
      });
      if (!response.ok) {
        throw new Error("선택한 범위의 난 묶음을 불러오지 못했습니다.");
      }
      const payload = (await response.json()) as { data: FarmStatusOrchidGroupList };
      setSelectedTarget({ type, id });
      setSelection(payload.data);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_430px]">
        <section className="space-y-3">
          {errorMessage ? <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-4 text-[#8f2f19]">{errorMessage}</div> : null}
          {loading ? <div className="rounded-md border border-[#d7ddd4] bg-white p-4 text-[#5c6a60]">불러오는 중입니다.</div> : null}

          <FarmOverviewLayer
            houses={mapData.houses}
            selectedTarget={selectedTarget}
            zoomLevel={zoomLevel}
            onSelectHouse={(house) => void loadSelection("HOUSE", house.houseId)}
          />
        </section>

        <SelectionSummaryPanel selection={selection} selectedHouse={selectedHouse} />
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
        <OrchidGroupTable selection={selection} selectedHouse={selectedHouse} />
        <RecentWorkSummary />
      </div>

      <div className="rounded-lg border border-[#d9e2d5] bg-[#f7fbf3] px-5 py-4 text-base text-[#3f5a43]">
        팁: 동을 선택하면 우측에서 해당 동의 난 묶음 목록을 확인할 수 있습니다.
      </div>
    </div>
  );
}

function FarmOverviewLayer({
  houses,
  selectedTarget,
  zoomLevel,
  onSelectHouse,
}: {
  houses: HouseStatusSummary[];
  selectedTarget: SelectedTarget | null;
  zoomLevel: FarmZoomLevel;
  onSelectHouse: (house: HouseStatusSummary) => void;
}) {
  return (
    <div className="relative min-h-[520px] overflow-hidden rounded-xl border border-[#cdd9c8] bg-[#97bb63] p-5 shadow-sm md:min-h-[620px]">
      <div className="absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.22),rgba(255,255,255,0)_38%)]" />
      <div className="absolute -left-6 right-[-20px] top-24 h-12 rotate-[-4deg] bg-[#d7aa5d]" />
      <div className="absolute -left-6 right-[-20px] top-28 h-5 rotate-[-4deg] bg-[#f0d59a]" />
      <div className="absolute left-8 top-8 h-20 w-40 rotate-[-12deg] rounded-md border border-[#cfd6cf] bg-[#ecefe9] shadow-md">
        <div className="grid h-full grid-cols-4 gap-px p-2 opacity-70">
          {Array.from({ length: 12 }, (_, index) => (
            <span key={index} className="bg-white" />
          ))}
        </div>
      </div>
      <MapTree className="left-8 bottom-10" />
      <MapTree className="right-12 top-10" />
      <MapTree className="right-16 bottom-8" />
      <MapTree className="left-60 bottom-6" />

      <div className="absolute bottom-5 left-5 z-10 flex flex-col gap-2">
        <MapControlButton label="+" />
        <MapControlButton label="-" />
        <MapControlButton label="□" />
      </div>

      <div className="absolute left-5 top-5 z-10 rounded-md bg-white/95 px-4 py-3 text-sm font-semibold text-[#29422e] shadow-sm">
        전체 농장 지도
        <span className="ml-2 rounded-full bg-[#eef6e9] px-2 py-1 text-xs text-[#39713d]">{zoomLevel}</span>
      </div>

      <div className="relative z-10 grid h-full grid-cols-3 items-end gap-3 pt-36 sm:grid-cols-5 lg:grid-cols-[repeat(15,minmax(0,1fr))]">
        {houses.map((house) => (
          <HouseSummaryBlock
            key={house.houseId}
            house={house}
            selected={selectedTarget?.type === "HOUSE" && selectedTarget.id === house.houseId}
            onSelect={() => onSelectHouse(house)}
          />
        ))}
      </div>

      <div className="absolute bottom-5 left-24 z-10 flex flex-wrap gap-4 rounded-md bg-white/95 px-4 py-3 text-base shadow">
        <span>
          <span className="mr-2 inline-block h-3 w-3 rounded-full bg-[#20a64d]" />
          정상
        </span>
        <span>
          <span className="mr-2 inline-block h-3 w-3 rounded-full bg-[#f59e0b]" />
          주의
        </span>
        <span>
          <span className="mr-2 inline-block h-3 w-3 rounded-full bg-[#ef4444]" />
          작업 필요
        </span>
      </div>
    </div>
  );
}

function HouseSummaryBlock({
  house,
  selected,
  onSelect,
}: {
  house: HouseStatusSummary;
  selected: boolean;
  onSelect: () => void;
}) {
  const hasWarning = house.warningCount > 0 || house.repotDueCount > 0;

  return (
    <button
      className={`group relative flex h-44 min-w-0 flex-col items-center rounded-b-xl rounded-t-md border bg-[#f7f8f5] px-1.5 pb-2 pt-7 text-left shadow-[0_8px_12px_rgba(37,72,39,0.28)] transition hover:translate-y-[-2px] md:h-60 ${
        selected ? "border-[#1d6ff2] bg-[#dcecff] ring-4 ring-[#2f80ff]" : "border-[#cfd8cc]"
      }`}
      onClick={onSelect}
      type="button"
    >
      <div className={`absolute -top-4 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-md px-2 py-1 text-sm font-semibold text-white shadow ${selected ? "bg-[#246df2]" : "bg-[#155c30]"}`}>
        {house.houseNumber}동
        <span className={`h-2.5 w-2.5 rounded-full ${hasWarning ? "bg-[#ffcc33]" : "bg-[#56d071]"}`} />
      </div>
      <div className="h-full w-full rounded-b-lg rounded-t-md border border-[#d9ded8] bg-[repeating-linear-gradient(90deg,#f4f5f2_0,#f4f5f2_10px,#e6e9e4_11px,#e6e9e4_12px),repeating-linear-gradient(0deg,rgba(180,190,180,0.28)_0,rgba(180,190,180,0.28)_18px,rgba(255,255,255,0)_19px,rgba(255,255,255,0)_38px)] opacity-95" />
      <span className={`absolute bottom-3 h-4 w-4 rounded-full ${hasWarning ? "bg-[#f59e0b]" : "bg-[#20a64d]"}`} />
      <span className="mt-2 text-xs font-semibold text-[#314037]">{house.orchidGroupCount}묶음</span>
    </button>
  );
}

function SelectionSummaryPanel({
  selection,
  selectedHouse,
}: {
  selection: FarmStatusOrchidGroupList | null;
  selectedHouse: HouseStatusSummary | null;
}) {
  const items = selection?.items ?? [];
  const statusLabel = selectedHouse && (selectedHouse.warningCount > 0 || selectedHouse.repotDueCount > 0) ? "주의" : "정상";

  return (
    <aside className="rounded-xl border border-[#d9e2d5] bg-white shadow-sm">
      <div className="flex items-start justify-between gap-3 border-b border-[#edf1ea] p-5">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-[#256ff0] px-4 py-1.5 text-base font-semibold text-white">
              {selection?.targetName ?? selectedHouse?.houseName ?? "선택 없음"}
            </span>
            <span className={`rounded-full px-3 py-1 text-sm font-semibold ${statusLabel === "정상" ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff3da] text-[#b76600]"}`}>
              {statusLabel}
            </span>
          </div>
          <h2 className="mt-4 text-2xl font-semibold text-[#17251b]">{selection?.targetName ?? selectedHouse?.houseName ?? "동 선택"}</h2>
        </div>
        <a className="rounded-md border border-[#d2dcd0] bg-[#f8faf7] px-3 py-2 text-sm font-semibold text-[#34503b]" href="/orchid-groups">
          관리에서 수정
        </a>
      </div>

      <div className="grid grid-cols-4 border-b border-[#edf1ea] text-center">
        <PanelMetric label="물리 배드" value="3개" />
        <PanelMetric label="논리 구역" value="6개" />
        <PanelMetric label="난 묶음" value={`${items.length}개`} />
        <PanelMetric label="최근 작업" value={selectedHouse?.latestWorkDate ?? "없음"} compact />
      </div>

      <div className="flex gap-2 border-b border-[#edf1ea] p-4">
        <button className="rounded-md bg-[#256ff0] px-3 py-2 text-sm font-semibold text-white" type="button">
          동 전체 난 묶음
        </button>
        <button className="rounded-md bg-[#eef4ed] px-3 py-2 text-sm font-semibold text-[#48604f]" type="button">
          배드별 보기
        </button>
        <button className="rounded-md bg-[#eef4ed] px-3 py-2 text-sm font-semibold text-[#48604f]" type="button">
          구역별 보기
        </button>
      </div>

      <div className="max-h-[360px] overflow-auto p-4">
        <OrchidMiniTable items={items} />
      </div>
    </aside>
  );
}

function OrchidMiniTable({ items }: { items: FarmStatusOrchidGroupItem[] }) {
  if (items.length === 0) {
    return <p className="rounded-md bg-[#f7f9f5] p-5 text-center text-[#6a766d]">선택한 범위에 난 묶음이 없습니다.</p>;
  }

  return (
    <table className="w-full border-separate border-spacing-y-2 text-left text-sm">
      <thead className="text-[#657269]">
        <tr>
          <th className="px-2 font-semibold">위치</th>
          <th className="px-2 font-semibold">품종명</th>
          <th className="px-2 text-right font-semibold">수량</th>
          <th className="px-2 font-semibold">상태</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.orchidGroupId} className="bg-[#f8faf7]">
            <td className="rounded-l-md px-2 py-3 text-[#4f6255]">
              {item.physicalBedName} / {item.bedZoneName}
            </td>
            <td className="px-2 py-3 font-semibold text-[#1e2d23]">{item.varietyName}</td>
            <td className="px-2 py-3 text-right text-[#1e2d23]">{item.quantity}</td>
            <td className="rounded-r-md px-2 py-3">
              <StatusBadge status={item.status} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function OrchidGroupTable({
  selection,
  selectedHouse,
}: {
  selection: FarmStatusOrchidGroupList | null;
  selectedHouse: HouseStatusSummary | null;
}) {
  const items = selection?.items ?? [];

  return (
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-[#17251b]">{selection?.targetName ?? selectedHouse?.houseName ?? "선택 동"} 전체 난 묶음 목록</h2>
          <p className="mt-1 text-sm text-[#66736a]">현재 선택한 동에 배치된 난 묶음입니다.</p>
        </div>
        <button className="rounded-md bg-[#1f8f48] px-4 py-2 text-sm font-semibold text-white" type="button">
          난 묶음 관리
        </button>
      </div>
      <div className="mt-4 overflow-x-auto">
        <OrchidMiniTable items={items} />
      </div>
    </section>
  );
}

function RecentWorkSummary() {
  const rows = [
    ["최근 농약", "2025-06-01"],
    ["최근 비료", "2025-05-20"],
    ["최근 분갈이", "2025-03-12"],
    ["잎 정리", "기록 없음"],
    ["잡초 정리", "기록 없음"],
    ["단화 정리", "기록 없음"],
  ];

  return (
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold text-[#17251b]">최근 작업 요약</h2>
      <div className="mt-4 space-y-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between rounded-md bg-[#f7f9f5] px-4 py-3 text-base">
            <span className="font-medium text-[#425348]">{label}</span>
            <span className="text-[#68766d]">{value}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

function PanelMetric({ label, value, compact = false }: { label: string; value: string; compact?: boolean }) {
  return (
    <div className="border-r border-[#edf1ea] px-2 py-4 last:border-r-0">
      <p className="text-xs font-semibold text-[#7a877e]">{label}</p>
      <p className={`mt-1 font-semibold text-[#17251b] ${compact ? "text-sm" : "text-lg"}`}>{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const isNormal = status === "정상";
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${isNormal ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff0df] text-[#c15b10]"}`}>
      {status}
    </span>
  );
}

function MapControlButton({ label }: { label: string }) {
  return (
    <button className="flex h-11 w-11 items-center justify-center rounded-md bg-white text-2xl font-semibold text-[#2b3a2f] shadow" type="button">
      {label}
    </button>
  );
}

function MapTree({ className }: { className: string }) {
  return (
    <div className={`absolute z-0 flex gap-1 ${className}`}>
      <span className="h-9 w-9 rounded-full bg-[#6b9f45] opacity-80" />
      <span className="mt-3 h-7 w-7 rounded-full bg-[#4f8538] opacity-80" />
      <span className="mt-1 h-8 w-8 rounded-full bg-[#7aaa4f] opacity-80" />
    </div>
  );
}
