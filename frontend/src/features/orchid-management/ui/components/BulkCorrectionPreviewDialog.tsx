"use client";

import { createPortal } from "react-dom";
import type { OrchidGroup, VarietyOption } from "@/entities/farm/types";
import type { OrchidGroupBatchUpdateItem } from "../../model/types";

export default function BulkCorrectionPreviewDialog({
  items,
  orchidGroups,
  selectedVariety,
  saving,
  errorMessage,
  onCancel,
  onConfirm,
}: {
  items: OrchidGroupBatchUpdateItem[];
  orchidGroups: OrchidGroup[];
  selectedVariety: VarietyOption | null;
  saving: boolean;
  errorMessage: string | null;
  onCancel: () => void;
  onConfirm: () => Promise<boolean>;
}) {
  const originals = new Map(orchidGroups.map((group) => [group.id, group]));

  return createPortal(
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
    >
      <section
        aria-modal="true"
        className="flex max-h-[85vh] w-full max-w-4xl flex-col rounded-lg bg-white p-4 shadow-xl"
        role="dialog"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-[#17251b]">
              일괄 보정 대상 확인
            </h2>
            <p className="mt-1 text-sm text-[#5c6a60]">
              아래 {items.length}개 난 묶음의 변경값을 확인하고 다시 보정 저장을
              눌러주세요.
            </p>
            <p className="mt-1.5 text-xs font-bold text-[#a65d00]">
              주황색으로 표시된 값만 실제로 변경됩니다.
            </p>
          </div>
          <button
            className="rounded-md border px-2 py-1 text-sm"
            onClick={onCancel}
            type="button"
          >
            닫기
          </button>
        </div>
        <div className="mt-3 min-h-0 flex-1 overflow-auto rounded-md border">
          <table className="w-full min-w-[900px] border-collapse text-left text-xs">
            <thead className="sticky top-0 bg-[#f5f7f3] text-[#435047]">
              <tr>
                <th className="p-2">난 묶음</th>
                <th className="p-2">품종</th>
                <th className="p-2">수량</th>
                <th className="p-2">년생</th>
                <th className="p-2">화분</th>
                <th className="p-2">상태</th>
                <th className="p-2">배치 규격</th>
                <th className="p-2">메모</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const before = originals.get(item.orchidGroupId);
                if (!before) return null;
                return (
                  <tr className="border-t" key={item.orchidGroupId}>
                    <td className="p-2 font-semibold">
                      #{before.id}
                      <br />
                      {before.houseNumber}동 {before.physicalBedNumber}다이
                    </td>
                    <ChangeCell
                      before={before.varietyName}
                      after={resolveVarietyName(
                        item.update.varietyId,
                        orchidGroups,
                        selectedVariety,
                      )}
                      changed={before.varietyId !== item.update.varietyId}
                    />
                    <ChangeCell
                      before={before.quantity}
                      after={item.update.quantity}
                    />
                    <ChangeCell
                      before={before.ageYear ?? "-"}
                      after={item.update.ageYear ?? "-"}
                    />
                    <ChangeCell
                      before={before.potSize ?? "-"}
                      after={item.update.potSize ?? "-"}
                    />
                    <ChangeCell
                      before={before.status}
                      after={item.update.status}
                    />
                    <ChangeCell
                      before={before.placementType ?? "-"}
                      after={item.update.placementType ?? "-"}
                    />
                    <ChangeCell
                      before={before.memo ?? "-"}
                      after={item.update.memo ?? "-"}
                    />
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {errorMessage ? (
          <p className="mt-3 rounded-md border border-[#efb1ad] bg-[#fff4f3] px-3 py-2 text-xs font-semibold text-[#a22b22]">
            {errorMessage}
          </p>
        ) : null}
        <button
          className="mt-3 w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={saving}
          onClick={() => void onConfirm()}
          type="button"
        >
          {saving ? "저장 중..." : "보정 저장"}
        </button>
      </section>
    </div>,
    document.body,
  );
}

function ChangeCell({
  before,
  after,
  changed = String(before) !== String(after),
}: {
  before: string | number;
  after: string | number;
  changed?: boolean;
}) {
  if (!changed) {
    return <td className="bg-[#fafbfa] p-2 text-[#879087]">{before}</td>;
  }

  return (
    <td className="bg-[#fff2d8] p-2 shadow-[inset_3px_0_0_#e38a12]">
      <span className="inline-flex rounded bg-[#e38a12] px-1.5 py-0.5 text-[10px] font-bold text-white">
        변경
      </span>
      <div className="mt-1 text-[11px] text-[#8a6a3f] line-through">
        {before}
      </div>
      <div className="mt-0.5 font-bold text-[#7a4200]">→ {after}</div>
    </td>
  );
}

function resolveVarietyName(
  varietyId: number,
  groups: OrchidGroup[],
  selectedVariety: VarietyOption | null,
) {
  if (selectedVariety?.id === varietyId) return selectedVariety.name;
  return (
    groups.find((group) => group.varietyId === varietyId)?.varietyName ??
    `품종 #${varietyId}`
  );
}
