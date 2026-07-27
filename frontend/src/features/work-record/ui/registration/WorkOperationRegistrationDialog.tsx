"use client";

import { useEffect } from "react";
import { X } from "lucide-react";
import type { House, WorkType } from "@/entities/farm/types";
import { useWorkOperationRegistration } from "../../model/registration/useWorkOperationRegistration";
import { WorkRecordResultDialog } from "./WorkRecordResultDialog";
import { InboundPottingTargetDialog } from "./InboundPottingTargetDialog";
import { WorkOperationPlanForm } from "./WorkOperationPlanForm";
import { WorkTargetSelectionDialog } from "./WorkTargetSelectionDialog";

export function WorkOperationRegistrationDialog({
  houses,
  workTypes,
  onClose,
  onSaved,
}: {
  houses: House[];
  workTypes: WorkType[];
  onClose: () => void;
  onSaved?: () => void;
}) {
  const registration = useWorkOperationRegistration({
    houses,
    onClose,
    onSaved,
    workTypes,
  });

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !registration.targetSelectorOpen) {
        onClose();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose, registration.targetSelectorOpen]);

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-6xl flex-col overflow-hidden rounded-md bg-[#f5fbf5] shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="작업 등록"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex shrink-0 items-start justify-between border-b border-[#dbe8dc] bg-white px-5 py-3">
          <div className="flex items-end gap-3">
            <h2 className="text-xl font-semibold text-[#17251b]">작업 등록</h2>
            <p className="text-sm text-[#5c6a60]">
              완료된 작업을 기록하거나 기간을 정해 작업을 계획합니다.
            </p>
          </div>
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </header>

        <div className="min-h-0 overflow-y-auto p-5">
          {registration.errorMessage ? (
            <p className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
              {registration.errorMessage}
            </p>
          ) : null}
          <WorkOperationPlanForm
            autoSplitWorkCount={registration.autoSplitWorkCount}
            canPreview={registration.canPreview}
            excludedIds={registration.excludedIds}
            form={registration.form}
            includedQuantity={registration.includedQuantity}
            includedTargetCount={registration.includedTargets.length}
            isDedicatedWorkflow={registration.isDedicatedWorkflow}
            isInboundPotting={registration.isInboundPotting}
            loading={registration.loading}
            optionsLoading={registration.optionsLoading}
            preview={registration.preview}
            registrationMode={registration.registrationMode}
            saveUnavailableReason={registration.saveUnavailableReason}
            selectedWorkType={registration.selectedWorkType}
            targetCount={
              registration.isInboundPotting
                ? registration.inboundRecordIds.size
                : registration.manualIds.size
            }
            targetSummary={registration.targetSummary}
            workTypes={registration.schedulableWorkTypes}
            onCancel={onClose}
            onChangeRegistrationMode={registration.setRegistrationMode}
            onLoadPreview={registration.loadPreview}
            onOpenTargetSelector={registration.openTargetSelector}
            onSave={registration.saveOperation}
            onSelectFarmTarget={registration.selectFarmTarget}
            onToggleExcluded={registration.toggleExcluded}
            onUpdateForm={registration.updateForm}
          />
        </div>
      </section>

      {registration.targetSelectorOpen ? (
        registration.isInboundPotting ? (
          <InboundPottingTargetDialog
            candidates={registration.inboundCandidates}
            initialSelectedIds={registration.inboundRecordIds}
            onClose={registration.closeTargetSelector}
            onConfirm={registration.confirmInboundTargets}
          />
        ) : (
          <WorkTargetSelectionDialog
            bedZones={registration.bedZones}
            groups={registration.orchidGroups}
            initialSelectedIds={registration.manualIds}
            onClose={registration.closeTargetSelector}
            onConfirm={registration.confirmManualTargets}
          />
        )
      ) : null}

      {registration.recordResultOpen &&
      registration.selectedWorkType &&
      registration.recordResultKind ? (
        <WorkRecordResultDialog
          candidates={registration.inboundCandidates}
          form={registration.form}
          houses={houses}
          inboundRecordIds={registration.inboundRecordIds}
          kind={registration.recordResultKind}
          orchidGroupIds={registration.recordTargetIds}
          orchidGroups={registration.orchidGroups}
          targets={registration.includedTargets}
          workType={registration.selectedWorkType}
          onClose={registration.closeRecordResult}
          onSaved={registration.recordSaved}
        />
      ) : null}
    </div>
  );
}
