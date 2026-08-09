import type { WorkOperation } from "@/entities/farm/types";
import type {
  WorkExecutionDetail,
  WorkExecutionResult,
  WorkExecutionSource,
  WorkOperationDetail,
} from "../../model/types";
import {
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";

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

export function WorkOperationDetails({
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
  const completed = operation.status === "COMPLETED";
  const corrected = operation.status === "CORRECTED";
  const showCompletionDetails = completed || corrected;
  const details = showCompletionDetails
    ? (detail?.fields.filter((field) => !isHiddenDetailKey(field.key)) ?? [])
    : Object.entries(operation.details ?? {})
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

  const items = [
    { key: "workType", label: "작업 유형", value: operation.workType },
    ...details,
    ...(operation.worker
      ? [{ key: "worker", label: "작업자", value: operation.worker }]
      : []),
    ...(operation.memo
      ? [{ key: "memo", label: "메모", value: operation.memo }]
      : []),
  ];

  const executions = detail?.executions.filter(hasDisplayResult) ?? [];

  return (
    <section className="mt-4 rounded-md border border-[#e1e6df] bg-[#fbfcfa] p-4">
      <h3 className="text-sm font-bold text-[#26352b]">작업 정보</h3>
      <dl className="mt-3 grid gap-x-5 gap-y-3 sm:grid-cols-2">
        {items.map((item) => (
          <div className="min-w-0" key={item.key}>
            <dt className="text-xs font-semibold text-[#6a766e]">
              {item.label}
            </dt>
            <dd className="mt-1 text-sm font-semibold break-words whitespace-pre-wrap text-[#26352b]">
              {item.value}
            </dd>
          </div>
        ))}
      </dl>

      {showCompletionDetails ? (
        <div className="mt-4 border-t border-[#e1e6df] pt-4">
          {loading ? (
            <p className="rounded-md bg-[#f4f7f3] p-3 text-xs text-[#5c6a60]">
              완료 상세 확인 중
            </p>
          ) : error ? (
            <p className="rounded-md border border-[#e2b5aa] bg-[#fff8f6] p-3 text-xs font-semibold text-[#a33a24]">
              {error}
            </p>
          ) : detail ? (
            <>
              <h3 className="text-sm font-bold text-[#26352b]">실행 결과</h3>
              {executions.length > 0 ? (
                <ol className="mt-3 space-y-2">
                  {executions.map((execution, index) => (
                    <li
                      className="rounded-md border border-[#e1e6df] bg-white p-3"
                      key={execution.id}
                    >
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <p className="text-xs font-bold text-[#344138]">
                          {resultTypeLabel(execution.resultType)}
                          {executions.length > 1 ? ` ${index + 1}회차` : ""}
                        </p>
                        <p className="text-xs text-[#6a766e]">
                          {execution.appliedAt.slice(0, 10)}
                          {execution.worker ? ` · ${execution.worker}` : ""}
                        </p>
                      </div>
                      <ExecutionContent execution={execution} />
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="mt-2 text-xs text-[#5c6a60]">
                  별도로 기록된 실행 결과가 없습니다.
                </p>
              )}

              {corrected ? (
                <div className="mt-4 border-t border-[#e1e6df] pt-4">
                  <h3 className="text-sm font-bold text-[#344138]">
                    보정 내역
                  </h3>
                  {detail.corrections.length > 0 ? (
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
                              {correction.worker
                                ? ` · ${correction.worker}`
                                : ""}
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
                              {adjustment.beforeQuantity} →{" "}
                              {adjustment.afterQuantity}분 · 상태{" "}
                              {adjustment.beforeStatus} →{" "}
                              {adjustment.afterStatus}
                            </p>
                          ))}
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <p className="mt-2 text-xs text-[#5c6a60]">
                      등록된 보정 내역이 없습니다.
                    </p>
                  )}
                </div>
              ) : null}
            </>
          ) : null}
        </div>
      ) : null}
    </section>
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
      {execution.results.map((result, index) => (
        <p key={`${result.orchidGroupId ?? "result"}-${index}`}>
          {resultLine(result, index)}
        </p>
      ))}
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

function resultLine(result: WorkExecutionResult, index: number) {
  const title =
    result.orchidGroupId == null
      ? `결과 ${index + 1}`
      : `결과 난 묶음 #${result.orchidGroupId}`;
  const values: string[] = [];
  if (result.quantity != null) values.push(`${result.quantity}분`);
  if (result.purpose) values.push(purposeLabel(result.purpose));
  if (result.bedZoneId != null) values.push(`구역 #${result.bedZoneId}`);
  if (result.startPosition != null && result.endPosition != null) {
    values.push(`위치 ${result.startPosition}~${result.endPosition}`);
  }
  if (result.potSize) values.push(result.potSize);
  if (result.ageYear != null) values.push(`${result.ageYear}년생`);
  return values.length > 0 ? `${title} · ${values.join(" · ")}` : title;
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
