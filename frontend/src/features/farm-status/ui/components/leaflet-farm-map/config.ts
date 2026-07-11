import type { LatLngBoundsExpression } from "leaflet";
import type { FarmZoomLevel } from "@/entities/farm/types";

export const WORLD_WIDTH = 1900;
export const WORLD_HEIGHT = 900;
export const INITIAL_ZOOM = -0.7;
export const MIN_ZOOM = -0.7;
export const MAX_ZOOM = 3.15;

export const HOUSE_WIDTH = 124;

export const BED_VISIBLE_ZOOM = -0.3;
export const ZONE_VISIBLE_ZOOM = 0.5;
export const ORCHID_VISIBLE_ZOOM = 1.3;
export const WHEEL_PX_PER_ZOOM_LEVEL = 180;

export const FARM_BOUNDS: LatLngBoundsExpression = [
  [0, 0],
  [WORLD_HEIGHT, WORLD_WIDTH],
];

export const LEVEL_RANK: Record<FarmZoomLevel, number> = {
  FARM: 0,
  HOUSE: 1,
  PHYSICAL_BED: 2,
  BED_ZONE: 3,
};

export function getLevelByMapZoom(zoom: number): FarmZoomLevel {
  if (zoom < BED_VISIBLE_ZOOM) return "FARM";
  if (zoom < ZONE_VISIBLE_ZOOM) return "HOUSE";
  if (zoom < ORCHID_VISIBLE_ZOOM) return "PHYSICAL_BED";
  return "BED_ZONE";
}
