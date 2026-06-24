"use client";

import type { BedZone, FarmStatusZoomData, FarmZoomLevel, PhysicalBed } from "@/entities/farm/types";

export function HouseDetailLayer({
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
