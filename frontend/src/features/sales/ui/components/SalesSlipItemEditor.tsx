import type { SalesOrchidGroupOption } from "@/entities/farm/types";
import {
  calculateSalesItemAllocated,
  calculateSalesItemAmount,
} from "../../lib/salesForm";
import type { SalesAllocationForm, SalesItemForm } from "../../model/types";
import { TextField } from "./FormFields";
import { SalesOrchidGroupSearchSelect } from "./SalesOrchidGroupSearchSelect";

export function SalesSlipItemEditor({
  item,
  index,
  canRemove,
  onAddAllocation,
  onAllocationChange,
  onAllocationRemove,
  onChange,
  onRemove,
}: {
  item: SalesItemForm;
  index: number;
  canRemove: boolean;
  onAddAllocation: (index: number, orchidGroup: SalesOrchidGroupOption) => void;
  onAllocationChange: (
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) => void;
  onAllocationRemove: (index: number, allocationIndex: number) => void;
  onChange: (index: number, field: keyof SalesItemForm, value: string) => void;
  onRemove: (index: number) => void;
}) {
  const amount = calculateSalesItemAmount(item);
  const allocatedQuantity = calculateSalesItemAllocated(item);
  const quantity = Number(item.quantity || 0);
  const allocationMatched = quantity === allocatedQuantity;

  return (
    <div className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-3">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1.2fr)_1fr_1fr_90px_110px_110px]">
        <TextField
          label="품종명"
          required
          value={item.itemName}
          onChange={(value) => onChange(index, "itemName", value)}
        />
        <TextField
          label="속"
          value={item.genus}
          onChange={(value) => onChange(index, "genus", value)}
        />
        <TextField
          label="등급/규격"
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

      <div className="mt-3 rounded-md border border-[#dfe5dc] bg-white p-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-[#17251b]">난 묶음 배분</p>
            <p
              className={`text-xs ${
                allocationMatched ? "text-[#16853b]" : "text-[#c65a11]"
              }`}
            >
              배분 {allocatedQuantity} / 품목 수량 {quantity}
            </p>
          </div>
          <div className="w-full max-w-[360px]">
            <SalesOrchidGroupSearchSelect
              onSelect={(orchidGroup) => onAddAllocation(index, orchidGroup)}
            />
          </div>
        </div>

        <div className="mt-3 space-y-2">
          {item.allocations.length === 0 ? (
            <p className="rounded-md bg-[#f6f8f5] px-3 py-3 text-sm text-[#66736a]">
              연결된 난 묶음이 없습니다.
            </p>
          ) : (
            item.allocations.map((allocation, allocationIndex) => (
              <div
                key={`${allocation.orchidGroupId}-${allocationIndex}`}
                className="grid gap-2 rounded-md border border-[#e5ebe2] bg-[#fbfcfa] p-3 md:grid-cols-[minmax(0,1.4fr)_120px_96px]"
              >
                <div>
                  <p className="text-sm font-semibold text-[#17251b]">
                    {allocation.varietyName}
                  </p>
                  <p className="text-xs text-[#6a766e]">
                    {allocation.locationLabel} / 가용{" "}
                    {allocation.availableQuantity}분
                  </p>
                </div>
                <TextField
                  label="배분 수량"
                  type="number"
                  value={allocation.quantity}
                  onChange={(value) =>
                    onAllocationChange(
                      index,
                      allocationIndex,
                      "quantity",
                      value,
                    )
                  }
                />
                <button
                  className="mt-6 rounded-md border border-[#e0b3aa] px-3 py-2 text-sm font-semibold text-[#b43b24]"
                  type="button"
                  onClick={() => onAllocationRemove(index, allocationIndex)}
                >
                  제거
                </button>
              </div>
            ))
          )}
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
