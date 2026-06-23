"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import type {
  BedZone,
  FarmStatusMapData,
  House,
  HouseStatusSummary,
  OrchidGroup,
  OrchidManagementViewMode,
  PhysicalBed,
  SelectedBedZone,
  SelectedOrchidGroup,
} from "@/types/farm";

type OrchidSelection = SelectedBedZone | SelectedOrchidGroup;

type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
};

export function OrchidManagementMap({ mapData, house }: OrchidManagementMapProps) {
  const firstOrchidGroup = useMemo(() => {
    for (const bed of house.physicalBeds) {
      for (const zone of bed.bedZones) {
        if (zone.orchidGroups[0]) {
          return zone.orchidGroups[0];
        }
      }
    }
    return null;
  }, [house.physicalBeds]);

  const [selection, setSelection] = useState<OrchidSelection | null>(
    firstOrchidGroup ? { type: "ORCHID_GROUP", orchidGroupId: firstOrchidGroup.id } : null,
  );
  const [viewMode, setViewMode] = useState<OrchidManagementViewMode>("REAL_DIRECTION");

  const selectedOrchidGroup = selection?.type === "ORCHID_GROUP" ? findOrchidGroup(house, selection.orchidGroupId) : null;
  const selectedBedZone = selection?.type === "BED_ZONE" ? findBedZone(house, selection.bedZoneId)?.zone ?? null : null;

  return (
    <div className="grid gap-6 2xl:grid-cols-[320px_minmax(0,1fr)_360px]">
      <HouseSelectorPanel houses={mapData.houses} selectedHouseId={house.id} />
      <section className="space-y-4">
        <HouseDetailHeader house={house} viewMode={viewMode} onViewModeChange={setViewMode} />
        <HouseDetailMap
          house={house}
          selection={selection}
          onSelectBedZone={(bedZoneId) => setSelection({ type: "BED_ZONE", bedZoneId })}
          onSelectOrchidGroup={(orchidGroupId) => setSelection({ type: "ORCHID_GROUP", orchidGroupId })}
        />
        <MapLegend />
      </section>
      <OrchidSelectionPanel house={house} selectedBedZone={selectedBedZone} selectedOrchidGroup={selectedOrchidGroup} />
    </div>
  );
}

function HouseSelectorPanel({ houses, selectedHouseId }: { houses: HouseStatusSummary[]; selectedHouseId: number }) {
  return (
    <aside className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">전체 동 보기</p>
          <h2 className="mt-1 text-2xl font-semibold">동 선택</h2>
        </div>
        <span className="rounded-full border border-[#d7ddd4] px-2 py-1 text-sm text-[#5c6a60]">?</span>
      </div>
      <div className="mt-5 grid grid-cols-3 gap-3 2xl:grid-cols-2">
        {houses.map((house) => {
          const selected = house.houseId === selectedHouseId;
          const warning = house.warningCount > 0;
          return (
            <Link
              key={house.houseId}
              href={`/orchid-groups?houseId=${house.houseId}`}
              className={`min-h-32 rounded-md border p-3 text-center shadow-sm transition hover:border-[#159447] ${
                selected ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]" : "border-[#d7ddd4] bg-white"
              }`}
            >
              <p className="text-xl font-semibold">{house.houseNumber}동</p>
              <div className="mx-auto mt-3 h-12 w-16 rounded-t-2xl border border-[#b9c7b9] bg-[linear-gradient(90deg,#f5f7f4_0,#f5f7f4_18%,#dfe7de_19%,#f5f7f4_20%,#f5f7f4_48%,#dfe7de_49%,#f5f7f4_50%,#f5f7f4_78%,#dfe7de_79%,#f5f7f4_80%)]" />
              <p className={`mt-2 text-sm font-semibold ${warning ? "text-[#f59e0b]" : "text-[#159447]"}`}>
                ● {warning ? "주의" : "정상"}
              </p>
            </Link>
          );
        })}
      </div>
    </aside>
  );
}

function HouseDetailHeader({
  house,
  viewMode,
  onViewModeChange,
}: {
  house: House;
  viewMode: OrchidManagementViewMode;
  onViewModeChange: (viewMode: OrchidManagementViewMode) => void;
}) {
  const viewModes: Array<{ value: OrchidManagementViewMode; label: string }> = [
    { value: "REAL_DIRECTION", label: "실제 방향 보기" },
    { value: "ROTATED", label: "회전 보기" },
    { value: "BY_BED", label: "배드별 보기" },
  ];

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">선택한 동</p>
          <h1 className="mt-1 text-3xl font-semibold">{house.number}동 상세 보기</h1>
        </div>
        <div className="flex flex-wrap gap-2">
          {viewModes.map((mode) => (
            <button
              key={mode.value}
              className={`rounded-md px-4 py-2 text-base font-semibold ${
                viewMode === mode.value ? "bg-[#159447] text-white" : "border border-[#d7ddd4] bg-white text-[#435047]"
              }`}
              onClick={() => onViewModeChange(mode.value)}
              type="button"
            >
              {mode.label}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-3 rounded-md border border-[#b9d0ff] bg-[#f3f7ff] px-4 py-3 text-base font-semibold text-[#246df2]">
        <span>동 내부 이동: 드래그</span>
        <span className="text-[#b4c2dc]">|</span>
        <span>다른 동 이동: 다음 단계에서 연결</span>
      </div>
    </section>
  );
}

function HouseDetailMap({
  house,
  selection,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  house: House;
  selection: OrchidSelection | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
      <div className="grid gap-5 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            selection={selection}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </section>
  );
}

function PhysicalBedBlock({
  bed,
  selection,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  selection: OrchidSelection | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <div className="rounded-md border border-[#cfe0cc] bg-[#f7faf6] p-3">
      <h3 className="text-center text-2xl font-semibold">{bed.number}배드</h3>
      <div className="mt-4 grid grid-cols-2 gap-3">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            zone={zone}
            selected={selection?.type === "BED_ZONE" && selection.bedZoneId === zone.id}
            selectedOrchidGroupId={selection?.type === "ORCHID_GROUP" ? selection.orchidGroupId : null}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}

function BedZoneBlock({
  zone,
  selected,
  selectedOrchidGroupId,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const emptySlotCount = Math.max(1, 5 - zone.orchidGroups.length);

  return (
    <button
      className={`min-h-[520px] rounded-md border p-3 text-left transition ${
        selected ? "border-[#246df2] bg-[#f3f7ff] ring-2 ring-[#246df2]" : "border-[#d7ddd4] bg-white hover:border-[#159447]"
      }`}
      onClick={() => onSelectBedZone(zone.id)}
      type="button"
    >
      <p className="text-center text-xl font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
      <div className="mt-4 space-y-3">
        {zone.orchidGroups.map((orchidGroup) => (
          <OrchidGroupBlock
            key={orchidGroup.id}
            orchidGroup={orchidGroup}
            selected={selectedOrchidGroupId === orchidGroup.id}
            onSelect={(event) => {
              event.stopPropagation();
              onSelectOrchidGroup(orchidGroup.id);
            }}
          />
        ))}
        {Array.from({ length: emptySlotCount }, (_, index) => (
          <div key={index} className="min-h-20 rounded-md border border-dashed border-[#d7ddd4] bg-[#f0f1ef]" />
        ))}
      </div>
    </button>
  );
}

function OrchidGroupBlock({
  orchidGroup,
  selected,
  onSelect,
}: {
  orchidGroup: OrchidGroup;
  selected: boolean;
  onSelect: (event: React.MouseEvent<HTMLDivElement>) => void;
}) {
  const warning = orchidGroup.status !== "정상" && orchidGroup.status !== "판매 가능";

  return (
    <div
      className={`min-h-24 cursor-pointer rounded-md border p-3 shadow-sm transition ${
        selected ? "border-[#246df2] bg-[#dcecff] ring-2 ring-[#246df2]" : "border-[#82c886] bg-[#bfe2b8] hover:border-[#159447]"
      }`}
      onClick={onSelect}
      role="button"
      tabIndex={0}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="font-semibold">{orchidGroup.varietyName}</p>
        <span className={warning ? "text-[#f59e0b]" : "text-[#159447]"}>●</span>
      </div>
      <p className="mt-1 text-sm font-semibold">{orchidGroup.quantity}분</p>
      <p className="mt-1 text-sm text-[#435047]">
        {[orchidGroup.potSize, orchidGroup.ageYear ? `${orchidGroup.ageYear}년생` : null].filter(Boolean).join(" · ")}
      </p>
    </div>
  );
}

function OrchidSelectionPanel({
  house,
  selectedBedZone,
  selectedOrchidGroup,
}: {
  house: House;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
}) {
  const resolvedZone = selectedOrchidGroup ? findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null : selectedBedZone;
  const totalQuantity = resolvedZone?.orchidGroups.reduce((sum, orchidGroup) => sum + orchidGroup.quantity, 0) ?? 0;

  return (
    <aside className="space-y-4">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">선택한 난 묶음</p>
        {selectedOrchidGroup ? (
          <div className="mt-4">
            <div className="flex gap-4">
              <div className="flex h-20 w-20 items-center justify-center rounded-md bg-[#d8edd5] text-4xl">♧</div>
              <div>
                <h2 className="text-2xl font-semibold">{selectedOrchidGroup.varietyName}</h2>
                <p className="mt-1 text-lg text-[#435047]">{selectedOrchidGroup.quantity}분</p>
                <p className="mt-1 text-[#246df2]">
                  {selectedOrchidGroup.houseNumber}동 &gt; {selectedOrchidGroup.physicalBedNumber}배드 &gt; {selectedOrchidGroup.bedZoneName}
                </p>
              </div>
            </div>
            <div className="mt-5 grid gap-3">
              <DisabledAction label="난 묶음 추가" primary />
              <DisabledAction label="작업 기록 추가" />
              <DisabledAction label="다른 위치로 이동" />
              <DisabledAction label="출력" />
            </div>
          </div>
        ) : (
          <p className="mt-4 text-[#5c6a60]">난 묶음을 선택하면 상세 정보가 표시됩니다.</p>
        )}
      </section>

      <section className="rounded-md border border-[#d7ddd4] bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <p className="text-xl font-semibold">선택한 구역 정보</p>
          {resolvedZone ? <span className="rounded-md bg-[#e6f0ff] px-2 py-1 text-sm font-semibold text-[#246df2]">{resolvedZone.name}</span> : null}
        </div>
        {resolvedZone ? (
          <>
            <div className="mt-5 grid grid-cols-4 gap-3 text-center">
              <InfoMetric label="난 묶음 수" value={`${resolvedZone.orchidGroups.length}개`} />
              <InfoMetric label="총 분 수" value={`${totalQuantity}분`} />
              <InfoMetric label="빈 공간" value={`${Math.max(0, 5 - resolvedZone.orchidGroups.length)}칸`} />
              <InfoMetric label="상태" value="정상" />
            </div>
            <div className="mt-5 border-t border-[#e1e6df] pt-4">
              <p className="font-semibold">최근 작업 요약</p>
              <dl className="mt-3 space-y-2 text-sm text-[#5c6a60]">
                <div className="flex justify-between"><dt>최근 농약</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 비료</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 분갈이</dt><dd>다음 단계에서 연결</dd></div>
              </dl>
            </div>
          </>
        ) : (
          <p className="mt-4 text-[#5c6a60]">구역 또는 난 묶음을 선택하세요.</p>
        )}
      </section>
    </aside>
  );
}

function InfoMetric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-sm text-[#5c6a60]">{label}</p>
      <p className="mt-1 text-lg font-semibold">{value}</p>
    </div>
  );
}

function DisabledAction({ label, primary = false }: { label: string; primary?: boolean }) {
  return (
    <button
      className={`rounded-md px-4 py-3 text-base font-semibold opacity-70 ${
        primary ? "bg-[#159447] text-white" : "border border-[#d7ddd4] bg-white text-[#435047]"
      }`}
      disabled
      type="button"
    >
      {label}
    </button>
  );
}

function MapLegend() {
  return (
    <div className="flex flex-wrap gap-6 rounded-md border border-[#d7ddd4] bg-white p-4 text-base shadow-sm">
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#246df2] bg-[#dcecff] align-middle" />선택 영역</span>
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#82c886] bg-[#bfe2b8] align-middle" />난 묶음 있음</span>
      <span><span className="mr-2 inline-block h-5 w-5 rounded border border-[#d7ddd4] bg-[#f0f1ef] align-middle" />비어 있음</span>
    </div>
  );
}

function findOrchidGroup(house: House, orchidGroupId: number) {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      const orchidGroup = zone.orchidGroups.find((item) => item.id === orchidGroupId);
      if (orchidGroup) {
        return orchidGroup;
      }
    }
  }
  return null;
}

function findBedZone(house: House, bedZoneId: number) {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      if (zone.id === bedZoneId) {
        return { bed, zone };
      }
    }
  }
  return null;
}
