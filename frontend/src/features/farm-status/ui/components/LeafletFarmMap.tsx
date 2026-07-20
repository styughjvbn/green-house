"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { MapContainer } from "react-leaflet";
import { canvas, CRS, type Map as LeafletMap } from "leaflet";
import type {
  BedZone,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import type {
  FarmStatusColorMode,
  FarmStatusFilterMatches,
  FarmStatusLayoutMode,
  SelectedFarmStatusOrchidGroup,
  SelectedTarget,
} from "../../model/types";
import { FarmBackgroundLayer } from "./leaflet-farm-map/FarmBackgroundLayer";
import { FarmHouseFeature } from "./leaflet-farm-map/FarmHouseFeature";
import { FarmMapOverlay } from "./leaflet-farm-map/FarmMapOverlay";
import {
  MapInstanceBridge,
  MapZoomBridge,
} from "./leaflet-farm-map/MapBridges";
import {
  FARM_ACTUAL_PAN_BOUNDS,
  FARM_PAN_BOUNDS,
  getLevelByMapZoom,
  INITIAL_ZOOM,
  MAX_ZOOM,
  MIN_ZOOM,
  WHEEL_PX_PER_ZOOM_LEVEL,
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from "./leaflet-farm-map/config";
import {
  findOrchidGroupCenter,
  getScaledHouseLayout,
  toLatLng,
} from "./leaflet-farm-map/geometry";

type LeafletFarmMapProps = {
  detailPanelOpen: boolean;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  houses: HouseStatusSummary[];
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedPhysicalBedId: number | null;
  selectedTarget: SelectedTarget | null;
  zoomData: FarmStatusZoomData | null;
  zoomLevel: FarmZoomLevel;
  onReset: () => void;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectHouse: (house: HouseStatusSummary) => void;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export default function LeafletFarmMap({
  detailPanelOpen,
  filterMatches,
  hasActiveSearch,
  houses,
  selectedBedZoneId,
  selectedHouseId,
  selectedOrchidGroup,
  selectedPhysicalBedId,
  selectedTarget,
  zoomData,
  zoomLevel,
  onReset,
  onSelectBedZone,
  onSelectHouse,
  onSelectOrchidGroup,
  onSelectPhysicalBed,
  onZoomIn,
  onZoomOut,
}: LeafletFarmMapProps) {
  const [map, setMap] = useState<LeafletMap | null>(null);
  const [mapZoom, setMapZoom] = useState(INITIAL_ZOOM);
  const [layoutMode, setLayoutMode] =
    useState<FarmStatusLayoutMode>("NORMALIZED");
  const [colorMode, setColorMode] = useState<FarmStatusColorMode>("STATUS");
  const mapRenderer = useMemo(() => canvas({ padding: 0.5 }), []);
  const currentLevel = getLevelByMapZoom(mapZoom);

  const houseByNumber = useMemo(() => {
    const items = new Map<number, HouseStatusSummary>();
    houses.forEach((house) => items.set(house.houseNumber, house));
    return items;
  }, [houses]);
  const scaledHouseLayout = useMemo(
    () => getScaledHouseLayout(houses, layoutMode),
    [houses, layoutMode],
  );

  const resetMap = useCallback(() => {
    map?.setView(
      toLatLng({ x: WORLD_WIDTH / 2, y: WORLD_HEIGHT / 2 }),
      INITIAL_ZOOM,
      {
        animate: true,
      },
    );
    onReset();
  }, [map, onReset]);

  useEffect(() => {
    if (
      !map ||
      (!selectedOrchidGroup && (!selectedHouseId || !detailPanelOpen))
    ) {
      return;
    }

    const center = selectedOrchidGroup
      ? findOrchidGroupCenter(
          selectedOrchidGroup.orchidGroupId,
          scaledHouseLayout,
          houseByNumber,
          zoomData,
        )
      : (() => {
          const selectedHouse = houses.find(
            (house) => house.houseId === selectedHouseId,
          );
          const geometry = scaledHouseLayout.find(
            (item) => item.houseNumber === selectedHouse?.houseNumber,
          );
          return geometry
            ? {
                x: geometry.x + geometry.width / 2,
                y: geometry.y + geometry.height / 2,
              }
            : null;
        })();
    if (!center) {
      return;
    }

    const selectedCenter = toLatLng(center);
    const targetCenter = detailPanelOpen
      ? map.unproject(
          map.project(selectedCenter, map.getZoom()).add([180, 0]),
          map.getZoom(),
        )
      : selectedCenter;
    map.panTo(targetCenter, { animate: true, duration: 0.35 });
  }, [
    detailPanelOpen,
    houseByNumber,
    houses,
    map,
    scaledHouseLayout,
    selectedHouseId,
    selectedOrchidGroup,
    zoomData,
  ]);

  return (
    <div className="relative h-full min-h-0 overflow-hidden border border-[#cdd9c8] bg-[#95b969] shadow-sm">
      <MapContainer
        attributionControl={false}
        center={toLatLng({ x: WORLD_WIDTH / 2, y: WORLD_HEIGHT / 2 })}
        className="farm-leaflet-map h-full w-full bg-[#a6c77a]"
        crs={CRS.Simple}
        doubleClickZoom={false}
        maxBounds={
          layoutMode === "ACTUAL" ? FARM_ACTUAL_PAN_BOUNDS : FARM_PAN_BOUNDS
        }
        maxBoundsViscosity={0.65}
        maxZoom={MAX_ZOOM}
        minZoom={MIN_ZOOM}
        renderer={mapRenderer}
        scrollWheelZoom
        wheelPxPerZoomLevel={WHEEL_PX_PER_ZOOM_LEVEL}
        zoom={INITIAL_ZOOM}
        zoomControl={false}
        zoomDelta={0.35}
        zoomSnap={0.05}
      >
        <MapInstanceBridge onReady={setMap} />
        <MapZoomBridge
          externalZoomLevel={zoomLevel}
          onMapZoomChange={setMapZoom}
          onZoomIn={onZoomIn}
          onZoomOut={onZoomOut}
        />
        <FarmBackgroundLayer />
        {scaledHouseLayout.map((geometry) => {
          const house = houseByNumber.get(geometry.houseNumber);
          if (!house) return null;

          return (
            <FarmHouseFeature
              key={house.houseId}
              geometry={geometry}
              filterMatches={filterMatches}
              hasActiveSearch={hasActiveSearch}
              house={house}
              layoutMode={layoutMode}
              mapZoom={mapZoom}
              colorMode={colorMode}
              selectedBedZoneId={selectedBedZoneId}
              selectedHouseId={selectedHouseId}
              selectedOrchidGroup={selectedOrchidGroup}
              selectedPhysicalBedId={selectedPhysicalBedId}
              selectedTarget={selectedTarget}
              zoomData={zoomData}
              onSelectBedZone={onSelectBedZone}
              onSelectHouse={onSelectHouse}
              onSelectOrchidGroup={onSelectOrchidGroup}
              onSelectPhysicalBed={onSelectPhysicalBed}
            />
          );
        })}
      </MapContainer>

      <FarmMapOverlay
        currentLevel={currentLevel}
        colorMode={colorMode}
        houses={houses}
        layoutMode={layoutMode}
        map={map}
        mapZoom={mapZoom}
        resetMap={resetMap}
        onColorModeChange={setColorMode}
        onLayoutModeChange={(mode) => {
          setLayoutMode(mode);
          resetMap();
        }}
      />
    </div>
  );
}
