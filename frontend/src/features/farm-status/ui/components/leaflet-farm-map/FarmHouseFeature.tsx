import { Marker, Rectangle } from "react-leaflet";
import type {
  BedZone,
  FarmStatusZoomData,
  HouseStatusSummary,
  PhysicalBed,
} from "@/entities/farm/types";
import { getOrchidVarietyColor } from "@/entities/farm/orchidColors";
import type {
  FarmStatusColorMode,
  FarmStatusFilterMatches,
  FarmStatusLayoutMode,
  SelectedFarmStatusOrchidGroup,
  SelectedTarget,
} from "../../../model/types";
import { hasHouseWarning } from "../../../lib/farmStatusView";
import {
  BED_VISIBLE_ZOOM,
  ORCHID_VISIBLE_ZOOM,
  ZONE_VISIBLE_ZOOM,
} from "./config";
import {
  boundsOf,
  getBedGeometries,
  getHouseDetailSource,
  getOrchidBlockGeometries,
  getZoneGeometries,
  toFarmStatusItem,
  toLatLng,
  type DetailSource,
  type FarmHouseGeometry,
  type VisualBedGeometry,
  type VisualZoneGeometry,
} from "./geometry";
import {
  createBottomMetricIcon,
  createLabelIcon,
  createOrchidBlockIcon,
  createSmallLabelIcon,
  createTinyBadgeIcon,
  createZoneLabelIcon,
  stopLeafletClick,
} from "./icons";

export function FarmHouseFeature({
  geometry,
  filterMatches,
  hasActiveSearch,
  house,
  layoutMode,
  mapZoom,
  colorMode,
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
  layoutMode: FarmStatusLayoutMode;
  mapZoom: number;
  colorMode: FarmStatusColorMode;
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
  const showBedLabels = mapZoom >= BED_VISIBLE_ZOOM;
  const showZoneLabels = mapZoom >= ZONE_VISIBLE_ZOOM;
  const showOrchidLabels = mapZoom >= ORCHID_VISIBLE_ZOOM;
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

      {!showBedLabels ? (
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
            y:
              layoutMode === "NORMALIZED"
                ? geometry.y + 16
                : geometry.y + geometry.height + 18,
          })}
        />
      ) : null}

      {bedGeometries.map(({ bed, rect }) => (
        <FarmBedFeature
          key={`${house.houseId}-${bed.number}`}
          bed={bed}
          detailSource={detailSource}
          filterMatches={filterMatches}
          hasActiveSearch={hasActiveSearch}
          colorMode={colorMode}
          mapZoom={mapZoom}
          rect={rect}
          selectedBedZoneId={selectedBedZoneId}
          selectedOrchidGroup={selectedOrchidGroup}
          selectedPhysicalBedId={selectedPhysicalBedId}
          showBedLabel={showBedLabels}
          showOrchidLabels={showOrchidLabels}
          showZoneLabels={showZoneLabels}
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
      ))}
    </>
  );
}

function FarmBedFeature({
  bed,
  detailSource,
  filterMatches,
  hasActiveSearch,
  colorMode,
  mapZoom,
  rect,
  selectedBedZoneId,
  selectedOrchidGroup,
  selectedPhysicalBedId,
  showBedLabel,
  showOrchidLabels,
  showZoneLabels,
  onSelectBed,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  detailSource: DetailSource;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  colorMode: FarmStatusColorMode;
  mapZoom: number;
  rect: VisualBedGeometry;
  selectedBedZoneId: number | null;
  selectedOrchidGroup: SelectedFarmStatusOrchidGroup | null;
  selectedPhysicalBedId: number | null;
  showBedLabel: boolean;
  showOrchidLabels: boolean;
  showZoneLabels: boolean;
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
      {showBedLabel ? (
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
      ) : null}
      {!showZoneLabels ? (
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

      {zoneGeometries.map(({ zone, zoneRect }) => (
        <FarmZoneFeature
          key={`${bed.id}-${zone.side}-${zone.id}`}
          detailSource={detailSource}
          filterMatches={filterMatches}
          hasActiveSearch={hasActiveSearch}
          colorMode={colorMode}
          mapZoom={mapZoom}
          selectedOrchidGroupId={selectedOrchidGroup?.orchidGroupId ?? null}
          selected={selectedBedZoneId === zone.id}
          showOrchidLabels={showOrchidLabels}
          showZoneLabel={showZoneLabels}
          bed={bed}
          zone={zone}
          zoneRect={zoneRect}
          onSelectBedZone={onSelectBedZone}
          onSelectOrchidGroup={onSelectOrchidGroup}
        />
      ))}
    </>
  );
}

function FarmZoneFeature({
  bed,
  detailSource,
  filterMatches,
  hasActiveSearch,
  colorMode,
  mapZoom,
  selectedOrchidGroupId,
  selected,
  showOrchidLabels,
  showZoneLabel,
  zone,
  zoneRect,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  detailSource: DetailSource;
  filterMatches: FarmStatusFilterMatches;
  hasActiveSearch: boolean;
  colorMode: FarmStatusColorMode;
  mapZoom: number;
  selectedOrchidGroupId: number | null;
  selected: boolean;
  showOrchidLabels: boolean;
  showZoneLabel: boolean;
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
  const orchidBlockGeometries = getOrchidBlockGeometries(zoneRect, zone, bed);

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
      {showZoneLabel ? (
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
      ) : null}
      {hasGroups
        ? orchidBlockGeometries.map(({ group, rect }) => {
            const selectedGroup = selectedOrchidGroupId === group.id;
            const matchedGroup =
              !hasActiveSearch || filterMatches.orchidGroupIds.has(group.id);
            const fillColor = getOrchidFillColor(group, colorMode);
            const statusColor = getStatusColor(group.status);

            return (
              <Rectangle
                key={group.id}
                bounds={boundsOf(rect)}
                eventHandlers={{
                  click: (event) => {
                    stopLeafletClick(event);
                    if (mapZoom >= BED_VISIBLE_ZOOM) {
                      onSelectOrchidGroup(toFarmStatusItem(group, zone));
                    }
                  },
                }}
                pathOptions={{
                  color: !matchedGroup
                    ? "#9aa19a"
                    : selectedGroup
                      ? "#1d6ff2"
                      : hasActiveSearch
                        ? "#7c3aed"
                        : statusColor,
                  fillColor: matchedGroup
                    ? selectedGroup
                      ? "#dbeafe"
                      : fillColor
                    : "#bfc5bf",
                  fillOpacity: matchedGroup
                    ? selectedGroup
                      ? 0.96
                      : 0.82
                    : 0.68,
                  opacity: matchedGroup ? 1 : 0.38,
                  weight:
                    selectedGroup || (hasActiveSearch && matchedGroup)
                      ? 4
                      : 1.5,
                }}
              />
            );
          })
        : null}
      {showOrchidLabels && hasGroups
        ? orchidBlockGeometries.map(({ group, rect }) => (
            <Marker
              key={`${group.id}-label`}
              icon={createOrchidBlockIcon(
                group,
                !hasActiveSearch || filterMatches.orchidGroupIds.has(group.id),
                getOrchidFillColor(group, colorMode),
                mapZoom,
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

function getStatusColor(status: string) {
  if (status === "이상" || status === "병해충") return "#dc2626";
  if (status === "주의") return "#d97706";
  return "#16853b";
}

function getOrchidFillColor(
  group: BedZone["orchidGroups"][number],
  colorMode: FarmStatusColorMode,
) {
  if (colorMode === "VARIETY") {
    return getOrchidVarietyColor(group).fill;
  }
  if (colorMode === "AGE") {
    const age = group.ageYear ?? 0;
    if (age >= 5) return "#6d28d9";
    if (age >= 3) return "#8b5cf6";
    if (age >= 1) return "#a78bfa";
    return "#c4b5fd";
  }
  return getStatusColor(group.status);
}
