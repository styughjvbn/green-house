import type { DirectPricePortion } from "../../api/directChangeTypes";
import { useDirectSaleChangeActions } from "./DirectSaleChangeActions";
import { salesDispatchLabel } from "../../lib/salesDisplay";
import { useState, type ReactNode } from "react";
import Link from "next/link";
import type { Route } from "next";
import { AuctionLotStatusBadge } from "../common/SalesStatusBadge";
import type { ColumnDef } from "@tanstack/react-table";
import { Ban, ChevronDown, Copy, Pencil, Printer, Truck } from "lucide-react";
import type { SalesSlip, SalesSlipItem } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import { SalesSlipPrintDialog } from "./SalesSlipPrintDialog";
import {
  DetailCard,
  DetailActionButton,
  DetailEmpty,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
const salesSlipItemColumns: ColumnDef<SalesSlipItem, unknown>[] = [
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
    accessorKey: "unit",
    header: "단위",
    cell: ({ row }) => row.original.unit ?? "-",
    size: 70,
  },
  {
    accessorKey: "boxes",
    header: "상자",
    cell: ({ row }) => row.original.boxes?.toLocaleString() ?? "-",
    size: 70,
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
        {row.original.allocations.length === 0 ? (
          <span className="text-xs text-[#6a766e]">난 묶음 연결 없음</span>
        ) : null}
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
function directItemColumns(
  portions: DirectPricePortion[] | undefined,
  returnedPortions: DirectPricePortion[],
): ColumnDef<SalesSlipItem, unknown>[] {
  const byItem = new Map<number, DirectPricePortion[]>();
  for (const portion of portions ?? []) {
    const group = byItem.get(portion.itemId) ?? [];
    group.push(portion);
    byItem.set(portion.itemId, group);
  }
  const returnedByItem = new Map<number, DirectPricePortion[]>();
  for (const portion of returnedPortions) {
    const group = returnedByItem.get(portion.itemId) ?? [];
    group.push(portion);
    returnedByItem.set(portion.itemId, group);
  }
  return salesSlipItemColumns.map((column) => {
    if (
      !("accessorKey" in column) ||
      !["quantity", "unitPrice", "amount"].includes(String(column.accessorKey))
    )
      return column;
    const key = column.accessorKey as "quantity" | "unitPrice" | "amount";
    return {
      ...column,
      cell: ({ row }) => {
        const item = row.original;
        const current = byItem.get(item.id) ?? [];
        const returned = returnedByItem.get(item.id) ?? [];
        const unchanged =
          current.length === 1 &&
          current[0].quantity === item.quantity &&
          current[0].unitPrice === item.unitPrice &&
          current[0].amount === item.amount;
        if (portions == null || (unchanged && returned.length === 0))
          return item[key].toLocaleString();
        const originalLabel = key === "unitPrice" ? "원단가" : "원금액";
        return (
          <div>
            <div className="space-y-1">
              {current.map((portion) => (
                <div
                  key={portion.sourceChangeId ?? "original"}
                  className="flex h-7 items-center justify-end gap-1 whitespace-nowrap"
                >
                  {key === "unitPrice" ? (
                    <span className="text-[10px] font-normal text-[#68756c]">
                      {portion.sourceChangeId == null ? "원가격" : "조정"}
                    </span>
                  ) : null}
                  <span>{portion[key].toLocaleString()}</span>
                </div>
              ))}
              {returned.map((portion) => (
                <div
                  key={`returned-${portion.sourceChangeId ?? "original"}`}
                  className="flex h-7 items-center justify-end gap-1 whitespace-nowrap text-[#8a968d]"
                >
                  {key === "unitPrice" ? (
                    <span className="text-[10px] font-normal">반품</span>
                  ) : null}
                  <del>{portion[key].toLocaleString()}</del>
                </div>
              ))}
              {current.length === 0 && returned.length === 0 ? (
                <div className="flex h-7 items-center justify-end text-[#68756c]">
                  —
                </div>
              ) : null}
            </div>
            {key !== "quantity" ? (
              <p className="mt-2 text-[10px] font-normal whitespace-nowrap text-[#68756c]">
                {originalLabel} {item[key].toLocaleString()}
              </p>
            ) : null}
          </div>
        );
      },
    };
  });
}

function auctionItemColumns(): ColumnDef<SalesSlipItem, unknown>[] {
  const columns = salesSlipItemColumns.filter(
    (column) =>
      !(
        "accessorKey" in column &&
        ["unitPrice", "amount"].includes(String(column.accessorKey))
      ),
  );
  const lotColumns: ColumnDef<SalesSlipItem, unknown>[] = [
    {
      id: "auctionLotStatus",
      header: "경매",
      cell: ({ row }) =>
        row.original.auctionLot ? (
          <AuctionLotStatusBadge
            status={row.original.auctionLot.currentStatus ?? "미상"}
          />
        ) : row.original.auctionShipmentLotId == null ? (
          "출하 전"
        ) : (
          "조회 불가"
        ),
      size: 120,
    },
    ...(
      [
        ["soldQuantity", "낙찰"],
        ["waitingQuantity", "대기"],
        ["returnedQuantity", "반환"],
      ] as const
    ).map(
      ([key, header]): ColumnDef<SalesSlipItem, unknown> => ({
        id: key,
        header,
        size: 70,
        cell: ({ row }) =>
          row.original.auctionLot?.[key]?.toLocaleString() ?? "-",
        meta: { align: "right" },
      }),
    ),
    {
      id: "auctionLotLink",
      header: "lot",
      size: 90,
      cell: ({ row }) =>
        row.original.auctionShipmentLotId == null ? (
          "-"
        ) : (
          <Link
            className="text-green-800 underline"
            href={
              `/sales/auction?lotId=${row.original.auctionShipmentLotId}` as Route
            }
          >
            상세 보기
          </Link>
        ),
    },
  ];
  return [...columns.slice(0, 3), ...lotColumns, ...columns.slice(3)];
}
type SalesSlipDetailProps = {
  loading?: boolean;
  salesSlip: SalesSlip | null;
  updatingSalesStatus: boolean;
  onCancelSalesSlip: (salesSlipId: number) => Promise<void>;
  onEditSalesSlip: (salesSlipId: number) => void;
  onCompleteSalesSlip: (salesSlipId: number) => Promise<void>;
};

export function SalesSlipDetail(props: SalesSlipDetailProps) {
  if (props.loading && !props.salesSlip)
    return <DetailEmpty>전표 상세를 불러오는 중입니다.</DetailEmpty>;
  if (!props.salesSlip)
    return <DetailEmpty>선택한 전표가 없습니다.</DetailEmpty>;
  return (
    <SalesSlipLoadedDetail
      key={props.salesSlip.id}
      {...props}
      salesSlip={props.salesSlip}
    />
  );
}

function SalesSlipLoadedDetail({
  salesSlip,
  updatingSalesStatus,
  onCancelSalesSlip,
  onEditSalesSlip,
  onCompleteSalesSlip,
}: Omit<SalesSlipDetailProps, "salesSlip"> & { salesSlip: SalesSlip }) {
  const [partnerInfoOpen, setPartnerInfoOpen] = useState(false);
  const [printPreviewOpen, setPrintPreviewOpen] = useState(false);
  const [slipInfoOpen, setSlipInfoOpen] = useState(false);
  const changes = useDirectSaleChangeActions(
    salesSlip.salesType === "DIRECT" ? salesSlip.id : null,
  );
  const currentPortions =
    salesSlip.financialReviewRequired === true || changes.priceQuery.isError
      ? undefined
      : changes.priceQuery.data?.portions;
  const directColumns = directItemColumns(
    currentPortions,
    currentPortions == null
      ? []
      : (changes.priceQuery.data?.returnedPortions ?? []),
  );
  const canComplete = salesSlip.availableActions.includes("COMPLETE");
  const canEdit = salesSlip.availableActions.includes("EDIT");
  const canCancel = salesSlip.availableActions.includes("CANCEL");
  // TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정.
  return (
    <>
      <DetailCard>
        {salesSlip.financialReviewRequired === true ? (
          <p
            role="status"
            className="border-b border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
          >
            금액 확인이 필요합니다. 검토가 끝날 때까지 금액 수정을 할 수
            없습니다.
          </p>
        ) : null}
        <DetailHeader
          eyebrow={`전표 #${salesSlip.id}`}
          title="전표 상세"
          summary={
            <DetailSummary
              align="left"
              columns="lg:grid-cols-4"
              items={[
                {
                  label: "판매 유형",
                  value:
                    salesSlip.salesType === "AUCTION" ? "경매" : "일반 판매",
                },
                {
                  label: "판매일자",
                  value: formatShortDate(salesSlip.saleDate),
                },
                {
                  label: "출고·출하",
                  value: salesDispatchLabel(
                    salesSlip.salesStatus,
                    salesSlip.salesType,
                  ),
                },
                {
                  label: "거래처",
                  value: salesSlip.partner.name,
                },
              ]}
            />
          }
          actions={
            <>
              {canEdit ? (
                <DetailActionButton
                  icon={Pencil}
                  onClick={() => onEditSalesSlip(salesSlip.id)}
                >
                  전표 수정
                </DetailActionButton>
              ) : null}
              {canComplete ? (
                <DetailActionButton
                  disabled={updatingSalesStatus}
                  icon={Truck}
                  tone="primary"
                  onClick={() => void onCompleteSalesSlip(salesSlip.id)}
                >
                  {salesSlip.salesType === "AUCTION"
                    ? "출하 완료"
                    : "출고 완료"}
                </DetailActionButton>
              ) : null}
              {canCancel ? (
                <DetailActionButton
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
                </DetailActionButton>
              ) : null}
              {changes.actions}
              <DetailActionButton
                icon={Printer}
                onClick={() => setPrintPreviewOpen(true)}
              >
                인쇄
              </DetailActionButton>
            </>
          }
        />

        <dl className="border-b border-[#e7ebe5] bg-[#fbfcfa] px-4 py-3">
          <dt className="text-xs font-semibold text-[#68756c]">전표 메모</dt>
          <dd className="mt-1 text-sm break-words whitespace-pre-wrap text-[#344138]">
            {salesSlip.memo?.trim() ? salesSlip.memo : "-"}
          </dd>
        </dl>

        <div className="p-4">
          <div className="mt-4">
            {salesSlip.salesType === "DIRECT" &&
            changes.priceQuery.isPending ? (
              <p className="mb-2 text-xs text-[#68756c]">
                현재 가격 구성을 불러오는 중입니다. 원판매 내용을 표시합니다.
              </p>
            ) : null}
            {salesSlip.salesType === "DIRECT" && changes.priceQuery.isError ? (
              <div
                role="alert"
                className="mb-2 flex items-center gap-2 text-xs text-red-700"
              >
                <span>
                  현재 가격 구성을 불러오지 못했습니다. 원판매 내용을
                  표시합니다.
                </span>
                <button
                  type="button"
                  className="underline"
                  onClick={() => void changes.priceQuery.refetch()}
                >
                  다시 불러오기
                </button>
              </div>
            ) : null}
            <DataTable
              columns={
                salesSlip.salesType === "AUCTION"
                  ? auctionItemColumns()
                  : directColumns
              }
              data={salesSlip.items}
              emptyMessage="품목이 없습니다."
              getRowId={(row) => String(row.id)}
              settingsKey={
                salesSlip.salesType === "AUCTION"
                  ? "sales.slipDetail.auctionItems"
                  : "sales.slipDetail.items"
              }
              title="품목"
              totalLabel={`총 ${salesSlip.items.length.toLocaleString()}건`}
            />
          </div>

          <div className="mt-3 flex justify-end rounded-md border border-[#dfe5dc] bg-white p-4 text-sm">
            <div className="text-right">
              <p className="font-semibold text-[#344138]">{"총 금액"}</p>
              <p className="mt-1 text-3xl font-bold text-[#159447]">
                {
                  <>
                    {salesSlip.totalAmount.toLocaleString()}
                    <span className="ml-1 text-sm text-[#17251b]">원</span>
                  </>
                }
              </p>
            </div>
          </div>

          {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. 수납 패널을 마운트하지 않는다. */}
        </div>
      </DetailCard>

      {changes.dialogs}

      {printPreviewOpen ? (
        <SalesSlipPrintDialog
          salesSlip={salesSlip}
          onClose={() => setPrintPreviewOpen(false)}
        />
      ) : null}
    </>
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
          className={`h-4 w-4 shrink-0 text-[#68756c] transition-transform ${open ? "rotate-180" : ""}`}
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
