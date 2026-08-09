import type {
  WorkExecutionDetail,
  WorkExecutionResult,
  WorkExecutionSource,
  WorkOperationDetail,
} from "../../model/types";

export function CompletedWorkDetails({
  detail,
  corrected,
  loading,
  error,
}: {
  detail: WorkOperationDetail | null;
  corrected: boolean;
  loading: boolean;
  error: string | null;
}) {
  if (loading) {
    return (
      <p className="mt-4 rounded-md bg-[#f4f7f3] p-3 text-xs text-[#5c6a60]">
        완료 상세 확인 중
      </p>
    );
  }
  if (error) {
    return (
      <p className="mt-4 rounded-md border border-[#e2b5aa] bg-[#fff8f6] p-3 text-xs font-semibold text-[#a33a24]">
        {error}
      </p>
    );
  }
  if (!detail) return null;

  const executions = detail.executions.filter(hasDisplayResult);

  return (
    <>
      {executions.length > 0 ? (
        <section className="mt-4 rounded-md border border-[#dbe5d9] bg-[#f8faf7] p-4">
          <h3 className="text-sm font-bold text-[#26352b]">완료 결과</h3>
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
        </section>
      ) : null}

      {corrected ? (
        <section className="mt-4 rounded-md border border-[#d8dfc4] bg-[#fbfcef] p-4">
          <h3 className="text-sm font-bold text-[#344138]">보정 내역</h3>
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
            <p className="mt-2 text-xs text-[#5c6a60]">
              등록된 보정 내역이 없습니다.
            </p>
          )}
        </section>
      ) : null}
    </>
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
