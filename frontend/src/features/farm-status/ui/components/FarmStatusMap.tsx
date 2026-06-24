"use client";

import { useMemo, useState } from "react";
import { API_BASE_URL } from "@/shared/api/client";
import type {
  BedZone,
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import type { SelectedTarget } from "../../model/types";
import { FarmMapCanvas } from "./FarmMapCanvas";
import { OrchidGroupTable, RecentWorkSummary, SelectionSummaryPanel } from "./StatusPanels";

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
