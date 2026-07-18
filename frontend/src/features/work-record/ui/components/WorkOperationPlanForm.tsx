import type { WorkTargetPreview, WorkType } from "@/entities/farm/types";
import {
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import type { WorkOperationFormState } from "../../model/types";
import { TextField } from "./FormFields";
import { WorkOperationTargetPreview } from "./WorkOperationTargetPreview";
import { workPlanGuidance } from "./workOperationPanelUtils";

type TargetSummary = {
  title: string;
  metrics: string;
  location: string;
};

const STRUCTURE_WORK_CODES = new Set([
  "MOVEMENT",
  "REPOT",
  "DIVIDE",
  "MERGE",
  "DISCARD",
  "POTTING",
]);

const PLAN_ONLY_CODES = new Set([
  "REPOT",
  "DIVIDE",
  "MERGE",
  "DISCARD",
  "POTTING",
]);

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
  registrationMode,
  recordDisabled,
  onChangeRegistrationMode,
  onCancel,
  onUpdateForm,
  onSelectFarmTarget,
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
  targetSummary: TargetSummary;
  targetCount: number;
  optionsLoading: boolean;
  loading: boolean;
  canPreview: boolean;
  preview: WorkTargetPreview | null;
  excludedIds: Set<number>;
  includedTargetCount: number;
  includedQuantity: number;
  saveUnavailableReason: string | null;
  registrationMode: "RECORD" | "PLAN";
  recordDisabled: boolean;
  onChangeRegistrationMode: (mode: "RECORD" | "PLAN") => void;
  onCancel: () => void;
  onUpdateForm: <K extends keyof WorkOperationFormState>(
    field: K,
    value: WorkOperationFormState[K],
  ) => void;
  onSelectFarmTarget: () => void;
  onOpenTargetSelector: () => void;
  onLoadPreview: () => void;
  onToggleExcluded: (id: number) => void;
  onSave: () => void;
}) {
  const planOnly = PLAN_ONLY_CODES.has(selectedWorkType?.code ?? "");
  const structureWork = STRUCTURE_WORK_CODES.has(selectedWorkType?.code ?? "");
  const recordMode = registrationMode === "RECORD";
  const saveLabel = recordMode
    ? `${selectedWorkType?.name ?? "작업"} 기록 저장`
    : `${selectedWorkType?.name ?? "작업"} 계획 저장`;

  return (
    <div className="space-y-4">
      <section className="rounded-md border border-[#cfe0d2] bg-white p-4">
        <SectionTitle title="작업 유형 선택" />
        <WorkTypeGroup
          title="일반 작업"
          codes={[
            "PESTICIDE",
            "FERTILIZER",
            "STATUS",
            "MEMO",
            "LEAF_CLEANUP",
            "FLOWER_CLEANUP",
            "WEED_CLEANUP",
          ]}
          workTypes={workTypes}
          selectedWorkType={selectedWorkType}
          onSelect={(workType) =>
            onUpdateForm("workTypeId", String(workType.id))
          }
        />
        <WorkTypeGroup
          title="구조 변경 작업"
          codes={["REPOT", "DIVIDE", "MERGE", "DISCARD", "POTTING"]} //TODO "MOVEMENT" 는 비활성화
          workTypes={workTypes}
          selectedWorkType={selectedWorkType}
          onSelect={(workType) =>
            onUpdateForm("workTypeId", String(workType.id))
          }
        />
      </section>

      <section className="rounded-md border border-[#cfe0d2] bg-white p-4">
        <SectionTitle title="등록 방법 선택" />
        <div className="grid gap-2 sm:grid-cols-2">
          <MethodButton
            title="작업 기록"
            description={
              planOnly || recordDisabled
                ? "결과 입력이 필요한 작업은 계획으로 등록합니다."
                : "이미 끝난 작업을 기록"
            }
            disabled={planOnly || recordDisabled}
            selected={recordMode}
            onClick={() => onChangeRegistrationMode("RECORD")}
          />
          <MethodButton
            title="작업 계획"
            description="앞으로 수행할 작업을 등록하고 진행 상태를 관리"
            selected={!recordMode}
            onClick={() => onChangeRegistrationMode("PLAN")}
          />
        </div>
      </section>

      <section className="rounded-md border border-[#cfe0d2] bg-white p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <SectionTitle title="작업 대상" />
            <p className="text-sm font-semibold text-[#26352b]">
              {targetSummary.title}
            </p>
            <p className="mt-1 text-sm text-[#5c6a60]">
              {targetSummary.metrics}
            </p>
            {targetSummary.location ? (
              <p className="mt-1 text-sm text-[#5c6a60]">
                {targetSummary.location}
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            {!isDedicatedWorkflow ? (
              <button
                className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a] hover:bg-[#f1faf3] disabled:opacity-50"
                disabled={optionsLoading}
                type="button"
                onClick={onSelectFarmTarget}
              >
                농장 전체
              </button>
            ) : null}
            <button
              className="rounded-md border border-[#159447] bg-white px-4 py-2 text-sm font-semibold text-[#10783a] hover:bg-[#f1faf3] disabled:opacity-50"
              disabled={optionsLoading}
              type="button"
              onClick={onOpenTargetSelector}
            >
              작업 대상 확인
            </button>
            {!isInboundPotting ? (
              <button
                className="rounded-md border border-[#cfd8cc] bg-white px-4 py-2 text-sm font-semibold text-[#435047] disabled:opacity-50"
                disabled={loading || optionsLoading || !canPreview}
                type="button"
                onClick={onLoadPreview}
              >
                {loading ? "확인 중" : "대상 요약 갱신"}
              </button>
            ) : null}
          </div>
        </div>
        {!isInboundPotting && preview ? (
          <>
            <p className="mt-3 text-sm font-semibold text-[#344138]">
              포함 {includedTargetCount}묶음 ·{" "}
              {includedQuantity.toLocaleString()}분
            </p>
            <WorkOperationTargetPreview
              preview={preview}
              excludedIds={excludedIds}
              onToggle={onToggleExcluded}
            />
          </>
        ) : null}
      </section>

      <section className="rounded-md border border-[#cfe0d2] bg-white p-4">
        <SectionTitle title="작업 정보" />
        <div className="grid gap-3 md:grid-cols-3">
          <TextField
            label="작업명"
            required
            value={form.title}
            onChange={(value) => onUpdateForm("title", value)}
          />
          <TextField
            label={recordMode ? "작업일" : "시작일"}
            required
            type="date"
            value={form.plannedStartDate}
            onChange={(value) => onUpdateForm("plannedStartDate", value)}
          />
          {!recordMode ? (
            <TextField
              label="종료 예정일"
              type="date"
              value={form.plannedEndDate}
              onChange={(value) => onUpdateForm("plannedEndDate", value)}
            />
          ) : null}
          {(!isDedicatedWorkflow || recordMode) &&
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
          {(!isDedicatedWorkflow || recordMode) &&
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
          {(!isDedicatedWorkflow || recordMode) &&
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
      </section>

      {structureWork && !recordMode ? (
        <section className="rounded-md border border-[#cfe0d2] bg-[#f7faf6] p-4">
          <SectionTitle title="구조 변경 작업 안내" />
          <p className="text-sm text-[#526057]">
            {workPlanGuidance(selectedWorkType?.code)}
          </p>
          <div className="mt-3 grid gap-2 text-sm font-semibold text-[#2f4636] md:grid-cols-4">
            {[
              "계획 저장",
              "작업 상세에서 대상별 실행",
              "결과 난 묶음·손실·배치 입력",
              "작업 완료",
            ].map((step, index) => (
              <div
                className="rounded-md border border-[#d7e4d6] bg-white px-3 py-2"
                key={step}
              >
                {index + 1}. {step}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <div className="rounded-md border border-[#cfe0d2] bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm font-semibold text-[#344138]">
            대상 {targetCount}개 ·{" "}
            {structureWork ? "구조 변경 작업" : "일반 작업"} ·{" "}
            {recordMode ? "작업 기록으로 저장" : "작업 계획으로 저장"}
          </p>
          <div className="flex gap-2">
            <button
              className="rounded-md border border-[#cfd8cc] bg-white px-4 py-2 text-sm font-semibold text-[#435047]"
              type="button"
              onClick={onCancel}
            >
              취소
            </button>
            <button
              className="rounded-md bg-[#159447] px-5 py-2 text-sm font-semibold text-white disabled:bg-[#9bb7a2]"
              disabled={saveUnavailableReason != null}
              type="button"
              onClick={onSave}
            >
              {saveLabel}
            </button>
          </div>
          {saveUnavailableReason ? (
            <p className="basis-full text-xs text-[#8a5b2f]">
              {saveUnavailableReason}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function SectionTitle({ title }: { title: string }) {
  return <p className="mb-3 text-sm font-bold text-[#26352b]">{title}</p>;
}

function WorkTypeGroup({
  title,
  codes,
  workTypes,
  selectedWorkType,
  onSelect,
}: {
  title: string;
  codes: string[];
  workTypes: WorkType[];
  selectedWorkType?: WorkType;
  onSelect: (workType: WorkType) => void;
}) {
  const items = codes.flatMap((code) => {
    const workType = workTypes.find((item) => item.code === code);
    return workType ? [workType] : [];
  });
  if (items.length === 0) return null;
  return (
    <div className="mt-3">
      <p className="mb-2 text-xs font-semibold text-[#6a766e]">{title}</p>
      <div className="flex flex-wrap gap-2">
        {items.map((workType) => {
          const selected = selectedWorkType?.id === workType.id;
          return (
            <button
              className={`rounded-md border px-3 py-2 text-sm font-semibold ${
                selected
                  ? "border-[#159447] bg-[#159447] text-white"
                  : "border-[#d5ddd6] bg-white text-[#344138] hover:border-[#159447]"
              }`}
              key={workType.id}
              type="button"
              onClick={() => onSelect(workType)}
            >
              {workType.name}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function MethodButton({
  title,
  description,
  selected,
  disabled = false,
  onClick,
}: {
  title: string;
  description: string;
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`rounded-md border px-4 py-3 text-left disabled:cursor-not-allowed disabled:opacity-45 ${
        selected
          ? "border-[#159447] bg-[#e8f6eb] text-[#10783a]"
          : "border-[#d5ddd6] bg-white text-[#344138] hover:border-[#159447]"
      }`}
      disabled={disabled}
      type="button"
      onClick={onClick}
    >
      <span className="block text-sm font-bold">{title}</span>
      <span className="mt-1 block text-xs font-medium text-[#6a766e]">
        {description}
      </span>
    </button>
  );
}
