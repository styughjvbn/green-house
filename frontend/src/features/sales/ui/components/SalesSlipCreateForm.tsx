import type { FormEvent } from "react";
import type { BusinessPartner } from "@/entities/farm/types";
import type { SalesItemForm, SalesSlipForm } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { SalesSlipItemEditor } from "./SalesSlipItemEditor";

export function SalesSlipCreateForm({
  partners,
  errorMessage,
  form,
  saving,
  totalAmount,
  onAddItem,
  onChange,
  onRemoveItem,
  onSubmit,
  onSalesTypeChange,
  onUpdateItem,
}: {
  partners: BusinessPartner[];
  errorMessage: string | null;
  form: SalesSlipForm;
  saving: boolean;
  totalAmount: number;
  onAddItem: () => void;
  onChange: <K extends keyof SalesSlipForm>(
    field: K,
    value: SalesSlipForm[K],
  ) => void;
  onRemoveItem: (index: number) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onSalesTypeChange: (salesType: SalesSlipForm["salesType"]) => void;
  onUpdateItem: (
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) => void;
}) {
  const auctionPartners = partners.filter(
    (partner) => partner.partnerType === "AUCTION_HOUSE",
  );
  const directPartners = partners.filter(
    (partner) => partner.partnerType !== "AUCTION_HOUSE",
  );

  return (
    <form
      className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm"
      onSubmit={onSubmit}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">판매 전표</p>
          <h2 className="mt-1 text-xl font-semibold">새 판매 전표</h2>
        </div>
        <div className="rounded-md bg-[#eef7ec] px-3 py-2 text-sm font-semibold text-[#246b38]">
          합계 {totalAmount.toLocaleString()}원
        </div>
      </div>

      <div className="mt-4 flex w-fit rounded-md border border-[#cfd8cc] bg-[#f6f8f5] p-1">
        <TypeButton
          active={form.salesType === "DIRECT"}
          label="일반 판매"
          onClick={() => onSalesTypeChange("DIRECT")}
        />
        <TypeButton
          active={form.salesType === "AUCTION"}
          label="경매 판매"
          onClick={() => onSalesTypeChange("AUCTION")}
        />
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <TextField
          label={form.salesType === "AUCTION" ? "출하일" : "판매일"}
          required
          type="date"
          value={form.saleDate}
          onChange={(value) => onChange("saleDate", value)}
        />
        <SelectField
          label={form.salesType === "AUCTION" ? "경매장" : "거래처"}
          value={form.partnerId}
          onChange={(value) => onChange("partnerId", value)}
        >
          <option value="">선택</option>
          {(form.salesType === "AUCTION"
            ? auctionPartners
            : directPartners
          ).map((partner) => (
            <option key={partner.id} value={partner.id}>
              {partner.name}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="입금 상태"
          value={form.paymentStatus}
          onChange={(value) => onChange("paymentStatus", value)}
        >
          {form.salesType === "AUCTION" ? (
            <>
              <option value="정산 대기">정산 대기</option>
              <option value="부분 입금">부분 입금</option>
              <option value="입금 완료">입금 완료</option>
            </>
          ) : (
            <>
              <option value="미입금">미입금</option>
              <option value="입금 완료">입금 완료</option>
            </>
          )}
        </SelectField>
        <SelectField
          label="판매 상태"
          value={form.salesStatus}
          onChange={(value) => onChange("salesStatus", value)}
        >
          {form.salesType === "AUCTION" ? (
            <>
              <option value="출하 완료">출하 완료</option>
              <option value="작성중">작성중</option>
            </>
          ) : (
            <>
              <option value="작성중">작성중</option>
              <option value="출고 완료">출고 완료</option>
            </>
          )}
        </SelectField>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <TextField
          label="결제 방법"
          value={form.paymentMethod}
          onChange={(value) => onChange("paymentMethod", value)}
        />
        <TextField
          label="메모"
          value={form.memo}
          onChange={(value) => onChange("memo", value)}
        />
      </div>

      {form.salesType === "AUCTION" ? (
        <div className="mt-3 rounded-md bg-[#f6f8f5] p-4 text-sm text-[#66736a]">
          전표 저장 시 경매장 출하 기록과 lot가 같이 생성됩니다.
        </div>
      ) : null}

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
        <button
          className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
          onClick={onAddItem}
          type="button"
        >
          {form.salesType === "AUCTION" ? "lot 추가" : "품목 추가"}
        </button>
        <button
          className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={saving || !form.partnerId || form.items.length === 0}
          type="submit"
        >
          {saving
            ? "저장 중"
            : form.salesType === "AUCTION"
              ? "경매 출하 전표 저장"
              : "판매 전표 저장"}
        </button>
      </div>

      {errorMessage ? (
        <p className="mt-3 rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
          {errorMessage}
        </p>
      ) : null}
    </form>
  );
}

function TypeButton({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`h-8 rounded px-4 text-sm font-bold ${
        active ? "bg-[#159447] text-white shadow-sm" : "text-[#526158]"
      }`}
      type="button"
      onClick={onClick}
    >
      {label}
    </button>
  );
}
