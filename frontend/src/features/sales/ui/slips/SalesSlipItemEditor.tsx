import { useId, useState } from "react";
import { ChevronDown, Trash2 } from "lucide-react";
import {
  calculateSalesItemAllocated,
  calculateSalesItemAmount,
} from "../../lib/salesForm";
import type {
  SalesAllocationForm,
  SalesItemForm,
  SalesReviewField,
} from "../../model/types";
import { TextField } from "../common/FormFields";
import {
  ReviewBadge,
  ReviewField,
  type InputReview,
} from "../common/InputReview";

export function SalesSlipItemEditor({
  item,
  index,
  showPricing,
  showAllocations = true,
  canRemove,
  disabled = false,
  onAutomaticAllocation,
  onResetAllocation,
  onAllocationChange,
  onChange,
  onRemove,
  onConfirmReview,
}: {
  item: SalesItemForm;
  index: number;
  showPricing: boolean;
  showAllocations?: boolean;
  canRemove: boolean;
  disabled?: boolean;
  onAutomaticAllocation: (index: number) => void;
  onResetAllocation: (index: number) => void;
  onAllocationChange: (
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) => void;
  onChange: (index: number, field: keyof SalesItemForm, value: string) => void;
  onRemove: (index: number) => void;
  onConfirmReview?: (field: SalesReviewField, indices?: number[]) => void;
}) {
  function reviewFor(field: SalesReviewField): InputReview | undefined {
    const reason = item.fieldReviews?.[field];
    return reason && onConfirmReview
      ? { reason, onConfirm: () => onConfirmReview(field, [index]) }
      : undefined;
  }
  const allocationId = useId();
  const [showAllocationDetails, setShowAllocationDetails] = useState(false);
  const amount = calculateSalesItemAmount(item);
  const allocatedQuantity = calculateSalesItemAllocated(item);
  const quantity = Number(item.quantity || 0);
  const allocationMatched = quantity > 0 && quantity === allocatedQuantity;
  const actionClass =
    "h-7 rounded-md border border-[#cfd8cc] bg-white px-3 text-xs font-semibold text-[#29422e] hover:bg-[#f7faf6] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447] disabled:opacity-60";

  return (
    <div
      data-sales-item-index={index}
      className="flex min-w-0 rounded-md border border-[#e5ebe6] bg-white"
    >
      <div className="min-w-0 flex-1 space-y-4 p-3">
        <div
          className={`grid grid-cols-2 items-end gap-3 ${showPricing ? "md:grid-cols-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.5fr)_minmax(0,2fr)_minmax(0,1.5fr)]" : "md:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.2fr)_minmax(0,1.5fr)]"}`}
        >
          <TextField
            disabled={disabled}
            label="등급"
            review={reviewFor("GRADE")}
            maxLength={100}
            value={item.spec}
            onChange={(value) => onChange(index, "spec", value)}
          />
          <TextField
            disabled={disabled}
            label="단위"
            review={reviewFor("UNIT")}
            type="number"
            min={1}
            value={item.unit}
            onChange={(value) => onChange(index, "unit", value)}
          />
          <TextField
            disabled={disabled}
            label="상자 수"
            review={reviewFor("BOXES")}
            type="number"
            value={item.boxes}
            onChange={(value) => onChange(index, "boxes", value)}
          />
          <TextField
            disabled={disabled}
            label="수량"
            review={reviewFor("QUANTITY")}
            required
            type="number"
            value={item.quantity}
            onChange={(value) => onChange(index, "quantity", value)}
          />
          {showPricing ? (
            <>
              <TextField
                disabled={disabled}
                label="단가"
                review={reviewFor("UNIT_PRICE")}
                required
                type="number"
                value={item.unitPrice}
                onChange={(value) => onChange(index, "unitPrice", value)}
              />
              <ReviewField
                id={`${allocationId}-amount-review`}
                review={reviewFor("AMOUNT")}
                disabled={disabled}
                className={`rounded-md px-3 py-2 ${reviewFor("AMOUNT") ? "border border-amber-500 bg-amber-50" : "bg-[#edf7ef]"}`}
              >
                <p className="text-sm font-semibold text-[#29422e]">
                  금액 {reviewFor("AMOUNT") ? <ReviewBadge /> : null}
                </p>
                <p
                  id={`${allocationId}-amount-value`}
                  tabIndex={!disabled && reviewFor("AMOUNT") ? 0 : -1}
                  aria-describedby={`${allocationId}-amount-review`}
                  className="mt-1 text-xl font-bold break-words text-[#10783a]"
                >
                  {amount.toLocaleString()}원
                </p>
              </ReviewField>
            </>
          ) : null}
          <div className="min-w-0">
            <TextField
              disabled={disabled}
              label="품목 메모"
              review={reviewFor("ITEM_MEMO")}
              maxLength={1000}
              value={item.memo}
              onChange={(value) => onChange(index, "memo", value)}
            />
          </div>
        </div>
        {showAllocations ? (
          <div className="rounded-md bg-[#f3f8f4] p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <button
                type="button"
                disabled={disabled}
                aria-expanded={showAllocationDetails}
                aria-controls={allocationId}
                className="inline-flex min-h-9 items-center gap-2 text-left text-sm font-semibold text-[#26352b] focus-visible:outline-2 focus-visible:outline-[#159447] disabled:opacity-60"
                onClick={() => setShowAllocationDetails((current) => !current)}
              >
                <ChevronDown
                  className={`h-4 w-4 shrink-0 transition-transform ${showAllocationDetails ? "" : "-rotate-90"}`}
                  aria-hidden="true"
                />
                재고 배분 내역 · {item.allocations.length}개 묶음 (수량{" "}
                {quantity.toLocaleString()})
              </button>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className={actionClass}
                  disabled={disabled || item.allocations.length === 0}
                  onClick={() => onAutomaticAllocation(index)}
                >
                  자동 배분
                </button>
                <button
                  type="button"
                  className={actionClass}
                  disabled={disabled || item.allocations.length === 0}
                  onClick={() => onResetAllocation(index)}
                >
                  배분 초기화
                </button>
              </div>
            </div>
            <div
              id={allocationId}
              hidden={!showAllocationDetails}
              className="mt-2"
            >
              {item.allocations.length === 0 ? (
                <p className="py-2 text-sm text-[#66736a]">
                  이 품종의 난 묶음을 먼저 선택하세요.
                </p>
              ) : (
                <div className="grid overflow-hidden rounded-md border border-[#d9e3db] bg-white lg:grid-cols-[minmax(0,1fr)_9rem]">
                  <div className="min-w-0 overflow-x-auto">
                    <table className="w-full min-w-[580px] text-left text-sm">
                      <caption className="sr-only">
                        {item.itemName || "판매 품목"} 재고 배분 내역
                      </caption>
                      <thead className="bg-[#f7faf6] text-[#435047]">
                        <tr>
                          <th scope="col" className="px-3 py-2 font-semibold">
                            난 묶음 정보
                          </th>
                          <th scope="col" className="px-3 py-2 font-semibold">
                            위치
                          </th>
                          <th
                            scope="col"
                            className="px-3 py-2 font-semibold whitespace-nowrap"
                          >
                            가용 수량
                          </th>
                          <th
                            scope="col"
                            className="px-3 py-2 font-semibold whitespace-nowrap"
                          >
                            배분 수량
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {item.allocations.map((allocation, allocationIndex) => (
                          <tr
                            key={allocation.orchidGroupId}
                            className="border-t border-[#e3e9e4]"
                          >
                            <th
                              scope="row"
                              className="px-3 py-3 font-medium text-[#17251b]"
                            >
                              {allocation.varietyName}
                              {allocation.genus ? ` / ${allocation.genus}` : ""}
                            </th>
                            <td className="px-3 py-3 text-[#526158]">
                              {allocation.locationLabel}
                            </td>
                            <td className="px-3 py-3 text-[#66736a] tabular-nums">
                              {allocation.availableQuantity.toLocaleString()}
                            </td>
                            <td className="px-3 py-3">
                              <label className="block min-w-28">
                                <span className="sr-only">
                                  {allocation.varietyName}{" "}
                                  {allocation.locationLabel} 배분 수량
                                </span>
                                <input
                                  type="number"
                                  min={0}
                                  disabled={disabled}
                                  value={allocation.quantity}
                                  className="min-h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-2 text-sm focus-visible:outline-2 focus-visible:outline-[#159447] disabled:bg-[#f5f7f3]"
                                  onChange={(event) =>
                                    onAllocationChange(
                                      index,
                                      allocationIndex,
                                      "quantity",
                                      event.target.value,
                                    )
                                  }
                                />
                              </label>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="flex flex-wrap items-center justify-center gap-3 border-t border-[#d9e3db] px-3 py-4 text-center lg:flex-col lg:border-t-0 lg:border-l">
                    <p className="text-sm text-[#526158]">배분 합계</p>
                    <p
                      className={`text-xl font-bold tabular-nums ${allocationMatched ? "text-[#10783a]" : "text-[#c65a11]"}`}
                    >
                      {allocatedQuantity.toLocaleString()} /{" "}
                      {quantity.toLocaleString()}
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : null}
      </div>
      {canRemove ? (
        <button
          type="button"
          disabled={disabled}
          aria-label="제거"
          title="품목 제거"
          className="flex w-11 shrink-0 items-center justify-center self-stretch rounded-r-md border-l border-[#e5ebe6] bg-[#fafcf9] text-[#6a766e] hover:bg-[#fff1ec] hover:text-[#b43b24] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-[#159447] disabled:opacity-60"
          onClick={() => onRemove(index)}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}
