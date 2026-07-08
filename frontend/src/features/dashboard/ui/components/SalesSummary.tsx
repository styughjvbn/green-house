import Link from "next/link";
import type { AnalyticsSlipSummary } from "@/features/analytics/model/types";
import { getPaymentStatusLabel } from "../../lib/dashboardView";
import { DashboardMetric, DashboardPanel } from "./DashboardPanel";

export function SalesSummary({
  salesSlips,
  totalAmount,
  unpaidCount,
}: {
  salesSlips: AnalyticsSlipSummary[];
  totalAmount: number;
  unpaidCount: number;
}) {
  return (
    <DashboardPanel
      title="판매 요약"
      action={
        <span className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold">
          이번 달
        </span>
      }
    >
      <div className="grid grid-cols-3 rounded-md border border-[#edf0ec] text-sm">
        <DashboardMetric label="전표 수" value={`${salesSlips.length}건`} />
        <DashboardMetric
          label="판매 금액"
          value={`${totalAmount.toLocaleString()}원`}
        />
        <DashboardMetric
          label="미입금 전표"
          value={`${unpaidCount}건`}
          warning
        />
      </div>
      <div className="mt-3 divide-y divide-[#edf0ec]">
        {salesSlips.map((slip) => (
          <Link
            key={slip.id}
            className="grid grid-cols-[1fr_1fr_90px_56px] items-center gap-2 py-2 text-sm"
            href="/sales"
          >
            <span className="font-semibold text-[#344138]">
              {slip.slipNumber}
            </span>
            <span className="truncate text-[#5c6a60]">{slip.partnerName}</span>
            <span className="text-right font-bold">
              {slip.totalAmount.toLocaleString()}원
            </span>
            <span
              className={`rounded-md px-2 py-1 text-center text-xs font-bold ${
                slip.paymentStatus === "미입금"
                  ? "bg-[#fff1d6] text-[#d88400]"
                  : "bg-[#e7f7e8] text-[#16853b]"
              }`}
            >
              {getPaymentStatusLabel(slip)}
            </span>
          </Link>
        ))}
      </div>
    </DashboardPanel>
  );
}
