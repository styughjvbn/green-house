"use client";

import { useState } from "react";
import type { SalesSlipPhotoInput } from "../../api/types";
import type { PhotoRegionDraft } from "../../lib/photoRegions";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";

/** The read-only overlay stays small; the Cropper editor loads on demand. */
export function SalesSlipSourceRegions({
  photo,
  regions,
  onSelect,
  disabled = false,
}: {
  photo: SalesSlipPhotoInput;
  regions: PhotoRegionDraft[];
  onSelect: (id?: string) => void;
  disabled?: boolean;
}) {
  const [ratio, setRatio] = useState(1);
  return (
    <div
      className="relative mx-auto max-w-full overflow-hidden rounded border border-[#d5e1d8] bg-[#f4f7f3]"
      style={{ width: `min(100%, ${400 * ratio}px)`, aspectRatio: ratio }}
    >
      <SalesSlipPhotoViewer
        src={`data:${photo.contentType};base64,${photo.contentBase64}`}
        alt="전표 사진 원본"
        width={1000}
        height={Math.round(1000 / ratio)}
        className="h-auto w-full"
        onLoad={(width, height) => setRatio(width / height)}
      />
      <svg
        viewBox="0 0 1 1"
        preserveAspectRatio="none"
        aria-label="원본의 전표 영역"
        className="pointer-events-none absolute inset-0 h-full w-full"
      >
        {regions.map((item, index) => (
          <g key={item.id}>
            <rect
              data-region-id={item.id}
              x={item.region.x}
              y={item.region.y}
              width={item.region.width}
              height={item.region.height}
              vectorEffect="non-scaling-stroke"
              stroke="#159447"
              strokeWidth={2}
              fill="#159447"
              fillOpacity={0.07}
              className={
                disabled
                  ? "pointer-events-none"
                  : "pointer-events-auto cursor-pointer"
              }
              onClick={disabled ? undefined : () => onSelect(item.id)}
            />
            <text
              x={item.region.x + 0.006}
              y={item.region.y + 0.025}
              fontSize=".022"
              fill="#075c2b"
              paintOrder="stroke"
              stroke="white"
              strokeWidth=".003"
            >
              {index + 1}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}
