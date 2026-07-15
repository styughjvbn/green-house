"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import type { House, VarietyOption } from "@/entities/farm/types";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import {
  createMultipleOrchidGroups,
  cancelMultiCreateWork,
  getMultiCreateCancellationEligibility,
  getOrchidGroupCollections,
} from "../../api/orchidManagementApi";
import type { OrchidGroupCollection } from "../../model/types";
import type { MultiCreateWorkResult } from "../../model/types";
import VarietySearchSelect from "./VarietySearchSelect";

type Row = {
  key: string;
  bedZoneId: string;
  variety: VarietyOption | null;
  quantity: string;
  potSize: string;
  ageYear: string;
  startCell: string;
  endCell: string;
  memo: string;
  collectionIds: number[];
};

export default function MultiCreateOrchidGroupForm({
  house,
  onClose,
}: {
  house: House;
  onClose: () => void;
}) {
  const router = useRouter();
  const zones = useMemo(
    () =>
      house.physicalBeds.flatMap((bed) =>
        bed.bedZones.map((zone) => ({
          id: zone.id,
          label: `${bed.number}배드 ${zone.name}`,
        })),
      ),
    [house],
  );
  const [title, setTitle] = useState(`${house.number}동 난 묶음 일괄 등록`);
  const [workDate, setWorkDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [worker, setWorker] = useState("");
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [rows, setRows] = useState<Row[]>(() => [newRow(zones[0]?.id, 1)]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<MultiCreateWorkResult | null>(null);
  const [canceling, setCanceling] = useState(false);
  const [collections, setCollections] = useState<OrchidGroupCollection[]>([]);

  useEffect(() => {
    let active = true;
    void getOrchidGroupCollections().then((items) => {
      if (active) {
        setCollections(items.filter((item) => item.status === "ACTIVE"));
      }
    });
    return () => {
      active = false;
    };
  }, []);

  function updateRow(key: string, patch: Partial<Row>) {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (rows.some((row) => !row.variety || !row.bedZoneId)) {
      setError("모든 행의 위치와 품종을 선택해주세요.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const result = await createMultipleOrchidGroups({
        idempotencyKey,
        title: title.trim(),
        workDate,
        worker: worker.trim() || null,
        memo: null,
        rows: rows.map((row) => ({
          orchidGroup: {
            bedZoneId: Number(row.bedZoneId),
            varietyId: row.variety!.id,
            quantity: Number(row.quantity),
            potSize: row.potSize || null,
            ageYear: row.ageYear ? Number(row.ageYear) : null,
            status: "정상",
            placementType: null,
            trayCount: null,
            splitPlacementAllowed: false,
            startPosition: Number(row.startCell) - 1,
            endPosition: Number(row.endCell),
            memo: row.memo.trim() || null,
          },
          collectionIds: row.collectionIds,
        })),
      });
      setResult(result);
      router.refresh();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "일괄 생성하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  async function cancelCreation() {
    if (!result) return;
    setCanceling(true);
    setError(null);
    try {
      const eligibility = await getMultiCreateCancellationEligibility(
        result.operation.id,
      );
      if (!eligibility.cancelable) {
        setError(
          eligibility.blockers.map((blocker) => blocker.message).join(" ") ||
            "현재 생성 작업을 취소할 수 없습니다.",
        );
        return;
      }
      setResult(await cancelMultiCreateWork(result.operation.id));
      router.refresh();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "생성 작업을 취소하지 못했습니다.",
      );
    } finally {
      setCanceling(false);
    }
  }

  return (
    <section className="min-h-0 overflow-y-auto rounded-md border border-[#9dcaaa] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-[#16713a]">난 묶음 일괄 추가</p>
          <p className="mt-1 text-xs text-[#5c6a60]">
            여러 위치를 하나의 생성 작업으로 기록합니다.
          </p>
        </div>
        <button
          className="rounded-md border px-2 py-1 text-xs"
          type="button"
          onClick={onClose}
        >
          닫기
        </button>
      </div>
      {result != null ? (
        <div className="mt-3 space-y-2 rounded-md bg-[#edf8ef] p-3 text-sm font-semibold text-[#16713a]">
          <p>
            {result.operation.status === "CANCELED"
              ? "다중 생성 작업을 취소했습니다."
              : `난 묶음 ${result.createdOrchidGroups.length}개를 생성했습니다.`}
          </p>
          {error ? <p className="text-[#9c321d]">{error}</p> : null}
          {result.operation.status !== "CANCELED" ? (
            <button
              className="rounded-md border border-[#b75b49] bg-white px-3 py-2 text-xs text-[#9c321d] disabled:opacity-50"
              disabled={canceling}
              type="button"
              onClick={() => void cancelCreation()}
            >
              {canceling ? "취소 가능 여부 확인 중" : "방금 생성한 작업 취소"}
            </button>
          ) : null}
        </div>
      ) : (
        <form className="mt-3 space-y-3" onSubmit={submit}>
          <div className="grid grid-cols-2 gap-2">
            <Field label="작업명" value={title} onChange={setTitle} />
            <Field
              label="작업일"
              type="date"
              value={workDate}
              onChange={setWorkDate}
            />
          </div>
          <Field label="작업자" value={worker} onChange={setWorker} />
          {rows.map((row, index) => (
            <div
              key={row.key}
              className="space-y-2 rounded-md border border-[#dce7dc] bg-[#f9fcf8] p-3"
            >
              <div className="flex justify-between text-sm font-bold">
                <span>생성 행 {index + 1}</span>
                {rows.length > 1 ? (
                  <button
                    type="button"
                    className="text-[#b23b2a]"
                    onClick={() =>
                      setRows((items) =>
                        items.filter((item) => item.key !== row.key),
                      )
                    }
                  >
                    행 삭제
                  </button>
                ) : null}
              </div>
              <label className="block text-sm font-semibold text-[#435047]">
                위치
                <select
                  className="mt-1 w-full rounded-md border px-2 py-2"
                  value={row.bedZoneId}
                  onChange={(event) =>
                    updateRow(row.key, { bedZoneId: event.target.value })
                  }
                >
                  {zones.map((zone) => (
                    <option key={zone.id} value={zone.id}>
                      {zone.label}
                    </option>
                  ))}
                </select>
              </label>
              <VarietySearchSelect
                selectedVariety={row.variety}
                disabled={saving}
                onSelect={(variety) =>
                  updateRow(row.key, {
                    variety,
                    potSize: row.potSize || variety.defaultPotSize || "",
                  })
                }
              />
              <div className="grid grid-cols-3 gap-2">
                <Field
                  label="수량"
                  type="number"
                  min="1"
                  value={row.quantity}
                  onChange={(value) => updateRow(row.key, { quantity: value })}
                />
                <label className="text-sm font-semibold text-[#435047]">
                  화분
                  <select
                    className="mt-1 w-full rounded-md border px-2 py-2"
                    value={row.potSize}
                    onChange={(event) =>
                      updateRow(row.key, { potSize: event.target.value })
                    }
                  >
                    {POT_SIZE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
                <Field
                  label="초기 년생"
                  type="number"
                  min="0"
                  value={row.ageYear}
                  onChange={(value) => updateRow(row.key, { ageYear: value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <Field
                  label="시작 칸"
                  type="number"
                  min="1"
                  value={row.startCell}
                  onChange={(value) => updateRow(row.key, { startCell: value })}
                />
                <Field
                  label="끝 칸"
                  type="number"
                  min="1"
                  value={row.endCell}
                  onChange={(value) => updateRow(row.key, { endCell: value })}
                />
              </div>
              <Field
                label="메모"
                value={row.memo}
                onChange={(value) => updateRow(row.key, { memo: value })}
              />
              {collections.length > 0 ? (
                <fieldset>
                  <legend className="text-sm font-semibold text-[#435047]">
                    사용자 그룹 소속
                  </legend>
                  <div className="mt-1 flex flex-wrap gap-2">
                    {collections.map((collection) => (
                      <label
                        key={collection.id}
                        className="flex items-center gap-1 rounded-md border bg-white px-2 py-1 text-xs"
                      >
                        <input
                          checked={row.collectionIds.includes(collection.id)}
                          type="checkbox"
                          onChange={(event) =>
                            updateRow(row.key, {
                              collectionIds: event.target.checked
                                ? [...row.collectionIds, collection.id]
                                : row.collectionIds.filter(
                                    (id) => id !== collection.id,
                                  ),
                            })
                          }
                        />
                        {collection.name}
                      </label>
                    ))}
                  </div>
                </fieldset>
              ) : null}
            </div>
          ))}
          {error ? (
            <p className="rounded-md bg-[#fff1ec] p-2 text-sm text-[#9c321d]">
              {error}
            </p>
          ) : null}
          <div className="flex gap-2">
            <button
              className="rounded-md border px-3 py-2 text-sm font-semibold"
              type="button"
              onClick={() =>
                setRows((items) => [
                  ...items,
                  newRow(zones[0]?.id, items.length + 1),
                ])
              }
            >
              행 추가
            </button>
            <button
              className="flex-1 rounded-md bg-[#159447] px-3 py-2 text-sm font-bold text-white disabled:opacity-50"
              disabled={saving || !title.trim()}
              type="submit"
            >
              {saving ? "저장 중" : `${rows.length}개 일괄 생성`}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

function newRow(bedZoneId: number | undefined, cell: number): Row {
  return {
    key: crypto.randomUUID(),
    bedZoneId: bedZoneId ? String(bedZoneId) : "",
    variety: null,
    quantity: "1",
    potSize: "",
    ageYear: "",
    startCell: String(cell),
    endCell: String(cell),
    memo: "",
    collectionIds: [],
  };
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  min,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  min?: string;
}) {
  return (
    <label className="block text-sm font-semibold text-[#435047]">
      {label}
      <input
        className="mt-1 w-full rounded-md border px-2 py-2 font-normal text-[#17251b]"
        required={
          label !== "작업자" && label !== "메모" && label !== "초기 년생"
        }
        min={min}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
