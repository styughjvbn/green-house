"use client";

import { FormEvent, useEffect, useState } from "react";
import type { House, HouseStatusSummary, OrchidGroup } from "@/types/farm";
import { fetchHouse } from "../../api/orchidManagementApi";

export default function OrchidMovePanel({
  currentHouse,
  houses,
  saving,
  selectedOrchidGroup,
  onCancel,
  onMove,
}: {
  currentHouse: House;
  houses: HouseStatusSummary[];
  saving: boolean;
  selectedOrchidGroup: OrchidGroup;
  onCancel: () => void;
  onMove: (toBedZoneId: number, memo: string) => Promise<void>;
}) {
  const [destinationHouseId, setDestinationHouseId] = useState(currentHouse.id);
  const [destinationHouse, setDestinationHouse] = useState<House>(currentHouse);
  const [physicalBedId, setPhysicalBedId] = useState(currentHouse.physicalBeds[0]?.id ?? 0);
  const [bedZoneId, setBedZoneId] = useState(currentHouse.physicalBeds[0]?.bedZones[0]?.id ?? 0);
  const [memo, setMemo] = useState("");
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadHouse() {
      setLoading(true);
      setLoadError(null);
      try {
        const nextHouse = await fetchHouse(destinationHouseId);
        if (cancelled) return;
        setDestinationHouse(nextHouse);
        const firstBed = nextHouse.physicalBeds[0];
        setPhysicalBedId(firstBed?.id ?? 0);
        setBedZoneId(firstBed?.bedZones[0]?.id ?? 0);
      } catch (error) {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadHouse();
    return () => {
      cancelled = true;
    };
  }, [destinationHouseId]);

  const selectedBed = destinationHouse.physicalBeds.find((bed) => bed.id === physicalBedId) ?? destinationHouse.physicalBeds[0] ?? null;
  const safeBedZoneId = selectedBed?.bedZones.some((zone) => zone.id === bedZoneId) ? bedZoneId : selectedBed?.bedZones[0]?.id ?? 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (safeBedZoneId > 0) {
      void onMove(safeBedZoneId, memo);
    }
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">다른 위치로 이동</p>
          <h3 className="mt-1 text-base font-semibold">{selectedOrchidGroup.varietyName}</h3>
        </div>
        <button className="rounded-md border border-[#d7ddd4] px-2 py-1.5 text-xs font-semibold" onClick={onCancel} type="button">
          닫기
        </button>
      </div>
      <p className="mt-2 text-xs text-[#5c6a60]">
        현재 위치: {selectedOrchidGroup.houseNumber}동 / {selectedOrchidGroup.physicalBedNumber}배드 / {selectedOrchidGroup.bedZoneName}
      </p>
      <form className="mt-3 space-y-2" onSubmit={handleSubmit}>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">목적 동</span>
          <select
            className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
            value={destinationHouseId}
            onChange={(event) => setDestinationHouseId(Number(event.target.value))}
          >
            {houses.map((house) => (
              <option key={house.houseId} value={house.houseId}>{house.houseNumber}동</option>
            ))}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-2">
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">목적 배드</span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={physicalBedId}
              onChange={(event) => setPhysicalBedId(Number(event.target.value))}
            >
              {destinationHouse.physicalBeds.map((bed) => (
                <option key={bed.id} value={bed.id}>{bed.number}배드</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">목적 구역</span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={safeBedZoneId}
              onChange={(event) => setBedZoneId(Number(event.target.value))}
            >
              {selectedBed?.bedZones.map((zone) => (
                <option key={zone.id} value={zone.id}>{zone.side === "LEFT" ? "좌" : "우"}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">이동 메모</span>
          <textarea className="mt-1 min-h-14 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm" value={memo} onChange={(event) => setMemo(event.target.value)} />
        </label>
        {loadError ? <p className="rounded-md bg-[#fff1ec] p-2 text-xs text-[#9b341e]">{loadError}</p> : null}
        <button
          className="w-full rounded-md bg-[#246df2] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={saving || loading || safeBedZoneId <= 0}
          type="submit"
        >
          {saving ? "이동 중" : "이동"}
        </button>
      </form>
    </section>
  );
}
