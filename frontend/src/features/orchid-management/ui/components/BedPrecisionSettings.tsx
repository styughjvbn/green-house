"use client";

import {
  ChevronDown,
  ChevronUp,
  Plus,
  Save,
  Settings2,
  Trash2,
} from "lucide-react";
import { useEffect, useState } from "react";
import type {
  BedZone,
  BedZonePlacementProfile,
  BedZoneSegment,
  BedZoneSegmentCapacity,
} from "@/entities/farm/types";
import {
  getBedZonePlacementProfile,
  saveBedZonePlacementProfile,
} from "../../api/orchidManagementApi";

const inputClass =
  "h-8 min-w-0 rounded-md border border-[#d5ddd5] bg-white px-2 text-xs outline-none focus:border-[#159447]";

export default function BedPrecisionSettings({
  zone,
}: {
  zone: BedZone | null;
}) {
  const [profile, setProfile] = useState<BedZonePlacementProfile | null>(null);
  const [draft, setDraft] = useState<BedZonePlacementProfile | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!zone) return;
      setLoading(true);
      setError(null);
      try {
        const next = await getBedZonePlacementProfile(zone.id);
        if (!cancelled) {
          setProfile(next);
          setDraft(next);
        }
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
  }, [zone]);

  if (!zone) return null;
  const current = editing ? draft : profile;

  async function save() {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await saveBedZonePlacementProfile(draft);
      setProfile(saved);
      setDraft(saved);
      setEditing(false);
    } catch (saveError) {
      setError(toMessage(saveError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white shadow-sm">
      <div className="flex items-center justify-between gap-3 p-3">
        <button
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
          type="button"
          onClick={() => setExpanded((value) => !value)}
        >
          <Settings2 className="h-4 w-4 shrink-0 text-[#16843d]" />
          <span className="text-sm font-bold">배드 정밀 설정</span>
          <span className="truncate text-xs text-[#6a766e]">
            {zone.name} · {profile?.segments.length ?? 0}구간
          </span>
          {expanded ? (
            <ChevronUp className="ml-auto h-4 w-4" />
          ) : (
            <ChevronDown className="ml-auto h-4 w-4" />
          )}
        </button>
        {expanded && profile ? (
          editing ? (
            <div className="flex gap-1">
              <button
                className="rounded border border-[#d5ddd5] px-3 py-1.5 text-xs font-semibold"
                type="button"
                onClick={() => {
                  setDraft(profile);
                  setEditing(false);
                }}
              >
                취소
              </button>
              <button
                className="flex items-center gap-1 rounded bg-[#159447] px-3 py-1.5 text-xs font-semibold text-white"
                disabled={saving}
                type="button"
                onClick={() => void save()}
              >
                <Save className="h-3.5 w-3.5" />
                저장
              </button>
            </div>
          ) : (
            <button
              className="rounded border border-[#d5ddd5] px-3 py-1.5 text-xs font-semibold"
              type="button"
              onClick={() => setEditing(true)}
            >
              설정 수정
            </button>
          )
        ) : null}
      </div>

      {expanded ? (
        <div className="border-t border-[#e3e8e3] p-3">
          {loading ? (
            <p className="text-xs text-[#6a766e]">불러오는 중...</p>
          ) : null}
          {current?.hasUnassignedGroups ? (
            <p className="mb-3 rounded-md border border-[#f0d299] bg-[#fff8e8] p-2 text-xs text-[#9a6611]">
              구간 미지정 난 묶음이 있어 이 구역은 추천 후보에서 제외됩니다.
            </p>
          ) : null}
          <div className="space-y-2">
            {current?.segments.map((segment, segmentIndex) => (
              <SegmentEditor
                key={segment.id ?? `new-${segmentIndex}`}
                editing={editing}
                segment={segment}
                onChange={(next) => updateSegment(setDraft, segmentIndex, next)}
                onRemove={() => removeSegment(setDraft, segmentIndex)}
              />
            ))}
          </div>
          {editing ? (
            <button
              className="mt-3 flex items-center gap-1 rounded-md border border-dashed border-[#9fc9a8] px-3 py-2 text-xs font-semibold text-[#16843d]"
              type="button"
              onClick={() => addSegment(setDraft)}
            >
              <Plus className="h-4 w-4" />
              구간 추가
            </button>
          ) : null}
          {error ? (
            <p className="mt-3 rounded bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
              {error}
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function SegmentEditor({
  segment,
  editing,
  onChange,
  onRemove,
}: {
  segment: BedZoneSegment;
  editing: boolean;
  onChange: (segment: BedZoneSegment) => void;
  onRemove: () => void;
}) {
  return (
    <article className="rounded-md border border-[#dce2dc] bg-[#fbfcfa] p-3">
      <div className="flex items-center gap-2">
        {editing ? (
          <>
            <input
              className={`${inputClass} flex-1`}
              value={segment.name}
              onChange={(event) =>
                onChange({ ...segment, name: event.target.value })
              }
            />
            <select
              className={inputClass}
              value={segment.segmentType}
              onChange={(event) =>
                onChange({
                  ...segment,
                  segmentType: event.target
                    .value as BedZoneSegment["segmentType"],
                })
              }
            >
              <option value="START">앞</option>
              <option value="MIDDLE">중간</option>
              <option value="END">끝</option>
              <option value="CUSTOM">사용자</option>
            </select>
            <button
              className="grid h-8 w-8 place-items-center rounded border border-[#e0c5c5] text-[#c44747]"
              type="button"
              onClick={onRemove}
              aria-label="구간 삭제"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </>
        ) : (
          <>
            <strong className="text-xs">{segment.name}</strong>
            <span className="rounded bg-[#edf3ed] px-2 py-0.5 text-[10px] text-[#68746d]">
              {segmentTypeLabel(segment.segmentType)}
            </span>
            <span className="ml-auto text-[10px] text-[#7b867f]">
              수용량 {segment.capacities.length}개
            </span>
          </>
        )}
      </div>
      {editing ? (
        <>
          <input
            className={`${inputClass} mt-2 w-full`}
            placeholder="간섭 및 구간 메모"
            value={segment.memo ?? ""}
            onChange={(event) =>
              onChange({ ...segment, memo: event.target.value || null })
            }
          />
          <div className="mt-2 space-y-1">
            {segment.capacities.map((capacity, index) => (
              <CapacityEditor
                capacity={capacity}
                key={`${capacity.id ?? "new"}-${index}`}
                onChange={(next) =>
                  onChange({
                    ...segment,
                    capacities: segment.capacities.map((item, itemIndex) =>
                      itemIndex === index ? next : item,
                    ),
                  })
                }
                onRemove={() =>
                  onChange({
                    ...segment,
                    capacities: segment.capacities.filter(
                      (_, itemIndex) => itemIndex !== index,
                    ),
                  })
                }
              />
            ))}
          </div>
          <button
            className="mt-2 flex items-center gap-1 text-xs font-semibold text-[#16843d]"
            type="button"
            onClick={() =>
              onChange({
                ...segment,
                capacities: [...segment.capacities, createCapacity()],
              })
            }
          >
            <Plus className="h-3.5 w-3.5" />
            수용량 추가
          </button>
        </>
      ) : segment.memo ? (
        <p className="mt-2 text-[11px] text-[#6a766e]">{segment.memo}</p>
      ) : null}
    </article>
  );
}

function CapacityEditor({
  capacity,
  onChange,
  onRemove,
}: {
  capacity: BedZoneSegmentCapacity;
  onChange: (capacity: BedZoneSegmentCapacity) => void;
  onRemove: () => void;
}) {
  return (
    <div className="grid gap-1 rounded border border-[#e5e9e5] bg-white p-2 md:grid-cols-[1fr_0.8fr_0.9fr_0.6fr_auto_auto]">
      <select
        className={inputClass}
        value={capacity.placementType}
        onChange={(event) =>
          onChange({ ...capacity, placementType: event.target.value })
        }
      >
        <option value="TRAY_15">15구 판</option>
        <option value="TRAY_20">20구 판</option>
        <option value="TRAY_24">24구 판</option>
        <option value="SINGLE_POT">단독 화분</option>
        <option value="HANGING">행잉</option>
        <option value="CUSTOM:기타">기타</option>
      </select>
      <input
        className={inputClass}
        placeholder="화분 크기(전체)"
        value={capacity.potSize ?? ""}
        onChange={(event) =>
          onChange({ ...capacity, potSize: event.target.value || null })
        }
      />
      <select
        className={inputClass}
        value={capacity.capacityMode}
        onChange={(event) =>
          onChange({
            ...capacity,
            capacityMode: event.target
              .value as BedZoneSegmentCapacity["capacityMode"],
          })
        }
      >
        <option value="SPACIOUS">여유</option>
        <option value="STANDARD">표준</option>
        <option value="EXPANDED">확장</option>
        <option value="COMPRESSED">압축</option>
        <option value="TEMPORARY">임시</option>
      </select>
      <input
        className={inputClass}
        min={0}
        type="number"
        value={capacity.capacityValue}
        onChange={(event) =>
          onChange({ ...capacity, capacityValue: Number(event.target.value) })
        }
      />
      <label className="flex items-center gap-1 px-1 text-[11px]">
        <input
          checked={capacity.allowed}
          type="checkbox"
          onChange={(event) =>
            onChange({ ...capacity, allowed: event.target.checked })
          }
        />
        허용
      </label>
      <button
        className="grid h-8 w-8 place-items-center text-[#c44747]"
        type="button"
        onClick={onRemove}
        aria-label="수용량 삭제"
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function createCapacity(): BedZoneSegmentCapacity {
  return {
    id: null,
    placementType: "TRAY_20",
    potSize: null,
    capacityMode: "STANDARD",
    capacityValue: 0,
    allowed: true,
    memo: null,
  };
}
function updateSegment(
  setter: React.Dispatch<React.SetStateAction<BedZonePlacementProfile | null>>,
  index: number,
  segment: BedZoneSegment,
) {
  setter((current) =>
    current
      ? {
          ...current,
          segments: current.segments.map((item, itemIndex) =>
            itemIndex === index ? segment : item,
          ),
        }
      : current,
  );
}
function removeSegment(
  setter: React.Dispatch<React.SetStateAction<BedZonePlacementProfile | null>>,
  index: number,
) {
  setter((current) =>
    current
      ? {
          ...current,
          segments: current.segments
            .filter((_, itemIndex) => itemIndex !== index)
            .map((segment, itemIndex) => ({
              ...segment,
              sortOrder: itemIndex + 1,
            })),
        }
      : current,
  );
}
function addSegment(
  setter: React.Dispatch<React.SetStateAction<BedZonePlacementProfile | null>>,
) {
  setter((current) =>
    current
      ? {
          ...current,
          segments: [
            ...current.segments,
            {
              id: null,
              name: `사용자 구간 ${current.segments.length + 1}`,
              segmentType: "CUSTOM",
              sortOrder: current.segments.length + 1,
              memo: null,
              capacities: [],
            },
          ],
        }
      : current,
  );
}
function segmentTypeLabel(value: BedZoneSegment["segmentType"]) {
  return { START: "앞", MIDDLE: "중간", END: "끝", CUSTOM: "사용자" }[value];
}
function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
