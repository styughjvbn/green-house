"use client";

import { useEffect, useMemo, useState } from "react";
import type {
  BedZone,
  FarmStatusOrchidGroupList,
  FarmStatusTargetType,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
} from "@/entities/farm/types";
import {
  fetchFarmStatusHouseZoom,
  fetchFarmStatusOrchidGroups,
  searchFarmStatusOrchidGroups,
} from "../api/farmStatusApi";
import { getNextZoomLevel, getPreviousZoomLevel } from "../lib/farmStatusView";
import {
  createLatestRequestCoordinator,
  type LatestRequest,
} from "./latestRequestCoordinator";
import type {
  FarmStatusFilterMatches,
  FarmStatusMapProps,
  FarmStatusSearchState,
  SelectedFarmStatusOrchidGroup,
  SelectedTarget,
} from "./types";

const EMPTY_FILTER_MATCHES: FarmStatusFilterMatches = {
  bedZoneIds: new Set<number>(),
  houseIds: new Set<number>(),
  orchidGroupIds: new Set<number>(),
  physicalBedKeys: new Set<string>(),
};

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
  const [selectedOrchidGroup, setSelectedOrchidGroup] =
    useState<SelectedFarmStatusOrchidGroup | null>(null);
  const [zoomData, setZoomData] = useState<FarmStatusZoomData | null>(
    initialZoom,
  );
  const [searchFilters, setSearchFilters] = useState<FarmStatusSearchState>({
    keyword: "",
    status: "",
  });
  const [searchResults, setSearchResults] = useState<OrchidGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestCoordinator] = useState(createLatestRequestCoordinator);

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
  const hasActiveSearch =
    searchFilters.keyword.trim().length > 0 ||
    searchFilters.status.trim().length > 0;
  const filterMatches = useMemo(() => {
    if (!hasActiveSearch) {
      return EMPTY_FILTER_MATCHES;
    }

    return {
      bedZoneIds: new Set(searchResults.map((group) => group.bedZoneId)),
      houseIds: new Set(searchResults.map((group) => group.houseId)),
      orchidGroupIds: new Set(searchResults.map((group) => group.id)),
      physicalBedKeys: new Set(
        searchResults.map(
          (group) => `${group.houseId}:${group.physicalBedNumber}`,
        ),
      ),
    };
  }, [hasActiveSearch, searchResults]);

  useEffect(() => {
    if (!hasActiveSearch) {
      return;
    }

    let ignore = false;
    const timeout = window.setTimeout(() => {
      setSearchLoading(true);
      searchFarmStatusOrchidGroups(searchFilters)
        .then((results) => {
          if (!ignore) {
            setSearchResults(results);
          }
        })
        .catch((error) => {
          if (!ignore) {
            setErrorMessage(
              error instanceof Error
                ? error.message
                : "검색 결과를 불러오지 못했습니다.",
            );
            setSearchResults([]);
          }
        })
        .finally(() => {
          if (!ignore) {
            setSearchLoading(false);
          }
        });
    }, 0);

    return () => {
      ignore = true;
      window.clearTimeout(timeout);
    };
  }, [hasActiveSearch, searchFilters]);

  useEffect(
    () => () => {
      requestCoordinator.cancel();
    },
    [requestCoordinator],
  );

  async function runRequest(task: (request: LatestRequest) => Promise<void>) {
    const request = requestCoordinator.begin();
    setLoading(true);
    setErrorMessage(null);
    try {
      await task(request);
    } catch (error) {
      if (request.isCurrent()) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "요청 중 문제가 발생했습니다.",
        );
      }
    } finally {
      if (requestCoordinator.complete(request)) {
        setLoading(false);
      }
    }
  }

  function cancelRequest() {
    requestCoordinator.cancel();
    setLoading(false);
  }

  async function loadSelectionInHouse({
    type,
    id,
    houseId,
    nextLevel,
    request,
  }: {
    type: FarmStatusTargetType;
    id: number;
    houseId: number;
    nextLevel: FarmZoomLevel;
    request: LatestRequest;
  }) {
    const shouldLoadHouseZoom =
      selectedHouseId !== houseId || zoomData?.houseId !== houseId;
    const [selectionData, houseZoomData] = await Promise.all([
      fetchFarmStatusOrchidGroups(type, id, request.signal),
      shouldLoadHouseZoom
        ? fetchFarmStatusHouseZoom(houseId, request.signal)
        : Promise.resolve(zoomData),
    ]);

    if (!request.isCurrent()) {
      return;
    }

    setSelectedHouseId(houseId);
    setSelectedTarget({ type, id });
    setSelection(selectionData);
    setSelectedOrchidGroup(null);
    setZoomData(houseZoomData);
    setZoomLevel(nextLevel);
  }

  async function loadHouseZoom(
    houseId: number,
    request: LatestRequest,
    nextLevel: FarmZoomLevel = "HOUSE",
  ) {
    const data = await fetchFarmStatusHouseZoom(houseId, request.signal);
    if (!request.isCurrent()) {
      return;
    }
    setSelectedHouseId(houseId);
    setZoomData(data);
    setZoomLevel(nextLevel);
  }

  async function handleSelectHouse(
    house: HouseStatusSummary,
    nextLevel: FarmZoomLevel = zoomLevel === "FARM" ? "HOUSE" : zoomLevel,
  ) {
    await runRequest(async (request) => {
      const [selectionData, houseZoomData] = await Promise.all([
        fetchFarmStatusOrchidGroups("HOUSE", house.houseId, request.signal),
        fetchFarmStatusHouseZoom(house.houseId, request.signal),
      ]);

      if (!request.isCurrent()) {
        return;
      }

      setSelectedTarget({ type: "HOUSE", id: house.houseId });
      setSelection(selectionData);
      setSelectedOrchidGroup(null);
      setSelectedHouseId(house.houseId);
      setZoomData(houseZoomData);
      setZoomLevel(nextLevel);
    });
  }

  async function handleSelectPhysicalBed(bed: PhysicalBed) {
    await runRequest((request) =>
      loadSelectionInHouse({
        type: "PHYSICAL_BED",
        id: bed.id,
        houseId: bed.houseId,
        nextLevel: "PHYSICAL_BED",
        request,
      }),
    );
  }

  async function handleSelectBedZone(zone: BedZone) {
    await runRequest((request) =>
      loadSelectionInHouse({
        type: "BED_ZONE",
        id: zone.id,
        houseId: zone.houseId,
        nextLevel: "BED_ZONE",
        request,
      }),
    );
  }

  async function handleZoomIn() {
    const nextLevel = getNextZoomLevel(zoomLevel);
    if (nextLevel === zoomLevel) {
      return;
    }
    if (nextLevel !== "FARM" && selectedHouseId) {
      if (zoomData?.houseId === selectedHouseId) {
        cancelRequest();
        setZoomLevel(nextLevel);
        return;
      }
      await runRequest((request) =>
        loadHouseZoom(selectedHouseId, request, nextLevel),
      );
      return;
    }
    cancelRequest();
    setZoomLevel(nextLevel);
  }

  function handleZoomOut() {
    cancelRequest();
    const nextLevel = getPreviousZoomLevel(zoomLevel);
    setZoomLevel(nextLevel);
    if (nextLevel === "FARM") {
      setSelectedTarget(
        selectedHouseId ? { type: "HOUSE", id: selectedHouseId } : null,
      );
    }
  }

  function resetToFarm() {
    cancelRequest();
    setZoomLevel("FARM");
    setSelectedOrchidGroup(null);
    if (selectedHouseId) {
      setSelectedTarget({ type: "HOUSE", id: selectedHouseId });
    }
  }

  async function handleSelectOrchidGroup(group: SelectedFarmStatusOrchidGroup) {
    await runRequest(async (request) => {
      const shouldLoadHouseZoom =
        selectedHouseId !== group.houseId ||
        zoomData?.houseId !== group.houseId;
      const [selectionData, houseZoomData] = await Promise.all([
        fetchFarmStatusOrchidGroups(
          "BED_ZONE",
          group.bedZoneId,
          request.signal,
        ),
        shouldLoadHouseZoom
          ? fetchFarmStatusHouseZoom(group.houseId, request.signal)
          : Promise.resolve(zoomData),
      ]);
      if (!request.isCurrent()) {
        return;
      }
      const detailedGroup = findOrchidGroupInZoomData(
        houseZoomData,
        group.orchidGroupId,
      );

      setSelectedOrchidGroup(
        toSelectedFarmStatusOrchidGroup(group, detailedGroup),
      );
      setSelectedTarget({ type: "BED_ZONE", id: group.bedZoneId });
      setSelection(selectionData);
      if (selectedHouseId !== group.houseId) {
        setSelectedHouseId(group.houseId);
      }
      setZoomData(houseZoomData);
    });
  }

  async function handleSelectSearchResult(group: OrchidGroup) {
    await runRequest(async (request) => {
      const [selectionData, houseZoomData] = await Promise.all([
        fetchFarmStatusOrchidGroups(
          "BED_ZONE",
          group.bedZoneId,
          request.signal,
        ),
        fetchFarmStatusHouseZoom(group.houseId, request.signal),
      ]);

      if (!request.isCurrent()) {
        return;
      }

      setSelectedHouseId(group.houseId);
      setSelectedTarget({ type: "BED_ZONE", id: group.bedZoneId });
      setSelection(selectionData);
      setZoomData(houseZoomData);
      setSelectedOrchidGroup(
        toSelectedFarmStatusOrchidGroup(
          {
            orchidGroupId: group.id,
            varietyName: group.varietyName,
            genus: group.genus,
            quantity: group.quantity,
            status: group.status,
            houseId: group.houseId,
            houseNumber: group.houseNumber,
            physicalBedId:
              findPhysicalBedId(
                mapData.houses,
                group.houseId,
                group.physicalBedNumber,
              ) ?? 0,
            physicalBedNumber: group.physicalBedNumber,
            physicalBedName: `${group.physicalBedNumber}배드`,
            bedZoneId: group.bedZoneId,
            bedZoneName: group.bedZoneName,
          },
          group,
        ),
      );
    });
  }

  function updateSearchFilter<K extends keyof FarmStatusSearchState>(
    field: K,
    value: FarmStatusSearchState[K],
  ) {
    setSearchFilters((current) => ({ ...current, [field]: value }));
  }

  function clearSearch() {
    setSearchFilters({ keyword: "", status: "" });
    setSearchResults([]);
  }

  return {
    errorMessage,
    loading,
    selectedBedZoneId,
    selectedHouse,
    selectedHouseId,
    selectedOrchidGroup,
    selectedPhysicalBedId,
    selectedTarget,
    selection,
    filterMatches,
    hasActiveSearch,
    searchFilters,
    searchLoading,
    searchResults,
    zoomData,
    zoomLevel,
    clearSearch,
    handleSelectBedZone,
    handleSelectHouse,
    handleSelectOrchidGroup,
    handleSelectPhysicalBed,
    handleSelectSearchResult,
    handleZoomIn,
    handleZoomOut,
    resetToFarm,
    updateSearchFilter,
  };
}

function findPhysicalBedId(
  houses: HouseStatusSummary[],
  houseId: number,
  physicalBedNumber: number,
) {
  return houses
    .find((house) => house.houseId === houseId)
    ?.physicalBeds.find((bed) => bed.number === physicalBedNumber)?.id;
}

function findOrchidGroupInZoomData(
  zoomData: FarmStatusZoomData | null,
  orchidGroupId: number,
) {
  return (
    zoomData?.physicalBeds
      .flatMap((bed) => bed.bedZones)
      .flatMap((zone) => zone.orchidGroups)
      .find((group) => group.id === orchidGroupId) ?? null
  );
}

function toSelectedFarmStatusOrchidGroup(
  summary: SelectedFarmStatusOrchidGroup,
  detail: OrchidGroup | null,
): SelectedFarmStatusOrchidGroup {
  if (!detail) {
    return summary;
  }

  return {
    ...summary,
    ageYear: detail.ageYear,
    endPosition: detail.endPosition,
    memo: detail.memo,
    placementType: detail.placementType,
    potSize: detail.potSize,
    sortOrder: detail.sortOrder,
    splitPlacementAllowed: detail.splitPlacementAllowed,
    startPosition: detail.startPosition,
    trayCount: detail.trayCount,
    varietyId: detail.varietyId,
  };
}
