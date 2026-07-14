import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Plus } from "lucide-react";
import type { BusinessPartner } from "@/entities/farm/types";
import { DataTable } from "@/shared/ui/DataTable";

export function BusinessPartnerList({
  currentPage,
  pageSize,
  partners,
  selectedBusinessPartnerId,
  totalPages,
  totalPartners,
  onSelectBusinessPartner,
  onCreateBusinessPartner,
  onPageChange,
  onPageSizeChange,
}: {
  currentPage: number;
  pageSize: number;
  partners: BusinessPartner[];
  selectedBusinessPartnerId: number | null;
  totalPages: number;
  totalPartners: number;
  onSelectBusinessPartner: (partnerId: number) => void;
  onCreateBusinessPartner: () => void;
  onPageChange: (pageIndex: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}) {
  const columns = useMemo<ColumnDef<BusinessPartner, unknown>[]>(
    () => [
      {
        accessorKey: "name",
        header: "거래처",
        size: 190,
        meta: { hideable: false, cellClassName: "font-semibold" },
      },
      {
        accessorKey: "partnerType",
        header: "유형",
        cell: ({ row }) => partnerTypeLabel(row.original.partnerType),
        size: 100,
      },
      {
        accessorKey: "ownerName",
        header: "대표자",
        cell: ({ row }) => row.original.ownerName || "-",
        size: 120,
      },
      {
        accessorKey: "phone",
        header: "연락처",
        cell: ({ row }) => row.original.phone || "-",
        size: 150,
      },
      {
        accessorKey: "active",
        header: "상태",
        cell: ({ row }) => (row.original.active ? "사용중" : "사용중지"),
        size: 100,
      },
    ],
    [],
  );

  return (
    <DataTable
      actions={
        <button
          className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
          type="button"
          onClick={onCreateBusinessPartner}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          거래처 등록
        </button>
      }
      columns={columns}
      data={partners}
      emptyMessage="등록된 거래처가 없습니다."
      getRowId={(row) => String(row.id)}
      pageIndex={currentPage}
      pageSize={pageSize}
      pageSizeOptions={[10, 20, 50]}
      selectedRowId={
        selectedBusinessPartnerId == null
          ? null
          : String(selectedBusinessPartnerId)
      }
      settingsKey="sales.partners"
      title="거래처 목록"
      totalLabel={`총 ${totalPartners.toLocaleString()}건`}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelectBusinessPartner(row.id)}
    />
  );
}

function partnerTypeLabel(type: BusinessPartner["partnerType"]) {
  if (type === "AUCTION_HOUSE") return "경매장";
  if (type === "RETAIL") return "소매";
  return "도매";
}
