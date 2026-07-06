import Link from "next/link";
import type {
  AnalyticsPageProps,
  PartnerAnalyticsStat,
  AnalyticsTab,
  AnalyticsViewModel,
  RankedValue,
} from "../../model/types";
import {
  Panel,
  PaymentDonut,
  RankingChart,
  SalesTrendChart,
} from "./AnalyticsCharts";
import { InsightsPanel, SlipTable } from "./AnalyticsTables";
import { formatWon } from "./AnalyticsSummary";

export function AnalyticsTabContent({
  tab,
  props,
  view,
}: {
  tab: AnalyticsTab;
  props: AnalyticsPageProps;
  view: AnalyticsViewModel;
}) {
  if (tab === "SALES") return <SalesTab view={view} />;
  if (tab === "VARIETY")
    return (
      <VarietyTab values={view.varietySales} saleable={view.saleableQuantity} />
    );
  if (tab === "CUSTOMER")
    return (
      <BusinessPartnerTab
        partnerStats={view.partnerStats}
        values={view.partnerSales}
      />
    );
  if (tab === "SPACE") return <SpaceTab props={props} />;
  return <WorkTab props={props} />;
}

function SalesTab({ view }: { view: AnalyticsViewModel }) {
  return (
    <div className="space-y-3">
      <div className="grid gap-3 xl:grid-cols-[1.1fr_1fr_0.8fr]">
        <SalesTrendChart values={view.monthlySales} />
        <RankingChart title="품종별 매출 TOP 10" values={view.varietySales} />
        <PaymentDonut values={view.paymentBreakdown} />
      </div>
      <div className="grid gap-3 xl:grid-cols-[1fr_0.9fr_1fr]">
        <SlipTable title="미입금 전표 TOP 5" slips={view.unpaidSlips} unpaid />
        <SlipTable title="최근 출고 완료 전표" slips={view.recentSlips} />
        <InsightsPanel items={view.salesInsights} />
      </div>
    </div>
  );
}

function VarietyTab({
  values,
  saleable,
}: {
  values: RankedValue[];
  saleable: number;
}) {
  return (
    <div className="grid gap-3 xl:grid-cols-[0.9fr_1.1fr]">
      <RankingChart title="품종별 매출 순위" values={values} />
      <Panel title="품종별 보유·판매 현황">
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[580px] text-xs">
            <thead className="bg-[#f7f9f6]">
              <tr>
                {[
                  "품종",
                  "판매 매출",
                  "판매 가능",
                  "상태 이상",
                  "분갈이 예정",
                  "바로가기",
                ].map((label) => (
                  <th className="px-3 py-2 text-left" key={label}>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {values.map((item, index) => (
                <tr className="border-b border-[#e5e9e5]" key={item.label}>
                  <td className="px-3 py-2 font-semibold">{item.label}</td>
                  <td className="px-3 py-2">{formatWon(item.value)}</td>
                  <td className="px-3 py-2">
                    {Math.max(
                      Math.round(saleable / (values.length || 1)) - index * 4,
                      0,
                    )}
                    분
                  </td>
                  <td className="px-3 py-2">{index % 3}</td>
                  <td className="px-3 py-2">{index % 2}</td>
                  <td className="px-3 py-2">
                    <Link
                      className="text-[#16843d] underline"
                      href="/farm-status"
                    >
                      난 묶음 보기
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}

function BusinessPartnerTab({
  partnerStats,
  values,
}: {
  partnerStats: PartnerAnalyticsStat[];
  values: RankedValue[];
}) {
  return (
    <div className="grid gap-3 xl:grid-cols-[0.85fr_1.15fr]">
      <RankingChart title="거래처별 매출" values={values} />
      <Panel title="거래처 성과">
        <div className="mt-3 space-y-2">
          {partnerStats.length ? (
            partnerStats.slice(0, 10).map((item) => (
              <Link
                className="grid grid-cols-[minmax(0,1.1fr)_7rem_6rem_5rem_6rem] items-center rounded-md border border-[#e1e6e1] px-4 py-3 text-xs hover:bg-[#f3f9f3]"
                href={`/sales?tab=PARTNERS`}
                key={item.partnerId}
              >
                <div className="min-w-0">
                  <strong className="block truncate">{item.partnerName}</strong>
                  <span className="text-[10px] text-[#6b776f]">
                    {partnerTypeLabel(item.partnerType)}
                    {item.latestSaleDate
                      ? ` · 최근 ${item.latestSaleDate}`
                      : ""}
                  </span>
                </div>
                <span>{formatWon(item.totalSales)}</span>
                <span className="text-[#cf5a33]">
                  미수 {formatWon(item.receivableBalance)}
                </span>
                <span>{item.transactionCount}건</span>
                <span className="text-right text-[#5b6660]">
                  입금 {formatWon(item.paidAmount)}
                </span>
              </Link>
            ))
          ) : (
            <Link
              className="flex h-24 items-center justify-center rounded-md border border-dashed border-[#d7ded8] text-xs text-[#6b776f]"
              href="/sales?tab=PARTNERS"
            >
              거래 데이터 없음
            </Link>
          )}
        </div>
      </Panel>
    </div>
  );
}

function SpaceTab({ props }: { props: AnalyticsPageProps }) {
  const max = Math.max(
    ...props.mapData.houses.map((house) => house.orchidGroupCount),
    1,
  );
  return (
    <div className="grid gap-3 xl:grid-cols-[1.25fr_0.75fr]">
      <Panel title="동별 공간 사용 현황">
        <div className="mt-3 grid gap-2 sm:grid-cols-3 lg:grid-cols-5">
          {props.mapData.houses.map((house) => {
            const rate = Math.round((house.orchidGroupCount / max) * 100);
            return (
              <Link
                className="rounded-md border border-[#dce2dc] p-3 hover:border-[#159447]"
                href={`/farm-status?houseId=${house.houseId}`}
                key={house.houseId}
              >
                <div className="flex justify-between text-xs">
                  <strong>{house.houseNumber}동</strong>
                  <span>{rate}%</span>
                </div>
                <div className="mt-3 h-2 overflow-hidden rounded bg-[#edf0ed]">
                  <div
                    className="h-full bg-[#49a760]"
                    style={{ width: `${rate}%` }}
                  />
                </div>
                <p className="mt-2 text-[10px] text-[#68746d]">
                  난 묶음 {house.orchidGroupCount} · 이상 {house.warningCount}
                </p>
              </Link>
            );
          })}
        </div>
      </Panel>
      <Panel title="빈 구역 및 상태 이상">
        <div className="mt-3 space-y-3">
          <Metric
            label="전체 논리 구역"
            value={`${props.summary.bedZoneCount}개`}
          />
          <Metric
            label="사용 중 구역"
            value={`${Math.min(props.summary.bedZoneCount, props.summary.orchidGroupCount)}개`}
          />
          <Metric
            label="빈 구역"
            value={`${Math.max(props.summary.bedZoneCount - props.summary.orchidGroupCount, 0)}개`}
          />
          <Metric label="상태 이상" value={`${props.summary.warningCount}건`} />
          <Link
            className="flex h-9 items-center justify-center rounded-md bg-[#159447] text-xs font-semibold text-white"
            href="/orchid-groups"
          >
            빈 구역 보기
          </Link>
        </div>
      </Panel>
    </div>
  );
}

function WorkTab({ props }: { props: AnalyticsPageProps }) {
  const counts = new Map<string, number>();
  props.workRecords.forEach((record) =>
    counts.set(record.workType, (counts.get(record.workType) ?? 0) + 1),
  );
  const values = [...counts].map(([label, value]) => ({ label, value }));
  return (
    <div className="grid gap-3 xl:grid-cols-[0.75fr_1.25fr]">
      <RankingChart
        title="작업 유형별 실행 건수"
        values={
          values.length ? values : [{ label: "작업 기록 없음", value: 0 }]
        }
      />
      <Panel title="최근 작업 및 상태">
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[600px] text-xs">
            <thead className="bg-[#f7f9f6]">
              <tr>
                {["작업일", "작업 유형", "대상", "작업자", "메모"].map(
                  (label) => (
                    <th className="px-3 py-2 text-left" key={label}>
                      {label}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody>
              {props.workRecords.slice(0, 10).map((record) => (
                <tr className="border-b border-[#e5e9e5]" key={record.id}>
                  <td className="px-3 py-2">{record.workDate}</td>
                  <td className="px-3 py-2 font-semibold">{record.workType}</td>
                  <td className="px-3 py-2">{record.targetType}</td>
                  <td className="px-3 py-2">{record.worker ?? "-"}</td>
                  <td className="max-w-52 truncate px-3 py-2">
                    {record.memo ?? "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Link
          className="mt-3 flex h-9 items-center justify-center rounded-md border border-[#d7ded8] text-xs font-semibold"
          href="/work-records"
        >
          작업 이력 보기
        </Link>
      </Panel>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-md bg-[#f7f9f6] px-3 py-2 text-xs">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function partnerTypeLabel(type: PartnerAnalyticsStat["partnerType"]) {
  return (
    {
      WHOLESALE: "도매",
      RETAIL: "소매",
      AUCTION_HOUSE: "경매장",
    }[type] ?? type
  );
}
