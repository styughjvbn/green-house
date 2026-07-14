import { useState, type ReactNode } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Ban, ChevronDown, Copy, Pencil, Printer, Truck } from "lucide-react";
import type { SalesSlip, SalesSlipItem } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import { confirmSalesSlipPayment } from "../../api/salesApi";
import { ManualPaymentPanel } from "../auction/ManualPaymentPanel";
import {
  SalesDetailCard,
  SalesDetailActionButton,
  SalesDetailEmpty,
  SalesDetailHeader,
  SalesDetailSummary,
} from "../common/SalesDetailCard";

const salesSlipItemColumns: ColumnDef<SalesSlipItem, unknown>[] = [
  {
    id: "rowNumber",
    header: "No.",
    cell: ({ row }) => row.index + 1,
    size: 60,
    meta: { align: "center", hideable: false },
  },
  {
    id: "itemName",
    header: "품종명 / 속",
    cell: ({ row }) => (
      <>
        <strong className="block">{row.original.itemName}</strong>
        <span className="text-[#6a766e]">{row.original.genus ?? "-"}</span>
      </>
    ),
    size: 180,
    meta: { cellClassName: "font-semibold" },
  },
  {
    accessorKey: "spec",
    header: "규격",
    cell: ({ row }) => row.original.spec ?? "-",
    size: 90,
  },
  {
    accessorKey: "quantity",
    header: "수량",
    size: 80,
    meta: { align: "right" },
  },
  {
    accessorKey: "unitPrice",
    header: "단가",
    cell: ({ row }) => row.original.unitPrice.toLocaleString(),
    size: 110,
    meta: { align: "right" },
  },
  {
    accessorKey: "amount",
    header: "금액",
    cell: ({ row }) => row.original.amount.toLocaleString(),
    size: 120,
    meta: { align: "right", cellClassName: "font-semibold" },
  },
  {
    id: "allocations",
    header: "배분 난 묶음",
    cell: ({ row }) => (
      <div className="space-y-2">
        {row.original.allocations.map((allocation) => (
          <div
            key={allocation.id}
            className="rounded-md bg-[#f6f8f5] px-2 py-2"
          >
            <p className="font-semibold text-[#17251b]">
              {allocation.varietyName} {allocation.allocatedQuantity}분
            </p>
            <p className="text-xs text-[#6a766e]">
              {allocation.houseNumber}동 {allocation.physicalBedNumber}
              배드 {allocation.bedZoneName}
            </p>
          </div>
        ))}
      </div>
    ),
    size: 220,
  },
  {
    accessorKey: "memo",
    header: "메모",
    cell: ({ row }) => row.original.memo ?? "-",
    size: 120,
  },
];

export function SalesSlipDetail({
  loading = false,
  salesSlip,
  updatingSalesStatus,
  onCancelSalesSlip,
  onEditSalesSlip,
  onCompleteSalesSlip,
  onPaymentConfirmed,
}: {
  loading?: boolean;
  salesSlip: SalesSlip | null;
  updatingSalesStatus: boolean;
  onCancelSalesSlip: (salesSlipId: number) => Promise<void>;
  onEditSalesSlip: (salesSlipId: number) => void;
  onCompleteSalesSlip: (salesSlipId: number) => Promise<void>;
  onPaymentConfirmed: (salesSlip: SalesSlip) => void;
}) {
  const [partnerInfoOpen, setPartnerInfoOpen] = useState(false);
  const [slipInfoOpen, setSlipInfoOpen] = useState(false);

  if (loading) {
    return <SalesDetailEmpty>전표 상세를 불러오는 중입니다.</SalesDetailEmpty>;
  }

  if (!salesSlip) {
    return <SalesDetailEmpty>선택한 전표가 없습니다.</SalesDetailEmpty>;
  }

  const supplyAmount = Math.round(salesSlip.totalAmount / 1.1);
  const vatAmount = salesSlip.totalAmount - supplyAmount;
  const canComplete =
    salesSlip.salesStatus !== "출고 완료" &&
    salesSlip.salesStatus !== "출하 완료" &&
    salesSlip.salesStatus !== "취소";
  const canEdit =
    salesSlip.salesType === "DIRECT" &&
    salesSlip.salesStatus === "작성중" &&
    salesSlip.paidAmount === 0;
  const canCancel = salesSlip.salesStatus !== "취소";

  return (
    <SalesDetailCard>
      <SalesDetailHeader
        eyebrow={`LOT #${salesSlip.slipNumber}`}
        title="전표 상세"
        summary={
          <SalesDetailSummary
            align="left"
            columns="lg:grid-cols-4"
            items={[
              {
                label: "판매 유형",
                value:
                  salesSlip.salesType === "AUCTION" ? "경매 판매" : "일반 판매",
              },
              {
                label: "판매일자",
                value: formatShortDate(salesSlip.saleDate),
              },
              { label: "입금 상태", value: salesSlip.paymentStatus },
              { label: "판매 상태", value: salesSlip.salesStatus },
            ]}
          />
        }
        actions={
          <>
            {canEdit ? (
              <SalesDetailActionButton
                icon={Pencil}
                onClick={() => onEditSalesSlip(salesSlip.id)}
              >
                전표 수정
              </SalesDetailActionButton>
            ) : null}
            {canComplete ? (
              <SalesDetailActionButton
                disabled={updatingSalesStatus}
                icon={Truck}
                tone="primary"
                onClick={() => void onCompleteSalesSlip(salesSlip.id)}
              >
                {salesSlip.salesType === "AUCTION" ? "출하 완료" : "출고 완료"}
              </SalesDetailActionButton>
            ) : null}
            {canCancel ? (
              <SalesDetailActionButton
                disabled={updatingSalesStatus}
                icon={Ban}
                tone="danger"
                onClick={() => {
                  if (
                    window.confirm(
                      "이 전표를 취소하시겠습니까? 취소 후 되돌릴 수 없습니다.",
                    )
                  ) {
                    void onCancelSalesSlip(salesSlip.id);
                  }
                }}
              >
                전표 취소
              </SalesDetailActionButton>
            ) : null}
            <SalesDetailActionButton
              href={`/print/sales-slips/${salesSlip.id}`}
              icon={Printer}
            >
              인쇄(미리보기)
            </SalesDetailActionButton>
            <SalesDetailActionButton icon={Copy}>
              전표 복사
            </SalesDetailActionButton>
          </>
        }
      />

      <div className="p-4">
        <div className="grid gap-3 lg:grid-cols-2">
          <InfoBox
            open={partnerInfoOpen}
            preview={salesSlip.partner.name}
            title="거래처"
            onToggle={() => setPartnerInfoOpen((current) => !current)}
          >
            <Description label="대표자명" value={salesSlip.partner.ownerName} />
            <Description label="연락처" value={salesSlip.partner.phone} />
            <Description label="주소" value={salesSlip.partner.address} />
            <Description label="메모" value={salesSlip.partner.memo} />
          </InfoBox>

          <InfoBox
            open={slipInfoOpen}
            title="전표 정보"
            onToggle={() => setSlipInfoOpen((current) => !current)}
          >
            <Description label="경매장" value={salesSlip.auctionMarket} />
            <Description label="결제 방식" value={salesSlip.paymentMethod} />
            <Description label="담당자" value="관리자" />
            <Description label="메모" value={salesSlip.memo} />
          </InfoBox>
        </div>

        <div className="mt-4">
          <DataTable
            columns={salesSlipItemColumns}
            data={salesSlip.items}
            emptyMessage="판매 품목이 없습니다."
            getRowId={(row) => String(row.id)}
            settingsKey="sales.slipDetail.items"
            title="판매 품목"
            totalLabel={`총 ${salesSlip.items.length.toLocaleString()}건`}
          />
        </div>

        <div className="mt-3 grid gap-3 rounded-md border border-[#dfe5dc] bg-white p-4 text-sm md:grid-cols-3 md:items-center">
          <Amount label="공급가액" value={supplyAmount} />
          <Amount label="부가세" value={vatAmount} />
          <div className="text-right">
            <p className="font-semibold text-[#344138]">총 금액</p>
            <p className="mt-1 text-3xl font-bold text-[#159447]">
              {salesSlip.totalAmount.toLocaleString()}
              <span className="ml-1 text-sm text-[#17251b]">원</span>
            </p>
          </div>
        </div>

        {salesSlip.salesType === "DIRECT" &&
        salesSlip.salesStatus !== "취소" ? (
          <div className="mt-3 rounded-md border border-[#dfe5dc]">
            <ManualPaymentPanel
              key={salesSlip.id}
              targetType="SALES_SLIP"
              targetId={salesSlip.id}
              remainingAmount={salesSlip.remainingAmount}
              expectedPaymentDate={salesSlip.expectedPaymentDate}
              onConfirm={async (payload) => {
                onPaymentConfirmed(
                  await confirmSalesSlipPayment(salesSlip.id, payload),
                );
              }}
            />
          </div>
        ) : null}
      </div>
    </SalesDetailCard>
  );
}

function InfoBox({
  children,
  open,
  preview,
  title,
  onToggle,
}: {
  children: ReactNode;
  open: boolean;
  preview?: string | null;
  title: string;
  onToggle: () => void;
}) {
  return (
    <div className="rounded-md border border-[#dfe5dc]">
      <button
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
        type="button"
        aria-expanded={open}
        onClick={onToggle}
      >
        <div className="flex min-w-0 items-center gap-3">
          <span className="shrink-0 font-bold text-[#17251b]">{title}</span>
          {preview ? (
            <span className="truncate text-sm font-semibold text-[#344138]">
              {preview}
            </span>
          ) : null}
        </div>
        <ChevronDown
          className={`h-4 w-4 shrink-0 text-[#68756c] transition-transform ${
            open ? "rotate-180" : ""
          }`}
          strokeWidth={1.8}
          aria-hidden="true"
        />
      </button>
      {open ? (
        <dl className="space-y-2 border-t border-[#edf0ec] px-4 py-3 text-sm">
          {children}
        </dl>
      ) : null}
    </div>
  );
}

function Description({
  label,
  value,
}: {
  label: string;
  value: string | null;
}) {
  return (
    <div className="grid grid-cols-[72px_minmax(0,1fr)] gap-3">
      <dt className="text-[#6a766e]">{label}</dt>
      <dd className="truncate font-medium text-[#344138]">{value ?? "-"}</dd>
    </div>
  );
}

function Amount({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="font-semibold text-[#6a766e]">{label}</p>
      <p className="mt-1 text-lg font-semibold text-[#344138]">
        {value.toLocaleString()}
      </p>
    </div>
  );
}
