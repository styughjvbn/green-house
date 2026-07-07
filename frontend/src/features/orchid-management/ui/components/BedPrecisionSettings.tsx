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
import type { Dispatch, SetStateAction } from "react";
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

const fixedPlacementTypes = [
  { value: "TRAY_15", label: "15구 판" },
  { value: "TRAY_20", label: "20구 판" },
  { value: "TRAY_24", label: "24구 판" },
  { value: "SINGLE_POT", label: "단독 화분" },
  { value: "HANGING", label: "행잉" },
] as const;

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
      if (!zone) {
        setProfile(null);
        setDraft(null);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const next = await getBedZonePlacementProfile(zone.id);
        if (!cancelled) {
          setProfile(next);
          setDraft(next);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(toMessage(loadError));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [zone]);

  if (!zone) {
    return null;
  }

  const current = editing ? draft : profile;

  async function save() {
    if (!draft) {
      return;
    }

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
            {zone.name} · 구간 {profile?.segments.length ?? 0}개
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

          {current ? (
            <div className="mb-3 grid gap-2 rounded-md border border-[#e2e7e1] bg-[#f8faf8] p-3 text-xs text-[#56635a] md:grid-cols-4">
              <InfoItem label="동" value={`${current.houseNumber}동`} />
              <InfoItem
                label="배드"
                value={`${current.physicalBedNumber}배드`}
              />
              <InfoItem label="기준 길이" value={formatPositionUnit(current)} />
              <InfoItem label="구역" value={current.bedZoneName} />
            </div>
          ) : null}

          {current?.hasUnassignedGroups ? (
            <p className="mb-3 rounded-md border border-[#f0d299] bg-[#fff8e8] p-2 text-xs text-[#9a6611]">
              구간 미지정 난 묶음이 있어 먼저 실제 배치 구간을 정리해야 합니다.
            </p>
          ) : null}

          <div className="space-y-2">
            {current?.segments.map((segment, segmentIndex) => (
              <SegmentEditor
                key={segment.id ?? `new-${segmentIndex}`}
                editing={editing}
                segment={segment}
                positionUnitLabel={current.positionUnitLabel}
                onChange={(next) => updateSegment(setDraft, segmentIndex, next)}
                onRemove={() => removeSegment(setDraft, segmentIndex)}
              />
            ))}
          </div>

          {editing ? (
            <button
              className="mt-3 flex items-center gap-1 rounded-md border border-dashed border-[#9fc9a8] px-3 py-2 text-xs font-semibold text-[#16843d]"
              type="button"
              onClick={() =>
                addSegment(setDraft, current?.positionUnitCount ?? null)
              }
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

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-1">
      <p className="text-[10px] font-semibold tracking-[0.04em] text-[#859187] uppercase">
        {label}
      </p>
      <p className="text-xs font-semibold text-[#243126]">{value}</p>
    </div>
  );
}

function SegmentEditor({
  segment,
  editing,
  positionUnitLabel,
  onChange,
  onRemove,
}: {
  segment: BedZoneSegment;
  editing: boolean;
  positionUnitLabel: string | null;
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
              <option value="CUSTOM">사용자 정의</option>
            </select>
            <input
              aria-label="구간 순서"
              className={`${inputClass} w-16`}
              min={1}
              type="number"
              value={segment.sortOrder}
              onChange={(event) =>
                onChange({ ...segment, sortOrder: Number(event.target.value) })
              }
            />
            <button
              aria-label="구간 삭제"
              className="grid h-8 w-8 place-items-center rounded border border-[#e0c5c5] text-[#c44747]"
              type="button"
              onClick={onRemove}
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
              수용 규칙 {segment.capacities.length}개
            </span>
          </>
        )}
      </div>

      <div className="mt-2 grid gap-2 md:grid-cols-[120px_120px_1fr]">
        <NumberField
          disabled={!editing}
          label={`시작 위치${suffixUnit(positionUnitLabel)}`}
          value={segment.startPosition}
          onChange={(value) => onChange({ ...segment, startPosition: value })}
        />
        <NumberField
          disabled={!editing}
          label={`종료 위치${suffixUnit(positionUnitLabel)}`}
          value={segment.endPosition}
          onChange={(value) => onChange({ ...segment, endPosition: value })}
        />
        <div className="space-y-1">
          <label className="text-[11px] font-semibold text-[#5f6a63]">
            메모
          </label>
          {editing ? (
            <input
              className={`${inputClass} w-full`}
              placeholder="간섭, 금지, 현장 메모"
              value={segment.memo ?? ""}
              onChange={(event) =>
                onChange({ ...segment, memo: event.target.value || null })
              }
            />
          ) : (
            <div className="flex h-8 items-center rounded-md border border-[#e3e8e3] bg-white px-2 text-xs text-[#5f6a63]">
              {segment.memo ?? "-"}
            </div>
          )}
        </div>
      </div>

      {editing ? (
        <>
          <div className="mt-2 space-y-1">
            {segment.capacities.map((capacity, index) => (
              <CapacityEditor
                key={`${capacity.id ?? "new"}-${index}`}
                capacity={capacity}
                positionUnitLabel={positionUnitLabel}
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
                capacities: [...segment.capacities, createCapacity(segment)],
              })
            }
          >
            <Plus className="h-3.5 w-3.5" />
            수용 규칙 추가
          </button>
        </>
      ) : (
        <div className="mt-2 grid gap-1">
          {segment.capacities.length === 0 ? (
            <p className="text-[11px] text-[#7b867f]">설정된 수용 규칙 없음</p>
          ) : (
            segment.capacities.map((capacity, index) => (
              <div
                key={`${capacity.id ?? "saved"}-${index}`}
                className="grid gap-2 rounded border border-[#e5e9e5] bg-white px-2 py-1.5 text-[11px] text-[#4f5a52] md:grid-cols-[120px_1fr_90px_90px]"
              >
                <span>{capacityTypeLabel(capacity.placementType)}</span>
                <span>
                  {capacity.potSize ?? "공통"} /{" "}
                  {capacityModeLabel(capacity.capacityMode)}
                </span>
                <span>수용 {capacity.capacityValue}</span>
                <span>
                  폭 {formatDecimal(capacity.unitSpan)}
                  {suffixUnit(positionUnitLabel)}
                </span>
              </div>
            ))
          )}
        </div>
      )}
    </article>
  );
}

function CapacityEditor({
  capacity,
  positionUnitLabel,
  onChange,
  onRemove,
}: {
  capacity: BedZoneSegmentCapacity;
  positionUnitLabel: string | null;
  onChange: (capacity: BedZoneSegmentCapacity) => void;
  onRemove: () => void;
}) {
  return (
    <div className="grid gap-1 rounded border border-[#e5e9e5] bg-white p-2 md:grid-cols-[1.1fr_0.7fr_0.8fr_0.7fr_0.55fr_0.75fr_auto_auto]">
      <div className="space-y-1">
        <select
          className={`${inputClass} w-full`}
          value={resolveCapacityType(capacity.placementType)}
          onChange={(event) =>
            onChange({
              ...capacity,
              placementType:
                event.target.value === "CUSTOM"
                  ? "CUSTOM:"
                  : event.target.value,
            })
          }
        >
          {fixedPlacementTypes.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
          <option value="CUSTOM">기타</option>
        </select>
        {capacity.placementType.startsWith("CUSTOM:") ? (
          <input
            className={`${inputClass} w-full`}
            placeholder="기타 규격명"
            value={capacity.placementType.slice(7)}
            onChange={(event) =>
              onChange({
                ...capacity,
                placementType: `CUSTOM:${event.target.value}`,
              })
            }
          />
        ) : null}
      </div>

      <input
        className={inputClass}
        placeholder="화분 크기"
        value={capacity.potSize ?? ""}
        onChange={(event) =>
          onChange({ ...capacity, potSize: event.target.value || null })
        }
      />

      <input
        className={inputClass}
        placeholder="메모"
        value={capacity.memo ?? ""}
        onChange={(event) =>
          onChange({ ...capacity, memo: event.target.value || null })
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

      <input
        className={inputClass}
        min={0}
        step="0.01"
        title={`점유 폭${suffixUnit(positionUnitLabel)}`}
        type="number"
        value={capacity.unitSpan}
        onChange={(event) =>
          onChange({ ...capacity, unitSpan: Number(event.target.value) })
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
        aria-label="수용 규칙 삭제"
        className="grid h-8 w-8 place-items-center text-[#c44747]"
        type="button"
        onClick={onRemove}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function NumberField({
  label,
  value,
  disabled,
  onChange,
}: {
  label: string;
  value: number;
  disabled: boolean;
  onChange: (value: number) => void;
}) {
  return (
    <div className="space-y-1">
      <label className="text-[11px] font-semibold text-[#5f6a63]">
        {label}
      </label>
      {disabled ? (
        <div className="flex h-8 items-center rounded-md border border-[#e3e8e3] bg-white px-2 text-xs text-[#243126]">
          {formatDecimal(value)}
        </div>
      ) : (
        <input
          className={inputClass}
          min={0}
          step="0.01"
          type="number"
          value={value}
          onChange={(event) => onChange(Number(event.target.value))}
        />
      )}
    </div>
  );
}

function createCapacity(segment: BedZoneSegment): BedZoneSegmentCapacity {
  return {
    id: null,
    placementType: "TRAY_20",
    potSize: null,
    capacityMode: "STANDARD",
    capacityValue: 0,
    unitSpan: Math.max(
      0,
      Number((segment.endPosition - segment.startPosition).toFixed(2)),
    ),
    allowed: true,
    memo: null,
  };
}

function resolveCapacityType(value: string) {
  return value.startsWith("CUSTOM:") ? "CUSTOM" : value;
}

function capacityTypeLabel(value: string) {
  const fixed = fixedPlacementTypes.find((item) => item.value === value);
  if (fixed) {
    return fixed.label;
  }
  if (value.startsWith("CUSTOM:")) {
    return value.slice(7) || "기타";
  }
  return value;
}

function segmentTypeLabel(value: BedZoneSegment["segmentType"]) {
  return {
    START: "앞",
    MIDDLE: "중간",
    END: "끝",
    CUSTOM: "사용자 정의",
  }[value];
}

function capacityModeLabel(value: BedZoneSegmentCapacity["capacityMode"]) {
  return {
    SPACIOUS: "여유",
    STANDARD: "표준",
    EXPANDED: "확장",
    COMPRESSED: "압축",
    TEMPORARY: "임시",
  }[value];
}

function formatPositionUnit(profile: BedZonePlacementProfile) {
  if (profile.positionUnitCount === null) {
    return "-";
  }
  return `${formatDecimal(profile.positionUnitCount)}${suffixUnit(profile.positionUnitLabel)}`;
}

function suffixUnit(label: string | null) {
  return label ? ` ${label}` : "";
}

function formatDecimal(value: number) {
  if (Number.isInteger(value)) {
    return String(value);
  }
  return value.toFixed(2).replace(/\.?0+$/, "");
}

function updateSegment(
  setter: Dispatch<SetStateAction<BedZonePlacementProfile | null>>,
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
  setter: Dispatch<SetStateAction<BedZonePlacementProfile | null>>,
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
  setter: Dispatch<SetStateAction<BedZonePlacementProfile | null>>,
  positionUnitCount: number | null,
) {
  setter((current) => {
    if (!current) {
      return current;
    }

    const startPosition =
      current.segments.length === 0
        ? 0
        : (current.segments[current.segments.length - 1]?.endPosition ?? 0);
    const endPosition = positionUnitCount ?? startPosition;

    return {
      ...current,
      segments: [
        ...current.segments,
        {
          id: null,
          name: `사용자 구간 ${current.segments.length + 1}`,
          segmentType: "CUSTOM",
          sortOrder: current.segments.length + 1,
          startPosition,
          endPosition,
          memo: null,
          capacities: [],
        },
      ],
    };
  });
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
