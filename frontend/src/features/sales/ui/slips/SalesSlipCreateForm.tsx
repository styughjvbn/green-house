import { salesDispatchLabel } from "../../lib/salesDisplay";
import { useRef, type SubmitEvent } from "react";
import { ChevronDown, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
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
import { SalesRecordedVarietySelect } from "./SalesRecordedVarietySelect";

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
  onResetAllocation,
  selectedGroups,
  allocationIssue,
  onAllocationChange,
  onCancel,
  onChange,
  onRemoveItem,
  onSubmit,
  onSalesTypeChange,
  onRecordingModeChange,
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
  onResetAllocation: (index: number) => void;
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
  onRecordingModeChange: (recordOnly: boolean) => void;
  onUpdateItem: (
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) => void;
}) {
  const openerRef = useRef<HTMLElement | null>(
    typeof document !== "undefined" &&
      document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null,
  );
  const itemGroups = new Map<
    string,
    {
      itemName: string;
      genus: string;
      rows: { item: SalesItemForm; index: number }[];
    }
  >();
  form.items.forEach((item, index) => {
    const key =
      form.recordOnly && !item.itemName
        ? `record-${index}`
        : JSON.stringify([item.genus, item.itemName]);
    const group = itemGroups.get(key);
    if (group) group.rows.push({ item, index });
    else
      itemGroups.set(key, {
        itemName: item.itemName,
        genus: item.genus,
        rows: [{ item, index }],
      });
  });

  const busy = saving;
  const totalQuantity = form.items.reduce(
    (sum, item) => sum + (Number(item.quantity) || 0),
    0,
  );

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onCancel();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="flex max-h-[calc(100dvh-2rem)] max-w-6xl flex-col overflow-hidden rounded-md bg-[#f5fbf5]"
        style={{
          top: "1rem",
          bottom: "1rem",
          left: "1rem",
          right: "1rem",
          margin: "auto",
          height: "fit-content",
          transform: "none",
          translate: "none",
        }}
        onEscapeKeyDown={(event) => {
          if (busy) event.preventDefault();
        }}
        onInteractOutside={(event) => {
          if (busy) event.preventDefault();
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          openerRef.current?.focus();
        }}
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-[#dbe8dc] bg-white px-5 py-3">
          <div className="flex flex-wrap items-end gap-x-3 gap-y-1">
            <DialogTitle className="text-xl font-semibold text-[#17251b]">
              {mode === "edit" ? "전표 수정" : "전표 작성"}
            </DialogTitle>
            <p className="text-sm text-[#5c6a60]">
              판매 품목을 입력하거나 완료된 판매·출하를 기록합니다.
            </p>
          </div>
          <button
            type="button"
            disabled={busy}
            aria-label="닫기"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3] disabled:opacity-60"
            onClick={onCancel}
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </header>
        <form
          className="min-h-0 overflow-y-auto"
          aria-busy={busy}
          onSubmit={(event) => {
            if (busy) {
              event.preventDefault();
              return;
            }
            onSubmit(event);
          }}
        >
          <div className="space-y-4 p-5">
            <fieldset
              disabled={busy || mode === "edit"}
              className="min-w-0 rounded-md border border-[#cfe0d2] bg-white p-4"
            >
              <legend className="sr-only">판매 유형</legend>
              <h3 className="mb-3 text-sm font-bold text-[#26352b]">
                판매 유형 선택
              </h3>
              <div className="flex flex-wrap gap-2">
                <TypeButton
                  active={form.salesType === "DIRECT"}
                  disabled={busy || mode === "edit"}
                  label="일반 판매"
                  onClick={() => onSalesTypeChange("DIRECT")}
                />
                <TypeButton
                  active={form.salesType === "AUCTION"}
                  disabled={busy || mode === "edit"}
                  label="경매 판매"
                  onClick={() => onSalesTypeChange("AUCTION")}
                />
              </div>
            </fieldset>
            {mode === "create" ? (
              <fieldset
                disabled={busy}
                className="min-w-0 rounded-md border border-[#cfe0d2] bg-white p-4"
              >
                <legend className="sr-only">등록 방식</legend>
                <h3 className="mb-3 text-sm font-bold text-[#26352b]">
                  등록 방법 선택
                </h3>
                <div className="grid gap-2 sm:grid-cols-2">
                  <MethodButton
                    title="난 묶음 연결"
                    description="난 묶음을 선택하고 출고·출하 상태에 따라 재고 반영"
                    selected={!form.recordOnly}
                    disabled={busy}
                    onClick={() => onRecordingModeChange(false)}
                  />
                  <MethodButton
                    title="기록만 등록"
                    description="이미 완료된 판매·출하를 재고 변경 없이 기록"
                    selected={form.recordOnly}
                    disabled={busy}
                    onClick={() => onRecordingModeChange(true)}
                  />
                </div>
              </fieldset>
            ) : null}
            <fieldset
              disabled={busy}
              className="min-w-0 rounded-md border border-[#cfe0d2] bg-white p-4"
            >
              <legend className="sr-only">전표 기본 정보</legend>
              <div className="mb-4 flex items-center justify-between gap-3">
                <h3 className="text-sm font-bold text-[#26352b]">기본 정보</h3>
                <span className="text-xs text-[#66736a]">* 필수 입력</span>
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
                  required
                  disabled={saving}
                />
                <SelectField
                  label="출고·출하"
                  required
                  disabled={mode === "edit" || form.recordOnly}
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

              {form.recordOnly ? (
                <p className="mt-3 text-sm text-[#66736a]">
                  실제 판매·출하일과 품목을 입력하세요. 재고는 예약하거나
                  차감하지 않습니다. 이미 등록된 전표·경매 출하는 다시 등록하지
                  마세요.
                </p>
              ) : null}
              <div className="mt-3">
                <TextField
                  label="메모"
                  value={form.memo}
                  onChange={(value) => onChange("memo", value)}
                />
              </div>
            </fieldset>
            <fieldset
              disabled={busy}
              className="min-w-0 rounded-md border border-[#cfe0d2] bg-white p-4"
            >
              <legend className="sr-only">판매 품목 입력</legend>
              <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="text-lg font-bold text-[#17251b]">
                    판매 품목
                  </h3>
                  <p className="mt-1 text-sm text-[#66736a]">
                    {form.recordOnly
                      ? "품종을 선택하고 수량과 품목 정보를 입력하세요."
                      : "판매할 난 묶음을 선택하고 수량과 품목 정보를 입력하세요."}
                  </p>
                </div>
                {!form.recordOnly ? (
                  <div className="w-full sm:w-auto">
                    <SalesOrchidGroupSearchSelect
                      disabled={busy}
                      selectedIds={selectedGroups.map(
                        (group) => group.orchidGroupId,
                      )}
                      onSelect={onSelectGroups}
                    />
                  </div>
                ) : null}
              </div>
              {!form.recordOnly && selectedGroups.length > 0 ? (
                <details
                  open
                  className="group mb-4 rounded-md bg-[#f3f8f4] p-3"
                >
                  <summary className="flex cursor-pointer list-none items-start gap-2 text-sm text-[#26352b] [&::-webkit-details-marker]:hidden">
                    <ChevronDown
                      className="mt-0.5 h-4 w-4 shrink-0 -rotate-90 transition-transform group-open:rotate-0"
                      aria-hidden="true"
                    />
                    <span>
                      <span className="block font-semibold">
                        선택한 난 묶음 {selectedGroups.length}개
                      </span>
                      <span className="mt-1 block text-xs text-[#526158]">
                        판매 가능 수량 총{" "}
                        {selectedGroups
                          .reduce(
                            (sum, group) => sum + group.availableQuantity,
                            0,
                          )
                          .toLocaleString()}
                        분
                      </span>
                    </span>
                  </summary>
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    {selectedGroups.map((group) => {
                      return (
                        <div
                          key={group.orchidGroupId}
                          className="flex items-center justify-between gap-3 rounded-md border border-[#d9e3db] bg-white px-3 py-3 text-sm"
                        >
                          <div>
                            <p className="font-semibold">
                              {group.varietyName}
                              {group.genus ? ` / ${group.genus}` : ""}
                            </p>
                            <p className="text-xs text-[#66736a]">
                              {group.locationLabel} · 가용{" "}
                              {group.availableQuantity.toLocaleString()}분
                            </p>
                          </div>
                          <button
                            type="button"
                            className="h-7 shrink-0 rounded-md border border-[#d9dfda] bg-[#f9fbf8] px-3 text-xs text-[#435047]"
                            onClick={() => onRemoveGroup(group.orchidGroupId)}
                          >
                            제거
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </details>
              ) : null}

              <div className="space-y-3">
                {[...itemGroups.entries()].map(([key, group]) => {
                  const allocations = new Map(
                    group.rows.flatMap(({ item }) =>
                      item.allocations.map(
                        (allocation) =>
                          [allocation.orchidGroupId, allocation] as const,
                      ),
                    ),
                  );
                  const availableQuantity = [...allocations.values()].reduce(
                    (sum, allocation) => sum + allocation.availableQuantity,
                    0,
                  );
                  return (
                    <section
                      key={key}
                      aria-label={`${group.itemName || "미선택"}${group.genus ? ` / ${group.genus}` : ""} 품목`}
                      className="rounded-md border border-[#e1e8e2] bg-white p-4"
                    >
                      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                        {form.recordOnly ? (
                          <SalesRecordedVarietySelect
                            name={group.itemName}
                            genus={group.genus}
                            disabled={busy}
                            onSelect={(variety) => {
                              group.rows.forEach(({ index }) => {
                                onUpdateItem(index, "itemName", variety.name);
                                onUpdateItem(index, "genus", variety.genus);
                              });
                            }}
                          />
                        ) : (
                          <div>
                            <h3 className="text-base font-bold text-[#17251b]">
                              {group.itemName || "난 묶음을 선택하세요"}
                              {group.genus ? ` / ${group.genus}` : ""}
                            </h3>
                            <p className="mt-1 text-xs text-[#66736a]">
                              선택된 난 묶음 {allocations.size}개 · 판매 가능{" "}
                              {availableQuantity.toLocaleString()}분
                            </p>
                          </div>
                        )}
                        {mode === "create" && group.itemName ? (
                          <button
                            type="button"
                            disabled={busy}
                            className="h-7 rounded-md border border-[#cfd8cc] px-3 text-xs font-semibold text-[#29422e] disabled:opacity-50"
                            onClick={() => onAddItem(group.rows.at(-1)!.index)}
                          >
                            품목 분리
                          </button>
                        ) : null}
                      </div>
                      <div className="space-y-4">
                        {group.rows.map(({ item, index }) => (
                          <SalesSlipItemEditor
                            key={index}
                            item={item}
                            disabled={busy}
                            index={index}
                            showPricing={form.salesType === "DIRECT"}
                            showAllocations={!form.recordOnly}
                            canRemove={
                              mode === "create" &&
                              (form.recordOnly
                                ? form.items.length > 1
                                : group.rows.length > 1)
                            }
                            onAutomaticAllocation={onAutomaticAllocation}
                            onResetAllocation={onResetAllocation}
                            onAllocationChange={onAllocationChange}
                            onChange={onUpdateItem}
                            onRemove={onRemoveItem}
                          />
                        ))}
                      </div>
                    </section>
                  );
                })}
                {mode === "create" && form.recordOnly ? (
                  <button
                    type="button"
                    disabled={busy}
                    className="flex h-8 w-full items-center justify-center rounded-md border border-dashed border-[#cfd8cc] text-sm font-semibold text-[#29422e] disabled:opacity-50"
                    onClick={() => onAddItem(-1)}
                  >
                    + 품종 추가
                  </button>
                ) : null}
              </div>
            </fieldset>
            <footer className="space-y-3 rounded-md border border-[#cfe0d2] bg-white p-4">
              {allocationIssue || errorMessage ? (
                <div
                  role="alert"
                  className="space-y-1 rounded-md bg-[#fff1ec] px-3 py-2 text-sm text-[#9b341e]"
                >
                  {allocationIssue ? <p>{allocationIssue}</p> : null}
                  {errorMessage ? <p>{errorMessage}</p> : null}
                </div>
              ) : null}
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-xs text-[#66736a]">
                    판매 품목 {form.items.length}건 · 총 수량{" "}
                    {totalQuantity.toLocaleString()}
                  </p>
                  {form.salesType === "DIRECT" ? (
                    <p className="mt-1 text-sm text-[#435047]">
                      합계{" "}
                      <strong className="ml-1 text-lg text-[#16713a]">
                        {totalAmount.toLocaleString()}원
                      </strong>
                    </p>
                  ) : null}
                </div>
                <div className="ml-auto flex items-center gap-2">
                  <button
                    className="h-10 rounded-md border border-[#d7ddd4] px-4 text-sm font-semibold text-[#435047] disabled:opacity-60"
                    disabled={busy}
                    onClick={onCancel}
                    type="button"
                  >
                    취소
                  </button>
                  <button
                    className="h-10 rounded-md bg-[#159447] px-5 text-sm font-semibold text-white disabled:opacity-60"
                    disabled={
                      busy ||
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
                </div>
              </div>
            </footer>
          </div>
        </form>
      </DialogContent>
    </Dialog>
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
      aria-pressed={active}
      className={`rounded-md border px-3 py-2 text-sm font-semibold ${active ? "border-[#159447] bg-[#159447] text-white" : "border-[#d5ddd6] bg-white text-[#344138] hover:border-[#159447]"} focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447] disabled:opacity-60`}
      disabled={disabled}
      type="button"
      onClick={onClick}
    >
      {label}
    </button>
  );
}

function MethodButton({
  title,
  description,
  selected,
  disabled,
  onClick,
}: {
  title: string;
  description: string;
  selected: boolean;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onClick}
      className={`rounded-md border px-4 py-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447] disabled:opacity-45 ${selected ? "border-[#159447] bg-[#e8f6eb] text-[#10783a]" : "border-[#d5ddd6] bg-white text-[#344138] hover:border-[#159447]"}`}
    >
      <span className="block text-sm font-bold">{title}</span>
      <span className="mt-1 block text-xs font-medium text-[#6a766e]">
        {description}
      </span>
    </button>
  );
}
