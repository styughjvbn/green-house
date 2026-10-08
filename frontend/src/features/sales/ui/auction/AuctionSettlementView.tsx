"use client";

import { useEffect, type ReactNode } from "react";
import Link from "next/link";
import type { Route } from "next";
import { salesV2Href } from "@/shared/config/routes";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import { useSearchParams } from "next/navigation";
import type {
  AuctionProceeds,
  AuctionProceedsResult,
} from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import {
  DetailCard,
  DetailEmpty,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
import { TabError, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { confirmAuctionProceedsPayment } from "../../api/salesApi";
import { readProceedsRouteState } from "../../lib/salesRouteParams";
import {
  auctionProceedsPageQueryOptions,
  auctionProceedsDetailQueryOptions,
} from "../../model/salesQueryOptions";
import { salesQueryKeys } from "../../model/salesQueryKeys";
import { ManualPaymentPanel } from "./ManualPaymentPanel";

const money = (value: number | null) =>
  value == null ? "미확인" : `${value.toLocaleString()}원`;
const columns: ColumnDef<AuctionProceeds, unknown>[] = [
  {
    accessorKey: "auctionHouseName",
    header: "경매장",
    meta: { hideable: false },
  },
  {
    accessorKey: "sourceReference",
    header: "대금 자료",
    cell: ({ row }) => row.original.sourceReference ?? "자료 확인 대기",
  },
  {
    accessorKey: "reportedGrossAmount",
    header: "제공 낙찰액",
    cell: ({ row }) => money(row.original.reportedGrossAmount),
    meta: { align: "right" },
  },
  {
    accessorKey: "receivableAmount",
    header: "받을 금액",
    cell: ({ row }) => money(row.original.receivableAmount),
    meta: { align: "right" },
  },
  {
    accessorKey: "paidAmount",
    header: "입금액",
    cell: ({ row }) => money(row.original.paidAmount),
    meta: { align: "right" },
  },
  {
    accessorKey: "remainingAmount",
    header: "잔액",
    cell: ({ row }) => money(row.original.remainingAmount),
    meta: { align: "right" },
  },
];

const resultColumns: ColumnDef<AuctionProceedsResult, unknown>[] = [
  {
    accessorKey: "auctionDate",
    header: "경매일",
    cell: ({ row }) => formatShortDate(row.original.auctionDate),
  },
  {
    accessorKey: "shipmentDate",
    header: "출하일",
    cell: ({ row }) => formatShortDate(row.original.shipmentDate),
  },
  {
    accessorKey: "varietyName",
    header: "품종·등급",
    cell: ({ row }) =>
      `${row.original.varietyName} · ${row.original.shipmentGrade || "-"}`,
  },
  {
    accessorKey: "quantity",
    header: "수량",
    cell: ({ row }) => `${row.original.quantity.toLocaleString()}분`,
    meta: { align: "right" },
  },
  {
    accessorKey: "unitPrice",
    header: "단가",
    cell: ({ row }) => money(row.original.unitPrice),
    meta: { align: "right" },
  },
  {
    accessorKey: "amount",
    header: "결과 금액",
    cell: ({ row }) => money(row.original.amount),
    meta: { align: "right" },
  },
];

export function AuctionSettlementView({
  v2 = false,
  actions,
}: {
  v2?: boolean;
  actions?: ReactNode;
}) {
  const queryClient = useQueryClient();
  const route = readProceedsRouteState(
    useSearchParams(),
    v2 ? "auction" : "default",
  );
  const writeUrlParams = useUrlSearchParamsWriter();
  const pageQuery = useQuery(auctionProceedsPageQueryOptions(route));
  const selectedId =
    route.selectedProceedsId ?? pageQuery.data?.content[0]?.id ?? null;
  const detailQuery = useQuery({
    ...auctionProceedsDetailQueryOptions(selectedId ?? 0),
    enabled: selectedId != null,
  });
  const selected = detailQuery.data ?? null;
  useEffect(() => {
    if (
      pageQuery.data &&
      route.page >= Math.max(1, pageQuery.data.totalPages)
    ) {
      writeUrlParams((params) =>
        params.set(
          v2 ? "proceedsPage" : "page",
          String(Math.max(0, pageQuery.data.totalPages - 1)),
        ),
      );
    }
  }, [pageQuery.data, route.page, writeUrlParams, v2]);
  function changePage(page: number, size = route.size) {
    writeUrlParams((params) => {
      params.set(v2 ? "proceedsPage" : "page", String(page));
      params.set(v2 ? "proceedsSize" : "size", String(size));
      params.delete("proceedsId");
      params.delete("settlementId");
      params.delete("paymentPage");
    }, "push");
  }
  const detailColumns = v2
    ? [
        {
          id: "lot",
          header: "출하 lot",
          cell: ({ row }: { row: { original: AuctionProceedsResult } }) => (
            <Link
              className="text-green-800 underline"
              href={
                salesV2Href("auction", { lotId: row.original.lotId }) as Route
              }
            >
              LOT #{row.original.lotId}
            </Link>
          ),
        },
        ...resultColumns,
      ]
    : resultColumns;
  const error = pageQuery.error ?? detailQuery.error;
  return (
    <TabStack>
      {!v2 ? (
        <section className="rounded-md border bg-white px-4 py-3">
          <h2 className="text-base font-bold">경매 대금</h2>
          <p className="mt-1 text-sm text-[#68756c]">
            경매장 대금 자료와 실제 입금을 확인합니다. 받을 금액이 미확인인
            자료는 입금 확인을 기다립니다.
          </p>
        </section>
      ) : null}
      <TabError message={error instanceof Error ? error.message : null} />
      <TabSplit
        columns="lg:grid-cols-[minmax(0,0.9fr)_minmax(480px,1.1fr)]"
        gap="gap-3"
      >
        <DataTable
          actions={actions}
          columns={columns}
          data={pageQuery.data?.content ?? []}
          isLoading={pageQuery.isPending}
          emptyMessage="등록된 경매 대금 자료가 없습니다."
          getRowId={(row) => String(row.id)}
          pageIndex={route.page}
          pageSize={route.size}
          pageSizeOptions={[10, 20, 50]}
          selectedRowId={selectedId == null ? null : String(selectedId)}
          settingsKey="sales.proceeds"
          title="대금 목록"
          totalLabel={`총 ${(pageQuery.data?.totalElements ?? 0).toLocaleString()}건`}
          totalPages={Math.max(1, pageQuery.data?.totalPages ?? 1)}
          onPageChange={(page) => changePage(page)}
          onPageSizeChange={(size) => changePage(0, size)}
          onRowClick={(row) =>
            writeUrlParams((params) => {
              params.set("proceedsId", String(row.id));
              params.delete("settlementId");
              params.delete("paymentPage");
            }, "push")
          }
        />
        {selected ? (
          <DetailCard>
            <DetailHeader
              eyebrow="경매 대금"
              title={selected.auctionHouseName}
              summary={
                <DetailSummary
                  items={[
                    {
                      label: "제공 낙찰액",
                      value: money(selected.reportedGrossAmount),
                    },
                    {
                      label: "받을 금액",
                      value: money(selected.receivableAmount),
                    },
                    { label: "입금액", value: money(selected.paidAmount) },
                    { label: "잔액", value: money(selected.remainingAmount) },
                  ]}
                />
              }
            />
            <div className="space-y-2 px-4 py-3 text-sm">
              {v2 ? (
                <Link
                  className="block text-green-800 underline"
                  href={
                    salesV2Href("payments", {
                      view: "allocations",
                      receiptPartnerId: selected.auctionHouseId,
                    }) as Route
                  }
                >
                  경매장 수납·배분 조회
                </Link>
              ) : null}
              <p>{selected.sourceReference ?? "대금 자료 확인 대기"}</p>
              <p>
                연결된 경매 결과 {selected.resultIds.length.toLocaleString()}건
              </p>
              {selected.reviewRequired ? (
                <p role="status">
                  입금 연결 검토가 필요합니다. 검토 전에는 새 입금을 확인할 수
                  없습니다.
                </p>
              ) : null}
              {!selected.matchingConfirmed ? (
                <p>대금 자료와 경매 결과 연결 확인을 기다립니다.</p>
              ) : null}
            </div>
            <div className="px-4 py-3">
              <DataTable
                columns={detailColumns}
                data={selected.resultDetails}
                getRowId={(row) => String(row.id)}
                title="연결된 경매 결과"
                emptyMessage="연결된 경매 결과가 없습니다."
                settingsKey="sales.proceeds.results"
              />
            </div>
            <ManualPaymentPanel
              key={selected.id}
              targetType="AUCTION_PROCEEDS"
              targetId={selected.id}
              remainingAmount={selected.remainingAmount}
              expectedPaymentDate={null}
              paymentAllowed={selected.paymentAllowed}
              onConfirm={async (payload) => {
                const updated = await confirmAuctionProceedsPayment(
                  selected.id,
                  payload,
                );
                const key = salesQueryKeys.auction.proceedsDetail(updated.id);
                await queryClient.cancelQueries({ queryKey: key, exact: true });
                queryClient.setQueryData(key, updated);
                await queryClient.invalidateQueries({
                  queryKey: salesQueryKeys.auction.proceedsPages,
                });
                return updated.remainingAmount;
              }}
            />
          </DetailCard>
        ) : (
          <DetailEmpty>
            {detailQuery.isFetching
              ? "대금 상세를 불러오는 중입니다."
              : "확인할 대금 자료를 선택하세요."}
          </DetailEmpty>
        )}
      </TabSplit>
    </TabStack>
  );
}
