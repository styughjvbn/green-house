"use client";

import type { BedZone, House, HouseStatusSummary, OrchidGroup, WorkRecord } from "@/entities/farm/types";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type { MutationMode, MutationPayload, WorkRecordQuickFormState, WorkRecordSummary } from "../../model/types";
import ActionButton, { DisabledAction } from "./ActionButton";
import InfoMetric from "./InfoMetric";
import OrchidGroupForm from "./OrchidGroupForm";
import OrchidMovePanel from "./OrchidMovePanel";
import OrchidWorkRecordForm from "./OrchidWorkRecordForm";

export default function OrchidSelectionPanel({
  errorMessage,
  house,
  houses,
  mutationMode,
  placementEditMode,
  resolvedZone,
  saving,
  selectedBedZone,
  selectedOrchidGroup,
  workRecordForm,
  workRecordSummary,
  workRecordSummaryLoading,
  workTypes,
  onCancelMutation,
  onCreate,
  onDelete,
  onEdit,
  onMove,
  onOpenCreate,
  onOpenEdit,
  onOpenMove,
  onOpenWorkRecord,
  onTogglePlacementEditMode,
  onUpdateWorkRecordForm,
  onWorkRecordCreate,
}: {
  errorMessage: string | null;
  house: House;
  houses: HouseStatusSummary[];
  mutationMode: MutationMode;
  placementEditMode: boolean;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
  workRecordForm: WorkRecordQuickFormState;
  workRecordSummary: WorkRecordSummary;
  workRecordSummaryLoading: boolean;
  workTypes: string[];
  onCancelMutation: () => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onDelete: () => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onMove: (toBedZoneId: number, memo: string) => Promise<void>;
  onOpenCreate: () => void;
  onOpenEdit: () => void;
  onOpenMove: () => void;
  onOpenWorkRecord: () => void;
  onTogglePlacementEditMode: () => void;
  onUpdateWorkRecordForm: <K extends keyof WorkRecordQuickFormState>(field: K, value: WorkRecordQuickFormState[K]) => void;
  onWorkRecordCreate: () => Promise<void>;
}) {
  const zone = selectedOrchidGroup ? findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null : selectedBedZone;
  const totalQuantity = zone?.orchidGroups.reduce((sum, orchidGroup) => sum + orchidGroup.quantity, 0) ?? 0;

  return (
    <aside className="space-y-3">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">선택한 난 묶음</p>
        {selectedOrchidGroup ? (
          <div className="mt-3">
            <div className="flex gap-3">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-[#d8edd5] text-2xl">●</div>
              <div>
                <h2 className="text-lg font-semibold">{selectedOrchidGroup.varietyName}</h2>
                <p className="mt-0.5 text-sm text-[#435047]">{selectedOrchidGroup.quantity}분</p>
                <p className="mt-0.5 text-sm text-[#246df2]">
                  {selectedOrchidGroup.houseNumber}동 &gt; {selectedOrchidGroup.physicalBedNumber}배드 &gt; {selectedOrchidGroup.bedZoneName}
                </p>
              </div>
            </div>
            <div className="mt-3 grid gap-2">
              <ActionButton label="난 묶음 추가" onClick={onOpenCreate} primary />
              <ActionButton label="난 묶음 수정" onClick={onOpenEdit} />
              <ActionButton label="난 묶음 삭제" onClick={onDelete} danger disabled={saving} />
              <ActionButton label={placementEditMode ? "배치 수정 종료" : "배치 수정 시작"} onClick={onTogglePlacementEditMode} />
              <ActionButton label="다른 위치로 이동" onClick={onOpenMove} />
              <ActionButton label="작업 기록 추가" onClick={onOpenWorkRecord} />
              <DisabledAction label="출력" />
            </div>
          </div>
        ) : (
          <div className="mt-3">
            <p className="text-sm text-[#5c6a60]">구역을 선택한 뒤 난 묶음을 추가할 수 있습니다.</p>
            <button className="mt-3 w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white" onClick={onOpenCreate} type="button">
              난 묶음 추가
            </button>
          </div>
        )}
        {errorMessage ? <p className="mt-3 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-2 text-xs text-[#9b341e]">{errorMessage}</p> : null}
        {placementEditMode ? (
          <p className="mt-3 rounded-md border border-[#b9d0ff] bg-[#f3f7ff] p-2 text-xs font-semibold text-[#246df2]">
            배치 수정 중: 난 묶음을 다른 좌/우 구역으로 드래그하세요.
          </p>
        ) : null}
      </section>

      {mutationMode === "CREATE" || mutationMode === "EDIT" ? (
        <OrchidGroupForm
          initialValue={mutationMode === "EDIT" ? selectedOrchidGroup : null}
          mode={mutationMode}
          saving={saving}
          targetZone={resolvedZone}
          onCancel={onCancelMutation}
          onSubmit={mutationMode === "EDIT" ? onEdit : onCreate}
        />
      ) : null}

      {mutationMode === "MOVE" && selectedOrchidGroup ? (
        <OrchidMovePanel
          currentHouse={house}
          houses={houses}
          saving={saving}
          selectedOrchidGroup={selectedOrchidGroup}
          onCancel={onCancelMutation}
          onMove={onMove}
        />
      ) : null}

      {mutationMode === "WORK_RECORD" ? (
        <OrchidWorkRecordForm
          form={workRecordForm}
          resolvedZone={resolvedZone}
          saving={saving}
          selectedOrchidGroup={selectedOrchidGroup}
          workTypes={workTypes}
          onCancel={onCancelMutation}
          onChange={onUpdateWorkRecordForm}
          onSubmit={onWorkRecordCreate}
        />
      ) : null}

      <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <p className="text-base font-semibold">선택한 구역 정보</p>
          {zone ? <span className="rounded-md bg-[#e6f0ff] px-2 py-1 text-sm font-semibold text-[#246df2]">{zone.name}</span> : null}
        </div>
        {zone ? (
          <>
            <div className="mt-3 grid grid-cols-4 gap-2 text-center">
              <InfoMetric label="난 묶음 수" value={`${zone.orchidGroups.length}개`} />
              <InfoMetric label="총 수량" value={`${totalQuantity}분`} />
              <InfoMetric label="빈 공간" value={`${Math.max(0, 5 - zone.orchidGroups.length)}칸`} />
              <InfoMetric label="상태" value="정상" />
            </div>
            <div className="mt-3 border-t border-[#e1e6df] pt-3">
              <p className="font-semibold">최근 작업 요약</p>
              <WorkRecordSummaryView loading={workRecordSummaryLoading} summary={workRecordSummary} />
            </div>
          </>
        ) : (
          <p className="mt-3 text-sm text-[#5c6a60]">구역 또는 난 묶음을 선택하세요.</p>
        )}
      </section>
    </aside>
  );
}

function WorkRecordSummaryView({ loading, summary }: { loading: boolean; summary: WorkRecordSummary }) {
  if (loading) {
    return <p className="mt-2 rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">최근 작업 확인 중</p>;
  }

  return (
    <div className="mt-2 space-y-3">
      <dl className="space-y-1 text-xs text-[#5c6a60]">
        <SummaryRow label="최근 농약" record={summary.latestByType.pesticide} />
        <SummaryRow label="최근 비료" record={summary.latestByType.fertilizer} />
        <SummaryRow label="최근 분갈이" record={summary.latestByType.repot} />
      </dl>

      {summary.latestRecords.length > 0 ? (
        <ul className="space-y-1">
          {summary.latestRecords.map((record) => (
            <li key={record.id} className="rounded-md border border-[#e1e6df] bg-[#fbfcfa] px-2 py-1.5 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold text-[#17251b]">{record.workType}</span>
                <span className="text-[#5c6a60]">{record.workDate}</span>
              </div>
              <p className="mt-0.5 truncate text-[#5c6a60]">{formatWorkRecordDetail(record)}</p>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">등록된 작업 기록 없음</p>
      )}
    </div>
  );
}

function SummaryRow({ label, record }: { label: string; record: WorkRecord | null }) {
  return (
    <div className="flex justify-between gap-3">
      <dt>{label}</dt>
      <dd className="font-semibold text-[#17251b]">{record ? record.workDate : "없음"}</dd>
    </div>
  );
}

function formatWorkRecordDetail(record: WorkRecord) {
  const details = [record.materialName, record.quantity, record.worker, record.memo].filter(Boolean);
  return details.length > 0 ? details.join(" · ") : formatWorkTarget(record);
}

function formatWorkTarget(record: WorkRecord) {
  if (record.targetType === "BED_ZONE") {
    return "구역 기록";
  }
  if (record.targetType === "ORCHID_GROUP") {
    return `난 묶음 #${record.targetId}`;
  }
  return "작업 기록";
}

