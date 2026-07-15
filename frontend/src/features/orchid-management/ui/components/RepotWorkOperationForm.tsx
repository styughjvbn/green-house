"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import type { House, OrchidGroup } from "@/entities/farm/types";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import {
  executeRepotWork,
  getOrchidGroupCollections,
} from "../../api/orchidManagementApi";
import type { OrchidGroupCollection, RepotWorkResult } from "../../model/types";

type ResultRow = {
  key: string;
  bedZoneId: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  startCell: string;
  endCell: string;
  memo: string;
};

export default function RepotWorkOperationForm({
  houses,
  source,
  onClose,
}: {
  houses: House[];
  source: OrchidGroup;
  onClose: () => void;
}) {
  const router = useRouter();
  const zones = useMemo(
    () =>
      houses.flatMap((house) =>
        house.physicalBeds.flatMap((bed) =>
          bed.bedZones.map((zone) => ({
            id: zone.id,
            label: `${house.number}동 ${bed.number}다이 ${zone.name}`,
          })),
        ),
      ),
    [houses],
  );
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [title, setTitle] = useState(`${source.varietyName} 분갈이`);
  const [workDate, setWorkDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [inputQuantity, setInputQuantity] = useState(String(source.quantity));
  const [lossQuantity, setLossQuantity] = useState("0");
  const [lossReason, setLossReason] = useState("");
  const [rows, setRows] = useState<ResultRow[]>(() => [newRow(source)]);
  const [sourceCollections, setSourceCollections] = useState<
    OrchidGroupCollection[]
  >([]);
  const [inheritCollectionIds, setInheritCollectionIds] = useState<number[]>(
    [],
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RepotWorkResult | null>(null);

  useEffect(() => {
    let active = true;
    void getOrchidGroupCollections()
      .then((collections) => {
        if (!active) return;
        setSourceCollections(
          collections.filter(
            (collection) =>
              collection.status === "ACTIVE" &&
              collection.members.some(
                (member) => member.orchidGroupId === source.id,
              ),
          ),
        );
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : "사용자 그룹을 불러오지 못했습니다.",
          );
        }
      });
    return () => {
      active = false;
    };
  }, [source.id]);

  const input = toInteger(inputQuantity);
  const loss = toInteger(lossQuantity);
  const resultTotal = rows.reduce(
    (sum, row) => sum + (toInteger(row.quantity) ?? 0),
    0,
  );
  const remaining = input == null ? null : source.quantity - input;
  const balanced =
    input != null && loss != null && resultTotal + loss === input;

  function updateRow(key: string, patch: Partial<ResultRow>) {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await executeRepotWork({
        idempotencyKey,
        title: title.trim(),
        workDate,
        worker: worker.trim() || null,
        memo: memo.trim() || null,
        sourceOrchidGroupId: source.id,
        inputQuantity: input!,
        lossQuantity: loss!,
        lossReason: loss! > 0 ? lossReason.trim() : null,
        inheritCollectionIds,
        results: rows.map((row) => ({
          bedZoneId: Number(row.bedZoneId),
          quantity: Number(row.quantity),
          potSize: row.potSize || null,
          ageYear: row.ageYear ? Number(row.ageYear) : null,
          placementType: null,
          trayCount: null,
          splitPlacementAllowed: false,
          startPosition: Number(row.startCell) - 1,
          endPosition: Number(row.endCell),
          memo: row.memo.trim() || null,
        })),
      });
      setResult(saved);
      router.refresh();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "분갈이하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  function validate() {
    if (input == null || input < 1 || input > source.quantity) {
      return `투입 수량은 1 이상 ${source.quantity} 이하로 입력해주세요.`;
    }
    if (loss == null || loss < 0) return "손실 수량은 0 이상이어야 합니다.";
    if (!balanced)
      return "투입 수량과 결과 수량 합계 + 손실 수량이 같아야 합니다.";
    if (loss > 0 && !lossReason.trim()) return "손실 사유를 입력해주세요.";
    if (
      rows.some(
        (row) =>
          !row.bedZoneId ||
          toInteger(row.quantity) == null ||
          Number(row.quantity) < 1 ||
          Number(row.startCell) < 1 ||
          Number(row.endCell) < Number(row.startCell),
      )
    ) {
      return "모든 결과 행의 위치, 수량, 시작·끝 칸을 확인해주세요.";
    }
    if (hasOverlappingResults(rows))
      return "결과 난 묶음의 배치 범위가 서로 겹칩니다.";
    if (remaining != null && remaining > 0 && overlapsSource(rows, source)) {
      return "부분 분갈이에서는 남은 원본과 결과 난 묶음의 위치가 겹칠 수 없습니다.";
    }
    return null;
  }

  return (
    <section className="min-h-0 overflow-y-auto rounded-md border border-[#9dcaaa] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-[#16713a]">난 묶음 분갈이</p>
          <p className="mt-1 text-xs text-[#5c6a60]">
            원본을 보존하고 결과 난 묶음과 계보를 새로 만듭니다.
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

      {result ? (
        <div className="mt-3 space-y-2 rounded-md bg-[#edf8ef] p-3 text-sm text-[#16713a]">
          <p className="font-bold">분갈이 작업을 완료했습니다.</p>
          <p>
            원본 잔여 {result.sourceOrchidGroup.quantity}분 · 결과{" "}
            {result.resultOrchidGroups.length}묶음 /{" "}
            {result.resultOrchidGroups.reduce(
              (sum, group) => sum + group.quantity,
              0,
            )}
            분
            {result.lossQuantity > 0 ? ` · 손실 ${result.lossQuantity}분` : ""}
          </p>
          <p className="text-xs text-[#526158]">
            작업 번호 #{result.operation.id}
          </p>
        </div>
      ) : (
        <form className="mt-3 space-y-3" onSubmit={submit}>
          <div className="rounded-md border border-[#dce7dc] bg-[#f7faf6] p-3 text-sm">
            <p className="font-bold text-[#17251b]">{source.varietyName}</p>
            <p className="mt-1 text-xs text-[#5c6a60]">
              현재 {source.quantity}분 · {source.houseNumber}동{" "}
              {source.physicalBedNumber}다이 {source.bedZoneName} ·{" "}
              {source.potSize ?? "화분 미지정"}
            </p>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field label="작업명" value={title} onChange={setTitle} />
            <Field
              label="작업일"
              type="date"
              value={workDate}
              onChange={setWorkDate}
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field
              label="작업자"
              required={false}
              value={worker}
              onChange={setWorker}
            />
            <Field
              label="작업 메모"
              required={false}
              value={memo}
              onChange={setMemo}
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Field
              label="분갈이 투입 수량"
              min="1"
              max={String(source.quantity)}
              type="number"
              value={inputQuantity}
              onChange={setInputQuantity}
            />
            <Field
              label="손실 수량"
              min="0"
              type="number"
              value={lossQuantity}
              onChange={setLossQuantity}
            />
          </div>
          {loss != null && loss > 0 ? (
            <Field
              label="손실 사유"
              value={lossReason}
              onChange={setLossReason}
            />
          ) : null}

          <div
            className={`rounded-md p-2 text-xs font-semibold ${balanced && remaining != null && remaining >= 0 ? "bg-[#edf8ef] text-[#16713a]" : "bg-[#fff1ec] text-[#9c321d]"}`}
          >
            투입 {input ?? "-"} = 결과 {resultTotal} + 손실 {loss ?? "-"} · 원본
            잔여 {remaining ?? "-"}
          </div>

          {rows.map((row, index) => (
            <div
              key={row.key}
              className="space-y-2 rounded-md border border-[#dce7dc] bg-[#f9fcf8] p-3"
            >
              <div className="flex items-center justify-between text-sm font-bold">
                <span>결과 난 묶음 {index + 1}</span>
                {rows.length > 1 ? (
                  <button
                    className="text-xs text-[#b23b2a]"
                    type="button"
                    onClick={() =>
                      setRows((current) =>
                        current.filter((item) => item.key !== row.key),
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
                  className="mt-1 w-full rounded-md border px-2 py-2 font-normal"
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
              <div className="grid grid-cols-3 gap-2">
                <Field
                  label="결과 수량"
                  min="1"
                  type="number"
                  value={row.quantity}
                  onChange={(value) => updateRow(row.key, { quantity: value })}
                />
                <label className="text-sm font-semibold text-[#435047]">
                  새 화분
                  <select
                    className="mt-1 w-full rounded-md border px-2 py-2 font-normal"
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
                  label="새 년생"
                  min="0"
                  required={false}
                  type="number"
                  value={row.ageYear}
                  onChange={(value) => updateRow(row.key, { ageYear: value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <Field
                  label="시작 칸"
                  min="1"
                  type="number"
                  value={row.startCell}
                  onChange={(value) => updateRow(row.key, { startCell: value })}
                />
                <Field
                  label="끝 칸"
                  min="1"
                  type="number"
                  value={row.endCell}
                  onChange={(value) => updateRow(row.key, { endCell: value })}
                />
              </div>
              <Field
                label="결과 메모"
                required={false}
                value={row.memo}
                onChange={(value) => updateRow(row.key, { memo: value })}
              />
            </div>
          ))}

          <button
            className="rounded-md border px-3 py-2 text-sm font-semibold"
            type="button"
            onClick={() =>
              setRows((current) => [...current, newRow(source, current.length)])
            }
          >
            결과 행 추가
          </button>

          {sourceCollections.length > 0 ? (
            <fieldset>
              <legend className="text-sm font-semibold text-[#435047]">
                사용자 그룹 상속
              </legend>
              <p className="mt-0.5 text-xs text-[#6a766e]">
                선택한 그룹만 모든 결과 난 묶음에 상속합니다.
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {sourceCollections.map((collection) => (
                  <label
                    key={collection.id}
                    className="flex items-center gap-1 rounded-md border bg-white px-2 py-1 text-xs"
                  >
                    <input
                      type="checkbox"
                      checked={inheritCollectionIds.includes(collection.id)}
                      onChange={(event) =>
                        setInheritCollectionIds((current) =>
                          event.target.checked
                            ? [...current, collection.id]
                            : current.filter((id) => id !== collection.id),
                        )
                      }
                    />
                    {collection.name}
                  </label>
                ))}
              </div>
            </fieldset>
          ) : null}

          {error ? (
            <p className="rounded-md bg-[#fff1ec] p-2 text-sm text-[#9c321d]">
              {error}
            </p>
          ) : null}
          <button
            className="w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-bold text-white disabled:opacity-50"
            disabled={saving || !title.trim()}
            type="submit"
          >
            {saving
              ? "분갈이 처리 중"
              : remaining === 0
                ? "전체 분갈이 완료"
                : "부분 분갈이 완료"}
          </button>
        </form>
      )}
    </section>
  );
}

function newRow(source: OrchidGroup, offset = 0): ResultRow {
  const sourceStart =
    source.startPosition == null ? 1 : Math.floor(source.startPosition) + 1;
  const sourceEnd =
    source.endPosition == null ? sourceStart : Math.ceil(source.endPosition);
  return {
    key: crypto.randomUUID(),
    bedZoneId: String(source.bedZoneId),
    quantity: offset === 0 ? String(source.quantity) : "1",
    potSize: source.potSize ?? "",
    ageYear: source.ageYear == null ? "" : String(source.ageYear),
    startCell: String(sourceStart + offset),
    endCell: String(sourceEnd + offset),
    memo: "",
  };
}

function toInteger(value: string) {
  if (!/^\d+$/.test(value)) return null;
  return Number(value);
}

function hasOverlappingResults(rows: ResultRow[]) {
  return rows.some((row, index) =>
    rows
      .slice(index + 1)
      .some(
        (other) =>
          row.bedZoneId === other.bedZoneId &&
          Number(row.startCell) - 1 < Number(other.endCell) &&
          Number(row.endCell) > Number(other.startCell) - 1,
      ),
  );
}

function overlapsSource(rows: ResultRow[], source: OrchidGroup) {
  if (source.startPosition == null || source.endPosition == null) return false;
  return rows.some(
    (row) =>
      Number(row.bedZoneId) === source.bedZoneId &&
      Number(row.startCell) - 1 < source.endPosition! &&
      Number(row.endCell) > source.startPosition!,
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  min,
  max,
  required = true,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  min?: string;
  max?: string;
  required?: boolean;
}) {
  return (
    <label className="block text-sm font-semibold text-[#435047]">
      {label}
      <input
        className="mt-1 w-full rounded-md border px-2 py-2 font-normal text-[#17251b]"
        max={max}
        min={min}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
