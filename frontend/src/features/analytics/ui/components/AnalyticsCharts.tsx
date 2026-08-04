"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { RankedValue } from "../../model/types";
import { formatWon } from "./AnalyticsSummary";

const COLORS = ["#58b66f", "#f3bf58", "#ef8995"];

export function SalesTrendChart({ values }: { values: RankedValue[] }) {
  return (
    <Panel title="월별 매출 추이">
      <div className="mt-3 h-56">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={values} margin={{ top: 8, right: 12, left: 4 }}>
            <CartesianGrid stroke="#e5e9e5" strokeDasharray="3 3" />
            <XAxis dataKey="label" tick={{ fontSize: 11 }} />
            <YAxis
              tick={{ fontSize: 10 }}
              tickFormatter={(value: number) => compactNumber(value)}
              width={48}
            />
            <Tooltip
              formatter={(value) => [formatWon(Number(value)), "매출"]}
              contentStyle={{ borderRadius: 6, fontSize: 12 }}
            />
            <Line
              type="monotone"
              dataKey="value"
              name="매출"
              stroke="#159447"
              strokeWidth={2.5}
              dot={{ r: 3, fill: "#159447" }}
              activeDot={{ r: 5 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  );
}

export function RankingChart({
  title,
  values,
  unit = "원",
}: {
  title: string;
  values: RankedValue[];
  unit?: "원" | "건" | "분";
}) {
  const data = values.slice(0, 10);
  return (
    <Panel title={title} action={`단위: ${unit}`}>
      {data.length ? (
        <div className="mt-3 h-56">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={data}
              layout="vertical"
              margin={{ top: 2, right: 18, bottom: 2, left: 12 }}
            >
              <CartesianGrid stroke="#edf0ed" strokeDasharray="3 3" />
              <XAxis
                type="number"
                tick={{ fontSize: 10 }}
                tickFormatter={(value: number) => compactNumber(value)}
              />
              <YAxis
                dataKey="label"
                type="category"
                tick={{ fontSize: 10 }}
                width={76}
              />
              <Tooltip
                formatter={(value) => [formatValue(Number(value), unit), title]}
                contentStyle={{ borderRadius: 6, fontSize: 12 }}
              />
              <Bar dataKey="value" fill="#57ad69" radius={[0, 3, 3, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <EmptyChart />
      )}
    </Panel>
  );
}

export function PaymentDonut({ values }: { values: RankedValue[] }) {
  const total = values.reduce((sum, item) => sum + item.value, 0);
  return (
    <Panel title="입금 상태별 전표 금액">
      {total > 0 ? (
        <div className="relative mt-3 h-56">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={values}
                dataKey="value"
                nameKey="label"
                innerRadius="50%"
                outerRadius="75%"
                paddingAngle={2}
              >
                {values.map((item, index) => (
                  <Cell fill={COLORS[index % COLORS.length]} key={item.label} />
                ))}
              </Pie>
              <Tooltip
                formatter={(value) => formatWon(Number(value))}
                contentStyle={{ borderRadius: 6, fontSize: 12 }}
              />
              <Legend
                formatter={(value) => (
                  <span className="text-[11px] text-[#4b5750]">{value}</span>
                )}
              />
            </PieChart>
          </ResponsiveContainer>
          <strong className="pointer-events-none absolute top-[43%] left-1/2 -translate-x-1/2 text-xs">
            {formatWon(total)}
          </strong>
        </div>
      ) : (
        <EmptyChart />
      )}
    </Panel>
  );
}

export function Panel({
  title,
  action,
  children,
}: {
  title: string;
  action?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold">{title}</h2>
        {action ? (
          <span className="shrink-0 text-[10px] text-[#68746d]">{action}</span>
        ) : null}
      </div>
      {children}
    </section>
  );
}

function EmptyChart() {
  return (
    <div className="mt-3 grid h-56 place-items-center rounded-md border border-dashed border-[#d7ded8] text-xs text-[#758078]">
      조건에 맞는 데이터가 없습니다.
    </div>
  );
}

function compactNumber(value: number) {
  return new Intl.NumberFormat("ko-KR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

function formatValue(value: number, unit: "원" | "건" | "분") {
  return unit === "원" ? formatWon(value) : `${value.toLocaleString()}${unit}`;
}
