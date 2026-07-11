import { CircleMarker, Polygon, Rectangle } from "react-leaflet";
import { FARM_BOUNDS, WORLD_WIDTH } from "./config";
import { boundsOf, toLatLng, toPolyline } from "./geometry";

export function FarmBackgroundLayer() {
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
