"use client";

import { useMemo, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import type {
  BedZone,
  FarmStatusMapData,
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

export function FarmStatusMap({ mapData, initialSelection, initialZoom }: FarmStatusMapProps) {
  const [zoomLevel, setZoomLevel] = useState<FarmZoomLevel>("FARM");
  const [selectedTarget, setSelectedTarget] = useState<SelectedTarget | null>(
    initialSelection ? { type: initialSelection.targetType, id: initialSelection.targetId } : null,
  );
  const [selection, setSelection] = useState<FarmStatusOrchidGroupList | null>(initialSelection);
  const [zoomData, setZoomData] = useState<FarmStatusZoomData | null>(initialZoom);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedHouseId = useMemo(() => {
    if (!selectedTarget) {
      return mapData.houses[0]?.houseId ?? null;
    }
    if (selectedTarget.type === "HOUSE") {
      return selectedTarget.id;
    }
    return zoomData?.houseId ?? mapData.houses[0]?.houseId ?? null;
  }, [mapData.houses, selectedTarget, zoomData?.houseId]);

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

  async function loadZoom(level: Exclude<FarmZoomLevel, "FARM">, params: { houseId?: number; physicalBedId?: number }) {
    setLoading(true);
    setErrorMessage(null);
    try {
      const search = new URLSearchParams({ level });
      if (params.houseId) {
        search.set("houseId", String(params.houseId));
      }
      if (params.physicalBedId) {
        search.set("physicalBedId", String(params.physicalBedId));
      }
      const response = await fetch(`${API_BASE_URL}/farm-status/zoom?${search.toString()}`, { cache: "no-store" });
      if (!response.ok) {
        throw new Error("농장맵 단계를 불러오지 못했습니다.");
      }
      const payload = (await response.json()) as { data: FarmStatusZoomData };
      setZoomData(payload.data);
      setZoomLevel(level);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function resetToFarm() {
    setZoomLevel("FARM");
    setZoomData(null);
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <section className="space-y-4">
        <ZoomControl
          zoomLevel={zoomLevel}
          selectedHouseId={selectedHouseId}
          onFarm={resetToFarm}
          onHouse={() => selectedHouseId && loadZoom("HOUSE", { houseId: selectedHouseId })}
          onPhysicalBed={() => selectedHouseId && loadZoom("PHYSICAL_BED", { houseId: selectedHouseId })}
        />
        {errorMessage ? <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-4 text-[#8f2f19]">{errorMessage}</div> : null}
        {loading ? <div className="rounded-md border border-[#d7ddd4] bg-white p-4 text-[#5c6a60]">불러오는 중입니다.</div> : null}

        {zoomLevel === "FARM" ? (
          <FarmOverviewLayer
            houses={mapData.houses}
            selectedTarget={selectedTarget}
            onSelectHouse={(house) => {
              void loadSelection("HOUSE", house.houseId);
              void loadZoom("HOUSE", { houseId: house.houseId });
            }}
          />
        ) : (
          <HouseLayer
            zoomLevel={zoomLevel}
            zoomData={zoomData}
            selectedTarget={selectedTarget}
            onSelectPhysicalBed={(bed) => {
              void loadSelection("PHYSICAL_BED", bed.id);
              void loadZoom("BED_ZONE", { physicalBedId: bed.id });
            }}
            onSelectBedZone={(zone) => void loadSelection("BED_ZONE", zone.id)}
          />
        )}
      </section>
      <SelectionSummaryPanel selection={selection} />
    </div>
  );
}

function ZoomControl({
  zoomLevel,
  selectedHouseId,
  onFarm,
  onHouse,
  onPhysicalBed,
}: {
  zoomLevel: FarmZoomLevel;
  selectedHouseId: number | null;
  onFarm: () => void;
  onHouse: () => void;
  onPhysicalBed: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex flex-wrap gap-2">
      <button className={zoomButtonClass(zoomLevel === "FARM")} onClick={onFarm} type="button">
        전체 농장
      </button>
      <button className={zoomButtonClass(zoomLevel === "HOUSE")} disabled={!selectedHouseId} onClick={onHouse} type="button">
        동 보기
      </button>
      <button
        className={zoomButtonClass(zoomLevel === "PHYSICAL_BED" || zoomLevel === "BED_ZONE")}
        disabled={!selectedHouseId}
        onClick={onPhysicalBed}
        type="button"
      >
        배드 보기
      </button>
      </div>
      <div className="flex gap-2">
        <span className="rounded-md border border-[#dfe6dc] px-3 py-2 text-base text-[#4d5b51]">100%</span>
        <span className="rounded-md border border-[#dfe6dc] px-3 py-2 text-base text-[#4d5b51]">조회 전용</span>
      </div>
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
    <div className="relative min-h-[520px] overflow-hidden rounded-md border border-[#cdd9c8] bg-[#95ba63] p-6 shadow-sm md:min-h-[620px]">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_10%_15%,rgba(255,255,255,0.24)_0,rgba(255,255,255,0)_16%),linear-gradient(135deg,rgba(255,255,255,0.2),rgba(255,255,255,0)_38%)]" />
      <div className="absolute left-0 right-0 top-24 h-12 rotate-[-4deg] bg-[#e9c780]" />
      <div className="absolute left-0 right-0 top-28 h-5 rotate-[-4deg] bg-[#f1d99e]" />
      <div className="absolute left-10 top-8 h-20 w-40 rotate-[-12deg] rounded-md border border-[#cfd6cf] bg-[#eef0ed] shadow-md">
        <div className="grid h-full grid-cols-4 gap-px p-2 opacity-60">
          {Array.from({ length: 12 }, (_, index) => (
            <span key={index} className="bg-white" />
          ))}
        </div>
      </div>
      <MapTree className="left-8 bottom-10" />
      <MapTree className="right-12 top-10" />
      <MapTree className="right-16 bottom-8" />
      <MapTree className="left-60 bottom-6" />
      <div className="absolute bottom-5 left-5 z-10 flex flex-col gap-3">
        <button className="flex h-12 w-12 items-center justify-center rounded-md bg-white text-3xl shadow" type="button">+</button>
        <button className="flex h-12 w-12 items-center justify-center rounded-md bg-white text-3xl shadow" type="button">−</button>
        <button className="flex h-12 w-12 items-center justify-center rounded-md bg-white text-2xl shadow" type="button">⌖</button>
      </div>
      <div className="relative z-10 grid h-full grid-cols-4 items-end gap-3 pt-36 md:grid-cols-8 xl:grid-cols-15">
        {houses.map((house) => (
          <HouseSummaryBlock
            key={house.houseId}
            house={house}
            selected={selectedTarget?.type === "HOUSE" && selectedTarget.id === house.houseId}
            onSelect={() => onSelectHouse(house)}
          />
        ))}
      </div>
      <div className="absolute bottom-5 left-24 z-10 flex gap-4 rounded-md bg-white px-4 py-3 text-base shadow">
        <span><span className="mr-2 text-[#1f9c4d]">●</span>정상</span>
        <span><span className="mr-2 text-[#f59e0b]">●</span>주의</span>
        <span><span className="mr-2 text-[#ef4444]">●</span>작업 필요</span>
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
  return (
    <button
      className={`group relative flex h-56 min-w-0 flex-col items-center rounded-b-xl rounded-t-md border bg-[#f7f8f5] px-2 pb-2 pt-8 text-left shadow-[0_8px_12px_rgba(37,72,39,0.28)] transition hover:translate-y-[-2px] hover:border-[#3d6f91] md:h-72 ${
        selected ? "border-[#1d6ff2] bg-[#d7e9ff] ring-4 ring-[#2f80ff]" : "border-[#cfd8cc]"
      }`}
      onClick={onSelect}
      type="button"
    >
      <div className={`absolute -top-4 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-md px-3 py-1 text-base font-semibold text-white shadow ${selected ? "bg-[#246df2]" : "bg-[#155c30]"}`}>
        {house.houseNumber}동 <span className={house.warningCount > 0 ? "text-[#ffcc33]" : "text-[#56d071]"}>●</span>
      </div>
      <div className="h-full w-full rounded-b-lg rounded-t-md border border-[#d9ded8] bg-[repeating-linear-gradient(90deg,#f4f5f2_0,#f4f5f2_10px,#e6e9e4_11px,#e6e9e4_12px),repeating-linear-gradient(0deg,rgba(180,190,180,0.28)_0,rgba(180,190,180,0.28)_18px,rgba(255,255,255,0)_19px,rgba(255,255,255,0)_38px)] opacity-95" />
      <div className={`absolute bottom-3 h-4 w-4 rounded-full ${house.warningCount > 0 ? "bg-[#f59e0b]" : "bg-[#20a64d]"}`} />
      <span className="mt-2 text-sm font-semibold text-[#314037]">{house.orchidGroupCount}묶음</span>
    </button>
  );
}

function HouseLayer({
  zoomLevel,
  zoomData,
  selectedTarget,
  onSelectPhysicalBed,
  onSelectBedZone,
}: {
  zoomLevel: FarmZoomLevel;
  zoomData: FarmStatusZoomData | null;
  selectedTarget: SelectedTarget | null;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
  onSelectBedZone: (zone: BedZone) => void;
}) {
  if (!zoomData) {
    return <div className="rounded-md border border-[#d7ddd4] bg-white p-8 text-[#5c6a60]">동을 선택하면 상세 구조가 표시됩니다.</div>;
  }

  return (
    <div className="space-y-4 rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div>
        <p className="text-sm font-semibold text-[#3d6f91]">선택한 동</p>
        <h2 className="mt-1 text-2xl font-semibold">{zoomData.houseNumber}동</h2>
      </div>
      {zoomLevel === "BED_ZONE" && zoomData.bedZones.length > 0 ? (
        <div className="grid gap-3 md:grid-cols-2">
          {zoomData.bedZones.map((zone) => (
            <button
              key={zone.id}
              className={`min-h-32 rounded-md border p-4 text-left transition hover:border-[#3d6f91] ${
                selectedTarget?.type === "BED_ZONE" && selectedTarget.id === zone.id
                  ? "border-[#2f6f3e] bg-[#eef7ec]"
                  : "border-[#d7ddd4] bg-[#f8faf7]"
              }`}
              onClick={() => onSelectBedZone(zone)}
              type="button"
            >
              <p className="text-xl font-semibold">{zone.name}</p>
              <p className="mt-2 text-base text-[#5c6a60]">난 묶음 {zone.orchidGroups.length}개</p>
            </button>
          ))}
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          {zoomData.physicalBeds.map((bed) => (
            <button
              key={bed.id}
              className={`min-h-48 rounded-md border p-4 text-left transition hover:border-[#3d6f91] ${
                selectedTarget?.type === "PHYSICAL_BED" && selectedTarget.id === bed.id
                  ? "border-[#2f6f3e] bg-[#eef7ec]"
                  : "border-[#d7ddd4] bg-[#f8faf7]"
              }`}
              onClick={() => onSelectPhysicalBed(bed)}
              type="button"
            >
              <p className="text-xl font-semibold">{bed.number}배드</p>
              <div className="mt-4 grid grid-cols-2 gap-3">
                {bed.bedZones.map((zone) => (
                  <div key={zone.id} className="min-h-24 rounded-md bg-white p-3 text-base">
                    <p className="font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
                    <p className="mt-2 text-sm text-[#5c6a60]">{zone.orchidGroups.length}개</p>
                  </div>
                ))}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function SelectionSummaryPanel({ selection }: { selection: FarmStatusOrchidGroupList | null }) {
  return (
    <aside className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">선택 범위</p>
          <h2 className="mt-1 text-2xl font-semibold">{selection?.targetName ?? "선택 없음"}</h2>
        </div>
        <a className="rounded-md bg-[#2f65f4] px-3 py-2 text-sm font-semibold text-white" href="/orchid-groups">
          관리에서 수정
        </a>
      </div>
      <div className="mt-5 space-y-3">
        {!selection ? <p className="text-[#5c6a60]">동, 배드, 논리 구역을 선택하면 난 묶음 목록이 표시됩니다.</p> : null}
        {selection && selection.items.length === 0 ? <p className="text-[#5c6a60]">이 범위에는 난 묶음이 없습니다.</p> : null}
        {selection?.items.map((item) => (
          <div key={item.orchidGroupId} className="rounded-md border border-[#d7ddd4] p-4">
            <p className="text-lg font-semibold">{item.varietyName}</p>
            <p className="mt-1 text-base text-[#5c6a60]">
              {item.physicalBedName} / {item.bedZoneName}
            </p>
            <div className="mt-3 flex flex-wrap gap-2 text-sm">
              <span className="bg-[#eef4ed] px-2 py-1">{item.quantity}분</span>
              <span className="bg-[#eef4ed] px-2 py-1">{item.status}</span>
              {item.genus ? <span className="bg-[#eef4ed] px-2 py-1">{item.genus}</span> : null}
            </div>
          </div>
        ))}
      </div>
    </aside>
  );
}

function zoomButtonClass(active: boolean) {
  return `rounded-md px-4 py-2 text-base font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${
    active ? "bg-[#2f65f4] text-white" : "bg-[#eef4ed] text-[#214f31] hover:bg-[#e0ecd8]"
  }`;
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
