import Link from "next/link";
import { AlertTriangle, CircleCheck, Info, TrendingUp } from "lucide-react";
import type { SalesSlip } from "@/entities/farm/types";
import { Panel } from "./AnalyticsCharts";
import { formatWon } from "./AnalyticsSummary";

export function SlipTable({
  title,
  slips,
  unpaid = false,
}: {
  title: string;
  slips: SalesSlip[];
  unpaid?: boolean;
}) {
  return (
    <Panel title={title}>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[520px] text-[11px]">
          <thead className="bg-[#f7f9f6] text-[#536057]">
            <tr>
              {[
                "전표 번호",
                "거래처",
                "판매일",
                "금액",
                ...(unpaid ? ["미입금 금액"] : []),
                "입금 상태",
              ].map((label) => (
                <th className="px-2 py-2 text-left font-semibold" key={label}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {slips.length ? (
              slips.map((slip) => (
                <tr className="border-b border-[#e5e9e5]" key={slip.id}>
                  <td className="px-2 py-2">
                    <Link
                      className="font-semibold text-[#216d3a]"
                      href={`/sales?slipId=${slip.id}`}
                    >
                      {slip.slipNumber}
                    </Link>
                  </td>
                  <td className="px-2 py-2">{slip.customer.name}</td>
                  <td className="px-2 py-2">{slip.saleDate}</td>
                  <td className="px-2 py-2">{formatWon(slip.totalAmount)}</td>
                  {unpaid ? (
                    <td className="px-2 py-2 font-semibold">
                      {formatWon(slip.totalAmount)}
                    </td>
                  ) : null}
                  <td className="px-2 py-2">
                    <PaymentBadge status={slip.paymentStatus} />
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td
                  className="py-10 text-center text-[#758078]"
                  colSpan={unpaid ? 6 : 5}
                >
                  조건에 맞는 전표 없음
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Link
        className="mt-3 flex h-9 items-center justify-center rounded-md border border-[#d7ded8] text-xs font-semibold"
        href={unpaid ? "/sales?paymentStatus=UNPAID" : "/sales"}
      >
        {unpaid ? "전체 미입금 전표 보기" : "전체 전표 보기"}
      </Link>
    </Panel>
  );
}

export function InsightsPanel() {
  const items = [
    {
      icon: TrendingUp,
      tone: "green",
      text: "6월 매출은 전월 대비 18.6% 증가했습니다.",
      action: "",
    },
    {
      icon: AlertTriangle,
      tone: "red",
      text: "3동 2배드 우측에서 상태 이상이 가장 많이 발생했습니다.",
      action: "해당 위치 보기",
    },
    {
      icon: AlertTriangle,
      tone: "orange",
      text: "7개 구역이 45일 이상 농약 작업 기록이 없습니다.",
      action: "누락 구역 보기",
    },
    {
      icon: Info,
      tone: "blue",
      text: "카틀레야 A의 판매가 활발하지만 보유 수량이 부족할 수 있습니다.",
      action: "품종 분석 보기",
    },
    {
      icon: CircleCheck,
      tone: "green",
      text: "이번 달 출하 수량은 전월 대비 12.4% 증가했습니다.",
      action: "",
    },
  ];
  return (
    <Panel title="한눈에 보는 인사이트">
      <div className="mt-2 divide-y divide-[#e5e9e5]">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <div
              className="flex min-h-12 items-center gap-3 py-2"
              key={item.text}
            >
              <span
                className={`grid h-7 w-7 shrink-0 place-items-center rounded-full ${insightTone(item.tone)}`}
              >
                <Icon className="h-4 w-4" />
              </span>
              <p className="min-w-0 flex-1 text-[11px] text-[#4b5750]">
                {item.text}
              </p>
              {item.action ? (
                <Link
                  className="shrink-0 rounded border border-[#d7ded8] px-2 py-1 text-[10px] font-semibold"
                  href="/farm-status"
                >
                  {item.action}
                </Link>
              ) : null}
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

function insightTone(tone: string) {
  return (
    {
      green: "bg-[#eaf7ed] text-[#159447]",
      red: "bg-[#fff0f1] text-[#e04251]",
      orange: "bg-[#fff6e5] text-[#d48a16]",
      blue: "bg-[#edf5ff] text-[#347fd4]",
    }[tone] ?? "bg-[#eef2ef] text-[#526058]"
  );
}

function PaymentBadge({ status }: { status: string }) {
  const paid = ["PAID", "입금완료", "입금 완료"].includes(status);
  const partial = status.includes("부분");
  return (
    <span
      className={`rounded border px-2 py-0.5 text-[10px] font-semibold ${paid ? "border-[#bce2c5] bg-[#edf8ef] text-[#18833d]" : partial ? "border-[#f1d799] bg-[#fff8e6] text-[#ae7412]" : "border-[#f1c0c5] bg-[#fff0f1] text-[#d93e4d]"}`}
    >
      {status}
    </span>
  );
}
