import type { SalesItemForm } from "../../model/types";
import { calculateSalesItemAmount } from "../../lib/salesForm";
import { TextField } from "./FormFields";

export function SalesSlipItemEditor({
  item,
  index,
  canRemove,
  onChange,
  onRemove,
}: {
  item: SalesItemForm;
  index: number;
  canRemove: boolean;
  onChange: (index: number, field: keyof SalesItemForm, value: string) => void;
  onRemove: (index: number) => void;
}) {
  const amount = calculateSalesItemAmount(item);

  return (
    <div className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-3">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1.2fr)_1fr_1fr_90px_110px_110px]">
        <TextField
          label="품목명"
          required
          value={item.itemName}
          onChange={(value) => onChange(index, "itemName", value)}
        />
        <TextField
          label="속명"
          value={item.genus}
          onChange={(value) => onChange(index, "genus", value)}
        />
        <TextField
          label="규격"
          value={item.spec}
          onChange={(value) => onChange(index, "spec", value)}
        />
        <TextField
          label="수량"
          required
          type="number"
          value={item.quantity}
          onChange={(value) => onChange(index, "quantity", value)}
        />
        <TextField
          label="단가"
          required
          type="number"
          value={item.unitPrice}
          onChange={(value) => onChange(index, "unitPrice", value)}
        />
        <div>
          <p className="text-sm font-semibold text-[#435047]">금액</p>
          <p className="mt-2 text-sm font-semibold">
            {amount.toLocaleString()}원
          </p>
        </div>
      </div>
      <div className="mt-2 flex gap-2">
        <TextField
          label="품목 메모"
          value={item.memo}
          onChange={(value) => onChange(index, "memo", value)}
        />
        {canRemove ? (
          <button
            className="mt-6 rounded-md border border-[#e0b3aa] px-3 py-2 text-sm font-semibold text-[#b43b24]"
            onClick={() => onRemove(index)}
            type="button"
          >
            삭제
          </button>
        ) : null}
      </div>
    </div>
  );
}
