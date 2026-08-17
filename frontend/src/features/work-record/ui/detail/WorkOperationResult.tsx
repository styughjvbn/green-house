import { useState } from "react";
import type { WorkOperation } from "@/entities/farm/types";
import { getWorkExecutionKind } from "../../model/work-types/workTypeDefinition";
import { WorkCompletionDateDialog } from "./WorkCompletionDateDialog";
import { WorkOperationDetails } from "./WorkOperationDetails";
import { operationStatusLabel } from "../common/workOperationLabels";

export function OperationResult({
  className = "mt-4",
  operation,
  loading,
  onComplete,
  onOperationAction,
  onTargetAction,
  onExecuteTarget,
}: {
  className?: string;
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
  const executionKind = getWorkExecutionKind(operation.workTypeWorkflow);
  const structureChange =
    executionKind === "STRUCTURE_CHANGE" || executionKind === "MOVEMENT";
  const potting = executionKind === "POTTING";
  const firstExecutableTarget = operation.targets.find((target) =>
    target.availableActions.includes("EXECUTE"),
  );

  return (
    <div
      className={`${className} rounded-md border border-[#cfe0d2] bg-white p-4`}
    >
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
            {operation.availableActions.includes("START") ? (
              <StatusAction
                label="작업 시작"
                disabled={loading}
                onClick={() => onOperationAction("start")}
              />
            ) : null}
            {operation.availableActions.includes("PAUSE") ? (
              <StatusAction
                label="일시중지"
                disabled={loading}
                onClick={() => onOperationAction("pause")}
              />
            ) : null}
            {operation.availableActions.includes("RESUME") ? (
              <StatusAction
                label="작업 재개"
                disabled={loading}
                onClick={() => onOperationAction("resume")}
              />
            ) : null}
            {structureChange && firstExecutableTarget ? (
              <StatusAction
                label={`${operation.workType} 회차 등록`}
                primary
                disabled={loading || !onExecuteTarget}
                onClick={() => onExecuteTarget?.(firstExecutableTarget)}
              />
            ) : null}
            {potting && firstExecutableTarget ? (
              <StatusAction
                label="포트 작업 결과 입력"
                primary
                disabled={loading || !onExecuteTarget}
                onClick={() => onExecuteTarget?.(firstExecutableTarget)}
              />
            ) : null}
            {operation.availableActions.includes("COMPLETE") ? (
              <StatusAction
                label="전체 완료"
                primary
                disabled={loading}
                onClick={() => setCompletionTargetId("operation")}
              />
            ) : null}
            {operation.availableActions.includes("CANCEL") ? (
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

      <WorkOperationDetails
        key={operation.id}
        actionLoading={loading}
        operation={operation}
        onExecuteTarget={onExecuteTarget}
        onRequestTargetCompletion={setCompletionTargetId}
        onTargetAction={(targetId, action) => onTargetAction(targetId, action)}
      />
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
