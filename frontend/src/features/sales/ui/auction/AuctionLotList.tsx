import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { AuctionLot } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  auctionInspectionLabel,
  auctionStatusLabel,
  auctionStatusTone,
} from "../../lib/auctionDisplay";

export function AuctionLotList({
  lots,
  page,
  pageSize,
  totalElements,
  totalPages,
  selectedId,
  onSelect,
  onPageChange,
  onPageSizeChange,
}: {
  lots: AuctionLot[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  const columns = useMemo<ColumnDef<AuctionLot, unknown>[]>(
    () => [
      {
        accessorKey: "shipmentDate",
        header: "출하일",
        cell: ({ row }) => formatShortDate(row.original.shipmentDate),
        size: 110,
      },
      {
        accessorKey: "auctionMarket",
        header: "경매장",
        size: 140,
        meta: { cellClassName: "font-semibold whitespace-nowrap" },
      },
      {
        id: "item",
        header: "품목 / 품종",
        cell: ({ row }) => (
          <>
            <strong className="block">{row.original.varietyName}</strong>
            <span className="text-[#738077]">{row.original.itemName}</span>
          </>
        ),
        size: 220,
      },
      {
        accessorKey: "shipmentGrade",
        header: "등급",
        cell: ({ row }) => row.original.shipmentGrade || "-",
        size: 80,
      },
      quantityColumn("shippedQuantity", "출하"),
      quantityColumn("soldQuantity", "판매"),
      quantityColumn("waitingQuantity", "대기", (value) => value > 0),
      quantityColumn("returnedQuantity", "반환"),
      {
        accessorKey: "latestAuctionDate",
        header: "최근 경매",
        cell: ({ row }) => formatShortDate(row.original.latestAuctionDate),
        size: 110,
      },
      {
        accessorKey: "failedCount",
        header: "유찰",
        size: 70,
        meta: { align: "center" },
      },
      {
        accessorKey: "currentStatus",
        header: "상태",
        cell: ({ row }) => <StatusBadge status={row.original.currentStatus} />,
        size: 105,
      },
      {
        accessorKey: "totalAmount",
        header: "금액",
        cell: ({ row }) => row.original.totalAmount.toLocaleString(),
        size: 110,
        meta: { align: "right", cellClassName: "whitespace-nowrap" },
      },
      {
        accessorKey: "inspectionStatus",
        header: "검수",
        cell: ({ row }) =>
          auctionInspectionLabel(row.original.inspectionStatus),
        size: 100,
      },
    ],
    [],
  );

  return (
    <DataTable
      columns={columns}
      data={lots}
      emptyMessage="조건에 맞는 출하 lot이 없습니다."
      getRowId={(row) => String(row.id)}
      pageIndex={page - 1}
      pageSize={pageSize}
      pageSizeOptions={[20, 50, 100]}
      selectedRowId={selectedId == null ? null : String(selectedId)}
      settingsKey="sales.auctionLots"
      title="출하 lot 목록"
      totalLabel={`총 ${totalElements.toLocaleString()}건`}
      totalPages={totalPages}
      onPageChange={(pageIndex) => onPageChange(pageIndex + 1)}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelect(row.id)}
    />
  );
}

function quantityColumn(
  key:
    | "shippedQuantity"
    | "soldQuantity"
    | "waitingQuantity"
    | "returnedQuantity",
  header: string,
  emphasize?: (value: number) => boolean,
): ColumnDef<AuctionLot, unknown> {
  return {
    accessorKey: key,
    header,
    size: 70,
    cell: ({ row }) => {
      const value = row.original[key];
      return (
        <span
          className={`font-semibold ${emphasize?.(value) ? "text-[#d67a00]" : ""}`}
        >
          {value.toLocaleString()}
        </span>
      );
    },
    meta: { align: "right" },
  };
}

export function StatusBadge({
  status,
}: {
  status: AuctionLot["currentStatus"];
}) {
  const tone = auctionStatusTone(status);
  const classes = {
    green: "bg-[#e5f5e8] text-[#16853b]",
    orange: "bg-[#fff1d8] text-[#c66f00]",
    red: "bg-[#fee9e7] text-[#c43d35]",
    blue: "bg-[#e9f1fb] text-[#286aa6]",
  }[tone];
  return (
    <span
      className={`inline-flex rounded px-2 py-1 text-[11px] font-bold whitespace-nowrap ${classes}`}
    >
      {auctionStatusLabel(status)}
    </span>
  );
}
