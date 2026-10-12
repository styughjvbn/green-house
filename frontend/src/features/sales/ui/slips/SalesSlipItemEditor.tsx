import {
  calculateSalesItemAllocated,
  calculateSalesItemAmount,
} from "../../lib/salesForm";
import type { SalesAllocationForm, SalesItemForm } from "../../model/types";
import { TextField } from "../common/FormFields";
import { useState } from "react";

export function SalesSlipItemEditor({
  item,
  index,
  showPricing,
  showAllocations = true,
  canRemove,
  onAutomaticAllocation,
  onAllocationChange,
  onChange,
  onRemove,
}: {
  item: SalesItemForm;
  index: number;
  showPricing: boolean;
  showAllocations?: boolean;
  canRemove: boolean;
  onAutomaticAllocation: (index: number) => void;
  onAllocationChange: (
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) => void;
  onChange: (index: number, field: keyof SalesItemForm, value: string) => void;
  onRemove: (index: number) => void;
}) {
  const [showAllocationDetails, setShowAllocationDetails] = useState(false);
  const amount = calculateSalesItemAmount(item);
  const allocatedQuantity = calculateSalesItemAllocated(item);
  const quantity = Number(item.quantity || 0);
  const allocationMatched = quantity === allocatedQuantity;

  return (
    <div
      data-sales-item-index={index}
      className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-3"
    >
      <div
        className={`grid gap-3 sm:grid-cols-2 ${showPricing ? "lg:grid-cols-[80px_70px_85px_80px_125px_135px_minmax(140px,1fr)]" : "lg:grid-cols-[80px_70px_85px_80px_minmax(140px,1fr)]"}`}
      >
        <TextField
          label="등급"
          value={item.spec}
          onChange={(value) => onChange(index, "spec", value)}
        />
        <TextField
          label="단위"
          maxLength={30}
          value={item.unit}
          onChange={(value) => onChange(index, "unit", value)}
        />
        <TextField
          label="상자 수"
          type="number"
          value={item.boxes}
          onChange={(value) => onChange(index, "boxes", value)}
        />
        <TextField
          label="수량"
          required
          type="number"
          value={item.quantity}
          onChange={(value) => onChange(index, "quantity", value)}
        />
        {showPricing ? (
          <>
            <TextField
              label="단가"
              required
              type="number"
              value={item.unitPrice}
              onChange={(value) => onChange(index, "unitPrice", value)}
            />
            <div>
              <p className="text-sm font-semibold text-[#435047]">금액</p>
              <p className="mt-2 text-sm font-semibold whitespace-nowrap">
                {amount.toLocaleString()}원
              </p>
            </div>
          </>
        ) : null}
        <TextField
          label="메모"
          value={item.memo}
          onChange={(value) => onChange(index, "memo", value)}
        />
      </div>

      {showAllocations ? (
        <div className="mt-3 rounded-md border border-[#dfe5dc] bg-white p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-[#17251b]">
                난 묶음 배분
              </p>
              <p
                className={`text-xs ${allocationMatched ? "text-[#16853b]" : "text-[#c65a11]"}`}
              >
                {item.allocationMode === "AUTO" ? "자동 배분" : "직접 조정"} ·
                배분 {allocatedQuantity} / 품목 수량 {quantity}
              </p>
            </div>
            <div className="flex gap-2">
              {item.allocationMode === "MANUAL" ? (
                <button
                  type="button"
                  className="rounded-md border border-[#cfd8cc] px-3 py-2 text-sm font-semibold text-[#29422e]"
                  onClick={() => onAutomaticAllocation(index)}
                >
                  자동 배분
                </button>
              ) : null}
              <button
                type="button"
                aria-expanded={showAllocationDetails}
                className="rounded-md border border-[#cfd8cc] px-3 py-2 text-sm font-semibold text-[#29422e]"
                onClick={() => setShowAllocationDetails((current) => !current)}
              >
                배분 조정
              </button>
            </div>
          </div>
          {showAllocationDetails ? (
            <div className="mt-3 space-y-2">
              {item.allocations.length === 0 ? (
                <p className="text-sm text-[#66736a]">
                  이 품종의 난 묶음을 먼저 선택하세요.
                </p>
              ) : (
                item.allocations.map((allocation, allocationIndex) => (
                  <div
                    key={allocation.orchidGroupId}
                    className="grid gap-2 rounded-md border border-[#e5ebe2] bg-[#fbfcfa] p-3 md:grid-cols-[minmax(0,1fr)_120px]"
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
                  </div>
                ))
              )}
            </div>
          ) : (
            <p className="mt-2 text-xs text-[#66736a]">
              {item.allocations
                .filter((allocation) => Number(allocation.quantity) > 0)
                .map(
                  (allocation) =>
                    `${allocation.locationLabel} ${allocation.quantity}분`,
                )
                .join(" · ") ||
                "품목 수량을 입력하면 선택한 묶음에 자동 배분됩니다."}
            </p>
          )}
        </div>
      ) : null}

      {canRemove ? (
        <div className="mt-2 flex justify-end">
          <button
            className="rounded-md border border-[#e0b3aa] px-3 py-2 text-sm font-semibold text-[#b43b24]"
            onClick={() => onRemove(index)}
            type="button"
          >
            제거
          </button>
        </div>
      ) : null}
    </div>
  );
}
