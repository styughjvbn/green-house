import Link from "next/link";
import type {
  AnalyticsPageProps,
  WorkAnalyticsData,
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
                href="/sales/partners"
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
              href="/sales/partners"
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
  const houseSpaceStats = props.houses
    .map((house) => {
      const totalZones = house.physicalBeds.reduce(
        (sum, bed) => sum + bed.bedZones.length,
        0,
      );
      const usedZones = house.physicalBeds.reduce(
        (sum, bed) =>
          sum +
          bed.bedZones.filter((zone) => zone.orchidGroups.length > 0).length,
        0,
      );
      const orchidGroupCount = house.physicalBeds.reduce(
        (sum, bed) =>
          sum +
          bed.bedZones.reduce(
            (zoneSum, zone) => zoneSum + zone.orchidGroups.length,
            0,
          ),
        0,
      );
      const summary = props.mapData.houses.find(
        (item) => item.houseId === house.id,
      );
      return {
        houseId: house.id,
        houseNumber: house.number,
        totalZones,
        usedZones,
        emptyZones: Math.max(totalZones - usedZones, 0),
        orchidGroupCount,
        usageRate:
          totalZones > 0 ? Math.round((usedZones / totalZones) * 100) : 0,
        warningCount: summary?.warningCount ?? 0,
        latestWorkDate: summary?.latestWorkDate ?? null,
      };
    })
    .sort((left, right) => left.houseNumber - right.houseNumber);
  const totalZones = houseSpaceStats.reduce(
    (sum, house) => sum + house.totalZones,
    0,
  );
  const usedZones = houseSpaceStats.reduce(
    (sum, house) => sum + house.usedZones,
    0,
  );
  const emptyZones = Math.max(totalZones - usedZones, 0);
  const highestUsageHouse = [...houseSpaceStats].sort(
    (left, right) => right.usageRate - left.usageRate,
  )[0];
  const mostWarningsHouse = [...houseSpaceStats].sort(
    (left, right) => right.warningCount - left.warningCount,
  )[0];
  return (
    <div className="grid gap-3 xl:grid-cols-[1.25fr_0.75fr]">
      <Panel title="동별 공간 사용 현황">
        <div className="mt-3 grid gap-2 sm:grid-cols-3 lg:grid-cols-5">
          {houseSpaceStats.map((house) => {
            return (
              <Link
                className="rounded-md border border-[#dce2dc] p-3 hover:border-[#159447]"
                href={`/farm-status?houseId=${house.houseId}`}
                key={house.houseId}
              >
                <div className="flex justify-between text-xs">
                  <strong>{house.houseNumber}동</strong>
                  <span>{house.usageRate}%</span>
                </div>
                <div className="mt-3 h-2 overflow-hidden rounded bg-[#edf0ed]">
                  <div
                    className="h-full bg-[#49a760]"
                    style={{ width: `${house.usageRate}%` }}
                  />
                </div>
                <p className="mt-2 text-[10px] text-[#68746d]">
                  사용 {house.usedZones}/{house.totalZones} · 빈 구역{" "}
                  {house.emptyZones}
                </p>
              </Link>
            );
          })}
        </div>
      </Panel>
      <Panel title="공간 요약">
        <div className="mt-3 space-y-3">
          <Metric label="전체 논리 구역" value={`${totalZones}개`} />
          <Metric label="사용 중 구역" value={`${usedZones}개`} />
          <Metric label="빈 구역" value={`${emptyZones}개`} />
          <Metric label="상태 이상" value={`${props.summary.warningCount}건`} />
          <Metric
            label="최고 사용 동"
            value={
              highestUsageHouse
                ? `${highestUsageHouse.houseNumber}동 ${highestUsageHouse.usageRate}%`
                : "-"
            }
          />
          <Metric
            label="주의 집중 동"
            value={
              mostWarningsHouse && mostWarningsHouse.warningCount > 0
                ? `${mostWarningsHouse.houseNumber}동 ${mostWarningsHouse.warningCount}건`
                : "-"
            }
          />
          <Link
            className="flex h-9 items-center justify-center rounded-md bg-[#159447] text-xs font-semibold text-white"
            href="/orchid-groups"
          >
            난 묶음 관리 보기
          </Link>
        </div>
      </Panel>
    </div>
  );
}

function WorkTab({ props }: { props: AnalyticsPageProps }) {
  const workAnalytics = props.workAnalytics;
  const values = workAnalytics?.workTypeCounts ?? [];
  const sortedRecords = workAnalytics?.recentRecords ?? [];
  const movementCount = workAnalytics?.movementCount ?? 0;
  const statusCount = workAnalytics?.statusCount ?? 0;
  const latestWorkDate = workAnalytics?.latestWorkDate ?? null;
  const reviewHouses = props.mapData.houses
    .filter((house) => requiresReview(house.latestWorkDate))
    .sort((left, right) => {
      const leftDate = left.latestWorkDate ?? "";
      const rightDate = right.latestWorkDate ?? "";
      return leftDate.localeCompare(rightDate);
    });
  const warningHouses = [...props.mapData.houses]
    .filter((house) => house.warningCount > 0)
    .sort((left, right) => right.warningCount - left.warningCount)
    .slice(0, 5);

  return (
    <div className="space-y-3">
      <div className="grid gap-3 xl:grid-cols-[0.78fr_1.22fr]">
        <RankingChart
          title="작업 유형별 실행 건수"
          values={
            values.length ? values : [{ label: "작업 기록 없음", value: 0 }]
          }
        />
        <Panel title="작업 현황 요약">
          <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
            <Metric
              label="전체 작업 기록"
              value={`${workAnalytics?.totalCount ?? 0}건`}
            />
            <Metric label="최근 작업일" value={latestWorkDate ?? "-"} />
            <Metric label="자리 이동 기록" value={`${movementCount}건`} />
            <Metric label="상태 기록" value={`${statusCount}건`} />
          </div>
          <div className="mt-3 grid gap-3 xl:grid-cols-[1fr_1fr]">
            <div className="rounded-md border border-[#e1e6e1] p-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold">점검 필요 동</h3>
                <span className="text-[11px] text-[#d07b15]">
                  {reviewHouses.length}개
                </span>
              </div>
              <div className="mt-2 space-y-2">
                {reviewHouses.length ? (
                  reviewHouses.slice(0, 5).map((house) => (
                    <Link
                      className="flex items-center justify-between rounded-md bg-[#fbfcfa] px-3 py-2 text-xs hover:bg-[#f3f9f3]"
                      href={`/farm-status?houseId=${house.houseId}`}
                      key={house.houseId}
                    >
                      <span>{house.houseNumber}동</span>
                      <span className="text-[#6b776f]">
                        {house.latestWorkDate ?? "작업 기록 없음"}
                      </span>
                    </Link>
                  ))
                ) : (
                  <p className="rounded-md bg-[#fbfcfa] px-3 py-2 text-xs text-[#6b776f]">
                    최근 작업 누락 동 없음
                  </p>
                )}
              </div>
            </div>
            <div className="rounded-md border border-[#e1e6e1] p-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold">상태 이상 동</h3>
                <span className="text-[11px] text-[#d63c50]">
                  {props.summary.warningCount}건
                </span>
              </div>
              <div className="mt-2 space-y-2">
                {warningHouses.length ? (
                  warningHouses.map((house) => (
                    <Link
                      className="flex items-center justify-between rounded-md bg-[#fbfcfa] px-3 py-2 text-xs hover:bg-[#fdf4f5]"
                      href={`/farm-status?houseId=${house.houseId}`}
                      key={house.houseId}
                    >
                      <span>{house.houseNumber}동</span>
                      <span className="font-semibold text-[#d63c50]">
                        이상 {house.warningCount}건
                      </span>
                    </Link>
                  ))
                ) : (
                  <p className="rounded-md bg-[#fbfcfa] px-3 py-2 text-xs text-[#6b776f]">
                    상태 이상 동 없음
                  </p>
                )}
              </div>
            </div>
          </div>
        </Panel>
      </div>
      <Panel title="최근 작업 기록">
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[760px] text-xs">
            <thead className="bg-[#f7f9f6]">
              <tr>
                {[
                  "작업일",
                  "작업 유형",
                  "대상",
                  "작업 내용",
                  "작업자",
                  "메모",
                ].map((label) => (
                  <th className="px-3 py-2 text-left" key={label}>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {sortedRecords.slice(0, 10).map((record) => (
                <tr className="border-b border-[#e5e9e5]" key={record.id}>
                  <td className="px-3 py-2">{record.workDate}</td>
                  <td className="px-3 py-2 font-semibold">{record.workType}</td>
                  <td className="px-3 py-2">
                    {formatTargetType(record.sourceScopeType)}
                  </td>
                  <td className="px-3 py-2">{formatWorkContent(record)}</td>
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
          href="/work-records/history"
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

function requiresReview(latestWorkDate: string | null) {
  if (!latestWorkDate) return true;
  const latest = new Date(`${latestWorkDate}T00:00:00`);
  const threshold = new Date();
  threshold.setDate(threshold.getDate() - 30);
  return latest < threshold;
}

function formatTargetType(
  targetType: WorkAnalyticsData["recentRecords"][number]["sourceScopeType"],
) {
  return (
    {
      HOUSE: "동",
      PHYSICAL_BED: "다이",
      BED_ZONE: "구역",
      ORCHID_GROUP: "난 묶음",
      FARM: "농장",
      NONE: "대상 없음",
      DERIVED_GROUP: "자동 그룹",
      USER_COLLECTION: "사용자 그룹",
      MANUAL_SELECTION: "직접 선택",
      INBOUND_RECORD_SELECTION: "입고 기록",
    }[targetType] ?? targetType
  );
}

function formatWorkContent(record: WorkAnalyticsData["recentRecords"][number]) {
  return record.title || "-";
}
