"use client";

import { useMemo, useState } from "react";
import { X } from "lucide-react";
import type { House } from "@/entities/farm/types";
import {
  PottingExecutionForm,
  type PottingExecutionValues,
} from "@/entities/farm/ui/PottingExecutionForm";
import { WorkRecordVarietyNavigation } from "@/shared/ui/WorkRecordVarietyNavigation";
import type { InboundPottingExecutionPayload } from "../../../api/workRecordApi";
import type { InboundPottingCandidate } from "../../../model/types";

export function PottingWorkRecordDialog({
  candidates,
  houses,
  workDate,
  worker,
  onClose,
  onSubmit,
}: {
  candidates: InboundPottingCandidate[];
  houses: House[];
  workDate: string;
  worker: string;
  onClose: () => void;
  onSubmit: (executions: InboundPottingExecutionPayload[]) => Promise<void>;
}) {
  const groups = useMemo(
    () => groupCandidatesByVariety(candidates),
    [candidates],
  );
  const [activeKey, setActiveKey] = useState(groups[0]?.key ?? "");
  const [executions, setExecutions] = useState<
    Map<number, InboundPottingExecutionPayload>
  >(new Map());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);

  function requestClose() {
    if (
      !isDirty ||
      window.confirm("작성 중인 내용이 초기화됩니다. 닫을까요?")
    ) {
      onClose();
    }
  }

  if (candidates.length === 0) return null;

  const navigationItems = groups.map((group) => ({
    key: group.key,
    label: group.varietyName,
    completed: group.candidates.every((candidate) =>
      executions.has(candidate.id),
    ),
  }));
  const activeGroup = groups.find((group) => group.key === activeKey) ?? null;

  async function saveAll() {
    if (executions.size !== candidates.length) return;
    setSaving(true);
    setError(null);
    try {
      await onSubmit(
        candidates
          .map((candidate) => executions.get(candidate.id)!)
          .filter(Boolean),
      );
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "포트 작업 기록을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={() => {
        if (!isDirty) onClose();
      }}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-4xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="포트 작업 결과 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-end justify-between border-b p-2">
          <div className="flex gap-2">
            <h3 className="font-bold text-[#17251b]">포트 작업 결과 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              모든 결과 입력을 완료한 뒤 작업 기록을 저장합니다.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={requestClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          {groups.map((group) => (
            <div
              className={group.key === activeKey ? "space-y-4" : "hidden"}
              key={group.key}
            >
              {group.candidates.map((candidate, index) => (
                <section
                  className={
                    group.candidates.length > 1
                      ? "rounded-lg border border-[#dfe5df] p-4"
                      : undefined
                  }
                  key={candidate.id}
                >
                  {group.candidates.length > 1 ? (
                    <p className="text-sm font-bold text-[#425047]">
                      입고 결과 {index + 1}
                    </p>
                  ) : null}
                  <PottingExecutionForm
                    formId={`potting-record-${candidate.id}`}
                    fixedPottingDate={workDate}
                    houses={houses}
                    initialActualQuantity={
                      candidate.actualQuantity ?? candidate.estimatedQuantity
                    }
                    initialPotSize={candidate.potSize}
                    initialWorker={worker}
                    recordItemMode
                    showRecordSubmitButton={false}
                    subject={candidate.varietyName}
                    onCancel={requestClose}
                    onRecordDirty={() => {
                      setIsDirty(true);
                      setExecutions((current) => {
                        if (!current.has(candidate.id)) return current;
                        const next = new Map(current);
                        next.delete(candidate.id);
                        return next;
                      });
                    }}
                    onSubmit={async (values: PottingExecutionValues) => {
                      setIsDirty(true);
                      setExecutions((current) => {
                        const next = new Map(current);
                        next.set(candidate.id, {
                          inboundRecordId: candidate.id,
                          pottingDate: values.pottingDate,
                          results: values.results,
                          worker: values.worker,
                          memo: values.memo,
                        });
                        return next;
                      });
                    }}
                  />
                </section>
              ))}
            </div>
          ))}
        </div>
        <footer className="border-t bg-white p-2">
          {error ? (
            <p className="mb-3 rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {error}
            </p>
          ) : null}
          <div className="flex flex-wrap items-center justify-end gap-2">
            <WorkRecordVarietyNavigation
              activeKey={activeKey}
              items={navigationItems}
              onSelect={setActiveKey}
            />
            {activeGroup?.candidates.map((candidate, index) => (
              <button
                className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                disabled={saving}
                form={`potting-record-${candidate.id}`}
                key={candidate.id}
                type="submit"
              >
                {activeGroup.candidates.length > 1
                  ? `입고 결과 ${index + 1} 입력 완료`
                  : "현재 품종 입력 완료"}
              </button>
            ))}
            <button
              className="ml-2 rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
              disabled={saving}
              type="button"
              onClick={requestClose}
            >
              취소
            </button>
            <button
              className="rounded-md bg-[#0f6f35] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
              disabled={saving || executions.size !== candidates.length}
              type="button"
              onClick={() => void saveAll()}
            >
              {saving ? "저장 중" : "전체 작업 기록 저장"}
            </button>
          </div>
        </footer>
      </section>
    </div>
  );
}

type CandidateGroup = {
  key: string;
  varietyName: string;
  candidates: InboundPottingCandidate[];
};

function groupCandidatesByVariety(
  candidates: InboundPottingCandidate[],
): CandidateGroup[] {
  const groups = new Map<string, CandidateGroup>();

  candidates.forEach((candidate) => {
    const key =
      candidate.varietyId != null
        ? `variety:${candidate.varietyId}`
        : `name:${candidate.varietyName}`;
    const existing = groups.get(key);
    if (existing) {
      existing.candidates.push(candidate);
      return;
    }
    groups.set(key, {
      key,
      varietyName: candidate.varietyName,
      candidates: [candidate],
    });
  });

  return Array.from(groups.values());
}
