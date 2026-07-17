import type { WorkTargetPreview, WorkType } from "@/entities/farm/types";
import {
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import type { WorkOperationFormState } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { WorkOperationTargetPreview } from "./WorkOperationTargetPreview";
import { workPlanGuidance } from "./workOperationPanelUtils";

export function WorkOperationPlanForm({
  form,
  workTypes,
  selectedWorkType,
  isInboundPotting,
  isDedicatedWorkflow,
  targetSummary,
  targetCount,
  optionsLoading,
  loading,
  canPreview,
  preview,
  excludedIds,
  includedTargetCount,
  includedQuantity,
  saveUnavailableReason,
  onOpenCompletedWork,
  onUpdateForm,
  onOpenTargetSelector,
  onLoadPreview,
  onToggleExcluded,
  onSave,
}: {
  form: WorkOperationFormState;
  workTypes: WorkType[];
  selectedWorkType?: WorkType;
  isInboundPotting: boolean;
  isDedicatedWorkflow: boolean;
  targetSummary: string;
  targetCount: number;
  optionsLoading: boolean;
  loading: boolean;
  canPreview: boolean;
  preview: WorkTargetPreview | null;
  excludedIds: Set<number>;
  includedTargetCount: number;
  includedQuantity: number;
  saveUnavailableReason: string | null;
  onOpenCompletedWork: () => void;
  onUpdateForm: <K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) => void;
  onOpenTargetSelector: () => void;
  onLoadPreview: () => void;
  onToggleExcluded: (id: number) => void;
  onSave: () => void;
}) {
  return (
    <>
      <div className="grid grid-cols-2 gap-2 rounded-md bg-[#e5eee5] p-1">
        <button
          className="rounded px-3 py-2 text-sm font-semibold text-[#526057] hover:bg-white/70"
          type="button"
          onClick={onOpenCompletedWork}
        >
          완료 작업 기록
        </button>
        <button
          className="rounded bg-white px-3 py-2 text-sm font-semibold text-[#10783a] shadow-sm"
          type="button"
          aria-current="page"
        >
          기간 작업 계획
        </button>
      </div>

      <section className="mt-4 grid gap-3 rounded-md border border-[#cfe0d2] bg-white p-4 md:grid-cols-[minmax(220px,0.45fr)_minmax(0,1fr)]">
        <SelectField
          label="작업 유형"
          value={form.workTypeId}
          onChange={(value) => onUpdateForm("workTypeId", value)}
        >
          {workTypes.map((workType) => (
            <option key={workType.id} value={workType.id}>
              {workType.name}
            </option>
          ))}
        </SelectField>
        <div className="rounded-md bg-[#f4f8f3] px-4 py-3 text-sm text-[#526057]">
          <p className="font-semibold text-[#26352b]">
            {selectedWorkType?.name ?? "작업 유형을 선택하세요"}
          </p>
          <p className="mt-1">{workPlanGuidance(selectedWorkType?.code)}</p>
        </div>
      </section>

      <section className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#cfe0d2] bg-white p-4">
        <div>
          <p className="text-sm font-semibold text-[#26352b]">작업 대상</p>
          <p className="mt-1 text-sm text-[#5c6a60]">{targetSummary}</p>
        </div>
        <button
          className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a] hover:bg-[#f1faf3]"
          disabled={optionsLoading}
          type="button"
          onClick={onOpenTargetSelector}
        >
          {targetCount > 0 ? "대상 변경" : "작업 대상 선택"}
        </button>
      </section>

      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <TextField
          label="작업명"
          required
          value={form.title}
          onChange={(value) => onUpdateForm("title", value)}
        />
        <TextField
          label="시작일"
          required
          type="date"
          value={form.plannedStartDate}
          onChange={(value) => onUpdateForm("plannedStartDate", value)}
        />
        <TextField
          label="종료 예정일"
          type="date"
          value={form.plannedEndDate}
          onChange={(value) => onUpdateForm("plannedEndDate", value)}
        />
        {!isDedicatedWorkflow &&
        isVisibleWorkRecordField(
          selectedWorkType?.template ?? null,
          "materialName",
        ) ? (
          <TextField
            label={getWorkRecordFieldLabel(
              selectedWorkType?.template ?? null,
              "materialName",
            )}
            value={form.materialName}
            onChange={(value) => onUpdateForm("materialName", value)}
          />
        ) : null}
        {!isDedicatedWorkflow &&
        isVisibleWorkRecordField(
          selectedWorkType?.template ?? null,
          "dilutionRatio",
        ) ? (
          <TextField
            label={getWorkRecordFieldLabel(
              selectedWorkType?.template ?? null,
              "dilutionRatio",
            )}
            value={form.dilutionRatio}
            onChange={(value) => onUpdateForm("dilutionRatio", value)}
          />
        ) : null}
        {!isDedicatedWorkflow &&
        isVisibleWorkRecordField(
          selectedWorkType?.template ?? null,
          "quantity",
        ) ? (
          <TextField
            label={getWorkRecordFieldLabel(
              selectedWorkType?.template ?? null,
              "quantity",
            )}
            value={form.quantity}
            onChange={(value) => onUpdateForm("quantity", value)}
          />
        ) : null}
        <TextField
          label="작업자"
          value={form.worker}
          onChange={(value) => onUpdateForm("worker", value)}
        />
      </div>

      <label className="mt-3 block text-sm font-semibold text-[#435047]">
        메모
        <textarea
          className="mt-1 min-h-20 w-full rounded-md border border-[#cfd8cc] bg-white px-3 py-2 font-normal"
          value={form.memo}
          onChange={(event) => onUpdateForm("memo", event.target.value)}
        />
      </label>

      {!isInboundPotting ? (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a]"
            disabled={loading || optionsLoading || !canPreview}
            type="button"
            onClick={onLoadPreview}
          >
            {loading ? "확인 중" : "실제 대상 미리보기"}
          </button>
          {preview ? (
            <span className="text-sm font-semibold text-[#344138]">
              포함 {includedTargetCount}묶음 · {includedQuantity}분
            </span>
          ) : null}
        </div>
      ) : null}

      {!isInboundPotting && preview ? (
        <WorkOperationTargetPreview
          preview={preview}
          excludedIds={excludedIds}
          onToggle={onToggleExcluded}
        />
      ) : null}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-[#6a766e]">
          {saveUnavailableReason ?? "등록할 준비가 되었습니다."}
        </p>
        <button
          className="rounded-md bg-[#159447] px-5 py-2.5 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
          disabled={saveUnavailableReason != null}
          type="button"
          onClick={onSave}
        >
          작업 저장
        </button>
      </div>
    </>
  );
}
