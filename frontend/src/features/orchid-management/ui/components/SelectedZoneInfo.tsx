"use client";

import { useState } from "react";
import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
} from "@/entities/farm/types";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type { OrchidSelection, WorkRecordSummary } from "../../model/types";
import InfoMetric from "./InfoMetric";

export default function SelectedZoneInfo({
  house,
  selectedBedZone,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
  workRecordSummary,
  workRecordSummaryLoading,
}: {
  house: House;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: PhysicalBed | null;
  selection: OrchidSelection | null;
  workRecordSummary: WorkRecordSummary;
  workRecordSummaryLoading: boolean;
}) {
  const zone = selectedOrchidGroup
    ? (findBedZone(house, selectedOrchidGroup.bedZoneId)?.zone ?? null)
    : selectedBedZone;
  const selectedHouse = selection?.type === "HOUSE";
  const physicalBed = zone
    ? (house.physicalBeds.find((bed) => bed.id === zone.physicalBedId) ?? null)
    : selectedPhysicalBed;
  const targetLabel = selectedOrchidGroup
    ? "선택한 난 묶음"
    : zone
      ? "선택한 구역"
      : physicalBed
        ? "선택한 다이"
        : selectedHouse
          ? "선택한 동"
          : null;
  const targetName = selectedOrchidGroup
    ? selectedOrchidGroup.varietyName
    : zone
      ? zone.name
      : physicalBed
        ? `${physicalBed.number}다이`
        : selectedHouse
          ? `${house.number}동`
          : null;
  const targetContext = zone
    ? `${zone.houseNumber}동 ${zone.physicalBedNumber}다이`
    : physicalBed
      ? `${house.number}동`
      : selectedHouse
        ? house.name
        : null;
  const targetGroups = selectedOrchidGroup
    ? [selectedOrchidGroup]
    : zone
      ? zone.orchidGroups
      : physicalBed
        ? physicalBed.bedZones.flatMap((bedZone) => bedZone.orchidGroups)
        : selectedHouse
          ? house.physicalBeds.flatMap((bed) =>
              bed.bedZones.flatMap((bedZone) => bedZone.orchidGroups),
            )
          : [];
  const totalQuantity =
    targetGroups.reduce((sum, orchidGroup) => sum + orchidGroup.quantity, 0) ??
    0;
  const zoneCount = physicalBed
    ? physicalBed.bedZones.length
    : selectedHouse
      ? house.physicalBeds.reduce((sum, bed) => sum + bed.bedZones.length, 0)
      : zone
        ? 1
        : 0;

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-2.5 shadow-sm">
      {targetLabel && targetName ? (
        <div className="flex flex-col gap-1 lg:flex-row lg:items-center">
          <div className="shrink-0 lg:w-44">
            <p className="text-xs font-semibold text-[#6f7b72]">
              {targetLabel}
            </p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              {targetContext ? (
                <span className="text-sm font-semibold text-[#5c6a60]">
                  {targetContext}
                </span>
              ) : null}
              <span className="rounded-md bg-[#e6f0ff] px-2 py-1 text-sm font-semibold text-[#246df2]">
                {targetName}
              </span>
            </div>
          </div>

          <div className="grid shrink-0 grid-cols-4 gap-1 text-center lg:w-60">
            <InfoMetric label="난 묶음 수" value={`${targetGroups.length}개`} />
            <InfoMetric label="총 수량" value={`${totalQuantity}분`} />
            <InfoMetric
              label={zone || selectedOrchidGroup ? "구역 수" : "하위 구역"}
              value={`${zoneCount}개`}
            />
            <InfoMetric label="상태" value="정상" />
          </div>

          <div className="min-w-0 flex-1 border-t border-[#e1e6df] pt-2 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-3">
            <WorkRecordSummaryView
              loading={workRecordSummaryLoading}
              summary={workRecordSummary}
            />
          </div>
        </div>
      ) : (
        <p className="text-sm text-[#5c6a60]">
          구역 또는 난 묶음을 선택하세요.
        </p>
      )}
    </section>
  );
}

function WorkRecordSummaryView({
  loading,
  summary,
}: {
  loading: boolean;
  summary: WorkRecordSummary;
}) {
  const [showTypeSummary, setShowTypeSummary] = useState(false);

  if (loading) {
    return (
      <p className="mt-1 rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">
        최근 작업 확인 중
      </p>
    );
  }

  const summaryRows = (
    <>
      <SummaryRow label="농약" record={summary.latestByType.pesticide} />
      <SummaryRow label="비료" record={summary.latestByType.fertilizer} />
      <SummaryRow label="분갈이" record={summary.latestByType.repot} />
    </>
  );

  return (
    <div className="relative mt-1 grid gap-2 xl:grid-cols-[210px_minmax(0,1fr)]">
      <dl className="hidden grid-cols-3 gap-1 text-[11px] text-[#5c6a60] xl:grid">
        {summaryRows}
      </dl>

      {showTypeSummary ? (
        <dl className="absolute right-0 bottom-full z-20 mb-2 grid w-56 grid-cols-3 gap-1 rounded-md border border-[#d7ddd4] bg-white p-2 text-[11px] text-[#5c6a60] shadow-lg xl:hidden">
          {summaryRows}
        </dl>
      ) : null}

      <button
        className="min-w-0 text-left xl:contents"
        type="button"
        onClick={() => setShowTypeSummary((current) => !current)}
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) {
            setShowTypeSummary(false);
          }
        }}
      >
        <RecentWorkList records={summary.latestRecords.slice(0, 2)} />
      </button>
    </div>
  );
}

function RecentWorkList({ records }: { records: WorkRecord[] }) {
  if (records.length === 0) {
    return (
      <p className="rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">
        등록된 작업 기록 없음
      </p>
    );
  }

  return (
    <ul className={"space-y-1"}>
      {records.map((record) => (
        <li
          key={record.id}
          className={`"px-2 py-1.5" rounded-md border border-[#e1e6df] bg-[#fbfcfa] text-xs`}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="truncate font-semibold text-[#17251b]">
              {record.workType}
            </span>
            <span className="shrink-0 text-[#5c6a60]">
              {formatShortDate(record.workDate)}
            </span>
          </div>
        </li>
      ))}
    </ul>
  );
}

function SummaryRow({
  label,
  record,
}: {
  label: string;
  record: WorkRecord | null;
}) {
  return (
    <div className="rounded-md bg-[#f5f7f3] px-2 py-1">
      <dt>{label}</dt>
      <dd className="mt-0.5 truncate font-semibold text-[#17251b]">
        {record ? formatShortDate(record.workDate) : "없음"}
      </dd>
    </div>
  );
}

function formatShortDate(value: string) {
  const [year, month, day] = value.split("-");

  if (!year || !month || !day) {
    return value;
  }

  return `${year.slice(-2)}${month}${day}`;
}
