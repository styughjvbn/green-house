"use client";

import { useMemo, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import type {
  BedZone,
  FarmStatusMapData,
  FarmStatusOrchidGroupItem,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
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

const zoomOrder: FarmZoomLevel[] = ["FARM", "HOUSE", "PHYSICAL_BED", "BED_ZONE"];

export function FarmStatusMap({ mapData, initialSelection, initialZoom }: FarmStatusMapProps) {
  const initialHouseId = initialZoom?.houseId ?? mapData.houses.find((house) => house.orchidGroupCount > 0)?.houseId ?? mapData.houses[0]?.houseId ?? null;
  const [zoomLevel, setZoomLevel] = useState<FarmZoomLevel>("FARM");
  const [selectedHouseId, setSelectedHouseId] = useState<number | null>(initialHouseId);
  const [selectedTarget, setSelectedTarget] = useState<SelectedTarget | null>(
    initialSelection ? { type: initialSelection.targetType, id: initialSelection.targetId } : null,
  );
  const [selection, setSelection] = useState<FarmStatusOrchidGroupList | null>(initialSelection);
  const [zoomData, setZoomData] = useState<FarmStatusZoomData | null>(initialZoom);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedHouse = useMemo(
    () => mapData.houses.find((house) => house.houseId === selectedHouseId) ?? mapData.houses[0] ?? null,
    [mapData.houses, selectedHouseId],
  );

  const selectedPhysicalBedId = selectedTarget?.type === "PHYSICAL_BED" ? selectedTarget.id : null;
  const selectedBedZoneId = selectedTarget?.type === "BED_ZONE" ? selectedTarget.id : null;

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

  async function loadHouseZoom(houseId: number, nextLevel: FarmZoomLevel = "HOUSE") {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/farm-status/zoom?level=HOUSE&houseId=${houseId}`, {
        cache: "no-store",
      });
      if (!response.ok) {
        throw new Error("선택한 동의 맵 정보를 불러오지 못했습니다.");
      }
      const payload = (await response.json()) as { data: FarmStatusZoomData };
      setSelectedHouseId(houseId);
      setZoomData(payload.data);
      setZoomLevel(nextLevel);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSelectHouse(house: HouseStatusSummary, nextLevel: FarmZoomLevel = zoomLevel === "FARM" ? "HOUSE" : zoomLevel) {
    await Promise.all([loadSelection("HOUSE", house.houseId), loadHouseZoom(house.houseId, nextLevel)]);
  }

  async function handleSelectPhysicalBed(bed: PhysicalBed) {
    setZoomLevel("PHYSICAL_BED");
    await loadSelection("PHYSICAL_BED", bed.id);
  }

  async function handleSelectBedZone(zone: BedZone) {
    setZoomLevel("BED_ZONE");
    await loadSelection("BED_ZONE", zone.id);
  }

  async function handleZoomIn() {
    const currentIndex = zoomOrder.indexOf(zoomLevel);
    const nextLevel = zoomOrder[Math.min(currentIndex + 1, zoomOrder.length - 1)];
    if (nextLevel === zoomLevel) {
      return;
    }
    if (nextLevel !== "FARM" && selectedHouseId) {
      await loadHouseZoom(selectedHouseId, nextLevel);
      return;
    }
    setZoomLevel(nextLevel);
  }

  function handleZoomOut() {
    const currentIndex = zoomOrder.indexOf(zoomLevel);
    const nextLevel = zoomOrder[Math.max(currentIndex - 1, 0)];
    setZoomLevel(nextLevel);
    if (nextLevel === "FARM") {
      setSelectedTarget(selectedHouseId ? { type: "HOUSE", id: selectedHouseId } : null);
    }
  }

  function resetToFarm() {
    setZoomLevel("FARM");
    if (selectedHouseId) {
      setSelectedTarget({ type: "HOUSE", id: selectedHouseId });
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_400px]">
        <section className="space-y-3">
          {errorMessage ? <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">{errorMessage}</div> : null}
          {loading ? <div className="rounded-md border border-[#d7ddd4] bg-white p-3 text-sm text-[#5c6a60]">불러오는 중입니다.</div> : null}

          <FarmMapCanvas
            houses={mapData.houses}
            selectedBedZoneId={selectedBedZoneId}
            selectedHouseId={selectedHouseId}
            selectedPhysicalBedId={selectedPhysicalBedId}
            selectedTarget={selectedTarget}
            zoomData={zoomData}
            zoomLevel={zoomLevel}
            onReset={resetToFarm}
            onSelectBedZone={(zone) => void handleSelectBedZone(zone)}
            onSelectHouse={(house) => void handleSelectHouse(house)}
            onSelectPhysicalBed={(bed) => void handleSelectPhysicalBed(bed)}
            onZoomIn={() => void handleZoomIn()}
            onZoomOut={handleZoomOut}
          />
        </section>

        <SelectionSummaryPanel selection={selection} selectedHouse={selectedHouse} />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_400px]">
        <OrchidGroupTable selection={selection} selectedHouse={selectedHouse} />
        <RecentWorkSummary />
      </div>
    </div>
  );
}

function FarmMapCanvas({
  houses,
  selectedBedZoneId,
  selectedHouseId,
  selectedPhysicalBedId,
  selectedTarget,
  zoomData,
  zoomLevel,
  onReset,
  onSelectBedZone,
  onSelectHouse,
  onSelectPhysicalBed,
  onZoomIn,
  onZoomOut,
}: {
  houses: HouseStatusSummary[];
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedPhysicalBedId: number | null;
  selectedTarget: SelectedTarget | null;
  zoomData: FarmStatusZoomData | null;
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectHouse: (house: HouseStatusSummary) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}) {
  return (
    <div className="relative min-h-[500px] overflow-hidden rounded-xl border border-[#cdd9c8] bg-[#95b969] p-4 shadow-sm">
      <MapBackdrop />
      <MapControls zoomLevel={zoomLevel} onReset={onReset} onZoomIn={onZoomIn} onZoomOut={onZoomOut} />
      <div className="absolute left-4 top-4 z-20 flex flex-wrap items-center gap-2 rounded-md bg-white/95 px-3 py-2 text-sm font-semibold text-[#29422e] shadow-sm">
        <span>{zoomLevel === "FARM" ? "전체 농장 지도" : `${zoomData?.houseNumber ?? ""}동 상세 지도`}</span>
        <span className="rounded-full bg-[#eef6e9] px-2 py-0.5 text-xs text-[#39713d]">{zoomLabel(zoomLevel)}</span>
      </div>

      <div className="relative z-10 h-full pt-24">
        {zoomLevel === "FARM" ? (
          <FarmOverviewLayer houses={houses} selectedTarget={selectedTarget} onSelectHouse={onSelectHouse} />
        ) : (
          <HouseDetailLayer
            selectedBedZoneId={selectedBedZoneId}
            selectedHouseId={selectedHouseId}
            selectedPhysicalBedId={selectedPhysicalBedId}
            zoomData={zoomData}
            zoomLevel={zoomLevel}
            onSelectBedZone={onSelectBedZone}
            onSelectPhysicalBed={onSelectPhysicalBed}
          />
        )}
      </div>

      <Legend />
    </div>
  );
}

function FarmOverviewLayer({
  houses,
  selectedTarget,
  onSelectHouse,
}: {
  houses: HouseStatusSummary[];
  selectedTarget: SelectedTarget | null;
  onSelectHouse: (house: HouseStatusSummary) => void;
}) {
  return (
    <div className="grid min-h-[370px] grid-cols-5 items-end gap-2 lg:grid-cols-[repeat(15,minmax(0,1fr))]">
      {houses.map((house) => (
        <HouseSummaryBlock
          key={house.houseId}
          house={house}
          selected={selectedTarget?.type === "HOUSE" && selectedTarget.id === house.houseId}
          onSelect={() => onSelectHouse(house)}
        />
      ))}
    </div>
  );
}

function HouseDetailLayer({
  selectedBedZoneId,
  selectedHouseId,
  selectedPhysicalBedId,
  zoomData,
  zoomLevel,
  onSelectBedZone,
  onSelectPhysicalBed,
}: {
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedPhysicalBedId: number | null;
  zoomData: FarmStatusZoomData | null;
  zoomLevel: FarmZoomLevel;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
}) {
  if (!zoomData || zoomData.houseId !== selectedHouseId) {
    return <div className="rounded-lg bg-white/90 p-6 text-center text-[#4f6255]">동을 선택하면 배드와 구역 지도가 표시됩니다.</div>;
  }

  return (
    <div className="rounded-xl border border-white/60 bg-white/80 p-4 shadow-sm backdrop-blur">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">{zoomData.houseNumber}동</p>
          <h3 className="text-xl font-semibold text-[#17251b]">물리 배드 / 논리 구역</h3>
        </div>
        <div className="flex gap-2 text-xs font-semibold">
          <span className="rounded-full bg-[#e7f0e6] px-3 py-1 text-[#34503b]">실제 방향</span>
          <span className="rounded-full bg-white px-3 py-1 text-[#5b6a60]">좌→우</span>
        </div>
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        {zoomData.physicalBeds.map((bed) => (
          <PhysicalBedMapBlock
            key={bed.id}
            bed={bed}
            selectedBedZoneId={selectedBedZoneId}
            selectedPhysicalBedId={selectedPhysicalBedId}
            showZoneDetail={zoomLevel === "BED_ZONE"}
            onSelectBedZone={onSelectBedZone}
            onSelectPhysicalBed={onSelectPhysicalBed}
          />
        ))}
      </div>
    </div>
  );
}

function PhysicalBedMapBlock({
  bed,
  selectedBedZoneId,
  selectedPhysicalBedId,
  showZoneDetail,
  onSelectBedZone,
  onSelectPhysicalBed,
}: {
  bed: PhysicalBed;
  selectedBedZoneId: number | null;
  selectedPhysicalBedId: number | null;
  showZoneDetail: boolean;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
}) {
  const selected = selectedPhysicalBedId === bed.id || bed.bedZones.some((zone) => zone.id === selectedBedZoneId);

  return (
    <div className={`rounded-lg border bg-[#f8faf7] p-3 shadow-sm ${selected ? "border-[#1976f3] ring-2 ring-[#1976f3]" : "border-[#d5ded0]"}`}>
      <button className="mb-3 flex w-full items-center justify-between rounded-md bg-[#155c30] px-3 py-2 text-left text-sm font-semibold text-white" onClick={() => onSelectPhysicalBed(bed)} type="button">
        <span>{bed.number}배드</span>
        <span>{bed.bedZones.reduce((sum, zone) => sum + zone.orchidGroups.length, 0)}묶음</span>
      </button>
      <div className="grid min-h-[250px] grid-cols-2 gap-2">
        {bed.bedZones.map((zone) => (
          <BedZoneMapBlock key={zone.id} selected={selectedBedZoneId === zone.id} showDetail={showZoneDetail} zone={zone} onSelect={() => onSelectBedZone(zone)} />
        ))}
      </div>
    </div>
  );
}

function BedZoneMapBlock({
  selected,
  showDetail,
  zone,
  onSelect,
}: {
  selected: boolean;
  showDetail: boolean;
  zone: BedZone;
  onSelect: () => void;
}) {
  return (
    <button
      className={`flex min-h-[250px] flex-col rounded-md border p-2 text-left transition hover:border-[#1976f3] ${
        selected ? "border-[#1976f3] bg-[#e7f0ff] ring-2 ring-[#1976f3]" : "border-[#cbd6c7] bg-[#edf1ea]"
      }`}
      onClick={onSelect}
      type="button"
    >
      <div className="flex items-center justify-between text-xs font-semibold text-[#34483a]">
        <span>{zone.side === "LEFT" ? "좌" : "우"}</span>
        <span>{zone.orchidGroups.length}묶음</span>
      </div>
      <div className="mt-2 flex flex-1 flex-col gap-1.5">
        {zone.orchidGroups.length > 0 ? (
          zone.orchidGroups.slice(0, showDetail ? 8 : 5).map((group) => (
            <span key={group.id} className={`rounded px-2 py-1 text-xs font-semibold ${group.status === "정상" ? "bg-[#2e9d4d] text-white" : "bg-[#f59e0b] text-white"}`}>
              {showDetail ? `${group.varietyName} ${group.quantity}분` : group.quantity}
            </span>
          ))
        ) : (
          <span className="flex flex-1 items-center justify-center rounded border border-dashed border-[#bec8ba] text-xs text-[#748078]">빈 구역</span>
        )}
      </div>
    </button>
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
      className={`group relative flex h-40 min-w-0 flex-col items-center rounded-b-lg rounded-t-md border bg-[#f7f8f5] px-1 pb-2 pt-6 text-left shadow-[0_7px_10px_rgba(37,72,39,0.26)] transition hover:translate-y-[-2px] lg:h-56 ${
        selected ? "border-[#1d6ff2] bg-[#dcecff] ring-3 ring-[#2f80ff]" : "border-[#cfd8cc]"
      }`}
      onClick={onSelect}
      type="button"
    >
      <div className={`absolute -top-3 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold text-white shadow ${selected ? "bg-[#246df2]" : "bg-[#155c30]"}`}>
        {house.houseNumber}동
        <span className={`h-2 w-2 rounded-full ${hasWarning ? "bg-[#ffcc33]" : "bg-[#56d071]"}`} />
      </div>
      <div className="h-full w-full rounded-b-md rounded-t-sm border border-[#d9ded8] bg-[repeating-linear-gradient(90deg,#f4f5f2_0,#f4f5f2_8px,#e6e9e4_9px,#e6e9e4_10px),repeating-linear-gradient(0deg,rgba(180,190,180,0.28)_0,rgba(180,190,180,0.28)_15px,rgba(255,255,255,0)_16px,rgba(255,255,255,0)_32px)] opacity-95" />
      <span className={`absolute bottom-3 h-3.5 w-3.5 rounded-full ${hasWarning ? "bg-[#f59e0b]" : "bg-[#20a64d]"}`} />
      <span className="mt-1 text-[11px] font-semibold text-[#314037]">{house.orchidGroupCount}묶음</span>
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
      <div className="flex items-start justify-between gap-3 border-b border-[#edf1ea] p-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-[#256ff0] px-3 py-1.5 text-sm font-semibold text-white">
              {selection?.targetName ?? selectedHouse?.houseName ?? "선택 없음"}
            </span>
            <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusLabel === "정상" ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff3da] text-[#b76600]"}`}>
              {statusLabel}
            </span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-[#17251b]">{selectionTitle(selection, selectedHouse)}</h2>
        </div>
        <a className="rounded-md border border-[#d2dcd0] bg-[#f8faf7] px-3 py-2 text-xs font-semibold text-[#34503b]" href="/orchid-groups">
          관리에서 수정
        </a>
      </div>

      <div className="grid grid-cols-4 border-b border-[#edf1ea] text-center">
        <PanelMetric label="물리 배드" value="3개" />
        <PanelMetric label="논리 구역" value="6개" />
        <PanelMetric label="난 묶음" value={`${items.length}개`} />
        <PanelMetric label="최근 작업" value={selectedHouse?.latestWorkDate ?? "없음"} compact />
      </div>

      <div className="flex gap-2 border-b border-[#edf1ea] p-3">
        <DisabledTab active label="선택 범위" />
        <DisabledTab label="배드별 보기" />
        <DisabledTab label="구역별 보기" />
      </div>

      <div className="max-h-[345px] overflow-auto p-3">
        <OrchidMiniTable items={items} />
      </div>
    </aside>
  );
}

function OrchidMiniTable({ items }: { items: FarmStatusOrchidGroupItem[] }) {
  if (items.length === 0) {
    return <p className="rounded-md bg-[#f7f9f5] p-4 text-center text-sm text-[#6a766d]">선택한 범위에 난 묶음이 없습니다.</p>;
  }

  return (
    <table className="w-full border-separate border-spacing-y-1.5 text-left text-xs">
      <thead className="text-[#657269]">
        <tr>
          <th className="px-2 font-semibold">배드/구역</th>
          <th className="px-2 font-semibold">품종명</th>
          <th className="px-2 text-right font-semibold">수량</th>
          <th className="px-2 font-semibold">상태</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.orchidGroupId} className="bg-[#f8faf7]">
            <td className="rounded-l-md px-2 py-2 text-[#4f6255]">
              {item.physicalBedName} {item.bedZoneName}
            </td>
            <td className="px-2 py-2 font-semibold text-[#1e2d23]">{item.varietyName}</td>
            <td className="px-2 py-2 text-right text-[#1e2d23]">{item.quantity}</td>
            <td className="rounded-r-md px-2 py-2">
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
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[#17251b]">{selectionTitle(selection, selectedHouse)}</h2>
          <p className="mt-1 text-xs text-[#66736a]">현재 선택한 범위에 배치된 난 묶음입니다.</p>
        </div>
        <a className="rounded-md bg-[#1f8f48] px-3 py-2 text-xs font-semibold text-white" href="/orchid-groups">
          난 묶음 관리
        </a>
      </div>
      <div className="mt-3 overflow-x-auto">
        <OrchidMiniTable items={items} />
      </div>
    </section>
  );
}

function RecentWorkSummary() {
  const rows = [
    ["최근 농약", "기록 없음"],
    ["최근 비료", "기록 없음"],
    ["최근 분갈이", "기록 없음"],
    ["잎 정리", "기록 없음"],
    ["잡초 정리", "기록 없음"],
    ["단화 정리", "기록 없음"],
  ];

  return (
    <section className="rounded-xl border border-[#d9e2d5] bg-white p-4 shadow-sm">
      <h2 className="text-lg font-semibold text-[#17251b]">최근 작업 요약</h2>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-md bg-[#f7f9f5] px-3 py-2 text-sm">
            <p className="font-medium text-[#425348]">{label}</p>
            <p className="mt-1 text-xs text-[#68766d]">{value}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function MapControls({
  zoomLevel,
  onReset,
  onZoomIn,
  onZoomOut,
}: {
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}) {
  return (
    <div className="absolute bottom-16 left-4 z-20 flex flex-col gap-2">
      <button aria-label="확대" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow" onClick={onZoomIn} type="button">
        +
      </button>
      <button aria-label="축소" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow" onClick={onZoomOut} type="button">
        -
      </button>
      <button aria-label="전체 보기" className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-sm font-semibold text-[#2b3a2f] shadow" onClick={onReset} type="button">
        ⌖
      </button>
      <span className="rounded-md bg-white px-2 py-1 text-center text-xs font-semibold text-[#405246] shadow">{zoomLabel(zoomLevel)}</span>
    </div>
  );
}

function Legend() {
  return (
    <div className="absolute bottom-4 left-16 z-20 flex flex-wrap gap-3 rounded-md bg-white/95 px-3 py-2 text-xs shadow">
      <LegendItem color="bg-[#20a64d]" label="정상" />
      <LegendItem color="bg-[#f59e0b]" label="주의" />
      <LegendItem color="bg-[#ef4444]" label="이상" />
      <LegendItem color="bg-[#1976f3]" label="선택" />
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`inline-block h-2.5 w-2.5 rounded-full ${color}`} />
      {label}
    </span>
  );
}

function MapBackdrop() {
  return (
    <>
      <div className="absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.22),rgba(255,255,255,0)_38%)]" />
      <div className="absolute -left-6 right-[-20px] top-24 h-10 rotate-[-4deg] bg-[#d7aa5d]" />
      <div className="absolute -left-6 right-[-20px] top-27 h-4 rotate-[-4deg] bg-[#f0d59a]" />
      <div className="absolute left-8 top-8 h-16 w-32 rotate-[-12deg] rounded-md border border-[#cfd6cf] bg-[#ecefe9] shadow-md">
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
    </>
  );
}

function MapTree({ className }: { className: string }) {
  return (
    <div className={`absolute z-0 flex gap-1 ${className}`}>
      <span className="h-8 w-8 rounded-full bg-[#6b9f45] opacity-80" />
      <span className="mt-3 h-6 w-6 rounded-full bg-[#4f8538] opacity-80" />
      <span className="mt-1 h-7 w-7 rounded-full bg-[#7aaa4f] opacity-80" />
    </div>
  );
}

function PanelMetric({ label, value, compact = false }: { label: string; value: string; compact?: boolean }) {
  return (
    <div className="border-r border-[#edf1ea] px-2 py-3 last:border-r-0">
      <p className="text-[11px] font-semibold text-[#7a877e]">{label}</p>
      <p className={`mt-1 font-semibold text-[#17251b] ${compact ? "text-xs" : "text-base"}`}>{value}</p>
    </div>
  );
}

function DisabledTab({ active = false, label }: { active?: boolean; label: string }) {
  return (
    <button className={`rounded-md px-3 py-2 text-xs font-semibold ${active ? "bg-[#256ff0] text-white" : "bg-[#eef4ed] text-[#48604f]"}`} disabled type="button">
      {label}
    </button>
  );
}

function StatusBadge({ status }: { status: string }) {
  const isNormal = status === "정상";
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${isNormal ? "bg-[#e8f7e8] text-[#16853b]" : "bg-[#fff0df] text-[#c15b10]"}`}>{status}</span>;
}

function zoomLabel(level: FarmZoomLevel) {
  return {
    FARM: "전체",
    HOUSE: "동",
    PHYSICAL_BED: "배드",
    BED_ZONE: "구역",
  }[level];
}

function selectionTitle(selection: FarmStatusOrchidGroupList | null, selectedHouse: HouseStatusSummary | null) {
  if (selection) {
    return `${selection.targetName} 난 묶음 목록`;
  }
  if (selectedHouse) {
    return `${selectedHouse.houseName} 난 묶음 목록`;
  }
  return "난 묶음 목록";
}
