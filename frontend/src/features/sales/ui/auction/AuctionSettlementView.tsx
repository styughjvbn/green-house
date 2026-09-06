"use client";

import { useEffect, useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import type {
  AuctionSettlement,
  AuctionSettlementLine,
  AuctionSettlementListItem,
} from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  confirmAuctionSettlementPayment,
  rebuildAuctionSettlement,
} from "../../api/salesApi";
import {
  auctionSettlementPageQueryOptions,
  auctionSettlementSummaryQueryOptions,
  auctionSettlementDetailQueryOptions,
} from "../../model/salesQueryOptions";
import { useSearchParams } from "next/navigation";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { readSettlementRouteState } from "../../lib/salesRouteParams";
import { salesQueryKeys } from "../../model/salesQueryKeys";
import { ManualPaymentPanel } from "./ManualPaymentPanel";
import { TabError, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import {
  DetailCard,
  DetailEmpty,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
import { AuctionSettlementStatusBadge } from "@/features/sales/ui/common/SalesStatusBadge";

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

export function AuctionSettlementView() {
  const queryClient = useQueryClient();
  const route = readSettlementRouteState(useSearchParams());
  const writeUrlParams = useUrlSearchParamsWriter();
  const settlementsQuery = useQuery(auctionSettlementPageQueryOptions(route));
  const summaryQuery = useQuery(auctionSettlementSummaryQueryOptions());
  const pageData = settlementsQuery.data;
  const selectedId =
    route.selectedSettlementId ?? pageData?.content[0]?.id ?? null;
  const detailQuery = useQuery({
    ...auctionSettlementDetailQueryOptions(selectedId ?? 0),
    enabled: selectedId != null,
  });
  const selected = detailQuery.data ?? null;
  const [mutating, setMutating] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const totalPages = Math.max(1, pageData?.totalPages ?? 1);

  useEffect(() => {
    if (pageData && route.page >= Math.max(1, pageData.totalPages)) {
      writeUrlParams((params) =>
        params.set("page", String(Math.max(0, pageData.totalPages - 1))),
      );
    }
  }, [pageData, route.page, writeUrlParams]);

  function changePage(page: number, size = route.size) {
    writeUrlParams((params) => {
      params.set("page", String(page));
      params.set("size", String(size));
      params.delete("settlementId");
    }, "push");
  }
  const columns = useMemo<ColumnDef<AuctionSettlementListItem, unknown>[]>(
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
    setMutating(true);
    setMutationError(null);
    try {
      const rebuilt = await rebuildAuctionSettlement(
        selected.auctionHouseId,
        selected.auctionDate,
      );
      await updateSettlement(rebuilt);
    } catch (requestError) {
      setMutationError(
        requestError instanceof Error
          ? requestError.message
          : "정산을 다시 계산하지 못했습니다.",
      );
    } finally {
      setMutating(false);
    }
  }

  async function updateSettlement(updated: AuctionSettlement) {
    const queryKey = salesQueryKeys.auction.settlementDetail(updated.id);
    await queryClient.cancelQueries({ queryKey, exact: true });
    queryClient.setQueryData(queryKey, updated);
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: salesQueryKeys.auction.settlementPages,
      }),
      queryClient.invalidateQueries({
        queryKey: salesQueryKeys.auction.settlementSummary,
      }),
    ]);
  }

  const loading =
    settlementsQuery.isFetching || detailQuery.isFetching || mutating;
  const queryError =
    settlementsQuery.error ?? summaryQuery.error ?? detailQuery.error;
  const error =
    mutationError ?? (queryError instanceof Error ? queryError.message : null);

  return (
    <TabStack>
      <section className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#dfe5dc] bg-white px-4 py-3 shadow-sm">
        <div>
          <h2 className="text-base font-bold">경매장 정산</h2>
          <p className="mt-0.5 text-xs text-[#68756c]">
            경매장과 경매일 기준으로 낙찰 결과를 묶어 관리합니다.
          </p>
        </div>
        <div className="flex items-center gap-5 text-right">
          <Summary
            label="예상 입금액"
            value={summaryQuery.data?.expectedDepositAmount}
          />
          <Summary
            label="미입금 잔액"
            value={summaryQuery.data?.remainingAmount}
          />
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

      <TabError message={error} />

      <TabSplit
        columns="lg:grid-cols-[minmax(0,0.9fr)_minmax(480px,1.1fr)]"
        gap="gap-3"
      >
        <DataTable
          columns={columns}
          data={pageData?.content ?? []}
          isLoading={settlementsQuery.isPending}
          emptyMessage="생성된 경매 정산이 없습니다."
          getRowId={(row) => String(row.id)}
          pageIndex={route.page}
          pageSize={route.size}
          pageSizeOptions={[10, 20, 50]}
          selectedRowId={selected?.id == null ? null : String(selected.id)}
          settingsKey="sales.settlements"
          title="정산 목록"
          totalLabel={`총 ${(pageData?.totalElements ?? 0).toLocaleString()}건`}
          totalPages={totalPages}
          onPageChange={(page) => changePage(page)}
          onPageSizeChange={(size) => changePage(0, size)}
          onRowClick={(row) =>
            writeUrlParams(
              (params) => params.set("settlementId", String(row.id)),
              "push",
            )
          }
        />

        {detailQuery.isFetching && !selected ? (
          <DetailEmpty>정산 상세를 불러오는 중입니다.</DetailEmpty>
        ) : (
          <SettlementDetail settlement={selected} onUpdate={updateSettlement} />
        )}
      </TabSplit>
    </TabStack>
  );
}

function SettlementDetail({
  settlement,
  onUpdate,
}: {
  settlement: AuctionSettlement | null;
  onUpdate: (settlement: AuctionSettlement) => Promise<void>;
}) {
  if (!settlement) {
    return <DetailEmpty>확인할 정산을 선택하세요.</DetailEmpty>;
  }

  return (
    <DetailCard>
      <DetailHeader
        eyebrow={`정산 #${settlement.id}`}
        eyebrowAside={
          <AuctionSettlementStatusBadge
            size="compact"
            status={settlement.status}
          />
        }
        title={`${settlement.auctionHouseName} · ${formatShortDate(settlement.auctionDate)}`}
        summary={
          <DetailSummary
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
          await onUpdate(
            await confirmAuctionSettlementPayment(settlement.id, payload),
          );
        }}
      />
    </DetailCard>
  );
}

function Summary({
  label,
  value,
}: {
  label: string;
  value: number | undefined;
}) {
  return (
    <div>
      <p className="text-[11px] text-[#68756c]">{label}</p>
      <p className="text-sm font-bold">
        {value == null ? "-" : `${value.toLocaleString()}원`}
      </p>
    </div>
  );
}
