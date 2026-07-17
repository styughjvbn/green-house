import { useState } from "react";
import type { WorkOperation } from "@/entities/farm/types";
import { WorkCompletionDateDialog } from "./WorkCompletionDateDialog";
import { WorkOperationDetails } from "./WorkOperationDetails";
import {
  operationStatusLabel,
  targetStatusLabel,
} from "./workOperationPanelUtils";

export function OperationResult({
  operation,
  loading,
  onComplete,
  onOperationAction,
  onTargetAction,
  onExecuteTarget,
}: {
  operation: WorkOperation;
  loading: boolean;
  onComplete: (completedDate: string) => void;
  onOperationAction: (action: "start" | "pause" | "resume" | "cancel") => void;
  onTargetAction: (
    targetId: number,
    action: "start" | "complete" | "skip",
    completedDate?: string,
  ) => void;
  onExecuteTarget?: (target: WorkOperation["targets"][number]) => void;
}) {
  const [completionTargetId, setCompletionTargetId] = useState<
    number | "operation" | null
  >(null);
  const completed = operation.status === "COMPLETED";
  const canceled = operation.status === "CANCELED";
  const corrected = operation.status === "CORRECTED";
  const terminal = completed || canceled || corrected;
  const active = operation.status === "IN_PROGRESS";
  const canComplete =
    active &&
    operation.progress.pending === 0 &&
    operation.progress.inProgress === 0 &&
    operation.progress.partial === 0 &&
    operation.progress.failed === 0;
  const hasRemainingWork = !canComplete;
  const structureChange = ["REPOT", "DIVIDE", "MERGE"].includes(
    operation.workTypeCode,
  );

  return (
    <div className="mt-4 rounded-md border border-[#cfe0d2] bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-bold text-[#17251b]">{operation.title}</p>
          <p className="mt-1 text-sm text-[#5c6a60]">
            {operation.plannedStartDate}
            {operation.plannedEndDate
              ? ` ~ ${operation.plannedEndDate}`
              : ""} · {operationStatusLabel(operation.status)}
            {operation.actualEndAt
              ? ` · 완료 ${operation.actualEndAt.slice(0, 10)}`
              : ""}
          </p>
        </div>
        {terminal ? (
          <span
            className={`rounded-full px-3 py-1.5 text-sm font-semibold ${
              completed || corrected
                ? "bg-[#e7f6eb] text-[#10783a]"
                : "bg-[#f2eeee] text-[#765f5a]"
            }`}
          >
            {corrected ? "보정됨" : completed ? "완료됨" : "취소됨"}
          </span>
        ) : (
          <div className="flex flex-wrap gap-2">
            {operation.status === "PLANNED" ? (
              <StatusAction
                label="작업 시작"
                disabled={loading}
                onClick={() => onOperationAction("start")}
              />
            ) : null}
            {active && hasRemainingWork ? (
              <StatusAction
                label="일시중지"
                disabled={loading}
                onClick={() => onOperationAction("pause")}
              />
            ) : null}
            {operation.status === "PAUSED" ? (
              <StatusAction
                label="작업 재개"
                disabled={loading}
                onClick={() => onOperationAction("resume")}
              />
            ) : null}
            {active && hasRemainingWork && structureChange ? (
              <StatusAction
                label={`${operation.workType} 실행 등록`}
                primary
                disabled={loading || !onExecuteTarget}
                onClick={() => {
                  const firstTarget = operation.targets.find(
                    (target) => target.remainingQuantity > 0,
                  );
                  if (firstTarget) onExecuteTarget?.(firstTarget);
                }}
              />
            ) : null}
            <StatusAction
              label="전체 완료"
              primary
              disabled={loading || !canComplete}
              onClick={() => setCompletionTargetId("operation")}
            />
            {hasRemainingWork ? (
              <StatusAction
                label="취소"
                danger
                disabled={loading}
                onClick={() => onOperationAction("cancel")}
              />
            ) : null}
          </div>
        )}
      </div>

      <WorkOperationDetails operation={operation} />

      <div className="mt-4 rounded-md bg-[#f4f7f3] p-3">
        <div className="flex items-center justify-between text-sm font-semibold text-[#344138]">
          <span>
            완료 {operation.progress.completed} · 진행{" "}
            {operation.progress.inProgress} · 부분 {operation.progress.partial}{" "}
            · 대기 {operation.progress.pending} · 건너뜀{" "}
            {operation.progress.skipped}
          </span>
          <span>{operation.progress.progressPercent}%</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-[#dce5dc]">
          <div
            className="h-full rounded-full bg-[#159447] transition-all"
            style={{ width: `${operation.progress.progressPercent}%` }}
          />
        </div>
      </div>

      <div className="mt-4 max-h-72 overflow-y-auto rounded-md border border-[#e1e6df]">
        {operation.targets.map((target) => (
          <div
            className="flex flex-wrap items-center gap-2 border-b border-[#edf0ec] px-3 py-2 last:border-b-0"
            key={
              target.id ??
              `${target.targetReferenceType}-${target.orchidGroupId ?? target.inboundRecordId}`
            }
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-[#26352b]">
                {target.varietyName}
              </p>
              <p className="mt-0.5 text-xs text-[#6a766e]">
                {target.targetReferenceType === "INBOUND_RECORD"
                  ? `${target.locationSnapshot.tempLocation ?? "임시 위치 미지정"} · 입고 #${target.inboundRecordId}`
                  : `${target.locationSnapshot.houseNumber}동 ${target.locationSnapshot.physicalBedNumber}다이 ${target.locationSnapshot.bedZoneName}`}{" "}
                · 계획 {target.quantitySnapshot}분
                {operation.workTypeCode === "DISCARD" &&
                typeof target.resultDetails?.discardedQuantity === "number"
                  ? ` · 폐기 ${target.resultDetails.discardedQuantity}분 · 현재 ${target.resultDetails.remainingQuantity}분`
                  : target.processedQuantity > 0
                    ? ` · 작업 ${target.processedQuantity}분 · 잔여 ${target.remainingQuantity}분`
                    : ""}
                {target.completedAt
                  ? ` · 완료 ${target.completedAt.slice(0, 10)}`
                  : ""}
              </p>
            </div>
            <span className="rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#526057]">
              {targetStatusLabel(target.executionStatus)}
            </span>
            {structureChange &&
            active &&
            target.id != null &&
            (target.executionStatus === "PENDING" ||
              target.executionStatus === "PARTIALLY_COMPLETED") ? (
              <StatusAction
                small
                label={
                  target.executionStatus === "PARTIALLY_COMPLETED"
                    ? "잔여 제외"
                    : "건너뛰기"
                }
                disabled={loading}
                onClick={() => onTargetAction(target.id!, "skip")}
              />
            ) : null}
            {!structureChange &&
            active &&
            target.id != null &&
            target.executionStatus === "PENDING" ? (
              <StatusAction
                small
                label="시작"
                disabled={loading}
                onClick={() => onTargetAction(target.id!, "start")}
              />
            ) : null}
            {!structureChange &&
            active &&
            target.id != null &&
            (target.executionStatus === "PENDING" ||
              target.executionStatus === "IN_PROGRESS") ? (
              <>
                <StatusAction
                  small
                  primary
                  label={
                    operation.workTypeCode === "REPOT" ||
                    operation.workTypeCode === "DIVIDE" ||
                    operation.workTypeCode === "POTTING" ||
                    operation.workTypeCode === "DISCARD" ||
                    operation.workTypeCode === "MOVEMENT"
                      ? "실행 입력"
                      : "완료"
                  }
                  disabled={
                    loading ||
                    ((operation.workTypeCode === "REPOT" ||
                      operation.workTypeCode === "DIVIDE" ||
                      operation.workTypeCode === "POTTING" ||
                      operation.workTypeCode === "DISCARD" ||
                      operation.workTypeCode === "MOVEMENT") &&
                      !onExecuteTarget)
                  }
                  onClick={() => {
                    if (
                      operation.workTypeCode === "REPOT" ||
                      operation.workTypeCode === "DIVIDE" ||
                      operation.workTypeCode === "POTTING" ||
                      operation.workTypeCode === "DISCARD" ||
                      operation.workTypeCode === "MOVEMENT"
                    ) {
                      onExecuteTarget?.(target);
                    } else {
                      setCompletionTargetId(target.id!);
                    }
                  }}
                />
                <StatusAction
                  small
                  label="건너뛰기"
                  disabled={loading}
                  onClick={() => onTargetAction(target.id!, "skip")}
                />
              </>
            ) : null}
          </div>
        ))}
      </div>
      {completionTargetId != null ? (
        <WorkCompletionDateDialog
          title={
            completionTargetId === "operation" ? "작업 완료" : "작업 실행 완료"
          }
          description="실제로 작업을 완료한 날짜를 확인해주세요."
          onClose={() => setCompletionTargetId(null)}
          onConfirm={(completedDate) => {
            if (completionTargetId === "operation") {
              onComplete(completedDate);
            } else {
              onTargetAction(completionTargetId, "complete", completedDate);
            }
          }}
        />
      ) : null}
    </div>
  );
}

function StatusAction({
  label,
  disabled,
  primary = false,
  danger = false,
  small = false,
  onClick,
}: {
  label: string;
  disabled: boolean;
  primary?: boolean;
  danger?: boolean;
  small?: boolean;
  onClick: () => void;
}) {
  const color = primary
    ? "border-[#159447] bg-[#159447] text-white"
    : danger
      ? "border-[#e2b5aa] bg-white text-[#a33a24]"
      : "border-[#cfd8cc] bg-white text-[#34503b]";

  return (
    <button
      className={`rounded-md border font-semibold disabled:opacity-45 ${color} ${
        small ? "px-2 py-1 text-xs" : "px-3 py-2 text-sm"
      }`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}
