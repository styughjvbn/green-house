import type { RankedValue } from "../../model/types";
import { formatWon } from "./AnalyticsSummary";

export function SalesTrendChart({ values }: { values: RankedValue[] }) {
  const max = Math.max(...values.map((item) => item.value), 1);
  const points = values
    .map((item, index) => `${8 + index * 18.4},${88 - (item.value / max) * 68}`)
    .join(" ");
  return (
    <Panel title="월별 매출 추이">
      <div className="mt-2 flex items-center gap-4 text-[10px] text-[#68746d]">
        <Legend color="#159447" label="매출(원)" />
      </div>
      <div className="relative mt-2 h-48">
        <div className="absolute inset-0 flex flex-col justify-between">
          {[
            "5,000,000",
            "4,000,000",
            "3,000,000",
            "2,000,000",
            "1,000,000",
            "0",
          ].map((label) => (
            <div
              className="flex items-center gap-2 text-[10px] text-[#748078]"
              key={label}
            >
              <span className="w-12 text-right">{label}</span>
              <span className="h-px flex-1 bg-[#e5e9e5]" />
            </div>
          ))}
        </div>
        <svg
          className="absolute top-0 right-0 bottom-5 left-14 h-[calc(100%-1.25rem)] w-[calc(100%-3.5rem)]"
          preserveAspectRatio="none"
          viewBox="0 0 100 100"
          aria-label="월별 매출 선 그래프"
        >
          <polyline
            fill="none"
            points={points}
            stroke="#159447"
            strokeWidth="2"
          />
          {values.map((item, index) => (
            <circle
              cx={8 + index * 18.4}
              cy={88 - (item.value / max) * 68}
              fill="#159447"
              key={item.label}
              r="1.8"
            />
          ))}
        </svg>
        <div className="absolute right-0 bottom-0 left-14 flex justify-between text-[10px] text-[#68746d]">
          {values.map((item) => (
            <span key={item.label}>{item.label}</span>
          ))}
        </div>
      </div>
    </Panel>
  );
}

export function RankingChart({
  title,
  values,
}: {
  title: string;
  values: RankedValue[];
}) {
  const max = Math.max(...values.map((item) => item.value), 1);
  return (
    <Panel title={title} action="단위: 원">
      <div className="mt-3 space-y-2">
        {values.slice(0, 8).map((item) => (
          <div
            className="grid grid-cols-[5rem_minmax(0,1fr)_4.5rem] items-center gap-2 text-[11px]"
            key={item.label}
          >
            <span className="truncate text-right">{item.label}</span>
            <div className="h-3 bg-[#f0f3ef]">
              <div
                className="h-full bg-[#57ad69]"
                style={{ width: `${Math.max((item.value / max) * 100, 4)}%` }}
              />
            </div>
            <span>{item.value.toLocaleString()}</span>
          </div>
        ))}
      </div>
    </Panel>
  );
}

export function PaymentDonut({ values }: { values: RankedValue[] }) {
  const total = values.reduce((sum, item) => sum + item.value, 0) || 1;
  const first = ((values[0]?.value ?? 0) / total) * 100;
  const second = first + ((values[1]?.value ?? 0) / total) * 100;
  return (
    <Panel title="입금 상태별 금액 비율">
      <div className="mt-5 flex items-center justify-center gap-6">
        <div
          className="relative h-32 w-32 shrink-0 rounded-full"
          style={{
            background: `conic-gradient(#58b66f 0 ${first}%, #f3bf58 ${first}% ${second}%, #ef8995 ${second}% 100%)`,
          }}
        >
          <div className="absolute inset-8 rounded-full bg-white" />
        </div>
        <div className="min-w-0 space-y-3">
          {values.map((item, index) => (
            <div
              className="flex items-center gap-2 text-[11px]"
              key={item.label}
            >
              <span
                className={`h-2.5 w-2.5 rounded-sm ${["bg-[#58b66f]", "bg-[#f3bf58]", "bg-[#ef8995]"][index]}`}
              />
              <span className="flex-1">{item.label}</span>
              <strong>{formatWon(item.value)}</strong>
            </div>
          ))}
          <div className="border-t border-[#dde2dd] pt-2 text-xs">
            <span>합계</span>
            <strong className="float-right">{formatWon(total)}</strong>
          </div>
        </div>
      </div>
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
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-bold">{title}</h2>
        {action ? (
          <span className="text-[10px] text-[#68746d]">{action}</span>
        ) : null}
      </div>
      {children}
    </section>
  );
}
function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1">
      <i className="h-0.5 w-5" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
