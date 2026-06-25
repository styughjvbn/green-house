"use client";

import { useMemo, useState } from "react";
import type {
  BedZone,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import {
  fetchFarmStatusHouseZoom,
  fetchFarmStatusOrchidGroups,
} from "../api/farmStatusApi";
import { getNextZoomLevel, getPreviousZoomLevel } from "../lib/farmStatusView";
import type { FarmStatusMapProps, SelectedTarget } from "./types";

export function useFarmStatusMap({
  mapData,
  initialSelection,
  initialZoom,
}: FarmStatusMapProps) {
  const initialHouseId =
    initialZoom?.houseId ??
    mapData.houses.find((house) => house.orchidGroupCount > 0)?.houseId ??
    mapData.houses[0]?.houseId ??
    null;

  const [zoomLevel, setZoomLevel] = useState<FarmZoomLevel>("FARM");
  const [selectedHouseId, setSelectedHouseId] = useState<number | null>(
    initialHouseId,
  );
  const [selectedTarget, setSelectedTarget] = useState<SelectedTarget | null>(
    initialSelection
      ? { type: initialSelection.targetType, id: initialSelection.targetId }
      : null,
  );
  const [selection, setSelection] = useState<FarmStatusOrchidGroupList | null>(
    initialSelection,
  );
  const [zoomData, setZoomData] = useState<FarmStatusZoomData | null>(
    initialZoom,
  );
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedHouse = useMemo(
    () =>
      mapData.houses.find((house) => house.houseId === selectedHouseId) ??
      mapData.houses[0] ??
      null,
    [mapData.houses, selectedHouseId],
  );

  const selectedPhysicalBedId =
    selectedTarget?.type === "PHYSICAL_BED" ? selectedTarget.id : null;
  const selectedBedZoneId =
    selectedTarget?.type === "BED_ZONE" ? selectedTarget.id : null;

  async function runRequest(task: () => Promise<void>) {
    setLoading(true);
    setErrorMessage(null);
    try {
      await task();
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function loadSelection(type: FarmStatusTargetType, id: number) {
    const data = await fetchFarmStatusOrchidGroups(type, id);
    setSelectedTarget({ type, id });
    setSelection(data);
  }

  async function loadHouseZoom(
    houseId: number,
    nextLevel: FarmZoomLevel = "HOUSE",
  ) {
    const data = await fetchFarmStatusHouseZoom(houseId);
    setSelectedHouseId(houseId);
    setZoomData(data);
    setZoomLevel(nextLevel);
  }

  async function handleSelectHouse(
    house: HouseStatusSummary,
    nextLevel: FarmZoomLevel = zoomLevel === "FARM" ? "HOUSE" : zoomLevel,
  ) {
    await runRequest(async () => {
      const [selectionData, houseZoomData] = await Promise.all([
        fetchFarmStatusOrchidGroups("HOUSE", house.houseId),
        fetchFarmStatusHouseZoom(house.houseId),
      ]);

      setSelectedTarget({ type: "HOUSE", id: house.houseId });
      setSelection(selectionData);
      setSelectedHouseId(house.houseId);
      setZoomData(houseZoomData);
      setZoomLevel(nextLevel);
    });
  }

  async function handleSelectPhysicalBed(bed: PhysicalBed) {
    setZoomLevel("PHYSICAL_BED");
    await runRequest(() => loadSelection("PHYSICAL_BED", bed.id));
  }

  async function handleSelectBedZone(zone: BedZone) {
    setZoomLevel("BED_ZONE");
    await runRequest(() => loadSelection("BED_ZONE", zone.id));
  }

  async function handleZoomIn() {
    const nextLevel = getNextZoomLevel(zoomLevel);
    if (nextLevel === zoomLevel) {
      return;
    }
    if (nextLevel !== "FARM" && selectedHouseId) {
      await runRequest(() => loadHouseZoom(selectedHouseId, nextLevel));
      return;
    }
    setZoomLevel(nextLevel);
  }

  function handleZoomOut() {
    const nextLevel = getPreviousZoomLevel(zoomLevel);
    setZoomLevel(nextLevel);
    if (nextLevel === "FARM") {
      setSelectedTarget(
        selectedHouseId ? { type: "HOUSE", id: selectedHouseId } : null,
      );
    }
  }

  function resetToFarm() {
    setZoomLevel("FARM");
    if (selectedHouseId) {
      setSelectedTarget({ type: "HOUSE", id: selectedHouseId });
    }
  }

  return {
    errorMessage,
    loading,
    selectedBedZoneId,
    selectedHouse,
    selectedHouseId,
    selectedPhysicalBedId,
    selectedTarget,
    selection,
    zoomData,
    zoomLevel,
    handleSelectBedZone,
    handleSelectHouse,
    handleSelectPhysicalBed,
    handleZoomIn,
    handleZoomOut,
    resetToFarm,
  };
}
