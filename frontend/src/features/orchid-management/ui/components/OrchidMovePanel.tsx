"use client";

import { FormEvent, useMemo, useState } from "react";
import { MapPin } from "lucide-react";
import type { House, OrchidGroup } from "@/entities/farm/types";
import {
  endCellToPosition,
  findBedZone,
  positionToEndCell,
  positionToStartCell,
  startCellToPosition,
} from "../../lib/orchidManagementUtils";
import type { MapCellRangePick, PreciseMovePayload } from "../../model/types";
import TextField from "./TextField";

type DestinationOption = {
  bedZoneId: number;
  label: string;
};

export default function OrchidMovePanel({
  house,
  preferredBedZoneId,
  saving,
  mapCellRangePick,
  selectedOrchidGroup,
  onCancel,
  onStartMapCellRangePick,
  onMove,
}: {
  house: House;
  preferredBedZoneId: number | null;
  saving: boolean;
  mapCellRangePick: MapCellRangePick;
  selectedOrchidGroup: OrchidGroup;
  onCancel: () => void;
  onStartMapCellRangePick: (options: {
    endCell: string;
    startCell: string;
    targetBedZoneId: number;
  }) => void;
  onMove: (payload: PreciseMovePayload) => Promise<void>;
}) {
  const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);
  const [startPosition, setStartPosition] = useState(
    selectedOrchidGroup.startPosition != null
      ? positionToStartCell(selectedOrchidGroup.startPosition)
      : "",
  );
  const [endPosition, setEndPosition] = useState(
    selectedOrchidGroup.endPosition != null
      ? positionToEndCell(selectedOrchidGroup.endPosition)
      : "",
  );
  const [ignoredMapPickVersion, setIgnoredMapPickVersion] = useState(0);
  const [memo, setMemo] = useState("");

  const currentZone = findBedZone(house, selectedOrchidGroup.bedZoneId);
  const destinationOptions = useMemo<DestinationOption[]>(
    () =>
      house.physicalBeds.flatMap((bed) =>
        bed.bedZones
          .filter((zone) => zone.id !== selectedOrchidGroup.bedZoneId)
          .map((zone) => ({
            bedZoneId: zone.id,
            label: `${house.number}동 ${bed.number}다이 ${zone.name}`,
          })),
      ),
    [house, selectedOrchidGroup.bedZoneId],
  );
  const fallbackZoneId =
    destinationOptions.find((option) => option.bedZoneId === preferredBedZoneId)
      ?.bedZoneId ??
    destinationOptions[0]?.bedZoneId ??
    null;
  const resolvedZoneId =
    selectedZoneId &&
    destinationOptions.some((option) => option.bedZoneId === selectedZoneId)
      ? selectedZoneId
      : fallbackZoneId;
  const rangePickActive =
    mapCellRangePick.active &&
    resolvedZoneId != null &&
    mapCellRangePick.targetBedZoneId === resolvedZoneId;
  const mapPickedRange =
    resolvedZoneId != null &&
    mapCellRangePick.targetBedZoneId === resolvedZoneId &&
    mapCellRangePick.startCell != null &&
    mapCellRangePick.endCell != null &&
    mapCellRangePick.version > ignoredMapPickVersion
      ? {
          startPosition: String(mapCellRangePick.startCell),
          endPosition: String(mapCellRangePick.endCell),
        }
      : null;
  const startPositionValue = mapPickedRange?.startPosition ?? startPosition;
  const endPositionValue = mapPickedRange?.endPosition ?? endPosition;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!resolvedZoneId) return;

    void onMove({
      toBedZoneId: resolvedZoneId,
      startPosition: startCellToPosition(startPositionValue),
      endPosition: endCellToPosition(endPositionValue),
      memo,
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">난 묶음 이동</p>
          <h3 className="mt-1 text-base font-semibold">
            {selectedOrchidGroup.varietyName}
          </h3>
        </div>
        <button
          className="rounded-md border border-[#d7ddd4] px-2 py-1.5 text-xs font-semibold"
          onClick={onCancel}
          type="button"
        >
          닫기
        </button>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 rounded-md bg-[#f5f7f3] p-2 text-xs">
        <InfoItem
          label="현재 위치"
          value={
            currentZone
              ? `${house.number}동 ${currentZone.bed.number}다이 ${currentZone.zone.name}`
              : "확인 불가"
          }
        />
        <InfoItem label="수량" value={`${selectedOrchidGroup.quantity}분`} />
      </div>

      <form className="mt-3 space-y-3" onSubmit={handleSubmit}>
        <label className="block text-xs font-semibold">
          이동할 구역
          <select
            className="mt-1 h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-3 text-sm"
            value={resolvedZoneId ?? ""}
            onChange={(event) => setSelectedZoneId(Number(event.target.value))}
          >
            {destinationOptions.map((option) => (
              <option key={option.bedZoneId} value={option.bedZoneId}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-end gap-2">
          <TextField
            label="시작 칸"
            min={1}
            step={1}
            type="number"
            value={startPositionValue}
            onChange={(value) => {
              setIgnoredMapPickVersion(mapCellRangePick.version);
              setStartPosition(value);
            }}
          />
          <button
            className={`mb-px h-[34px] rounded-md border px-3 text-xs font-semibold transition ${
              rangePickActive
                ? "border-[#246df2] bg-[#246df2] text-white"
                : "border-[#cfd8cc] bg-white text-[#36513d] hover:bg-[#f3f8f2]"
            }`}
            disabled={!resolvedZoneId || saving}
            type="button"
            onClick={() => {
              if (!resolvedZoneId) return;
              onStartMapCellRangePick({
                targetBedZoneId: resolvedZoneId,
                startCell: startPositionValue,
                endCell: endPositionValue,
              });
            }}
          >
            맵에서 지정
          </button>
          <TextField
            label="끝 칸"
            min={1}
            step={1}
            type="number"
            value={endPositionValue}
            onChange={(value) => {
              setIgnoredMapPickVersion(mapCellRangePick.version);
              setEndPosition(value);
            }}
          />
        </div>

        {resolvedZoneId ? (
          <div className="rounded-md border border-[#dce2dc] bg-[#fbfcfa] p-2 text-xs text-[#4a5650]">
            <div className="flex items-center gap-2 font-semibold text-[#17251b]">
              <MapPin className="h-3.5 w-3.5 text-[#159447]" />
              이동 대상
            </div>
            <p className="mt-1">
              {
                destinationOptions.find(
                  (option) => option.bedZoneId === resolvedZoneId,
                )?.label
              }
            </p>
          </div>
        ) : null}

        <label className="block text-xs font-semibold">
          이동 메모
          <textarea
            className="mt-1 min-h-14 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
          />
        </label>

        <button
          className="w-full rounded-md bg-[#246df2] px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={saving || !resolvedZoneId}
          type="submit"
        >
          {saving ? "이동 중..." : "이동 저장"}
        </button>

        {destinationOptions.length === 0 ? (
          <p className="rounded-md border border-[#f0d299] bg-[#fff8e8] p-3 text-xs text-[#96650f]">
            이동 가능한 다른 구역이 없습니다.
          </p>
        ) : null}
      </form>
    </section>
  );
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[#758078]">{label}</p>
      <strong className="mt-1 block text-[#27332b]">{value}</strong>
    </div>
  );
}
