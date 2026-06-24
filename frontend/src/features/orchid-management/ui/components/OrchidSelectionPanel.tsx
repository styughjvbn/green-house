"use client";

import type { BedZone, House, HouseStatusSummary, OrchidGroup } from "@/types/farm";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type { MutationMode, MutationPayload } from "../../model/types";
import ActionButton, { DisabledAction } from "./ActionButton";
import InfoMetric from "./InfoMetric";
import OrchidGroupForm from "./OrchidGroupForm";
import OrchidMovePanel from "./OrchidMovePanel";

export default function OrchidSelectionPanel({
  errorMessage,
  house,
  houses,
  mutationMode,
  resolvedZone,
  saving,
  selectedBedZone,
  selectedOrchidGroup,
  onCancelMutation,
  onCreate,
  onDelete,
  onEdit,
  onMove,
  onOpenCreate,
  onOpenEdit,
  onOpenMove,
}: {
  errorMessage: string | null;
  house: House;
  houses: HouseStatusSummary[];
  mutationMode: MutationMode;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
  onCancelMutation: () => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onDelete: () => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onMove: (toBedZoneId: number, memo: string) => Promise<void>;
  onOpenCreate: () => void;
  onOpenEdit: () => void;
  onOpenMove: () => void;
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
              <ActionButton label="다른 위치로 이동" onClick={onOpenMove} />
              <DisabledAction label="작업 기록 추가" />
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
              <dl className="mt-2 space-y-1 text-xs text-[#5c6a60]">
                <div className="flex justify-between"><dt>최근 농약</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 비료</dt><dd>다음 단계에서 연결</dd></div>
                <div className="flex justify-between"><dt>최근 분갈이</dt><dd>다음 단계에서 연결</dd></div>
              </dl>
            </div>
          </>
        ) : (
          <p className="mt-3 text-sm text-[#5c6a60]">구역 또는 난 묶음을 선택하세요.</p>
        )}
      </section>
    </aside>
  );
}
