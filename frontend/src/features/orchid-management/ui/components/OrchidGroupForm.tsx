"use client";

import { FormEvent, useState } from "react";
import type { BedZone, OrchidGroup } from "@/types/farm";
import { nullableNumber, nullableText } from "../../lib/orchidManagementUtils";
import type { MutationPayload, OrchidFormState } from "../../model/types";
import TextField from "./TextField";

export default function OrchidGroupForm({
  initialValue,
  mode,
  saving,
  targetZone,
  onCancel,
  onSubmit,
}: {
  initialValue: OrchidGroup | null;
  mode: "CREATE" | "EDIT";
  saving: boolean;
  targetZone: BedZone | null;
  onCancel: () => void;
  onSubmit: (payload: MutationPayload) => Promise<void>;
}) {
  const [form, setForm] = useState<OrchidFormState>(() => ({
    genus: initialValue?.genus ?? "",
    varietyName: initialValue?.varietyName ?? "",
    quantity: initialValue ? String(initialValue.quantity) : "1",
    potSize: initialValue?.potSize ?? "",
    ageYear: initialValue?.ageYear ? String(initialValue.ageYear) : "",
    status: initialValue?.status ?? "정상",
    placementType: initialValue?.placementType ?? "",
    trayCount: initialValue?.trayCount ? String(initialValue.trayCount) : "",
    memo: initialValue?.memo ?? "",
  }));

  function updateField(field: keyof OrchidFormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSubmit({
      genus: nullableText(form.genus),
      varietyName: form.varietyName.trim(),
      quantity: Number(form.quantity),
      potSize: nullableText(form.potSize),
      ageYear: nullableNumber(form.ageYear),
      status: form.status.trim(),
      placementType: nullableText(form.placementType),
      trayCount: nullableNumber(form.trayCount),
      memo: nullableText(form.memo),
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">{mode === "CREATE" ? "난 묶음 추가" : "난 묶음 수정"}</p>
          <h3 className="mt-1 text-base font-semibold">{targetZone?.name ?? "구역 선택 필요"}</h3>
        </div>
        <button className="rounded-md border border-[#d7ddd4] px-2 py-1.5 text-xs font-semibold" onClick={onCancel} type="button">
          닫기
        </button>
      </div>
      <form className="mt-3 space-y-2" onSubmit={handleSubmit}>
        <TextField label="품종명" required value={form.varietyName} onChange={(value) => updateField("varietyName", value)} />
        <div className="grid grid-cols-2 gap-2">
          <TextField label="속명" value={form.genus} onChange={(value) => updateField("genus", value)} />
          <TextField label="수량" required type="number" value={form.quantity} onChange={(value) => updateField("quantity", value)} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField label="화분 크기" value={form.potSize} onChange={(value) => updateField("potSize", value)} />
          <TextField label="년생" type="number" value={form.ageYear} onChange={(value) => updateField("ageYear", value)} />
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">상태</span>
          <select className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm" value={form.status} onChange={(event) => updateField("status", event.target.value)}>
            <option value="정상">정상</option>
            <option value="주의">주의</option>
            <option value="이상">이상</option>
            <option value="판매 가능">판매 가능</option>
          </select>
        </label>
        <div className="grid grid-cols-2 gap-2">
          <TextField label="배치 유형" value={form.placementType} onChange={(value) => updateField("placementType", value)} />
          <TextField label="트레이 수" type="number" value={form.trayCount} onChange={(value) => updateField("trayCount", value)} />
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">메모</span>
          <textarea className="mt-1 min-h-16 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm" value={form.memo} onChange={(event) => updateField("memo", event.target.value)} />
        </label>
        <button className="w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" disabled={saving || !targetZone} type="submit">
          {saving ? "저장 중" : "저장"}
        </button>
      </form>
    </section>
  );
}
