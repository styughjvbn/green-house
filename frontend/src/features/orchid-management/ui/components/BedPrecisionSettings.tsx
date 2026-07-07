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
  BedZoneCapacity,
  BedZonePlacementProfile,
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

  if (!zone) return null;

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

  const current = editing ? draft : profile;

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
            {zone.name} / 규칙 {profile?.capacities.length ?? 0}개
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
          <div className="space-y-2">
            {current?.capacities.map((capacity, index) => (
              <CapacityEditor
                key={`${capacity.id ?? "new"}-${index}`}
                capacity={capacity}
                editing={editing}
                onChange={(next) =>
                  setDraft((currentDraft) =>
                    currentDraft
                      ? {
                          ...currentDraft,
                          capacities: currentDraft.capacities.map(
                            (item, itemIndex) =>
                              itemIndex === index ? next : item,
                          ),
                        }
                      : currentDraft,
                  )
                }
                onRemove={() =>
                  setDraft((currentDraft) =>
                    currentDraft
                      ? {
                          ...currentDraft,
                          capacities: currentDraft.capacities.filter(
                            (_, itemIndex) => itemIndex !== index,
                          ),
                        }
                      : currentDraft,
                  )
                }
              />
            ))}
          </div>
          {editing ? (
            <button
              className="mt-3 flex items-center gap-1 rounded-md border border-dashed border-[#9fc9a8] px-3 py-2 text-xs font-semibold text-[#16843d]"
              type="button"
              onClick={() =>
                setDraft((currentDraft) =>
                  currentDraft
                    ? {
                        ...currentDraft,
                        capacities: [
                          ...currentDraft.capacities,
                          createCapacity(),
                        ],
                      }
                    : currentDraft,
                )
              }
            >
              <Plus className="h-4 w-4" />
              규칙 추가
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

function CapacityEditor({
  capacity,
  editing,
  onChange,
  onRemove,
}: {
  capacity: BedZoneCapacity;
  editing: boolean;
  onChange: (capacity: BedZoneCapacity) => void;
  onRemove: () => void;
}) {
  if (!editing) {
    return (
      <div className="grid gap-2 rounded border border-[#e5e9e5] bg-white px-2 py-1.5 text-[11px] text-[#4f5a52] md:grid-cols-[120px_1fr_90px_90px]">
        <span>{capacityTypeLabel(capacity.placementType)}</span>
        <span>
          {capacity.potSize ?? "공통"} / {capacity.capacityMode}
        </span>
        <span>수용 {capacity.capacityValue}</span>
        <span>폭 {capacity.unitSpan}</span>
      </div>
    );
  }

  return (
    <div className="grid gap-1 rounded border border-[#e5e9e5] bg-white p-2 md:grid-cols-[1.1fr_0.7fr_0.7fr_0.55fr_0.75fr_auto_auto]">
      <select
        className={`${inputClass} w-full`}
        value={capacity.placementType}
        onChange={(event) =>
          onChange({ ...capacity, placementType: event.target.value })
        }
      >
        <option value="TRAY_15">15구 트레이</option>
        <option value="TRAY_20">20구 트레이</option>
        <option value="TRAY_24">24구 트레이</option>
        <option value="SINGLE_POT">단독 화분</option>
        <option value="HANGING">행잉</option>
        <option value="CUSTOM:기타">기타</option>
      </select>
      <input
        className={inputClass}
        placeholder="화분 크기"
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
            capacityMode: event.target.value as BedZoneCapacity["capacityMode"],
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
        className="grid h-8 w-8 place-items-center text-[#c44747]"
        type="button"
        onClick={onRemove}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function createCapacity(): BedZoneCapacity {
  return {
    id: null,
    placementType: "TRAY_20",
    potSize: null,
    capacityMode: "STANDARD",
    capacityValue: 0,
    unitSpan: 6,
    allowed: true,
    memo: null,
  };
}

function capacityTypeLabel(value: string) {
  return value.startsWith("CUSTOM:") ? value.slice(7) || "기타" : value;
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
