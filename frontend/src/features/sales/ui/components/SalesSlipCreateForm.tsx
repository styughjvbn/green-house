import type { FormEvent } from "react";
import type { Customer } from "@/entities/farm/types";
import type { SalesItemForm, SalesSlipForm } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { SalesSlipItemEditor } from "./SalesSlipItemEditor";

export function SalesSlipCreateForm({
  customers,
  errorMessage,
  form,
  saving,
  totalAmount,
  onAddItem,
  onChange,
  onRemoveItem,
  onSubmit,
  onUpdateItem,
}: {
  customers: Customer[];
  errorMessage: string | null;
  form: SalesSlipForm;
  saving: boolean;
  totalAmount: number;
  onAddItem: () => void;
  onChange: <K extends keyof SalesSlipForm>(field: K, value: SalesSlipForm[K]) => void;
  onRemoveItem: (index: number) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateItem: (index: number, field: keyof SalesItemForm, value: string) => void;
}) {
  return (
    <form className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm" onSubmit={onSubmit}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">판매 전표</p>
          <h2 className="mt-1 text-xl font-semibold">새 판매 전표</h2>
        </div>
        <div className="rounded-md bg-[#eef7ec] px-3 py-2 text-sm font-semibold text-[#246b38]">
          합계 {totalAmount.toLocaleString()}원
        </div>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <TextField label="판매일" required type="date" value={form.saleDate} onChange={(value) => onChange("saleDate", value)} />
        <SelectField label="거래처" value={form.customerId} onChange={(value) => onChange("customerId", value)}>
          <option value="">선택</option>
          {customers.map((customer) => (
            <option key={customer.id} value={customer.id}>
              {customer.name}
            </option>
          ))}
        </SelectField>
        <SelectField label="입금 상태" value={form.paymentStatus} onChange={(value) => onChange("paymentStatus", value)}>
          <option value="미입금">미입금</option>
          <option value="입금 완료">입금 완료</option>
        </SelectField>
        <SelectField label="판매 상태" value={form.salesStatus} onChange={(value) => onChange("salesStatus", value)}>
          <option value="작성중">작성중</option>
          <option value="출고 완료">출고 완료</option>
        </SelectField>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <TextField label="결제 방법" value={form.paymentMethod} onChange={(value) => onChange("paymentMethod", value)} />
        <TextField label="메모" value={form.memo} onChange={(value) => onChange("memo", value)} />
      </div>

      <div className="mt-4 space-y-3">
        {form.items.map((item, index) => (
          <SalesSlipItemEditor
            key={index}
            item={item}
            index={index}
            canRemove={form.items.length > 1}
            onChange={onUpdateItem}
            onRemove={onRemoveItem}
          />
        ))}
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <button className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold" onClick={onAddItem} type="button">
          품목 추가
        </button>
        <button
          className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={saving || !form.customerId}
          type="submit"
        >
          {saving ? "저장 중" : "판매 전표 저장"}
        </button>
      </div>

      {errorMessage ? <p className="mt-3 rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">{errorMessage}</p> : null}
    </form>
  );
}

