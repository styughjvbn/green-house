import { useMemo, type ReactNode } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { BusinessPartner } from "@/entities/farm/types";
import { DataTable, type DataTableToolbar } from "@/shared/ui/DataTable";
import { DataTableCreateButton } from "@/shared/ui/DataTableCreateButton";

export function BusinessPartnerList({
  toolbar,
  emptyAction,
  hasAppliedFilters = false,
  currentPage,
  loading,
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
  toolbar?: DataTableToolbar;
  emptyAction?: ReactNode;
  hasAppliedFilters?: boolean;
  currentPage: number;
  loading: boolean;
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
      toolbar={toolbar}
      emptyAction={emptyAction}
      hasAppliedFilters={hasAppliedFilters}
      actions={
        <DataTableCreateButton onClick={onCreateBusinessPartner}>
          거래처 등록
        </DataTableCreateButton>
      }
      columns={columns}
      data={partners}
      emptyMessage="등록된 거래처가 없습니다."
      filteredEmptyMessage="조건에 맞는 거래처가 없습니다."
      getRowId={(row) => String(row.id)}
      isLoading={loading}
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
