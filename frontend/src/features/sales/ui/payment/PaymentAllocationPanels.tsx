import type { RefObject } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import Link from "next/link";
import type { Route } from "next";
import { DataTable } from "@/shared/ui/DataTable";
import { TabSplit } from "@/shared/ui/TabLayout";
import { DetailCard, DetailEmpty, DetailHeader } from "@/shared/ui/DetailCard";
import { Button } from "@/shared/ui/primitives/button";
import { salesV2Href } from "@/shared/config/routes";
import { formatShortDate } from "@/shared/lib/dateFormat";
import type {
  getAllocationReceipts,
  getAllocationReceipt,
  getReceiptAllocations,
} from "../../api/salesApi";

type Receipts = Awaited<ReturnType<typeof getAllocationReceipts>>;
type Receipt = Awaited<ReturnType<typeof getAllocationReceipt>>;
type Allocations = Awaited<ReturnType<typeof getReceiptAllocations>>;

export function PaymentAllocationPanels({
  sources,
  receipt,
  allocations,
  receiptId,
  page,
  allocationPage,
  checked,
  disabled,
  addDisabled,
  correctionDisabled,
  headingRef,
  onSelect,
  onPage,
  onAllocationPage,
  onCheck,
  onAdd,
  onCorrect,
}: {
  sources: UseQueryResult<Receipts>;
  receipt: UseQueryResult<Receipt>;
  allocations: UseQueryResult<Allocations>;
  receiptId: number | null;
  page: number;
  allocationPage: number;
  checked: number[];
  disabled: boolean;
  addDisabled: boolean;
  correctionDisabled: boolean;
  headingRef: RefObject<HTMLElement | null>;
  onSelect: (id: number) => void;
  onPage: (page: number) => void;
  onAllocationPage: (page: number) => void;
  onCheck: (id: number, checked: boolean) => void;
  onAdd: (button: HTMLButtonElement) => void;
  onCorrect: (button: HTMLButtonElement) => void;
}) {
  const sourceColumns: ColumnDef<Receipts["content"][number]>[] = [
    {
      id: "receipt",
      header: "수납",
      size: 95,
      cell: ({ row }) => (
        <Button
          variant="link"
          size="sm"
          onClick={(e) => {
            e.stopPropagation();
            onSelect(row.original.id);
          }}
        >
          수납 #{row.original.id}
        </Button>
      ),
    },
    {
      accessorKey: "paymentDate",
      header: "입금일",
      size: 90,
      cell: ({ row }) => formatShortDate(row.original.paymentDate),
    },
    {
      accessorKey: "amount",
      header: "입금액",
      size: 110,
      cell: ({ row }) => `${row.original.amount.toLocaleString()}원`,
      meta: { align: "right" },
    },
    {
      accessorKey: "availableAmount",
      header: "미배분",
      size: 110,
      cell: ({ row }) => `${row.original.availableAmount.toLocaleString()}원`,
      meta: { align: "right" },
    },
    {
      id: "status",
      header: "상태",
      size: 80,
      cell: ({ row }) =>
        row.original.status === "CANCELLED"
          ? "취소됨"
          : row.original.reviewRequired
            ? "검토 필요"
            : "수납",
    },
  ];
  const allocationColumns: ColumnDef<Allocations["content"][number]>[] = [
    {
      id: "select",
      header: "선택",
      size: 45,
      enableSorting: false,
      meta: { hideable: false },
      cell: ({ row }) => (
        <input
          type="checkbox"
          className="accent-[#159447]"
          aria-label={`배분 #${row.original.id} 선택`}
          checked={checked.includes(row.original.id)}
          disabled={disabled || !row.original.cancellationAllowed}
          onChange={(e) => onCheck(row.original.id, e.target.checked)}
        />
      ),
    },
    {
      id: "target",
      header: "배분 대상",
      size: 160,
      cell: ({ row }) => (
        <Link
          className="font-semibold text-[#159447] underline underline-offset-2"
          aria-label="배분 대상 보기"
          href={
            salesV2Href(
              row.original.targetType === "SALES_SLIP" ? "slips" : "auction",
              row.original.targetType === "SALES_SLIP"
                ? { slipId: row.original.targetId, paymentPage: 0 }
                : {
                    panel: "proceeds",
                    proceedsId: row.original.targetId,
                    paymentPage: 0,
                  },
            ) as Route
          }
        >
          {row.original.targetType === "SALES_SLIP"
            ? "일반 판매 전표"
            : "경매 대금"}{" "}
          #{row.original.targetId}
        </Link>
      ),
    },
    {
      accessorKey: "amount",
      header: "배분액",
      size: 110,
      cell: ({ row }) => `${row.original.amount.toLocaleString()}원`,
      meta: { align: "right" },
    },
    {
      id: "status",
      header: "상태",
      size: 90,
      cell: ({ row }) =>
        row.original.status === "CANCELLED" ? "취소됨" : "유효 배분",
    },
  ];
  return (
    <TabSplit
      columns="lg:grid-cols-[minmax(0,1.05fr)_minmax(24rem,1fr)]"
      gap="gap-3"
    >
      <div
        data-sales-payment-panel="sources"
        className="h-full min-h-0 min-w-0"
      >
        <DataTable
          columns={sourceColumns}
          data={sources.data?.content ?? []}
          isLoading={sources.isPending}
          errorMessage={sources.isError ? "수납을 불러오지 못했습니다." : null}
          emptyMessage="수납 내역이 없습니다."
          getRowId={(item) => String(item.id)}
          title="수납 선택"
          settingsKey="sales.v2.paymentReceipts"
          totalLabel={`총 ${(sources.data?.totalElements ?? 0).toLocaleString()}건`}
          selectedRowId={receiptId == null ? null : String(receiptId)}
          pageIndex={page}
          pageSize={10}
          totalPages={sources.data?.totalPages ?? 0}
          onPageChange={onPage}
          onRowClick={(item) => onSelect(item.id)}
          actions={
            sources.isError ? (
              <Button
                variant="outline"
                size="sm"
                onClick={() => void sources.refetch()}
              >
                다시 조회
              </Button>
            ) : undefined
          }
        />
      </div>
      <div
        data-sales-payment-panel="allocation"
        className="h-full min-h-0 min-w-0"
      >
        {receiptId == null ? (
          <DetailEmpty>배분할 수납을 목록에서 선택하세요.</DetailEmpty>
        ) : (
          <DetailCard className="flex flex-col">
            <DetailHeader
              title={
                <span ref={headingRef} tabIndex={-1}>
                  선택 수납 #{receiptId}의 배분 내역
                </span>
              }
              actions={
                <>
                  <Button
                    size="sm"
                    disabled={addDisabled}
                    onClick={(e) => onAdd(e.currentTarget)}
                  >
                    배분 추가
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={correctionDisabled}
                    onClick={(e) => onCorrect(e.currentTarget)}
                  >
                    선택 배분 정정
                  </Button>
                </>
              }
            />
            <div className="flex min-h-0 flex-1 flex-col gap-3 p-4">
              {receipt.isError ? (
                <p role="alert" className="text-sm text-[#a64835]">
                  선택 수납을 불러오지 못했습니다.{" "}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => void receipt.refetch()}
                  >
                    다시 조회
                  </Button>
                </p>
              ) : receipt.data ? (
                <p className="text-sm font-semibold text-[#344138]">
                  입금 {receipt.data.amount.toLocaleString()}원 / 미배분{" "}
                  {receipt.data.availableAmount.toLocaleString()}원
                  {receipt.data.reviewRequired
                    ? " · 원장 검토가 필요합니다."
                    : ""}
                </p>
              ) : (
                <p className="text-sm text-[#68756c]">수납 확인 중</p>
              )}
              <DataTable
                columns={allocationColumns}
                data={allocations.data?.content ?? []}
                isLoading={allocations.isPending}
                errorMessage={
                  allocations.isError
                    ? "배분 내역을 불러오지 못했습니다."
                    : null
                }
                emptyMessage="배분 내역이 없습니다."
                getRowId={(item) => String(item.id)}
                title="배분 이력"
                settingsKey="sales.v2.paymentAllocations"
                totalLabel={`총 ${(allocations.data?.totalElements ?? 0).toLocaleString()}건`}
                pageIndex={allocationPage}
                pageSize={10}
                totalPages={allocations.data?.totalPages ?? 0}
                onPageChange={onAllocationPage}
                actions={
                  allocations.isError ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => void allocations.refetch()}
                    >
                      다시 조회
                    </Button>
                  ) : undefined
                }
              />
            </div>
          </DetailCard>
        )}
      </div>
    </TabSplit>
  );
}
