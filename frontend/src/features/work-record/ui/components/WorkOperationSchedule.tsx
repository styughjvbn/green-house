"use client";

import { useEffect, useMemo, useState } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  WorkOperation,
  WorkOperationStatus,
  WorkOperationTarget,
} from "@/entities/farm/types";
import {
  completeWorkOperation,
  getWorkOperations,
  transitionWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import { OperationResult } from "./HouseWorkOperationPanel";
import { StructureWorkExecutionDialog } from "./StructureWorkExecutionDialog";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];

export function WorkOperationSchedule({
  bedZones = [],
  houses = [],
  orchidGroups = [],
  onClose,
  refreshKey = 0,
}: {
  bedZones?: BedZone[];
  houses?: House[];
  orchidGroups?: OrchidGroup[];
  onClose?: () => void;
  refreshKey?: number;
}) {
  const [month, setMonth] = useState(() =>
    new Date().toISOString().slice(0, 7),
  );
  const [status, setStatus] = useState<WorkOperationStatus | "">("");
  const [operations, setOperations] = useState<WorkOperation[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);
  const range = useMemo(() => monthRange(month), [month]);
  const selected = operations.find((item) => item.id === selectedId) ?? null;

  useEffect(() => {
    let active = true;
    void getWorkOperations({ from: range.from, to: range.to, status })
      .then((result) => {
        if (!active) return;
        setOperations(result);
        setSelectedId((current) =>
          result.some((item) => item.id === current) ? current : null,
        );
        setError(null);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : "기간 작업을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [range.from, range.to, status, refreshKey]);

  function changeMonth(offset: number) {
    const [year, monthNumber] = month.split("-").map(Number);
    const next = new Date(Date.UTC(year, monthNumber - 1 + offset, 1));
    setLoading(true);
    setMonth(next.toISOString().slice(0, 7));
  }

  function updateOperation(updated: WorkOperation) {
    setOperations((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
  }

  async function run(action: () => Promise<WorkOperation>) {
    setLoading(true);
    setError(null);
    try {
      updateOperation(await action());
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "작업 상태를 변경하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  const cells = calendarCells(month);

  return (
    <section className="rounded-md border border-[#b9d7bf] bg-[#f5fbf5] p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[#17251b]">기간 작업 캘린더</h2>
          <p className="mt-1 text-sm text-[#5c6a60]">
            저장된 작업을 다시 열어 전체·대상별 진행 상태를 관리합니다.
          </p>
        </div>
        {onClose ? (
          <button
            className="rounded-md border bg-white px-3 py-2 text-sm"
            type="button"
            onClick={onClose}
          >
            닫기
          </button>
        ) : null}
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            className="rounded-md border bg-white px-3 py-2 text-sm"
            type="button"
            onClick={() => changeMonth(-1)}
          >
            이전
          </button>
          <strong className="min-w-24 text-center text-[#26352b]">
            {month.replace("-", "년 ")}월
          </strong>
          <button
            className="rounded-md border bg-white px-3 py-2 text-sm"
            type="button"
            onClick={() => changeMonth(1)}
          >
            다음
          </button>
        </div>
        <label className="text-sm font-semibold text-[#435047]">
          상태{" "}
          <select
            className="rounded-md border bg-white px-3 py-2 font-normal"
            value={status}
            onChange={(event) => {
              setLoading(true);
              setStatus(event.target.value as WorkOperationStatus | "");
            }}
          >
            <option value="">전체</option>
            <option value="DRAFT">초안</option>
            <option value="PLANNED">계획</option>
            <option value="IN_PROGRESS">진행 중</option>
            <option value="PAUSED">일시중지</option>
            <option value="COMPLETED">완료</option>
            <option value="CORRECTED">보정됨</option>
            <option value="CANCELED">취소</option>
          </select>
        </label>
      </div>

      {error ? (
        <p className="mt-3 rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
          {error}
        </p>
      ) : null}

      <div className="mt-3 grid grid-cols-7 overflow-hidden rounded-md border border-[#d7ddd4] bg-white">
        {WEEKDAYS.map((day) => (
          <div
            className="border-b bg-[#f2f6f1] px-2 py-1.5 text-center text-xs font-bold text-[#526057]"
            key={day}
          >
            {day}
          </div>
        ))}
        {cells.map((date, index) => {
          const dayOperations = date
            ? operations.filter((operation) => includesDate(operation, date))
            : [];
          return (
            <div
              className="min-h-24 border-r border-b border-[#edf0ec] p-1.5 last:border-r-0"
              key={date ?? `blank-${index}`}
            >
              {date ? (
                <p className="text-xs font-semibold text-[#6a766e]">
                  {Number(date.slice(-2))}
                </p>
              ) : null}
              <div className="mt-1 space-y-1">
                {dayOperations.slice(0, 3).map((operation) => (
                  <button
                    className={`block w-full truncate rounded px-1.5 py-1 text-left text-[11px] font-semibold ${selectedId === operation.id ? "bg-[#16713a] text-white" : statusClass(operation.status)}`}
                    key={operation.id}
                    title={operation.title}
                    type="button"
                    onClick={() => setSelectedId(operation.id)}
                  >
                    {operation.title}
                  </button>
                ))}
                {dayOperations.length > 3 ? (
                  <p className="text-[10px] text-[#6a766e]">
                    +{dayOperations.length - 3}건
                  </p>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
      {loading ? (
        <p className="mt-3 text-sm text-[#5c6a60]">기간 작업 확인 중</p>
      ) : null}

      {selected ? (
        <OperationResult
          operation={selected}
          loading={loading}
          onComplete={() => void run(() => completeWorkOperation(selected.id))}
          onOperationAction={(action) =>
            void run(() => transitionWorkOperation(selected.id, action))
          }
          onTargetAction={(targetId, action) =>
            void run(() =>
              transitionWorkOperationTarget(
                selected.id,
                targetId,
                action,
                selected.worker,
              ),
            )
          }
          onExecuteTarget={setExecutionTarget}
        />
      ) : null}
      {selected && executionTarget ? (
        <StructureWorkExecutionDialog
          bedZones={bedZones}
          houses={houses}
          operation={selected}
          orchidGroups={orchidGroups}
          source={
            executionTarget.orchidGroupId == null
              ? null
              : (orchidGroups.find(
                  (group) => group.id === executionTarget.orchidGroupId,
                ) ?? null)
          }
          target={executionTarget}
          onClose={() => setExecutionTarget(null)}
          onSaved={(updated) => {
            updateOperation(updated);
            setExecutionTarget(null);
          }}
        />
      ) : null}
    </section>
  );
}

function monthRange(month: string) {
  const [year, monthNumber] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  return {
    from: `${month}-01`,
    to: `${month}-${String(lastDay).padStart(2, "0")}`,
  };
}

function calendarCells(month: string): Array<string | null> {
  const [year, monthNumber] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const sundayBased = new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay();
  const leading = (sundayBased + 6) % 7;
  const cells: Array<string | null> = Array.from(
    { length: leading },
    () => null,
  );
  for (let day = 1; day <= lastDay; day += 1) {
    cells.push(`${month}-${String(day).padStart(2, "0")}`);
  }
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

function includesDate(operation: WorkOperation, date: string) {
  return (
    operation.plannedStartDate <= date &&
    (operation.plannedEndDate ?? operation.plannedStartDate) >= date
  );
}

function statusClass(status: WorkOperationStatus) {
  if (status === "COMPLETED" || status === "CORRECTED")
    return "bg-[#e7f6eb] text-[#10783a]";
  if (status === "IN_PROGRESS") return "bg-[#e6f0ff] text-[#246df2]";
  if (status === "PAUSED") return "bg-[#fff3d8] text-[#8a5a12]";
  if (status === "CANCELED") return "bg-[#f2eeee] text-[#765f5a]";
  return "bg-[#eef2ed] text-[#435047]";
}
