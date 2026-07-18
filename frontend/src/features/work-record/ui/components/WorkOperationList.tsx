"use client";

import { Plus, RefreshCw } from "lucide-react";
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
import { OperationResult } from "./WorkOperationResult";
import { StructureWorkExecutionDialog } from "./StructureWorkExecutionDialog";

export function WorkOperationList({
  bedZones,
  houses,
  orchidGroups,
  refreshKey,
  view = "MANAGEMENT",
  onCreateWork,
}: {
  bedZones: BedZone[];
  houses: House[];
  orchidGroups: OrchidGroup[];
  refreshKey: number;
  view?: "MANAGEMENT" | "HISTORY";
  onCreateWork?: () => void;
}) {
  const [operations, setOperations] = useState<WorkOperation[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [status, setStatus] = useState<WorkOperationStatus | "">("");
  const [keyword, setKeyword] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [executionTarget, setExecutionTarget] =
    useState<WorkOperationTarget | null>(null);

  useEffect(() => {
    let active = true;
    void getWorkOperations({ status, view })
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
              : "작업 목록을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [refreshKey, reloadVersion, status, view]);

  const filtered = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) return operations;
    return operations.filter((operation) =>
      [operation.title, operation.workType, operation.worker, operation.memo]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(normalized)),
    );
  }, [keyword, operations]);
  const selected = operations.find((item) => item.id === selectedId) ?? null;

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

  return (
    <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(380px,0.8fr)]">
      <section className="flex min-h-0 flex-col rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-[#17251b]">
              {view === "MANAGEMENT" ? "작업 목록" : "작업 이력"}
            </h2>
            <p className="mt-1 text-sm text-[#6a766e]">
              {view === "MANAGEMENT"
                ? `진행할 작업과 오늘 변경된 작업 ${filtered.length}건`
                : `완료·취소·보정된 작업 ${filtered.length}건`}
            </p>
          </div>
          {onCreateWork ? (
            <div className="flex flex-wrap gap-2">
              <button
                className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white"
                type="button"
                onClick={onCreateWork}
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                작업 등록
              </button>
            </div>
          ) : null}
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <input
            className="min-w-56 flex-1 rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
            placeholder="작업명, 유형, 작업자 검색"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <select
            className="rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-sm"
            value={status}
            onChange={(event) => {
              setLoading(true);
              setStatus(event.target.value as WorkOperationStatus | "");
            }}
          >
            <option value="">
              {view === "MANAGEMENT" ? "관리 대상 전체" : "종료 상태 전체"}
            </option>
            {view === "MANAGEMENT" ? (
              <>
                <option value="PLANNED">계획</option>
                <option value="IN_PROGRESS">진행 중</option>
                <option value="PAUSED">일시중지</option>
              </>
            ) : null}
            <option value="COMPLETED">완료</option>
            <option value="CORRECTED">보정됨</option>
            <option value="CANCELED">취소</option>
          </select>
          <button
            aria-label="작업 목록 새로고침"
            className="rounded-md border border-[#cfd8cc] bg-white px-3 py-2 text-[#435047]"
            type="button"
            onClick={() => {
              setLoading(true);
              setReloadVersion((current) => current + 1);
            }}
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        {error ? (
          <p className="mt-3 rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
            {error}
          </p>
        ) : null}
        {loading ? (
          <p className="mt-3 text-sm text-[#5c6a60]">작업 확인 중</p>
        ) : null}

        <div className="mt-3 min-h-0 flex-1 overflow-auto rounded-md border border-[#e1e6df]">
          <table className="w-full min-w-[720px] border-collapse text-left text-sm">
            <thead className="sticky top-0 bg-[#f5f7f3] text-[#435047]">
              <tr>
                <th className="px-3 py-2">기간</th>
                <th className="px-3 py-2">작업</th>
                <th className="px-3 py-2">범위</th>
                <th className="px-3 py-2">진행률</th>
                <th className="px-3 py-2">상태</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((operation) => (
                <tr
                  className={`cursor-pointer border-t border-[#edf0ec] hover:bg-[#eef7ec] ${selectedId === operation.id ? "bg-[#eaf7eb]" : "bg-white"}`}
                  key={operation.id}
                  onClick={() => setSelectedId(operation.id)}
                >
                  <td className="px-3 py-2 text-xs whitespace-nowrap">
                    {operation.plannedStartDate}
                    {operation.plannedEndDate
                      ? ` ~ ${operation.plannedEndDate}`
                      : ""}
                  </td>
                  <td className="px-3 py-2">
                    <p className="font-bold text-[#26352b]">
                      {operation.title}
                    </p>
                    <p className="text-xs text-[#6a766e]">
                      {operation.workType}
                    </p>
                  </td>
                  <td className="px-3 py-2 text-xs">{scopeLabel(operation)}</td>
                  <td className="px-3 py-2">
                    {operation.progress.progressPercent}%
                  </td>
                  <td className="px-3 py-2">
                    <span className="rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#435047]">
                      {statusLabel(operation.status)}
                    </span>
                  </td>
                </tr>
              ))}
              {!loading && filtered.length === 0 ? (
                <tr>
                  <td
                    className="px-3 py-12 text-center text-[#5c6a60]"
                    colSpan={5}
                  >
                    조건에 맞는 작업이 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>

      <div className="min-h-0 overflow-auto">
        {selected ? (
          <OperationResult
            operation={selected}
            loading={loading}
            onComplete={(completedDate) =>
              void run(() => completeWorkOperation(selected.id, completedDate))
            }
            onOperationAction={(action) =>
              void run(() => transitionWorkOperation(selected.id, action))
            }
            onTargetAction={(targetId, action, completedDate) =>
              void run(() =>
                transitionWorkOperationTarget(
                  selected.id,
                  targetId,
                  action,
                  selected.worker,
                  undefined,
                  completedDate,
                ),
              )
            }
            onExecuteTarget={setExecutionTarget}
          />
        ) : (
          <div className="rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#5c6a60] shadow-sm">
            상세를 확인할 작업을 선택하세요.
          </div>
        )}
      </div>
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
    </div>
  );
}

function scopeLabel(operation: WorkOperation) {
  const label = {
    NONE: "대상 없음",
    FARM: "농장 전체",
    HOUSE: "동",
    PHYSICAL_BED: "다이",
    BED_ZONE: "논리 구역",
    ORCHID_GROUP: "난 묶음",
    DERIVED_GROUP: "자동 그룹",
    USER_COLLECTION: "사용자 그룹",
    MANUAL_SELECTION: "직접 선택",
    INBOUND_RECORD_SELECTION: "입고 포트 대상",
  }[operation.sourceScopeType];
  return operation.sourceScopeId == null
    ? label
    : `${label} #${operation.sourceScopeId}`;
}

function statusLabel(status: WorkOperationStatus) {
  return {
    PLANNED: "계획",
    IN_PROGRESS: "진행 중",
    PAUSED: "일시중지",
    COMPLETED: "완료",
    CANCELED: "취소",
    CORRECTED: "보정됨",
  }[status];
}
