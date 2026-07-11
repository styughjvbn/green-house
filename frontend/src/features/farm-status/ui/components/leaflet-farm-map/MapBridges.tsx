"use client";

import { useEffect, useRef } from "react";
import { useMap, useMapEvents } from "react-leaflet";
import type { Map as LeafletMap } from "leaflet";
import type { FarmZoomLevel } from "@/entities/farm/types";
import { getLevelByMapZoom, LEVEL_RANK } from "./config";

export function MapInstanceBridge({
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

export function MapZoomBridge({
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
