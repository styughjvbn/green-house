"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, MapPin } from "lucide-react";
import type {
  OrchidGroup,
  PlacementRecommendation,
  PlacementRecommendationAllocation,
  PlacementRecommendationCandidate,
} from "@/entities/farm/types";
import { getPlacementRecommendations } from "../../api/orchidManagementApi";
import type { PreciseMovePayload } from "../../model/types";

export default function OrchidMovePanel({
  preferredBedZoneId,
  saving,
  selectedOrchidGroup,
  onCancel,
  onMove,
}: {
  preferredBedZoneId: number | null;
  saving: boolean;
  selectedOrchidGroup: OrchidGroup;
  onCancel: () => void;
  onMove: (payload: PreciseMovePayload) => Promise<void>;
}) {
  const [recommendation, setRecommendation] =
    useState<PlacementRecommendation | null>(null);
  const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);
  const [allocations, setAllocations] = useState<
    PlacementRecommendationAllocation[]
  >([]);
  const [memo, setMemo] = useState("");
  const [reorganizeDueDate, setReorganizeDueDate] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const data = await getPlacementRecommendations(selectedOrchidGroup.id);
        if (cancelled) return;
        setRecommendation(data);
        const available = data.candidates.filter(
          (candidate) => candidate.status !== "UNAVAILABLE",
        );
        const initial =
          available.find(
            (candidate) => candidate.bedZoneId === preferredBedZoneId,
          ) ?? available[0];
        selectCandidate(initial ?? null);
      } catch (loadError) {
        if (!cancelled) setError(toMessage(loadError));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [preferredBedZoneId, selectedOrchidGroup.id]);

  const selectedCandidate = useMemo(
    () =>
      recommendation?.candidates.find(
        (candidate) => candidate.bedZoneId === selectedZoneId,
      ) ?? null,
    [recommendation, selectedZoneId],
  );
  const candidates =
    recommendation?.candidates.filter(
      (candidate) => candidate.status !== "UNAVAILABLE",
    ) ?? [];
  const unavailableCount =
    recommendation?.candidates.filter(
      (candidate) => candidate.status === "UNAVAILABLE",
    ).length ?? 0;
  const trayUnits =
    recommendation?.requirement.placementType.startsWith("TRAY_") ||
    recommendation?.requirement.placementType.startsWith("CUSTOM:");

  function selectCandidate(candidate: PlacementRecommendationCandidate | null) {
    setSelectedZoneId(candidate?.bedZoneId ?? null);
    setAllocations(candidate?.allocations ?? []);
    setReorganizeDueDate("");
  }

  function updateAllocation(
    index: number,
    field: "quantity" | "occupancyUnits",
    value: number,
  ) {
    setAllocations((current) =>
      current.map((allocation, itemIndex) =>
        itemIndex === index ? { ...allocation, [field]: value } : allocation,
      ),
    );
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedCandidate?.requiredMode) return;
    void onMove({
      toBedZoneId: selectedCandidate.bedZoneId,
      placementMode: selectedCandidate.requiredMode,
      placements: allocations.map((allocation) => ({
        segmentId: allocation.segmentId,
        quantity: allocation.quantity,
        trayCount: trayUnits ? allocation.occupancyUnits : null,
      })),
      reorganizeDueDate:
        selectedCandidate.requiredMode === "TEMPORARY"
          ? reorganizeDueDate || null
          : null,
      memo,
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">
            배치 추천 및 이동
          </p>
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

      {recommendation ? (
        <div className="mt-3 grid grid-cols-3 gap-2 rounded-md bg-[#f5f7f3] p-2 text-center text-[11px]">
          <Metric
            label="배치 규격"
            value={formatPlacementType(
              recommendation.requirement.placementType,
            )}
          />
          <Metric
            label="필요 점유"
            value={`${recommendation.requirement.occupancyUnits}${trayUnits ? "판" : "분"}`}
          />
          <Metric
            label="분할"
            value={recommendation.requirement.splitAllowed ? "가능" : "불가"}
          />
        </div>
      ) : null}

      {loading ? (
        <p className="mt-3 text-sm text-[#68746d]">추천 후보 계산 중...</p>
      ) : null}
      {error ? (
        <p className="mt-3 rounded-md bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
          {error}
        </p>
      ) : null}

      {!loading && candidates.length > 0 ? (
        <form className="mt-3 space-y-3" onSubmit={handleSubmit}>
          <div className="max-h-56 space-y-2 overflow-y-auto pr-1">
            {candidates.slice(0, 15).map((candidate) => {
              const selected = candidate.bedZoneId === selectedZoneId;
              return (
                <button
                  key={candidate.bedZoneId}
                  className={`w-full rounded-md border p-2 text-left ${selected ? "border-[#246df2] bg-[#f3f7ff]" : "border-[#dce2dc] hover:border-[#159447]"}`}
                  type="button"
                  onClick={() => selectCandidate(candidate)}
                >
                  <div className="flex items-center gap-2">
                    <MapPin className="h-4 w-4 text-[#526058]" />
                    <strong className="text-xs">
                      {candidate.houseNumber}동 {candidate.physicalBedNumber}
                      배드 {candidate.bedZoneName}
                    </strong>
                    <CandidateBadge candidate={candidate} />
                  </div>
                  <p className="mt-1 text-[11px] text-[#68746d]">
                    {candidate.allocations
                      .map(
                        (allocation) =>
                          `${allocation.segmentName} ${allocation.occupancyUnits}`,
                      )
                      .join(" · ")}
                  </p>
                </button>
              );
            })}
          </div>

          {selectedCandidate ? (
            <div className="rounded-md border border-[#dce2dc] p-2">
              <p className="text-xs font-bold">구간별 배치 확정</p>
              <div className="mt-2 space-y-2">
                {allocations.map((allocation, index) => (
                  <div
                    className="grid grid-cols-[1fr_5rem_5rem] items-end gap-2"
                    key={allocation.segmentId}
                  >
                    <div>
                      <p className="text-xs font-semibold">
                        {allocation.segmentName}
                      </p>
                      <p className="text-[10px] text-[#758078]">
                        배치 후 여유 {allocation.remainingUnits}
                      </p>
                    </div>
                    <NumberField
                      label="수량"
                      value={allocation.quantity}
                      onChange={(value) =>
                        updateAllocation(index, "quantity", value)
                      }
                    />
                    <NumberField
                      label={trayUnits ? "판 수" : "점유"}
                      value={allocation.occupancyUnits}
                      onChange={(value) =>
                        updateAllocation(index, "occupancyUnits", value)
                      }
                    />
                  </div>
                ))}
              </div>
              {selectedCandidate.warnings.length ? (
                <div className="mt-2 rounded bg-[#fff8e8] p-2 text-[11px] text-[#96650f]">
                  {selectedCandidate.warnings.map((warning) => (
                    <p key={warning}>· {warning}</p>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          {selectedCandidate?.requiredMode === "TEMPORARY" ? (
            <label className="block text-xs font-semibold">
              재정리 예정일
              <input
                className="mt-1 h-9 w-full rounded-md border border-[#cfd8cc] px-2"
                required
                type="date"
                value={reorganizeDueDate}
                onChange={(event) => setReorganizeDueDate(event.target.value)}
              />
            </label>
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
            disabled={saving || !selectedCandidate}
            type="submit"
          >
            {saving ? "이동 중" : "배치 확정 및 이동"}
          </button>
        </form>
      ) : null}

      {!loading && !error && candidates.length === 0 ? (
        <p className="mt-3 rounded-md border border-[#f0d299] bg-[#fff8e8] p-3 text-xs text-[#96650f]">
          사용 가능한 추천 후보가 없습니다. 구역별 수용량과 기존 난 묶음 구간을
          먼저 설정하세요.
        </p>
      ) : null}
      {unavailableCount > 0 ? (
        <p className="mt-2 flex items-center gap-1 text-[10px] text-[#7a857e]">
          <AlertTriangle className="h-3.5 w-3.5" />
          제외된 구역 {unavailableCount}개
        </p>
      ) : null}
    </section>
  );
}

function CandidateBadge({
  candidate,
}: {
  candidate: PlacementRecommendationCandidate;
}) {
  const map = {
    RECOMMENDED: ["추천", "bg-[#eaf7ed] text-[#16843d]"],
    POSSIBLE: ["확장 필요", "bg-[#eef4ff] text-[#246df2]"],
    WARNING: ["주의", "bg-[#fff1e5] text-[#c66b13]"],
    UNAVAILABLE: ["불가", "bg-[#f2f3f2] text-[#68746d]"],
  } as const;
  const [label, className] = map[candidate.status];
  return (
    <span
      className={`ml-auto rounded px-2 py-0.5 text-[10px] font-bold ${className}`}
    >
      {candidate.status === "RECOMMENDED" ? (
        <CheckCircle2 className="mr-1 inline h-3 w-3" />
      ) : null}
      {label}
    </span>
  );
}
function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[#758078]">{label}</p>
      <strong className="mt-1 block text-[#27332b]">{value}</strong>
    </div>
  );
}
function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="text-[10px] text-[#68746d]">
      {label}
      <input
        className="mt-1 h-8 w-full rounded border border-[#d5ddd5] px-2 text-xs"
        min={1}
        type="number"
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      />
    </label>
  );
}
function formatPlacementType(value: string) {
  return (
    {
      TRAY_15: "15구 판",
      TRAY_20: "20구 판",
      TRAY_24: "24구 판",
      SINGLE_POT: "단독 화분",
      HANGING: "행잉",
    }[value] ?? value.replace("CUSTOM:", "")
  );
}
function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
