"use client";

import { useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import type {
  AuctionSettlement,
  AuctionSettlementStatus,
} from "@/entities/farm/types";
import { PaginationControls } from "@/shared/ui/PaginationControls";
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
        <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
          <header className="border-b border-[#e5e9e3] px-4 py-3">
            <h3 className="text-sm font-bold">정산 목록</h3>
            <p className="mt-0.5 text-xs text-[#68756c]">
              총 {settlements.length.toLocaleString()}건
            </p>
          </header>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-sm">
              <thead className="bg-[#f7f9f6] text-xs text-[#66736a]">
                <tr>
                  <th className="px-3 py-2 text-left">경매장</th>
                  <th className="px-3 py-2 text-left">경매일</th>
                  <th className="px-3 py-2 text-right">총 낙찰액</th>
                  <th className="px-3 py-2 text-right">예상 입금액</th>
                  <th className="px-3 py-2 text-right">잔액</th>
                  <th className="px-3 py-2 text-center">상태</th>
                </tr>
              </thead>
              <tbody>
                {paginatedSettlements.map((settlement) => (
                  <tr
                    key={settlement.id}
                    className={`cursor-pointer border-t border-[#edf0ec] ${selected?.id === settlement.id ? "bg-[#eef7ec]" : "hover:bg-[#fafbf9]"}`}
                    onClick={() => setSelectedId(settlement.id)}
                  >
                    <td className="px-3 py-2.5 font-semibold">
                      {settlement.auctionHouseName}
                    </td>
                    <td className="px-3 py-2.5">{settlement.auctionDate}</td>
                    <td className="px-3 py-2.5 text-right">
                      {settlement.grossAmount.toLocaleString()}원
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      {settlement.expectedDepositAmount.toLocaleString()}원
                    </td>
                    <td className="px-3 py-2.5 text-right font-semibold">
                      {settlement.remainingAmount.toLocaleString()}원
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      <SettlementStatus status={settlement.status} />
                    </td>
                  </tr>
                ))}
                {paginatedSettlements.length === 0 ? (
                  <tr>
                    <td
                      colSpan={6}
                      className="py-14 text-center text-[#68756c]"
                    >
                      생성된 경매 정산이 없습니다.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <div className="border-t border-[#e5e9e3] px-4 py-3">
            <PaginationControls
              nextLabel="다음"
              pageCount={totalPages}
              pageIndex={visiblePage}
              pageSize={pageSize}
              pageSizeOptions={[10, 20, 50]}
              previousLabel="이전"
              onPageChange={setPage}
              onPageSizeChange={(nextPageSize) => {
                setPageSize(nextPageSize);
                setPage(0);
              }}
            />
          </div>
        </section>

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
    return (
      <section className="rounded-md border border-[#dfe5dc] bg-white p-10 text-center text-sm text-[#68756c]">
        확인할 정산을 선택하세요.
      </section>
    );
  }

  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e5e9e3] px-4 py-3">
        <div>
          <p className="text-xs font-semibold text-[#68756c]">
            정산 #{settlement.id}
          </p>
          <h3 className="mt-0.5 text-base font-bold">
            {settlement.auctionHouseName} · {settlement.auctionDate}
          </h3>
        </div>
        <SettlementStatus status={settlement.status} />
      </header>

      <div className="grid grid-cols-2 gap-px border-b border-[#e5e9e3] bg-[#e5e9e3] sm:grid-cols-4">
        <Metric label="총 낙찰액" value={settlement.grossAmount} />
        <Metric label="예상 입금액" value={settlement.expectedDepositAmount} />
        <Metric label="입금액" value={settlement.paidAmount} />
        <Metric label="잔액" value={settlement.remainingAmount} />
      </div>

      <div className="px-4 py-3">
        <div className="mb-2 flex items-center justify-between">
          <h4 className="text-sm font-bold">포함 경매 결과</h4>
          <span className="text-xs text-[#68756c]">
            {settlement.lines.length.toLocaleString()}건
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[620px] text-xs">
            <thead className="bg-[#f7f9f6] text-[#66736a]">
              <tr>
                <th className="px-2 py-2 text-left">출하일</th>
                <th className="px-2 py-2 text-left">품종·등급</th>
                <th className="px-2 py-2 text-right">수량</th>
                <th className="px-2 py-2 text-right">단가</th>
                <th className="px-2 py-2 text-right">금액</th>
              </tr>
            </thead>
            <tbody>
              {settlement.lines.map((line) => (
                <tr key={line.id} className="border-t border-[#edf0ec]">
                  <td className="px-2 py-2">{line.shipmentDate}</td>
                  <td className="px-2 py-2 font-semibold">
                    {line.varietyName} · {line.shipmentGrade || "-"}
                  </td>
                  <td className="px-2 py-2 text-right">
                    {line.quantity.toLocaleString()}분
                  </td>
                  <td className="px-2 py-2 text-right">
                    {line.unitPrice.toLocaleString()}원
                  </td>
                  <td className="px-2 py-2 text-right font-semibold">
                    {line.amount.toLocaleString()}원
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
    </section>
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

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-white px-3 py-3 text-right">
      <p className="text-[11px] text-[#68756c]">{label}</p>
      <p className="mt-1 text-sm font-bold">{value.toLocaleString()}원</p>
    </div>
  );
}

const statusLabels: Record<AuctionSettlementStatus, string> = {
  CREATED: "생성",
  PAYMENT_WAITING: "입금 대기",
  PARTIALLY_PAID: "부분 입금",
  PAID: "정산 완료",
  AMOUNT_MISMATCH: "금액 불일치",
  REVIEW_REQUIRED: "확인 필요",
  CANCELLED: "취소",
};

function SettlementStatus({ status }: { status: AuctionSettlementStatus }) {
  const warning = ["AMOUNT_MISMATCH", "REVIEW_REQUIRED"].includes(status);
  const done = status === "PAID";
  return (
    <span
      className={`inline-flex rounded px-2 py-1 text-[11px] font-semibold ${warning ? "bg-[#fff0ed] text-[#c4473c]" : done ? "bg-[#e8f6ec] text-[#158442]" : "bg-[#fff5df] text-[#a96a00]"}`}
    >
      {statusLabels[status]}
    </span>
  );
}
