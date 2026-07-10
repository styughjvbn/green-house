"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  CircleMarker,
  MapContainer,
  Marker,
  Polygon,
  Rectangle,
  useMap,
  useMapEvents,
} from "react-leaflet";
import {
  CRS,
  divIcon,
  type LatLngBoundsExpression,
  type LatLngExpression,
  type LeafletMouseEvent,
  type Map as LeafletMap,
} from "leaflet";
import type {
  BedZone,
  FarmStatusZoomData,
  FarmZoomLevel,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
} from "@/entities/farm/types";
import type {
  FarmStatusFilterMatches,
  SelectedFarmStatusOrchidGroup,
  SelectedTarget,
} from "../../model/types";
import { hasHouseWarning } from "../../lib/farmStatusView";
import { zoomLabel } from "../../lib/farmStatusView";

type LeafletFarmMapProps = {
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

type Rect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

type FarmHouseGeometry = Rect & {
  houseNumber: number;
};

type VisualBedGeometry = Rect & {
  number: number;
};

type VisualZoneGeometry = Rect & {
  side: "LEFT" | "RIGHT" | string;
};

type DetailSource = {
  isReal: boolean;
  physicalBeds: PhysicalBed[];
};

const WORLD_WIDTH = 1900;
const WORLD_HEIGHT = 900;
const INITIAL_ZOOM = -0.7;
const MIN_ZOOM = -0.7;
const MAX_ZOOM = 3.15;

const HOUSE_WIDTH = 124;

const BED_VISIBLE_ZOOM = 0.7;
const ZONE_VISIBLE_ZOOM = 1.55;
const ORCHID_VISIBLE_ZOOM = 2.35;

const FARM_BOUNDS: LatLngBoundsExpression = [
  [0, 0],
  [WORLD_HEIGHT, WORLD_WIDTH],
];

const LEVEL_RANK: Record<FarmZoomLevel, number> = {
  FARM: 0,
  HOUSE: 1,
  PHYSICAL_BED: 2,
  BED_ZONE: 3,
};

export default function LeafletFarmMap({
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
  const currentLevel = getLevelByMapZoom(mapZoom);

  const houseByNumber = useMemo(() => {
    const items = new Map<number, HouseStatusSummary>();
    houses.forEach((house) => items.set(house.houseNumber, house));
    return items;
  }, [houses]);
  const scaledHouseLayout = useMemo(
    () => getScaledHouseLayout(houses),
    [houses],
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
    if (!map || !selectedOrchidGroup) {
      return;
    }

    const center = findOrchidGroupCenter(
      selectedOrchidGroup.orchidGroupId,
      scaledHouseLayout,
      houseByNumber,
      zoomData,
    );
    if (!center) {
      return;
    }

    map.panTo(toLatLng(center), { animate: true, duration: 0.35 });
  }, [houseByNumber, map, scaledHouseLayout, selectedOrchidGroup, zoomData]);

  return (
    <div className="relative min-h-0 flex-1 overflow-hidden rounded-xl border border-[#cdd9c8] bg-[#95b969] shadow-sm">
      <MapContainer
        attributionControl={false}
        center={toLatLng({ x: WORLD_WIDTH / 2, y: WORLD_HEIGHT / 2 })}
        className="farm-leaflet-map h-full w-full bg-[#a6c77a]"
        crs={CRS.Simple}
        doubleClickZoom={false}
        maxBounds={FARM_BOUNDS}
        maxBoundsViscosity={0.85}
        maxZoom={MAX_ZOOM}
        minZoom={MIN_ZOOM}
        scrollWheelZoom
        wheelPxPerZoomLevel={95}
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
              mapZoom={mapZoom}
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
        map={map}
        mapZoom={mapZoom}
        resetMap={resetMap}
      />
    </div>
  );
}

function MapInstanceBridge({
  onReady,
}: {
  onReady: (map: LeafletMap) => void;
}) {
  const map = useMap();

  useEffect(() => {
    onReady(map);
  }, [map, onReady]);

  return null;
}

function MapZoomBridge({
  externalZoomLevel,
  onMapZoomChange,
  onZoomIn,
  onZoomOut,
}: {
  externalZoomLevel: FarmZoomLevel;
  onMapZoomChange: (zoom: number) => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}) {
  const lastSyncedLevelRef = useRef<FarmZoomLevel>(externalZoomLevel);

  useEffect(() => {
    lastSyncedLevelRef.current = externalZoomLevel;
  }, [externalZoomLevel]);

  useMapEvents({
    zoomend(event) {
      const zoom = event.target.getZoom();
      const nextLevel = getLevelByMapZoom(zoom);
      const previousLevel = lastSyncedLevelRef.current;

      onMapZoomChange(zoom);

      if (LEVEL_RANK[nextLevel] > LEVEL_RANK[previousLevel]) {
        onZoomIn();
      } else if (LEVEL_RANK[nextLevel] < LEVEL_RANK[previousLevel]) {
        onZoomOut();
      }

      lastSyncedLevelRef.current = nextLevel;
    },
  });

  return null;
}

function FarmMapOverlay({
  currentLevel,
  map,
  mapZoom,
  resetMap,
}: {
  currentLevel: FarmZoomLevel;
  map: LeafletMap | null;
  mapZoom: number;
  resetMap: () => void;
}) {
  const zoomIn = () => map?.setZoom(Math.min(MAX_ZOOM, map.getZoom() + 0.45));
  const zoomOut = () => map?.setZoom(Math.max(MIN_ZOOM, map.getZoom() - 0.45));

  return (
    <>
      <div className="pointer-events-none absolute top-4 left-4 z-[1000] flex flex-wrap items-center gap-2 rounded-md bg-white/95 px-3 py-2 text-sm font-semibold text-[#29422e] shadow-sm">
        <span>전체 농장 지도</span>
        <span className="rounded-full bg-[#eef6e9] px-2 py-0.5 text-xs text-[#39713d]">
          {zoomLabel(currentLevel)}
        </span>
        <span className="rounded-full bg-[#f3f6f1] px-2 py-0.5 text-xs text-[#63726a]">
          scale {mapZoom.toFixed(2)}
        </span>
      </div>

      <div className="absolute bottom-16 left-4 z-[1000] flex flex-col gap-2">
        <button
          aria-label="확대"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={zoomIn}
          type="button"
        >
          +
        </button>
        <button
          aria-label="축소"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-xl font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={zoomOut}
          type="button"
        >
          -
        </button>
        <button
          aria-label="전체 보기"
          className="flex h-9 w-9 items-center justify-center rounded-md bg-white text-sm font-semibold text-[#2b3a2f] shadow transition hover:bg-[#f4f7f2]"
          onClick={resetMap}
          type="button"
        >
          ⌂
        </button>
      </div>

      <div className="pointer-events-none absolute bottom-4 left-16 z-[1000] flex flex-wrap gap-3 rounded-md bg-white/95 px-3 py-2 text-xs shadow">
        <LegendItem color="bg-[#20a64d]" label="정상" />
        <LegendItem color="bg-[#f59e0b]" label="주의" />
        <LegendItem color="bg-[#ef4444]" label="이상" />
        <LegendItem color="bg-[#1976f3]" label="선택" />
      </div>
    </>
  );
}

function FarmBackgroundLayer() {
  return (
    <>
      <Rectangle
        bounds={FARM_BOUNDS}
        interactive={false}
        pathOptions={{ fillColor: "#9fbe72", fillOpacity: 1, opacity: 0 }}
      />
      <Polygon
        interactive={false}
        pathOptions={{
          color: "#d8aa5f",
          fillColor: "#d8aa5f",
          fillOpacity: 1,
          opacity: 0,
        }}
        positions={toPolyline([
          { x: 0, y: 260 },
          { x: 510, y: 130 },
          { x: 1300, y: 130 },
          { x: WORLD_WIDTH, y: 250 },
          { x: WORLD_WIDTH, y: 310 },
          { x: 1300, y: 190 },
          { x: 510, y: 190 },
          { x: 0, y: 320 },
        ])}
      />
      <Polygon
        interactive={false}
        pathOptions={{
          color: "#f0d59a",
          fillColor: "#f0d59a",
          fillOpacity: 1,
          opacity: 0,
        }}
        positions={toPolyline([
          { x: 0, y: 292 },
          { x: 520, y: 160 },
          { x: 1300, y: 160 },
          { x: WORLD_WIDTH, y: 280 },
          { x: WORLD_WIDTH, y: 302 },
          { x: 1300, y: 182 },
          { x: 520, y: 182 },
          { x: 0, y: 314 },
        ])}
      />
      <Rectangle
        bounds={boundsOf({ x: 80, y: 50, width: 260, height: 150 })}
        interactive={false}
        pathOptions={{
          color: "#d2d8d1",
          fillColor: "#ecefe9",
          fillOpacity: 1,
          opacity: 1,
          weight: 1,
        }}
      />
      <TreeCluster x={1500} y={70} />
      <TreeCluster x={1650} y={110} />
      <TreeCluster x={170} y={820} />
      <TreeCluster x={1770} y={800} />
    </>
  );
}

function FarmHouseFeature({
  geometry,
  filterMatches,
  hasActiveSearch,
  house,
  mapZoom,
  selectedBedZoneId,
  selectedHouseId,
  selectedOrchidGroup,
  selectedPhysicalBedId,
  selectedTarget,
  zoomData,
  onSelectBedZone,
  onSelectHouse,
  onSelectOrchidGroup,
  onSelectPhysicalBed,
}: {
  geometry: FarmHouseGeometry;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  house: HouseStatusSummary;
  mapZoom: number;
  selectedBedZoneId: number | null;
  selectedHouseId: number | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedPhysicalBedId: number | null;
  selectedTarget: SelectedTarget | null;
  zoomData: FarmStatusZoomData | null;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectHouse: (house: HouseStatusSummary) => void;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
  onSelectPhysicalBed: (bed: PhysicalBed) => void;
}) {
  const selected =
    selectedHouseId === house.houseId ||
    (selectedTarget?.type === "HOUSE" && selectedTarget.id === house.houseId);
  const warning = hasHouseWarning(house);
  const matched = !hasActiveSearch || filterMatches.houseIds.has(house.houseId);
  const mutedOpacity = matched ? 1 : 0.34;
  const showBeds = mapZoom >= BED_VISIBLE_ZOOM;
  const showZones = mapZoom >= ZONE_VISIBLE_ZOOM;
  const showOrchids = mapZoom >= ORCHID_VISIBLE_ZOOM;
  const detailSource = getHouseDetailSource(house, zoomData);
  const bedGeometries = getBedGeometries(geometry, detailSource.physicalBeds);

  return (
    <>
      <Rectangle
        bounds={boundsOf(geometry)}
        eventHandlers={{
          click: (event) => {
            stopLeafletClick(event);
            onSelectHouse(house);
          },
        }}
        pathOptions={{
          color: matched
            ? selected
              ? "#1d6ff2"
              : warning
                ? "#f59e0b"
                : "#6e9169"
            : "#9aa19a",
          fillColor: matched ? (selected ? "#dcecff" : "#f7f8f5") : "#e4e6e3",
          fillOpacity: matched ? (selected ? 0.94 : 0.88) : 0.72,
          opacity: mutedOpacity,
          weight: selected ? 5 : 2,
        }}
      />

      <Marker
        icon={createLabelIcon({
          background: selected ? "#246df2" : warning ? "#b76600" : "#155c30",
          text: String(house.houseNumber) + "동",
          width: 54,
        })}
        interactive={false}
        keyboard={false}
        position={toLatLng({
          x: geometry.x + geometry.width / 2,
          y: geometry.y - 8,
        })}
      />

      {!showBeds ? (
        <Marker
          icon={createBottomMetricIcon({
            status: warning ? "주의" : "정상",
            text: String(house.orchidGroupCount) + "묶음",
            warning,
          })}
          interactive={false}
          keyboard={false}
          position={toLatLng({
            x: geometry.x + geometry.width / 2,
            y: geometry.y + geometry.height + 18,
          })}
        />
      ) : null}

      {showBeds
        ? bedGeometries.map(({ bed, rect }) => (
            <FarmBedFeature
              key={`${house.houseId}-${bed.number}`}
              bed={bed}
              detailSource={detailSource}
              filterMatches={filterMatches}
              hasActiveSearch={hasActiveSearch}
              rect={rect}
              selectedBedZoneId={selectedBedZoneId}
              selectedOrchidGroup={selectedOrchidGroup}
              selectedPhysicalBedId={selectedPhysicalBedId}
              showOrchids={showOrchids}
              showZones={showZones}
              onSelectBed={(selectedBed) => {
                if (detailSource.isReal) {
                  onSelectPhysicalBed(selectedBed);
                  return;
                }

                onSelectHouse(house);
              }}
              onSelectBedZone={onSelectBedZone}
              onSelectOrchidGroup={onSelectOrchidGroup}
            />
          ))
        : null}
    </>
  );
}

function FarmBedFeature({
  bed,
  detailSource,
  filterMatches,
  hasActiveSearch,
  rect,
  selectedBedZoneId,
  selectedOrchidGroup,
  selectedPhysicalBedId,
  showOrchids,
  showZones,
  onSelectBed,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  detailSource: DetailSource;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  rect: VisualBedGeometry;
  selectedBedZoneId: number | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedPhysicalBedId: number | null;
  showOrchids: boolean;
  showZones: boolean;
  onSelectBed: (bed: PhysicalBed) => void;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
}) {
  const selected =
    selectedPhysicalBedId === bed.id ||
    bed.bedZones.some((zone) => zone.id === selectedBedZoneId);
  const matched =
    !hasActiveSearch ||
    filterMatches.physicalBedKeys.has(`${bed.houseId}:${bed.number}`);
  const mutedOpacity = matched ? 1 : 0.34;
  const zoneGeometries = getZoneGeometries(rect, bed.bedZones);
  const groupCount = bed.bedZones.reduce(
    (sum, zone) => sum + zone.orchidGroups.length,
    0,
  );

  return (
    <>
      <Rectangle
        bounds={boundsOf(rect)}
        eventHandlers={{
          click: (event) => {
            stopLeafletClick(event);
            onSelectBed(bed);
          },
        }}
        pathOptions={{
          color: matched ? (selected ? "#1976f3" : "#9fb49a") : "#9aa19a",
          fillColor: matched ? (selected ? "#e7f0ff" : "#dbe9d1") : "#e4e6e3",
          fillOpacity: matched ? 0.96 : 0.72,
          opacity: mutedOpacity,
          weight: selected ? 4 : 2,
        }}
      />
      <Marker
        icon={createSmallLabelIcon({
          background: selected ? "#246df2" : "#28713f",
          text: String(bed.number) + "다이",
          width: 48,
        })}
        interactive={false}
        keyboard={false}
        position={toLatLng({ x: rect.x + rect.width / 2, y: rect.y - 7 })}
      />
      {!showZones ? (
        <Marker
          icon={createTinyBadgeIcon(String(groupCount) + "묶음")}
          interactive={false}
          keyboard={false}
          position={toLatLng({
            x: rect.x + rect.width / 2,
            y: rect.y + rect.height + 11,
          })}
        />
      ) : null}

      {showZones
        ? zoneGeometries.map(({ zone, zoneRect }) => (
            <FarmZoneFeature
              key={`${bed.id}-${zone.side}-${zone.id}`}
              detailSource={detailSource}
              filterMatches={filterMatches}
              hasActiveSearch={hasActiveSearch}
              selectedOrchidGroupId={selectedOrchidGroup?.orchidGroupId ?? null}
              selected={selectedBedZoneId === zone.id}
              showOrchids={showOrchids}
              zone={zone}
              zoneRect={zoneRect}
              onSelectBedZone={onSelectBedZone}
              onSelectOrchidGroup={onSelectOrchidGroup}
            />
          ))
        : null}
    </>
  );
}

function FarmZoneFeature({
  detailSource,
  filterMatches,
  hasActiveSearch,
  selectedOrchidGroupId,
  selected,
  showOrchids,
  zone,
  zoneRect,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  detailSource: DetailSource;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  selectedOrchidGroupId: number | null;
  selected: boolean;
  showOrchids: boolean;
  zone: BedZone;
  zoneRect: VisualZoneGeometry;
  onSelectBedZone: (zone: BedZone) => void;
  onSelectOrchidGroup: (group: SelectedFarmStatusOrchidGroup) => void;
}) {
  const hasGroups = zone.orchidGroups.length > 0;
  const matched = !hasActiveSearch || filterMatches.bedZoneIds.has(zone.id);
  const mutedOpacity = matched ? 1 : 0.34;
  const sideLabel =
    zone.side === "LEFT" ? "좌" : zone.side === "RIGHT" ? "우" : zone.side;

  return (
    <>
      <Rectangle
        bounds={boundsOf(zoneRect)}
        eventHandlers={{
          click: (event) => {
            stopLeafletClick(event);
            if (detailSource.isReal) {
              onSelectBedZone(zone);
            }
          },
        }}
        pathOptions={{
          color: matched
            ? selected
              ? "#1976f3"
              : hasGroups
                ? "#2e9d4d"
                : "#8fa88b"
            : "#9aa19a",
          fillColor: matched
            ? selected
              ? "#dbeafe"
              : hasGroups
                ? "#cde6c4"
                : "#eef2ea"
            : "#e4e6e3",
          fillOpacity: matched ? 0.98 : 0.72,
          opacity: mutedOpacity,
          weight: selected ? 4 : 2,
        }}
      />
      <Marker
        icon={createZoneLabelIcon({
          selected,
          text: `${sideLabel} ${zone.orchidGroups.length}`,
        })}
        interactive={false}
        keyboard={false}
        position={toLatLng({
          x: zoneRect.x + zoneRect.width / 2,
          y: zoneRect.y + 16,
        })}
      />
      {showOrchids && hasGroups
        ? getOrchidBlockGeometries(zoneRect, zone).map(({ group, rect }) => {
            const selectedGroup = selectedOrchidGroupId === group.id;
            const matchedGroup =
              !hasActiveSearch || filterMatches.orchidGroupIds.has(group.id);

            return (
              <Rectangle
                key={group.id}
                bounds={boundsOf(rect)}
                eventHandlers={{
                  click: (event) => {
                    stopLeafletClick(event);
                    onSelectOrchidGroup(toFarmStatusItem(group, zone));
                  },
                }}
                pathOptions={{
                  color: matchedGroup
                    ? selectedGroup
                      ? "#1d6ff2"
                      : "#17813a"
                    : "#9aa19a",
                  fillColor: matchedGroup
                    ? selectedGroup
                      ? "#dbeafe"
                      : "#2e9d4d"
                    : "#bfc5bf",
                  fillOpacity: matchedGroup
                    ? selectedGroup
                      ? 0.96
                      : 0.82
                    : 0.68,
                  opacity: matchedGroup ? 1 : 0.38,
                  weight: selectedGroup ? 3 : 1.5,
                }}
              />
            );
          })
        : null}
      {showOrchids && hasGroups
        ? getOrchidBlockGeometries(zoneRect, zone).map(({ group, rect }) => (
            <Marker
              key={`${group.id}-label`}
              icon={createOrchidBlockIcon(
                group,
                !hasActiveSearch || filterMatches.orchidGroupIds.has(group.id),
              )}
              interactive={false}
              keyboard={false}
              position={toLatLng({
                x: rect.x + rect.width / 2,
                y: rect.y + rect.height / 2,
              })}
            />
          ))
        : null}
    </>
  );
}

function getOrchidBlockGeometries(zoneRect: VisualZoneGeometry, zone: BedZone) {
  const groups = [...zone.orchidGroups].sort((a, b) => {
    const aStart = a.startPosition ?? a.sortOrder;
    const bStart = b.startPosition ?? b.sortOrder;
    return aStart - bStart;
  });
  const gap = 3;
  const top = zoneRect.y + 34;
  const availableHeight = zoneRect.height - 44;
  const blockHeight = Math.max(
    18,
    (availableHeight - gap * Math.max(0, groups.length - 1)) /
      Math.max(1, groups.length),
  );

  return groups.map((group, index) => ({
    group,
    rect: {
      x: zoneRect.x + 2,
      y: top + index * (blockHeight + gap),
      width: zoneRect.width - 4,
      height: Math.min(blockHeight, availableHeight),
    },
  }));
}

function toFarmStatusItem(
  group: OrchidGroup,
  zone: BedZone,
): SelectedFarmStatusOrchidGroup {
  return {
    ageYear: group.ageYear,
    orchidGroupId: group.id,
    varietyName: group.varietyName,
    genus: group.genus,
    endPosition: group.endPosition,
    memo: group.memo,
    placementType: group.placementType,
    potSize: group.potSize,
    quantity: group.quantity,
    sortOrder: group.sortOrder,
    splitPlacementAllowed: group.splitPlacementAllowed,
    startPosition: group.startPosition,
    status: group.status,
    trayCount: group.trayCount,
    varietyId: group.varietyId,
    houseId: group.houseId,
    houseNumber: group.houseNumber,
    physicalBedId: zone.physicalBedId,
    physicalBedNumber: group.physicalBedNumber,
    physicalBedName: `${group.physicalBedNumber}다이`,
    bedZoneId: zone.id,
    bedZoneName: zone.name,
  };
}

function TreeCluster({ x, y }: { x: number; y: number }) {
  return (
    <>
      <CircleMarker
        center={toLatLng({ x, y })}
        interactive={false}
        pathOptions={{
          color: "#6b9f45",
          fillColor: "#6b9f45",
          fillOpacity: 0.82,
          opacity: 0,
        }}
        radius={16}
      />
      <CircleMarker
        center={toLatLng({ x: x + 34, y: y + 14 })}
        interactive={false}
        pathOptions={{
          color: "#4f8538",
          fillColor: "#4f8538",
          fillOpacity: 0.82,
          opacity: 0,
        }}
        radius={13}
      />
      <CircleMarker
        center={toLatLng({ x: x + 58, y: y - 6 })}
        interactive={false}
        pathOptions={{
          color: "#7aaa4f",
          fillColor: "#7aaa4f",
          fillOpacity: 0.82,
          opacity: 0,
        }}
        radius={15}
      />
    </>
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

function getHouseDetailSource(
  house: HouseStatusSummary,
  zoomData: FarmStatusZoomData | null,
): DetailSource {
  const houseWithOptionalBeds = house as HouseStatusSummary & {
    physicalBeds?: PhysicalBed[];
  };

  if (Array.isArray(houseWithOptionalBeds.physicalBeds)) {
    return {
      isReal: true,
      physicalBeds: sortPhysicalBeds(houseWithOptionalBeds.physicalBeds),
    };
  }

  if (zoomData?.houseId === house.houseId) {
    return {
      isReal: true,
      physicalBeds: sortPhysicalBeds(zoomData.physicalBeds),
    };
  }

  return {
    isReal: false,
    physicalBeds: createVisualOnlyBeds(house.houseId, house.houseNumber),
  };
}

function findOrchidGroupCenter(
  orchidGroupId: number,
  houseLayout: FarmHouseGeometry[],
  houseByNumber: Map<number, HouseStatusSummary>,
  zoomData: FarmStatusZoomData | null,
) {
  for (const geometry of houseLayout) {
    const house = houseByNumber.get(geometry.houseNumber);
    if (!house) continue;

    const detailSource = getHouseDetailSource(house, zoomData);
    const bedGeometries = getBedGeometries(geometry, detailSource.physicalBeds);

    for (const { bed, rect } of bedGeometries) {
      const zoneGeometries = getZoneGeometries(rect, bed.bedZones);

      for (const { zone, zoneRect } of zoneGeometries) {
        const orchidGeometry = getOrchidBlockGeometries(zoneRect, zone).find(
          ({ group }) => group.id === orchidGroupId,
        );

        if (orchidGeometry) {
          return {
            x: orchidGeometry.rect.x + orchidGeometry.rect.width / 2,
            y: orchidGeometry.rect.y + orchidGeometry.rect.height / 2,
          };
        }
      }
    }
  }

  return null;
}

function getScaledHouseLayout(
  houses: HouseStatusSummary[],
): FarmHouseGeometry[] {
  const orderedHouses = [...houses].sort(
    (a, b) => b.houseNumber - a.houseNumber,
  );
  const lengthByHouse = new Map<number, number>();

  orderedHouses.forEach((house) => {
    lengthByHouse.set(house.houseNumber, getMaxPhysicalBedLength(house));
  });

  const values = [...lengthByHouse.values()];
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const minHeight = 360;
  const maxHeight = 650;
  const houseBottom = 810;

  const horizontalGap = 2;
  const totalWidth =
    orderedHouses.length * HOUSE_WIDTH +
    Math.max(0, orderedHouses.length - 1) * horizontalGap;
  const startX = (WORLD_WIDTH - totalWidth) / 2;

  return orderedHouses.map((house, index) => {
    const value = lengthByHouse.get(house.houseNumber) ?? minValue;
    const ratio =
      maxValue === minValue ? 0.5 : (value - minValue) / (maxValue - minValue);
    const height = Math.round(minHeight + ratio * (maxHeight - minHeight));

    return {
      houseNumber: house.houseNumber,
      x: startX + index * (HOUSE_WIDTH + horizontalGap),
      width: HOUSE_WIDTH,
      y: houseBottom - height,
      height,
    };
  });
}

function getMaxPhysicalBedLength(house: HouseStatusSummary | undefined) {
  const values =
    house?.physicalBeds
      .map((bed) => Number(bed.positionUnitCount ?? bed.lengthCm ?? 0))
      .filter((value) => Number.isFinite(value) && value > 0) ?? [];

  return Math.max(1, ...values);
}

function sortPhysicalBeds(beds: PhysicalBed[]) {
  return [...beds].sort((a, b) => {
    const aOrder =
      (a as PhysicalBed & { displayOrder?: number }).displayOrder ?? a.number;
    const bOrder =
      (b as PhysicalBed & { displayOrder?: number }).displayOrder ?? b.number;
    return aOrder - bOrder;
  });
}

function createVisualOnlyBeds(
  houseId: number,
  houseNumber: number,
): PhysicalBed[] {
  return [1, 2, 3].map((bedNumber) => ({
    id: -1 * (houseId * 10 + bedNumber),
    houseId,
    houseNumber,
    number: bedNumber,
    displayOrder: bedNumber,
    lengthCm: null,
    widthCm: null,
    wireCount: null,
    supportIntervalCm: null,
    positionUnitCount: bedNumber === 3 ? 28 : 24,
    positionUnitLabel: "칸",
    memo: null,
    bedZones: [
      {
        id: -1 * (houseId * 100 + bedNumber * 10 + 1),
        physicalBedId: -1 * (houseId * 10 + bedNumber),
        physicalBedNumber: bedNumber,
        houseId,
        houseNumber,
        name: String(bedNumber) + "다이 좌",
        side: "LEFT",
        zoneType: "DEFAULT",
        sortOrder: 1,
        active: true,
        memo: null,
        orchidGroups: [],
      },
      {
        id: -1 * (houseId * 100 + bedNumber * 10 + 2),
        physicalBedId: -1 * (houseId * 10 + bedNumber),
        physicalBedNumber: bedNumber,
        houseId,
        houseNumber,
        name: String(bedNumber) + "다이 우",
        side: "RIGHT",
        zoneType: "DEFAULT",
        sortOrder: 2,
        active: true,
        memo: null,
        orchidGroups: [],
      },
    ],
  }));
}

function getBedGeometries(
  houseRect: FarmHouseGeometry,
  beds: PhysicalBed[],
): Array<{ bed: PhysicalBed; rect: VisualBedGeometry }> {
  const paddingX = 3;
  const topPadding = 34;
  const bottomPadding = 24;
  const gap = 3;
  const bedWidth = (houseRect.width - paddingX * 2 - gap * 2) / 3;
  const bedHeight = houseRect.height - topPadding - bottomPadding;

  return beds.slice(0, 3).map((bed, index) => ({
    bed,
    rect: {
      number: bed.number,
      x: houseRect.x + paddingX + (2 - index) * (bedWidth + gap),
      y: houseRect.y + topPadding,
      width: bedWidth,
      height: bedHeight,
    },
  }));
}

function getZoneGeometries(
  bedRect: VisualBedGeometry,
  zones: BedZone[],
): Array<{ zone: BedZone; zoneRect: VisualZoneGeometry }> {
  const gap = 2;
  const zoneWidth = (bedRect.width - gap) / 2;
  const sortedZones = [...zones].sort((a, b) => {
    const sideOrder = (side: string) =>
      side === "RIGHT" ? 0 : side === "LEFT" ? 1 : 2;
    return sideOrder(a.side) - sideOrder(b.side);
  });

  return sortedZones.slice(0, 2).map((zone, index) => ({
    zone,
    zoneRect: {
      side: zone.side,
      x: bedRect.x + index * (zoneWidth + gap),
      y: bedRect.y + 8,
      width: zoneWidth,
      height: bedRect.height - 16,
    },
  }));
}

function getLevelByMapZoom(zoom: number): FarmZoomLevel {
  if (zoom < BED_VISIBLE_ZOOM) return "FARM";
  if (zoom < ZONE_VISIBLE_ZOOM) return "HOUSE";
  if (zoom < ORCHID_VISIBLE_ZOOM) return "PHYSICAL_BED";
  return "BED_ZONE";
}

function boundsOf(rect: Rect): LatLngBoundsExpression {
  const rotated = rotateRect(rect);
  const top = WORLD_HEIGHT - rotated.y;
  const bottom = WORLD_HEIGHT - rotated.y - rotated.height;

  return [
    [bottom, rotated.x],
    [top, rotated.x + rotated.width],
  ];
}

function toLatLng(point: { x: number; y: number }): LatLngExpression {
  const rotated = rotatePoint(point);
  return [WORLD_HEIGHT - rotated.y, rotated.x];
}

function toPolyline(
  points: Array<{ x: number; y: number }>,
): LatLngExpression[] {
  return points.map(toLatLng);
}

function rotatePoint(point: { x: number; y: number }) {
  return {
    x: WORLD_WIDTH - point.x,
    y: WORLD_HEIGHT - point.y,
  };
}

function rotateRect(rect: Rect): Rect {
  return {
    x: WORLD_WIDTH - rect.x - rect.width,
    y: WORLD_HEIGHT - rect.y - rect.height,
    width: rect.width,
    height: rect.height,
  };
}

function stopLeafletClick(event: LeafletMouseEvent) {
  event.originalEvent.preventDefault();
  event.originalEvent.stopPropagation();
}

function createLabelIcon({
  background,
  text,
  width,
}: {
  background: string;
  text: string;
  width: number;
}) {
  return divIcon({
    className: "",
    html: `<div style="width:${width}px;border-radius:8px;background:${background};color:white;font-size:15px;font-weight:800;line-height:24px;text-align:center;box-shadow:0 2px 6px rgba(20,50,20,.25);">${escapeHtml(text)}</div>`,
    iconAnchor: [width / 2, 12],
    iconSize: [width, 24],
  });
}

function createSmallLabelIcon({
  background,
  text,
  width,
}: {
  background: string;
  text: string;
  width: number;
}) {
  return divIcon({
    className: "",
    html: `<div style="width:${width}px;border-radius:6px;background:${background};color:white;font-size:10px;font-weight:800;line-height:18px;text-align:center;box-shadow:0 1px 4px rgba(20,50,20,.25);">${escapeHtml(text)}</div>`,
    iconAnchor: [width / 2, 9],
    iconSize: [width, 18],
  });
}

function createZoneLabelIcon({
  selected,
  text,
}: {
  selected: boolean;
  text: string;
}) {
  const background = selected ? "#246df2" : "#2f7a43";

  return divIcon({
    className: "",
    html: `<div style="width:38px;border-radius:999px;background:${background};color:white;font-size:9px;font-weight:800;line-height:16px;text-align:center;box-shadow:0 1px 4px rgba(20,50,20,.22);">${escapeHtml(text)}</div>`,
    iconAnchor: [19, 8],
    iconSize: [38, 16],
  });
}

function createTinyBadgeIcon(text: string) {
  return divIcon({
    className: "",
    html: `<div style="border-radius:999px;background:rgba(255,255,255,.92);color:#1e5f32;font-size:10px;font-weight:800;line-height:16px;padding:0 6px;box-shadow:0 1px 4px rgba(20,50,20,.18);white-space:nowrap;">${escapeHtml(text)}</div>`,
    iconAnchor: [18, 8],
    iconSize: [36, 16],
  });
}

function createBottomMetricIcon({
  text,
  warning,
}: {
  status: string;
  text: string;
  warning: boolean;
}) {
  const dot = warning ? "#f59e0b" : "#20a64d";

  return divIcon({
    className: "",
    html: `<div style="display:flex;align-items:center;gap:4px;border-radius:999px;background:rgba(255,255,255,.94);color:#2f3d34;font-size:10px;font-weight:800;line-height:18px;padding:0 7px;box-shadow:0 1px 4px rgba(20,50,20,.18);white-space:nowrap;"><span style="width:6px;height:6px;border-radius:999px;background:${dot};display:inline-block;"></span>${escapeHtml(text)}</div>`,
    iconAnchor: [36, 9],
    iconSize: [72, 18],
  });
}

function createOrchidBlockIcon(group: OrchidGroup, matched: boolean) {
  const bg = matched
    ? group.status === "정상"
      ? "#1f8f48"
      : "#f59e0b"
    : "#9aa19a";
  const title =
    group.varietyName.length > 7
      ? `${group.varietyName.slice(0, 7)}...`
      : group.varietyName;

  return divIcon({
    className: "",
    html: `<div style="width:96px;height:32px;border-radius:7px;background:${bg};color:white;padding:4px 7px;box-shadow:0 2px 6px rgba(20,50,20,.22);line-height:1.15;box-sizing:border-box;"><div style="font-size:11px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(title)}</div><div style="font-size:10px;font-weight:700;opacity:.95;">${group.quantity}분</div></div>`,
    iconAnchor: [48, 16],
    iconSize: [96, 32],
  });
}

function createOrchidPreviewIcon(zone: BedZone) {
  const firstGroup = zone.orchidGroups[0];
  const title = firstGroup?.varietyName ?? `${zone.orchidGroups.length}묶음`;
  const status = firstGroup?.status ?? "";
  const bg = status === "정상" ? "#2e9d4d" : "#f59e0b";

  return divIcon({
    className: "",
    html: `<div style="width:84px;height:36px;border-radius:8px;background:${bg};color:white;padding:5px 7px;box-shadow:0 2px 6px rgba(20,50,20,.22);box-sizing:border-box;"><div style="font-size:11px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(title)}</div><div style="font-size:9px;font-weight:700;opacity:.9;">${zone.orchidGroups.length}묶음</div></div>`,
    iconAnchor: [42, 18],
    iconSize: [84, 36],
  });
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
