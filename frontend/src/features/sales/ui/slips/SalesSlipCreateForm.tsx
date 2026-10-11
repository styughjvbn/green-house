import { salesDispatchLabel } from "../../lib/salesDisplay";
import type { SubmitEvent } from "react";
import { X } from "lucide-react";
import type {
  SalesAllocationForm,
  SalesItemForm,
  SalesSlipForm,
  SalesSlipFormMode,
} from "../../model/types";
import { BusinessPartnerSelect } from "../common/BusinessPartnerSelect";
import { SelectField, TextField } from "../common/FormFields";
import { SalesSlipItemEditor } from "./SalesSlipItemEditor";
import { SalesOrchidGroupSearchSelect } from "./SalesOrchidGroupSearchSelect";

export function SalesSlipCreateForm({
  errorMessage,
  form,
  mode,
  saving,
  totalAmount,
  onAddItem,
  onSelectGroups,
  onRemoveGroup,
  onAutomaticAllocation,
  selectedGroups,
  allocationIssue,
  onAllocationChange,
  onCancel,
  onChange,
  onRemoveItem,
  onSubmit,
  onSalesTypeChange,
  onUpdateItem,
}: {
  errorMessage: string | null;
  form: SalesSlipForm;
  mode: SalesSlipFormMode;
  saving: boolean;
  totalAmount: number;
  onAddItem: (index: number) => void;
  onSelectGroups: (
    groups: import("@/entities/farm/types").SalesOrchidGroupOption[],
  ) => void;
  onRemoveGroup: (id: string) => void;
  onAutomaticAllocation: (index: number) => void;
  selectedGroups: SalesAllocationForm[];
  allocationIssue: string | null;
  onAllocationChange: (
    index: number,
    allocationIndex: number,
    field: keyof SalesAllocationForm,
    value: string,
  ) => void;
  onCancel: () => void;
  onChange: <K extends keyof SalesSlipForm>(
    field: K,
    value: SalesSlipForm[K],
  ) => void;
  onRemoveItem: (index: number) => void;
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => void;
  onSalesTypeChange: (salesType: SalesSlipForm["salesType"]) => void;
  onUpdateItem: (
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) => void;
}) {
  const itemGroups = new Map<
    string,
    {
      itemName: string;
      genus: string;
      rows: { item: SalesItemForm; index: number }[];
    }
  >();
  form.items.forEach((item, index) => {
    const key = JSON.stringify([item.genus, item.itemName]);
    const group = itemGroups.get(key);
    if (group) group.rows.push({ item, index });
    else
      itemGroups.set(key, {
        itemName: item.itemName,
        genus: item.genus,
        rows: [{ item, index }],
      });
  });

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onCancel}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-5xl flex-col rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label={mode === "edit" ? "전표 수정" : "전표 작성"}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#edf0ec] p-3">
          <div>
            <h2 className="mt-1 text-xl font-semibold">
              {mode === "edit" ? "전표 수정" : "전표 작성"}
            </h2>
          </div>
          <div className="flex items-center gap-2">
            {form.salesType === "DIRECT" ? (
              <div className="rounded-md bg-[#eef7ec] px-3 py-1 text-sm font-semibold text-[#246b38]">
                합계 {totalAmount.toLocaleString()}원
              </div>
            ) : null}
            <button
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
              type="button"
              onClick={onCancel}
              aria-label="닫기"
            >
              <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            </button>
          </div>
        </div>

        <form
          className="min-h-0 space-y-4 overflow-y-auto p-5"
          onSubmit={onSubmit}
        >
          <div className="flex w-fit rounded-md border border-[#cfd8cc] bg-[#f6f8f5] p-1">
            <TypeButton
              active={form.salesType === "DIRECT"}
              disabled={saving || mode === "edit"}
              label="일반 판매"
              onClick={() => onSalesTypeChange("DIRECT")}
            />
            <TypeButton
              active={form.salesType === "AUCTION"}
              disabled={saving || mode === "edit"}
              label="경매 판매"
              onClick={() => onSalesTypeChange("AUCTION")}
            />
          </div>

          <div className="grid gap-3 md:grid-cols-3">
            <TextField
              label={form.salesType === "AUCTION" ? "출하일" : "판매일"}
              required
              type="date"
              value={form.saleDate}
              onChange={(value) => onChange("saleDate", value)}
            />
            <BusinessPartnerSelect
              key={form.salesType}
              label={form.salesType === "AUCTION" ? "경매장" : "거래처"}
              value={form.partnerId}
              onChange={(value) => onChange("partnerId", value)}
              auctionHouse={form.salesType === "AUCTION"}
              activeOnly
              disabled={saving}
            />
            <SelectField
              label="출고·출하"
              disabled={mode === "edit"}
              value={form.salesStatus}
              onChange={(value) => onChange("salesStatus", value)}
            >
              {form.salesType === "AUCTION" ? (
                <>
                  <option value="출하 완료">출하 완료</option>
                  <option value="작성중">
                    {salesDispatchLabel("작성중", form.salesType)}
                  </option>
                </>
              ) : (
                <>
                  <option value="작성중">
                    {salesDispatchLabel("작성중", form.salesType)}
                  </option>
                  <option value="출고 완료">출고 완료</option>
                </>
              )}
            </SelectField>
          </div>

          <div className="grid gap-3">
            <TextField
              label="메모"
              value={form.memo}
              onChange={(value) => onChange("memo", value)}
            />
          </div>

          {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. */}

          <section className="space-y-3 rounded-md border border-[#d7ddd4] p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="text-sm font-bold text-[#17251b]">
                  난 묶음 선택
                </h3>
                <p className="text-xs text-[#66736a]">
                  선택한 묶음을 품목 수량에 따라 자동 배분합니다.
                </p>
              </div>
              <div className="w-full max-w-[260px]">
                <SalesOrchidGroupSearchSelect
                  disabled={saving}
                  selectedIds={selectedGroups.map(
                    (group) => group.orchidGroupId,
                  )}
                  onSelect={onSelectGroups}
                />
              </div>
            </div>
            {selectedGroups.length === 0 ? (
              <p className="text-sm text-[#66736a]">
                판매할 난 묶음을 선택하세요.
              </p>
            ) : (
              <div className="grid gap-2 sm:grid-cols-2">
                {selectedGroups.map((group) => {
                  const used = form.items
                    .flatMap((item) => item.allocations)
                    .filter(
                      (allocation) =>
                        allocation.orchidGroupId === group.orchidGroupId,
                    )
                    .reduce(
                      (sum, allocation) =>
                        sum + (Number(allocation.quantity) || 0),
                      0,
                    );
                  return (
                    <div
                      key={group.orchidGroupId}
                      className="flex items-center justify-between gap-2 rounded-md bg-[#f6f8f5] px-3 py-2 text-sm"
                    >
                      <div>
                        <p className="font-semibold">
                          {group.genus} / {group.varietyName}
                        </p>
                        <p className="text-xs text-[#66736a]">
                          {group.locationLabel} · 가용 {group.availableQuantity}
                          분 · 배분 {used}분
                        </p>
                      </div>
                      <button
                        type="button"
                        className="shrink-0 rounded border border-[#d9dfda] px-2 py-1 text-xs"
                        onClick={() => onRemoveGroup(group.orchidGroupId)}
                      >
                        제거
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          <div className="space-y-4">
            {[...itemGroups.entries()].map(([key, group]) => (
              <section
                key={key}
                aria-label={`${group.itemName || "미선택"}${group.genus ? ` / ${group.genus}` : ""} 품목`}
                className="rounded-md border border-[#cfd8cc] bg-white p-3"
              >
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-xs font-semibold text-[#66736a]">
                      품종명 / 속
                    </p>
                    <h3 className="text-base font-bold text-[#17251b]">
                      {group.itemName || "난 묶음을 선택하세요"}
                      {group.genus ? ` / ${group.genus}` : ""}
                    </h3>
                  </div>
                  {mode === "create" && group.itemName ? (
                    <button
                      type="button"
                      disabled={saving}
                      className="rounded-md border border-[#cfd8cc] px-3 py-2 text-sm font-semibold text-[#29422e] disabled:opacity-50"
                      onClick={() => onAddItem(group.rows.at(-1)!.index)}
                    >
                      품목 분리
                    </button>
                  ) : null}
                </div>
                <div className="space-y-3">
                  {group.rows.map(({ item, index }) => (
                    <SalesSlipItemEditor
                      key={index}
                      item={item}
                      index={index}
                      showPricing={form.salesType === "DIRECT"}
                      canRemove={mode === "create" && group.rows.length > 1}
                      onAutomaticAllocation={onAutomaticAllocation}
                      onAllocationChange={onAllocationChange}
                      onChange={onUpdateItem}
                      onRemove={onRemoveItem}
                    />
                  ))}
                </div>
              </section>
            ))}
          </div>

          <div className="flex flex-wrap gap-2">
            {allocationIssue ? (
              <p
                role="alert"
                className="w-full rounded-md bg-[#fff1ec] px-3 py-2 text-sm text-[#9b341e]"
              >
                {allocationIssue}
              </p>
            ) : null}
            <button
              className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={
                saving ||
                !form.partnerId ||
                form.items.length === 0 ||
                allocationIssue != null
              }
              type="submit"
            >
              {saving
                ? "저장 중"
                : mode === "edit"
                  ? "판매 전표 수정"
                  : form.salesType === "AUCTION"
                    ? "경매 출하 전표 저장"
                    : "판매 전표 저장"}
            </button>
            <button
              className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
              onClick={onCancel}
              type="button"
            >
              취소
            </button>
          </div>

          {errorMessage ? (
            <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {errorMessage}
            </p>
          ) : null}
        </form>
      </section>
    </div>
  );
}

function TypeButton({
  active,
  disabled = false,
  label,
  onClick,
}: {
  active: boolean;
  disabled?: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`h-8 rounded px-4 text-sm font-bold ${
        active ? "bg-[#159447] text-white shadow-sm" : "text-[#526158]"
      } disabled:cursor-not-allowed disabled:opacity-60`}
      disabled={disabled}
      type="button"
      onClick={onClick}
    >
      {label}
    </button>
  );
}
