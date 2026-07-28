"use client";

import type {
  House,
  OrchidGroup,
  WorkOperationTarget,
  WorkType,
} from "@/entities/farm/types";
import {
  createDiscardRecord,
  createInboundPottingRecord,
  createStructureChangeRecords,
} from "../../api/workRecordApi";
import type {
  InboundPottingCandidate,
  WorkOperationFormState,
} from "../../model/types";
import type { WorkRecordResultKind } from "../../model/work-types/workTypeDefinition";
import { DiscardWorkRecordDialog } from "../work-types/discard/DiscardWorkRecordDialog";
import { PottingWorkRecordDialog } from "../work-types/potting/PottingWorkRecordDialog";
import { StructureChangeWorkRecordDialog } from "../work-types/structure-change/StructureChangeWorkRecordDialog";

type WorkRecordResultDialogProps = {
  candidates: InboundPottingCandidate[];
  form: WorkOperationFormState;
  houses: House[];
  inboundRecordIds: Set<number>;
  kind: WorkRecordResultKind;
  orchidGroupIds: number[];
  orchidGroups: OrchidGroup[];
  targets: WorkOperationTarget[];
  workType: WorkType;
  onClose: () => void;
  onSaved: () => void;
};

export function WorkRecordResultDialog({
  candidates,
  form,
  houses,
  inboundRecordIds,
  kind,
  orchidGroupIds,
  orchidGroups,
  targets,
  workType,
  onClose,
  onSaved,
}: WorkRecordResultDialogProps) {
  const operation = {
    workTypeId: workType.id,
    title: form.title.trim(),
    plannedStartDate: form.plannedStartDate,
    plannedEndDate: form.plannedStartDate,
    sourceScopeType: "MANUAL_SELECTION" as const,
    sourceOrchidGroupIds: orchidGroupIds,
    details: {},
    worker: form.worker.trim() || null,
    memo: form.memo.trim() || null,
    excludedOrchidGroupIds: [],
  };

  switch (kind) {
    case "STRUCTURE_CHANGE":
      return (
        <StructureChangeWorkRecordDialog
          baseOperation={operation}
          houses={houses}
          orchidGroups={orchidGroups}
          targets={targets}
          workType={workType}
          onClose={onClose}
          onSubmit={async (records) => {
            await createStructureChangeRecords(records);
            onSaved();
          }}
        />
      );
    case "DISCARD":
      return (
        <DiscardWorkRecordDialog
          groups={orchidGroups.filter((group) =>
            orchidGroupIds.includes(group.id),
          )}
          initialCompletedDate={form.plannedStartDate}
          initialWorker={form.worker}
          onClose={onClose}
          onSubmit={async ({ completedDate, worker, results }) => {
            await createDiscardRecord({
              operation,
              completedDate,
              worker,
              results,
            });
            onSaved();
          }}
        />
      );
    case "POTTING":
      return (
        <PottingWorkRecordDialog
          candidates={candidates.filter((candidate) =>
            inboundRecordIds.has(candidate.id),
          )}
          houses={houses}
          workDate={form.plannedStartDate}
          worker={form.worker}
          onClose={onClose}
          onSubmit={async (executions) => {
            await createInboundPottingRecord({
              plan: {
                title: form.title.trim(),
                plannedStartDate: form.plannedStartDate,
                plannedEndDate: form.plannedStartDate,
                inboundRecordIds: [...inboundRecordIds],
                worker: form.worker.trim() || null,
                memo: form.memo.trim() || null,
              },
              executions,
            });
            onSaved();
          }}
        />
      );
  }
}
