"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { RefreshCw } from "lucide-react";
import type {
  AuctionSettlement,
  AuctionSettlementLine,
} from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  confirmAuctionSettlementPayment,
  rebuildAuctionSettlement,
} from "../../api/salesApi";
import { ManualPaymentPanel } from "./ManualPaymentPanel";
import {
  SalesTabError,
  SalesTabSplit,
  SalesTabStack,
} from "../common/SalesTabLayout";
import {
  SalesDetailCard,
  SalesDetailEmpty,
  SalesDetailHeader,
  SalesDetailSummary,
} from "../common/SalesDetailCard";
import { AuctionSettlementStatusBadge } from "../common/SalesStatusBadge";

const settlementLineColumns: ColumnDef<AuctionSettlementLine, unknown>[] = [
  {
    accessorKey: "shipmentDate",
    header: "출하일",
    cell: ({ row }) => formatShortDate(row.original.shipmentDate),
    size: 100,
    meta: { hideable: false },
  },
  {
    id: "varietyGrade",
    header: "품종·등급",
    cell: ({ row }) =>
      `${row.original.varietyName} · ${row.original.shipmentGrade || "-"}`,
    size: 180,
    meta: { cellClassName: "font-semibold" },
  },
  {
    accessorKey: "quantity",
    header: "수량",
    cell: ({ row }) => `${row.original.quantity.toLocaleString()}분`,
    size: 90,
    meta: { align: "right" },
  },
  {
    accessorKey: "unitPrice",
    header: "단가",
    cell: ({ row }) => `${row.original.unitPrice.toLocaleString()}원`,
    size: 110,
    meta: { align: "right" },
  },
  {
    accessorKey: "amount",
    header: "금액",
    cell: ({ row }) => `${row.original.amount.toLocaleString()}원`,
    size: 120,
    meta: { align: "right", cellClassName: "font-semibold" },
  },
];

export function AuctionSettlementView({
  initialSettlements,
}: {
  initialSettlements: AuctionSettlement[];
}) {
  const [settlements, setSettlements] = useState(initialSettlements);
  const [selectedId, setSelectedId] = useState<number | null>(
    initialSettlements[0]?.id ?? null,
  );
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selected =
    settlements.find((settlement) => settlement.id === selectedId) ??
    settlements[0] ??
    null;
  const totals = useMemo(
    () => ({
      expected: settlements.reduce(
        (sum, settlement) => sum + settlement.expectedDepositAmount,
        0,
      ),
      remaining: settlements.reduce(
        (sum, settlement) => sum + settlement.remainingAmount,
        0,
      ),
    }),
    [settlements],
  );
  const totalPages = Math.max(1, Math.ceil(settlements.length / pageSize));
  const visiblePage = Math.min(page, totalPages - 1);
  const paginatedSettlements = useMemo(() => {
    const start = visiblePage * pageSize;
    return settlements.slice(start, start + pageSize);
  }, [pageSize, settlements, visiblePage]);
  const columns = useMemo<ColumnDef<AuctionSettlement, unknown>[]>(
    () => [
      {
        accessorKey: "auctionHouseName",
        header: "경매장",
        size: 180,
        meta: { hideable: false, cellClassName: "font-semibold" },
      },
      {
        accessorKey: "auctionDate",
        header: "경매일",
        cell: ({ row }) => formatShortDate(row.original.auctionDate),
        size: 110,
      },
      {
        accessorKey: "grossAmount",
        header: "총 낙찰액",
        cell: ({ row }) => `${row.original.grossAmount.toLocaleString()}원`,
        size: 130,
        meta: { align: "right" },
      },
      {
        accessorKey: "expectedDepositAmount",
        header: "예상 입금액",
        cell: ({ row }) =>
          `${row.original.expectedDepositAmount.toLocaleString()}원`,
        size: 140,
        meta: { align: "right" },
      },
      {
        accessorKey: "remainingAmount",
        header: "잔액",
        cell: ({ row }) => row.original.remainingAmount.toLocaleString() + "원",
        size: 120,
        meta: { align: "right", cellClassName: "font-semibold" },
      },
      {
        accessorKey: "status",
        header: "상태",
        cell: ({ row }) => (
          <AuctionSettlementStatusBadge status={row.original.status} />
        ),
        size: 110,
        meta: { align: "center" },
      },
    ],
    [],
  );

  async function rebuildSelected() {
    if (!selected) return;
    setLoading(true);
    setError(null);
    try {
      const rebuilt = await rebuildAuctionSettlement(
        selected.auctionHouseId,
        selected.auctionDate,
      );
      setSettlements((current) =>
        current.map((item) => (item.id === rebuilt.id ? rebuilt : item)),
      );
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "정산을 다시 계산하지 못했습니다.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <SalesTabStack>
      <section className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#dfe5dc] bg-white px-4 py-3 shadow-sm">
        <div>
          <h2 className="text-base font-bold">경매장 정산</h2>
          <p className="mt-0.5 text-xs text-[#68756c]">
            경매장과 경매일 기준으로 낙찰 결과를 묶어 관리합니다.
          </p>
        </div>
        <div className="flex items-center gap-5 text-right">
          <Summary label="예상 입금액" value={totals.expected} />
          <Summary label="미입금 잔액" value={totals.remaining} />
          <button
            className="inline-flex h-9 items-center gap-1.5 rounded-md border border-[#ccd6ca] px-3 text-xs font-semibold disabled:opacity-50"
            type="button"
            disabled={!selected || loading}
            onClick={rebuildSelected}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`}
            />
            정산 다시 계산
          </button>
        </div>
      </section>

      <SalesTabError message={error} />

      <SalesTabSplit
        columns="lg:grid-cols-[minmax(0,0.9fr)_minmax(480px,1.1fr)]"
        gap="gap-3"
      >
        <DataTable
          columns={columns}
          data={paginatedSettlements}
          emptyMessage="생성된 경매 정산이 없습니다."
          getRowId={(row) => String(row.id)}
          pageIndex={visiblePage}
          pageSize={pageSize}
          pageSizeOptions={[10, 20, 50]}
          selectedRowId={selected?.id == null ? null : String(selected.id)}
          settingsKey="sales.settlements"
          title="정산 목록"
          totalLabel={`총 ${settlements.length.toLocaleString()}건`}
          totalPages={totalPages}
          onPageChange={setPage}
          onPageSizeChange={(nextPageSize) => {
            setPageSize(nextPageSize);
            setPage(0);
          }}
          onRowClick={(row) => setSelectedId(row.id)}
        />

        <SettlementDetail
          settlement={selected}
          onUpdate={(updated) =>
            setSettlements((current) =>
              current.map((item) => (item.id === updated.id ? updated : item)),
            )
          }
        />
      </SalesTabSplit>
    </SalesTabStack>
  );
}

function SettlementDetail({
  settlement,
  onUpdate,
}: {
  settlement: AuctionSettlement | null;
  onUpdate: (settlement: AuctionSettlement) => void;
}) {
  if (!settlement) {
    return <SalesDetailEmpty>확인할 정산을 선택하세요.</SalesDetailEmpty>;
  }

  return (
    <SalesDetailCard>
      <SalesDetailHeader
        eyebrow={`정산 #${settlement.id}`}
        eyebrowAside={
          <AuctionSettlementStatusBadge
            size="compact"
            status={settlement.status}
          />
        }
        title={`${settlement.auctionHouseName} · ${formatShortDate(settlement.auctionDate)}`}
        summary={
          <SalesDetailSummary
            items={[
              {
                label: "총 낙찰액",
                value: `${settlement.grossAmount.toLocaleString()}원`,
              },
              {
                label: "예상 입금액",
                value: `${settlement.expectedDepositAmount.toLocaleString()}원`,
              },
              {
                label: "입금액",
                value: `${settlement.paidAmount.toLocaleString()}원`,
              },
              {
                label: "잔액",
                value: `${settlement.remainingAmount.toLocaleString()}원`,
              },
            ]}
          />
        }
      />

      <div className="px-4 py-3">
        <DataTable
          columns={settlementLineColumns}
          data={settlement.lines}
          emptyMessage="포함된 경매 결과가 없습니다."
          getRowId={(row) => String(row.id)}
          settingsKey="sales.settlementDetail.lines"
          title="포함 경매 결과"
          totalLabel={`총 ${settlement.lines.length.toLocaleString()}건`}
        />
      </div>

      <ManualPaymentPanel
        key={settlement.id}
        targetType="AUCTION_SETTLEMENT"
        targetId={settlement.id}
        remainingAmount={settlement.remainingAmount}
        expectedPaymentDate={settlement.expectedPaymentDate}
        onConfirm={async (payload) => {
          onUpdate(
            await confirmAuctionSettlementPayment(settlement.id, payload),
          );
        }}
      />
    </SalesDetailCard>
  );
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-[11px] text-[#68756c]">{label}</p>
      <p className="text-sm font-bold">{value.toLocaleString()}원</p>
    </div>
  );
}
