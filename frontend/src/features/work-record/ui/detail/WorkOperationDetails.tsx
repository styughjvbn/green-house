import { useState } from "react";
import type { WorkOperation } from "@/entities/farm/types";
import {
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";
import { workOperationScopeLabel } from "../../lib/workOperationDisplay";
import type {
  WorkExecutionDetail,
  WorkExecutionResult,
  WorkExecutionSource,
  WorkOperationDetail,
} from "../../model/types";
import { getWorkExecutionKind } from "../../model/work-types/workTypeDefinition";
import {
  operationStatusLabel,
  targetStatusLabel,
} from "../common/workOperationLabels";

const DETAIL_LABELS: Record<string, string> = {
  actualQuantity: "실제 수량",
  ageYear: "년생",
  bottleCount: "병 수",
  createdCount: "생성된 난 묶음 수",
  dilutionRatio: "희석 배수",
  estimatedQuantity: "예상 수량",
  genus: "속명",
  growthStage: "생육 단계",
  inboundType: "입고 유형",
  materialName: "자재명",
  originalWorkOperationId: "원본 작업",
  placementType: "배치 규격",
  potSize: "화분 크기",
  pottingDueDate: "포트 예정일",
  quantity: "사용량",
  reason: "보정 사유",
  resultCount: "결과 난 묶음 수",
  rowCount: "생성 예정 건수",
  status: "상태",
  tempLocation: "임시 위치",
  trayCount: "판수",
  varietyName: "품종",
};

const HIDDEN_DETAIL_KEYS = new Set([
  "inboundRecordId",
  "orchidGroupId",
  "requestKey",
  "idempotencyKey",
]);

type DetailTab = "overview" | "execution" | "targets";

const TABS: Array<{ id: DetailTab; label: string }> = [
  { id: "overview", label: "개요" },
  { id: "execution", label: "실행" },
  { id: "targets", label: "대상" },
];

export function WorkOperationDetails({
  operation,
  detail,
  loading,
  error,
  actionLoading,
  onTargetAction,
  onExecuteTarget,
  onRequestTargetCompletion,
}: {
  operation: WorkOperation;
  detail: WorkOperationDetail | null;
  loading: boolean;
  error: string | null;
  actionLoading: boolean;
  onTargetAction: (
    targetId: number,
    action: "start" | "complete" | "skip",
  ) => void;
  onExecuteTarget?: (target: WorkOperation["targets"][number]) => void;
  onRequestTargetCompletion: (targetId: number) => void;
}) {
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");
  const details =
    detail?.fields.filter((field) => !isHiddenDetailKey(field.key)) ??
    Object.entries(operation.details ?? {})
      .filter(
        ([key, value]) =>
          !isHiddenDetailKey(key) &&
          value !== null &&
          value !== undefined &&
          value !== "",
      )
      .map(([key, value]) => ({
        key,
        label: detailLabel(operation, key),
        value: formatDetailValue(value),
      }));

  return (
    <section className="mt-4 overflow-hidden rounded-md border border-[#dce4da] bg-[#fbfcfa]">
      <div
        aria-label="작업 상세 정보"
        className="flex border-b border-[#dce4da] bg-white px-2"
        role="tablist"
      >
        {TABS.map((tab) => (
          <button
            aria-controls={`work-detail-${operation.id}-${tab.id}`}
            aria-selected={activeTab === tab.id}
            className={`border-b-2 px-4 py-3 text-sm font-bold transition-colors ${
              activeTab === tab.id
                ? "border-[#159447] text-[#10783a]"
                : "border-transparent text-[#657168] hover:text-[#344138]"
            }`}
            id={`work-detail-tab-${operation.id}-${tab.id}`}
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            role="tab"
            type="button"
          >
            {tab.label}
            {tab.id === "targets" ? ` ${operation.targets.length}` : ""}
          </button>
        ))}
      </div>

      <div
        aria-labelledby={`work-detail-tab-${operation.id}-${activeTab}`}
        className="p-4"
        id={`work-detail-${operation.id}-${activeTab}`}
        role="tabpanel"
      >
        {activeTab === "overview" ? (
          <OverviewTab details={details} operation={operation} />
        ) : null}
        {activeTab === "execution" ? (
          <ExecutionTab
            detail={detail}
            error={error}
            loading={loading}
            operation={operation}
          />
        ) : null}
        {activeTab === "targets" ? (
          <TargetsTab
            actionLoading={actionLoading}
            operation={operation}
            onExecuteTarget={onExecuteTarget}
            onRequestTargetCompletion={onRequestTargetCompletion}
            onTargetAction={onTargetAction}
          />
        ) : null}
      </div>
    </section>
  );
}

function OverviewTab({
  operation,
  details,
}: {
  operation: WorkOperation;
  details: Array<{ key: string; label: string; value: string }>;
}) {
  const orchidGroupCount = operation.targets.filter(
    (target) => target.targetReferenceType === "ORCHID_GROUP",
  ).length;
  const inboundCount = operation.targets.length - orchidGroupCount;
  const totalQuantity = operation.targets.reduce(
    (sum, target) => sum + target.quantitySnapshot,
    0,
  );
  const period = operation.plannedEndDate
    ? `${operation.plannedStartDate} ~ ${operation.plannedEndDate}`
    : operation.plannedStartDate;
  const commonItems = [
    { key: "workType", label: "작업 유형", value: operation.workType },
    {
      key: "status",
      label: "상태",
      value: operationStatusLabel(operation.status),
    },
    { key: "period", label: "계획 기간", value: period },
    ...(operation.actualStartAt
      ? [
          {
            key: "actualStartAt",
            label: "실제 시작",
            value: formatDateTime(operation.actualStartAt),
          },
        ]
      : []),
    ...(operation.actualEndAt
      ? [
          {
            key: "actualEndAt",
            label: "실제 완료",
            value: formatDateTime(operation.actualEndAt),
          },
        ]
      : []),
    ...(operation.worker
      ? [{ key: "worker", label: "작업자", value: operation.worker }]
      : []),
    ...(operation.memo
      ? [{ key: "memo", label: "메모", value: operation.memo }]
      : []),
  ];

  return (
    <div className="space-y-5">
      <div>
        <SectionTitle>작업 정보</SectionTitle>
        <DetailList items={commonItems} />
      </div>

      <div className="border-t border-[#e1e6df] pt-4">
        <SectionTitle>작업별 정보</SectionTitle>
        {details.length > 0 ? (
          <DetailList items={details} />
        ) : (
          <EmptyText>추가로 입력된 작업 정보가 없습니다.</EmptyText>
        )}
      </div>

      <div className="border-t border-[#e1e6df] pt-4">
        <SectionTitle>대상 및 진행 요약</SectionTitle>
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
          <SummaryValue label="난 묶음" value={`${orchidGroupCount}개`} />
          <SummaryValue label="입고 대상" value={`${inboundCount}건`} />
          <SummaryValue label="계획 수량" value={`${totalQuantity}분`} />
          <SummaryValue
            label="진행률"
            value={`${operation.progress.progressPercent}%`}
          />
        </div>
        <ProgressSummary operation={operation} />
      </div>
    </div>
  );
}

function ExecutionTab({
  operation,
  detail,
  loading,
  error,
}: {
  operation: WorkOperation;
  detail: WorkOperationDetail | null;
  loading: boolean;
  error: string | null;
}) {
  const executions = detail?.executions.filter(hasDisplayResult) ?? [];

  if (loading) return <LoadingMessage />;
  if (error) return <ErrorMessage>{error}</ErrorMessage>;

  return (
    <div className="space-y-5">
      <div>
        <SectionTitle>
          {executions.length > 0 ? "실행 회차" : "완료 기록"}
        </SectionTitle>
        {executions.length > 0 ? (
          <ol className="mt-3 space-y-2">
            {executions.map((execution, index) => (
              <li
                className="rounded-md border border-[#e1e6df] bg-white p-3"
                key={execution.id}
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-sm font-bold text-[#344138]">
                    {index + 1}회차 · {resultTypeLabel(execution.resultType)}
                  </p>
                  <p className="text-xs text-[#6a766e]">
                    {formatDateTime(execution.appliedAt)}
                    {execution.worker ? ` · ${execution.worker}` : ""}
                  </p>
                </div>
                <ExecutionContent execution={execution} />
              </li>
            ))}
          </ol>
        ) : (
          <TargetCompletionHistory operation={operation} />
        )}
      </div>

      {operation.status === "CORRECTED" ? (
        <div className="border-t border-[#e1e6df] pt-4">
          <SectionTitle>보정 내역</SectionTitle>
          {detail && detail.corrections.length > 0 ? (
            <ol className="mt-3 space-y-2">
              {detail.corrections.map((correction) => (
                <li
                  className="rounded-md border border-[#e1e6cf] bg-white p-3"
                  key={correction.id}
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <p className="text-sm font-bold text-[#26352b]">
                      {correction.title}
                    </p>
                    <p className="text-xs text-[#6a766e]">
                      {correction.workDate}
                      {correction.worker ? ` · ${correction.worker}` : ""}
                    </p>
                  </div>
                  <p className="mt-1 text-xs text-[#526057]">
                    사유: {correction.reason}
                  </p>
                  {correction.adjustments.map((adjustment) => (
                    <p
                      className="mt-1 text-xs text-[#344138]"
                      key={adjustment.orchidGroupId}
                    >
                      난 묶음 #{adjustment.orchidGroupId} · 수량{" "}
                      {adjustment.beforeQuantity} → {adjustment.afterQuantity}분
                      · 상태 {adjustment.beforeStatus} →{" "}
                      {adjustment.afterStatus}
                    </p>
                  ))}
                </li>
              ))}
            </ol>
          ) : (
            <EmptyText>등록된 보정 내역이 없습니다.</EmptyText>
          )}
        </div>
      ) : null}
    </div>
  );
}

function TargetCompletionHistory({ operation }: { operation: WorkOperation }) {
  const completedTargets = operation.targets.filter(
    (target) =>
      target.completedAt != null ||
      target.executionStatus === "SKIPPED" ||
      target.executionStatus === "CANCELED",
  );

  if (completedTargets.length === 0) {
    if (operation.actualEndAt) {
      return (
        <p className="mt-3 rounded-md border border-[#e1e6df] bg-white p-3 text-sm text-[#344138]">
          전체 작업 완료 · {formatDateTime(operation.actualEndAt)}
        </p>
      );
    }
    return <EmptyText>아직 기록된 실행 또는 완료 내역이 없습니다.</EmptyText>;
  }

  return (
    <ol className="mt-3 max-h-96 space-y-2 overflow-y-auto pr-1">
      {completedTargets.map((target) => (
        <li
          className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-[#e1e6df] bg-white p-3"
          key={targetKey(target)}
        >
          <div>
            <p className="text-sm font-bold text-[#26352b]">
              {targetTitle(target)}
            </p>
            <p className="mt-1 text-xs text-[#6a766e]">
              {target.completedAt
                ? `${formatDateTime(target.completedAt)} 완료`
                : targetStatusLabel(target.executionStatus)}
              {target.worker ? ` · ${target.worker}` : ""}
            </p>
          </div>
          <span className="rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#526057]">
            {targetStatusLabel(target.executionStatus)}
          </span>
        </li>
      ))}
    </ol>
  );
}

function TargetsTab({
  operation,
  actionLoading,
  onTargetAction,
  onExecuteTarget,
  onRequestTargetCompletion,
}: {
  operation: WorkOperation;
  actionLoading: boolean;
  onTargetAction: (
    targetId: number,
    action: "start" | "complete" | "skip",
  ) => void;
  onExecuteTarget?: (target: WorkOperation["targets"][number]) => void;
  onRequestTargetCompletion: (targetId: number) => void;
}) {
  const active = operation.status === "IN_PROGRESS";
  const executionKind = getWorkExecutionKind(operation.workTypeCode);
  const structureChange =
    executionKind === "STRUCTURE_CHANGE" || executionKind === "MOVEMENT";
  const potting = executionKind === "POTTING";
  const requiresResultEntry = executionKind != null;

  return (
    <div>
      <SectionTitle>대상 선택</SectionTitle>
      <dl className="mt-3 grid gap-3 rounded-md border border-[#e1e6df] bg-white p-3 sm:grid-cols-2">
        <div>
          <dt className="text-xs font-semibold text-[#6a766e]">선택 방식</dt>
          <dd className="mt-1 text-sm font-bold text-[#26352b]">
            {workOperationScopeLabel(operation)}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-semibold text-[#6a766e]">포함 대상</dt>
          <dd className="mt-1 text-sm font-bold text-[#26352b]">
            {operation.targets.length}건
          </dd>
        </div>
      </dl>

      <div className="mt-5 flex items-center justify-between gap-3">
        <SectionTitle>포함된 난 묶음</SectionTitle>
        <span className="text-xs font-semibold text-[#6a766e]">
          총 {operation.targets.length}건
        </span>
      </div>
      {operation.targets.length > 0 ? (
        <div className="mt-3 max-h-96 overflow-y-auto rounded-md border border-[#e1e6df] bg-white">
          {operation.targets.map((target) => (
            <div
              className="flex flex-wrap items-center gap-2 border-b border-[#edf0ec] px-3 py-3 last:border-b-0"
              key={targetKey(target)}
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-[#26352b]">
                  {targetTitle(target)}
                </p>
                <p className="mt-0.5 text-xs text-[#6a766e]">
                  {targetLocation(target)} · 계획 {target.quantitySnapshot}분
                  {target.processedQuantity > 0
                    ? ` · 작업 ${target.processedQuantity}분 · 잔여 ${target.remainingQuantity}분`
                    : ""}
                  {target.completedAt
                    ? ` · 완료 ${formatDateTime(target.completedAt)}`
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
                <TargetAction
                  label={
                    target.executionStatus === "PARTIALLY_COMPLETED"
                      ? "잔여 제외"
                      : "건너뛰기"
                  }
                  disabled={actionLoading}
                  onClick={() => onTargetAction(target.id!, "skip")}
                />
              ) : null}
              {!structureChange &&
              !potting &&
              active &&
              target.id != null &&
              target.executionStatus === "PENDING" ? (
                <TargetAction
                  label="시작"
                  disabled={actionLoading}
                  onClick={() => onTargetAction(target.id!, "start")}
                />
              ) : null}
              {!structureChange &&
              !potting &&
              active &&
              target.id != null &&
              (target.executionStatus === "PENDING" ||
                target.executionStatus === "IN_PROGRESS") ? (
                <>
                  <TargetAction
                    primary
                    label={requiresResultEntry ? "결과 입력" : "완료"}
                    disabled={
                      actionLoading || (requiresResultEntry && !onExecuteTarget)
                    }
                    onClick={() => {
                      if (requiresResultEntry) {
                        onExecuteTarget?.(target);
                      } else {
                        onRequestTargetCompletion(target.id!);
                      }
                    }}
                  />
                  <TargetAction
                    label="건너뛰기"
                    disabled={actionLoading}
                    onClick={() => onTargetAction(target.id!, "skip")}
                  />
                </>
              ) : null}
              {potting &&
              active &&
              target.id != null &&
              target.executionStatus === "PENDING" ? (
                <TargetAction
                  label="건너뛰기"
                  disabled={actionLoading}
                  onClick={() => onTargetAction(target.id!, "skip")}
                />
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <EmptyText>이 작업에 포함된 대상이 없습니다.</EmptyText>
      )}
    </div>
  );
}

function DetailList({
  items,
}: {
  items: Array<{ key: string; label: string; value: string }>;
}) {
  return (
    <dl className="mt-3 grid gap-x-5 gap-y-3 sm:grid-cols-2">
      {items.map((item) => (
        <div className="min-w-0" key={item.key}>
          <dt className="text-xs font-semibold text-[#6a766e]">{item.label}</dt>
          <dd className="mt-1 text-sm font-semibold break-words whitespace-pre-wrap text-[#26352b]">
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function SummaryValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-white p-3 ring-1 ring-[#e1e6df]">
      <p className="text-xs font-semibold text-[#6a766e]">{label}</p>
      <p className="mt-1 text-lg font-bold text-[#26352b]">{value}</p>
    </div>
  );
}

function ProgressSummary({ operation }: { operation: WorkOperation }) {
  return (
    <div className="mt-3 rounded-md bg-[#f1f5f0] p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs font-semibold text-[#344138]">
        <span>
          완료 {operation.progress.completed} · 진행{" "}
          {operation.progress.inProgress}
          {operation.progress.partial > 0
            ? ` · 부분 ${operation.progress.partial}`
            : ""}
          {` · 대기 ${operation.progress.pending}`}
          {operation.progress.skipped > 0
            ? ` · 건너뜀 ${operation.progress.skipped}`
            : ""}
          {operation.progress.failed > 0
            ? ` · 실패 ${operation.progress.failed}`
            : ""}
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
  );
}

function SectionTitle({ children }: { children: string }) {
  return <h3 className="text-sm font-bold text-[#26352b]">{children}</h3>;
}

function EmptyText({ children }: { children: string }) {
  return <p className="mt-3 text-xs text-[#5c6a60]">{children}</p>;
}

function LoadingMessage() {
  return (
    <p className="rounded-md bg-[#f4f7f3] p-3 text-xs text-[#5c6a60]">
      실행 내역 확인 중
    </p>
  );
}

function ErrorMessage({ children }: { children: string }) {
  return (
    <p className="rounded-md border border-[#e2b5aa] bg-[#fff8f6] p-3 text-xs font-semibold text-[#a33a24]">
      {children}
    </p>
  );
}

function TargetAction({
  label,
  disabled,
  primary = false,
  onClick,
}: {
  label: string;
  disabled: boolean;
  primary?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      className={`rounded-md border px-2 py-1 text-xs font-semibold disabled:opacity-45 ${
        primary
          ? "border-[#159447] bg-[#159447] text-white"
          : "border-[#cfd8cc] bg-white text-[#34503b]"
      }`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}

function isHiddenDetailKey(key: string) {
  return (
    HIDDEN_DETAIL_KEYS.has(key) ||
    key === "migrationSource" ||
    key.startsWith("legacy")
  );
}

function ExecutionContent({ execution }: { execution: WorkExecutionDetail }) {
  return (
    <div className="mt-2 space-y-1 text-xs text-[#526057]">
      {execution.inboundRecordId != null ? (
        <p>입고 기록 #{execution.inboundRecordId}</p>
      ) : null}
      {execution.sources.map((source, index) => (
        <p key={`${source.orchidGroupId}-${index}`}>{sourceLine(source)}</p>
      ))}
      {execution.results.length > 0 ? (
        <div className="mt-3 space-y-2">
          {execution.results.map((result, index) => (
            <ExecutionResultCard
              index={index}
              key={`${result.orchidGroupId ?? "result"}-${index}`}
              result={result}
            />
          ))}
        </div>
      ) : null}
      {execution.actualQuantity != null ? (
        <p>실제 수량 {execution.actualQuantity}분</p>
      ) : null}
      {execution.lossQuantity != null ? (
        <p>손실 수량 {execution.lossQuantity}분</p>
      ) : null}
      {execution.reason ? <p>사유: {execution.reason}</p> : null}
      {execution.linkedWorkOperationId != null ? (
        <p>연결 작업 #{execution.linkedWorkOperationId}</p>
      ) : null}
    </div>
  );
}

function sourceLine(source: WorkExecutionSource) {
  const id = `원본 난 묶음 #${source.orchidGroupId}`;
  const values: string[] = [];
  if (
    source.beforeQuantity != null &&
    source.afterQuantity != null &&
    source.inputQuantity != null
  ) {
    values.push(
      `수량 ${source.beforeQuantity} → ${source.afterQuantity}분 · ${source.inputQuantity}분 처리`,
    );
  } else if (source.inputQuantity != null) {
    values.push(`투입 ${source.inputQuantity}분`);
  }
  if (source.remainingQuantity != null && source.afterQuantity == null) {
    values.push(`잔여 ${source.remainingQuantity}분`);
  }
  if (source.beforeStatus && source.afterStatus) {
    values.push(`상태 ${source.beforeStatus} → ${source.afterStatus}`);
  }
  if (source.fromBedZoneId != null) {
    values.push(`이동 전 구역 #${source.fromBedZoneId}`);
  }
  return values.length > 0 ? `${id} · ${values.join(" · ")}` : id;
}

function ExecutionResultCard({
  result,
  index,
}: {
  result: WorkExecutionResult;
  index: number;
}) {
  const attributes: string[] = [];
  if (result.quantity != null) attributes.push(`${result.quantity}분`);
  if (result.purpose) attributes.push(purposeLabel(result.purpose));
  if (result.potSize) attributes.push(result.potSize);
  if (result.ageYear != null) attributes.push(`${result.ageYear}년생`);
  if (result.placementType) attributes.push(result.placementType);
  if (result.trayCount != null) attributes.push(`${result.trayCount}판`);

  const location = result.location
    ? `${result.location.houseNumber}동 ${result.location.physicalBedNumber}다이 ${result.location.bedZoneName}`
    : result.bedZoneId != null
      ? `구역 #${result.bedZoneId}`
      : null;
  const placement: string[] = [];
  if (location) placement.push(location);
  if (result.startPosition != null && result.endPosition != null) {
    placement.push(`위치 ${result.startPosition}~${result.endPosition}`);
  }

  return (
    <div className="rounded-md border border-[#e1e6df] bg-[#f8faf7] p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-sm font-bold text-[#26352b]">
          결과 {index + 1}
          {result.varietyName ? ` · ${result.varietyName}` : ""}
        </p>
        {result.orchidGroupId != null ? (
          <p className="text-[11px] text-[#7a857d]">
            난 묶음 #{result.orchidGroupId}
          </p>
        ) : null}
      </div>
      {attributes.length > 0 ? (
        <p className="mt-1.5 font-semibold text-[#344138]">
          {attributes.join(" · ")}
        </p>
      ) : null}
      {placement.length > 0 ? (
        <p className="mt-1 text-[#526057]">{placement.join(" · ")}</p>
      ) : null}
      {result.memo ? (
        <p className="mt-1 text-[#526057]">메모: {result.memo}</p>
      ) : null}
    </div>
  );
}

function hasDisplayResult(execution: WorkExecutionDetail) {
  return (
    execution.resultType !== "RECORD_ONLY" &&
    (execution.sources.length > 0 ||
      execution.results.length > 0 ||
      execution.actualQuantity != null ||
      execution.lossQuantity != null ||
      execution.reason != null ||
      execution.linkedWorkOperationId != null)
  );
}

function resultTypeLabel(resultType: string) {
  return (
    {
      DISCARD: "폐기 결과",
      MOVE: "자리 이동 결과",
      MOVEMENT: "자리 이동 결과",
      POTTING: "포트 작업 결과",
      REPOT: "분갈이 결과",
      DIVIDE: "분주 결과",
      MERGE: "합식 결과",
      MULTI_CREATE: "난 묶음 생성 결과",
      CORRECTION: "보정 결과",
    }[resultType] ?? "작업 결과"
  );
}

function purposeLabel(purpose: string) {
  return (
    {
      NORMAL: "일반",
      DIVIDE_CANDIDATE: "분주 예정",
      HELD: "별도 보관",
    }[purpose] ?? purpose
  );
}

function targetKey(target: WorkOperation["targets"][number]) {
  return (
    target.id ??
    `${target.targetReferenceType}-${target.orchidGroupId ?? target.inboundRecordId}`
  );
}

function targetTitle(target: WorkOperation["targets"][number]) {
  if (target.targetReferenceType === "INBOUND_RECORD") {
    return `${target.varietyName} · 입고 #${target.inboundRecordId}`;
  }
  return `${target.varietyName} · 난 묶음 #${target.orchidGroupId}`;
}

function targetLocation(target: WorkOperation["targets"][number]) {
  if (target.targetReferenceType === "INBOUND_RECORD") {
    return target.locationSnapshot.tempLocation ?? "임시 위치 미지정";
  }
  return `${target.locationSnapshot.houseNumber}동 ${target.locationSnapshot.physicalBedNumber}다이 ${target.locationSnapshot.bedZoneName}`;
}

function formatDateTime(value: string) {
  return value.length >= 16 ? value.slice(0, 16).replace("T", " ") : value;
}

function detailLabel(operation: WorkOperation, key: string) {
  if (
    key === "materialName" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "materialName")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "materialName");
  }
  if (
    key === "dilutionRatio" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "dilutionRatio")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "dilutionRatio");
  }
  if (
    key === "quantity" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "quantity")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "quantity");
  }
  return DETAIL_LABELS[key] ?? key;
}

function formatDetailValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(formatDetailValue).join(", ");
  }
  if (typeof value === "object" && value !== null) {
    return Object.entries(value)
      .map(
        ([key, nestedValue]) =>
          `${DETAIL_LABELS[key] ?? key}: ${formatDetailValue(nestedValue)}`,
      )
      .join(" / ");
  }
  if (typeof value === "boolean") {
    return value ? "예" : "아니오";
  }
  return String(value);
}
