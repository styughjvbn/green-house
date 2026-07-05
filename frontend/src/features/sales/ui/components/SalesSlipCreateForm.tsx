import type { FormEvent } from "react";
import type {
  AuctionShipmentOption,
  BusinessPartner,
} from "@/entities/farm/types";
import type { SalesItemForm, SalesSlipForm } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { SalesSlipItemEditor } from "./SalesSlipItemEditor";

export function SalesSlipCreateForm({
  auctionShipments,
  partners,
  errorMessage,
  form,
  saving,
  loadingAuctionShipments,
  totalAmount,
  onAddItem,
  onChange,
  onRemoveItem,
  onSubmit,
  onSalesTypeChange,
  onAuctionShipmentChange,
  onUpdateItem,
}: {
  auctionShipments: AuctionShipmentOption[];
  partners: BusinessPartner[];
  errorMessage: string | null;
  form: SalesSlipForm;
  saving: boolean;
  loadingAuctionShipments: boolean;
  totalAmount: number;
  onAddItem: () => void;
  onChange: <K extends keyof SalesSlipForm>(
    field: K,
    value: SalesSlipForm[K],
  ) => void;
  onRemoveItem: (index: number) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onSalesTypeChange: (salesType: SalesSlipForm["salesType"]) => void;
  onAuctionShipmentChange: (shipmentId: string) => void;
  onUpdateItem: (
    index: number,
    field: keyof SalesItemForm,
    value: string,
  ) => void;
}) {
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

      {form.salesType === "DIRECT" ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <TextField
              label="판매일"
              required
              type="date"
              value={form.saleDate}
              onChange={(value) => onChange("saleDate", value)}
            />
            <SelectField
              label="거래처"
              value={form.partnerId}
              onChange={(value) => onChange("partnerId", value)}
            >
              <option value="">선택</option>
              {partners
                .filter((partner) => partner.partnerType !== "AUCTION_HOUSE")
                .map((partner) => (
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
              <option value="미입금">미입금</option>
              <option value="입금 완료">입금 완료</option>
            </SelectField>
            <SelectField
              label="판매 상태"
              value={form.salesStatus}
              onChange={(value) => onChange("salesStatus", value)}
            >
              <option value="작성중">작성중</option>
              <option value="출고 완료">출고 완료</option>
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
        </>
      ) : (
        <AuctionShipmentFields
          form={form}
          loading={loadingAuctionShipments}
          shipments={auctionShipments}
          onChange={onAuctionShipmentChange}
          onMemoChange={(value) => onChange("memo", value)}
        />
      )}

      {form.salesType === "DIRECT" ? (
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
      ) : null}

      <div className="mt-4 flex flex-wrap gap-2">
        {form.salesType === "DIRECT" ? (
          <button
            className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold"
            onClick={onAddItem}
            type="button"
          >
            품목 추가
          </button>
        ) : null}
        <button
          className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={
            saving ||
            (form.salesType === "DIRECT"
              ? !form.partnerId
              : !form.auctionShipmentId)
          }
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
      className={`h-8 rounded px-4 text-sm font-bold ${active ? "bg-[#159447] text-white shadow-sm" : "text-[#526158]"}`}
      type="button"
      onClick={onClick}
    >
      {label}
    </button>
  );
}

function AuctionShipmentFields({
  form,
  loading,
  shipments,
  onChange,
  onMemoChange,
}: {
  form: SalesSlipForm;
  loading: boolean;
  shipments: AuctionShipmentOption[];
  onChange: (shipmentId: string) => void;
  onMemoChange: (value: string) => void;
}) {
  const selected = shipments.find(
    (shipment) => String(shipment.id) === form.auctionShipmentId,
  );

  return (
    <div className="mt-4 space-y-3">
      <div className="grid gap-3 md:grid-cols-[minmax(0,2fr)_1fr_1fr]">
        <SelectField
          label="경매장 출하 기록"
          value={form.auctionShipmentId}
          onChange={onChange}
        >
          <option value="">{loading ? "불러오는 중" : "출하 기록 선택"}</option>
          {shipments.map((shipment) => (
            <option key={shipment.id} value={shipment.id}>
              {shipment.shipmentDate} · {shipment.auctionMarket} ·{" "}
              {shipment.lots.length}개 lot
            </option>
          ))}
        </SelectField>
        <ReadOnlyField label="출하일" value={selected?.shipmentDate ?? "-"} />
        <ReadOnlyField
          label="거래처"
          value={selected ? `${selected.auctionMarket} 자동 연결` : "-"}
        />
      </div>
      {selected ? (
        <div className="overflow-x-auto rounded-md border border-[#d7ddd4]">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="bg-[#f6f8f5] text-[#526158]">
              <tr>
                <th className="px-3 py-2 text-left">품목 / 품종</th>
                <th className="px-3 py-2 text-left">등급</th>
                <th className="px-3 py-2 text-right">출하 수량</th>
                <th className="px-3 py-2 text-right">초기 금액</th>
              </tr>
            </thead>
            <tbody>
              {selected.lots.map((lot) => (
                <tr key={lot.id} className="border-t border-[#edf0ec]">
                  <td className="px-3 py-2">
                    <strong>{lot.varietyName}</strong>
                    <span className="ml-2 text-xs text-[#738077]">
                      {lot.itemName}
                    </span>
                  </td>
                  <td className="px-3 py-2">{lot.shipmentGrade || "-"}</td>
                  <td className="px-3 py-2 text-right">
                    {lot.shippedQuantity.toLocaleString()}
                  </td>
                  <td className="px-3 py-2 text-right">0원</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="rounded-md bg-[#f6f8f5] p-4 text-sm text-[#66736a]">
          판매 전표가 없는 경매장 출하 기록만 표시됩니다.
        </p>
      )}
      <TextField label="메모" value={form.memo} onChange={onMemoChange} />
    </div>
  );
}

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-sm font-semibold text-[#435047]">{label}</p>
      <p className="mt-1 rounded-md border border-[#dbe1d8] bg-[#f7f9f6] px-3 py-2 text-sm">
        {value}
      </p>
    </div>
  );
}
