"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type {
  BedZone,
  House,
  OrchidGroup,
  OrchidGroupWorkHistory,
  PhysicalBed,
} from "@/entities/farm/types";
import { findBedZone } from "../../lib/orchidManagementUtils";
import type {
  OrchidGroupLineage,
  OrchidGroupLineageItem,
  OrchidGroupLineageRelationType,
  OrchidSelection,
  WorkRecordSummary,
} from "../../model/types";
import InfoMetric from "./InfoMetric";

export default function SelectedZoneInfo({
  house,
  selectedBedZone,
  selectedOrchidGroup,
  selectedPhysicalBed,
  selection,
  workRecordSummary,
  workRecordSummaryLoading,
  orchidGroupHistory,
  orchidGroupHistoryLoading,
  orchidGroupLineage,
  orchidGroupLineageLoading,
  onOpenCorrection,
}: {
  house: House;
  selectedBedZone: BedZone | null;
  selectedOrchidGroup: OrchidGroup | null;
  selectedPhysicalBed: PhysicalBed | null;
  selection: OrchidSelection | null;
  workRecordSummary: WorkRecordSummary;
  workRecordSummaryLoading: boolean;
  orchidGroupHistory: OrchidGroupWorkHistory[];
  orchidGroupHistoryLoading: boolean;
  orchidGroupLineage: OrchidGroupLineage | null;
  orchidGroupLineageLoading: boolean;
  onOpenCorrection: (workOperationId: number) => void;
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

          <div
            className={`grid shrink-0 gap-1 text-center ${
              selectedOrchidGroup
                ? "grid-cols-2 lg:w-32"
                : "grid-cols-4 lg:w-60"
            }`}
          >
            {!selectedOrchidGroup ? (
              <InfoMetric
                label="난 묶음 수"
                value={`${targetGroups.length}개`}
              />
            ) : null}
            <InfoMetric label="총 수량" value={`${totalQuantity}분`} />
            {!selectedOrchidGroup ? (
              <InfoMetric
                label={zone ? "구역 수" : "하위 구역"}
                value={`${zoneCount}개`}
              />
            ) : null}
            <InfoMetric
              label="상태"
              value={selectedOrchidGroup?.status ?? "정상"}
            />
          </div>

          <div className="min-w-0 flex-1 border-t border-[#e1e6df] pt-2 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-3">
            {selectedOrchidGroup ? (
              <OrchidGroupActivityView
                history={orchidGroupHistory}
                loading={orchidGroupHistoryLoading}
                lineage={orchidGroupLineage}
                lineageLoading={orchidGroupLineageLoading}
                onOpenCorrection={onOpenCorrection}
              />
            ) : (
              <WorkRecordSummaryView
                loading={workRecordSummaryLoading}
                summary={workRecordSummary}
              />
            )}
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

function OrchidGroupLineageDetail({
  lineage,
  loading,
}: {
  lineage: OrchidGroupLineage | null;
  loading: boolean;
}) {
  const sources = lineage?.sources ?? [];
  const results = lineage?.results ?? [];

  return (
    <div className="mt-3 border-t border-[#e1e6df] pt-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-[#344138]">난 묶음 계보</h3>
        {!loading ? (
          <span className="text-xs text-[#6f7b72]">
            원본 {sources.length} · 결과 {results.length}
          </span>
        ) : null}
      </div>
      {loading ? (
        <p className="mt-2 text-xs text-[#5c6a60]">계보 확인 중</p>
      ) : sources.length === 0 && results.length === 0 ? (
        <p className="mt-2 text-xs text-[#5c6a60]">연결된 계보가 없습니다.</p>
      ) : (
        <div className="mt-2 grid gap-2 lg:grid-cols-2">
          {sources.length > 0 ? (
            <LineageGroup direction="SOURCE" items={sources} />
          ) : null}
          {results.length > 0 ? (
            <LineageGroup direction="RESULT" items={results} />
          ) : null}
        </div>
      )}
    </div>
  );
}

function LineageGroup({
  direction,
  items,
}: {
  direction: "SOURCE" | "RESULT";
  items: OrchidGroupLineageItem[];
}) {
  return (
    <div className="rounded-md border border-[#dce7dc] bg-[#f9fcf8] p-2.5">
      <p className="text-xs font-bold text-[#16713a]">
        {direction === "SOURCE"
          ? "이 난 묶음의 원본"
          : "이 난 묶음에서 나온 결과"}
      </p>
      <ul className="mt-2 max-h-40 space-y-2 overflow-y-auto pr-1">
        {items.map((item) => {
          const connected =
            direction === "SOURCE"
              ? item.sourceOrchidGroup
              : item.resultOrchidGroup;
          return (
            <li
              key={item.id}
              className="rounded-md border bg-white p-2 text-xs"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate font-bold text-[#17251b]">
                    {connected.varietyName}
                  </p>
                  <p className="mt-0.5 text-[#6a766e]">
                    {connected.houseNumber}동 {connected.physicalBedNumber}다이{" "}
                    {connected.bedZoneName} · 현재 {connected.quantity}분
                  </p>
                </div>
                <span className="shrink-0 rounded bg-[#edf8ef] px-1.5 py-0.5 font-semibold text-[#16713a]">
                  {lineageLabel(item.relationType)}
                </span>
              </div>
              <p className="mt-1 text-[#435047]">
                투입 {item.sourceQuantity}분 → 결과 {item.resultQuantity}분 ·
                작업 #{item.workOperationId}
              </p>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function lineageLabel(relationType: OrchidGroupLineageRelationType) {
  return (
    {
      CREATED_FROM_INBOUND: "입고 생성",
      REPOTTED_TO: "분갈이",
      SPLIT_TO: "분주",
      MERGED_TO: "병합",
      POTTED_TO: "포트 작업",
      CORRECTED_TO: "보정",
    } satisfies Record<OrchidGroupLineageRelationType, string>
  )[relationType];
}

function OrchidGroupActivityView({
  history,
  lineage,
  lineageLoading,
  loading,
  onOpenCorrection,
}: {
  history: OrchidGroupWorkHistory[];
  lineage: OrchidGroupLineage | null;
  lineageLoading: boolean;
  loading: boolean;
  onOpenCorrection: (workOperationId: number) => void;
}) {
  const [detailOpen, setDetailOpen] = useState(false);
  const lineageCount =
    (lineage?.sources.length ?? 0) + (lineage?.results.length ?? 0);
  const hasDetails = history.length > 0 || lineageLoading || lineageCount > 0;

  useEffect(() => {
    if (!detailOpen) return;

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setDetailOpen(false);
    };

    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [detailOpen]);

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-xs font-bold text-[#344138]">최근 작업</h3>
        {hasDetails ? (
          <button
            className="text-[11px] font-semibold text-[#16713a]"
            type="button"
            aria-expanded={detailOpen}
            onClick={() => setDetailOpen((current) => !current)}
          >
            상세 보기
          </button>
        ) : (
          <span className="text-[11px] text-[#6f7b72]">{history.length}건</span>
        )}
      </div>
      {loading ? (
        <p className="mt-1 rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">
          최근 작업 확인 중
        </p>
      ) : history.length === 0 ? (
        <p className="mt-1 rounded-md bg-[#f5f7f3] p-2 text-xs text-[#5c6a60]">
          등록된 작업 없음
        </p>
      ) : (
        <ul className="mt-1 grid gap-1 sm:grid-cols-2">
          {history.slice(0, 2).map((item) => (
            <ActivityItem item={item} key={historyItemKey(item)} />
          ))}
        </ul>
      )}

      {detailOpen
        ? createPortal(
            <div
              className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/20 p-4"
              role="presentation"
              onMouseDown={() => setDetailOpen(false)}
            >
              <section
                className="flex max-h-[min(32rem,calc(100dvh-2rem))] w-full max-w-md flex-col rounded-md border border-[#cfd8cc] bg-white p-3 shadow-xl"
                role="dialog"
                aria-modal="true"
                aria-label="난 묶음 상세"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <div className="flex shrink-0 items-center justify-between gap-3">
                  <p className="text-sm font-bold text-[#17251b]">
                    난 묶음 상세
                  </p>
                  <button
                    className="text-xs font-semibold text-[#5c6a60]"
                    type="button"
                    onClick={() => setDetailOpen(false)}
                  >
                    닫기
                  </button>
                </div>
                <div className="mt-2 min-h-0 overflow-y-auto">
                  <p className="text-xs font-bold text-[#344138]">최근 작업</p>
                  {history.length > 0 ? (
                    <ul className="mt-2 space-y-2">
                      {history.slice(0, 5).map((item) => (
                        <ActivityItem
                          detailed
                          item={item}
                          key={historyItemKey(item)}
                          onOpenCorrection={(workOperationId) => {
                            setDetailOpen(false);
                            onOpenCorrection(workOperationId);
                          }}
                        />
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-2 text-xs text-[#5c6a60]">
                      등록된 작업 없음
                    </p>
                  )}
                  {/* <OrchidGroupLineageDetail
                    lineage={lineage}
                    loading={lineageLoading}
                  />  TODO: 난 묶음 관리 페이지 맵 정리 후 활성화 */}
                </div>
              </section>
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}

function ActivityItem({
  detailed = false,
  item,
  onOpenCorrection,
}: {
  detailed?: boolean;
  item: OrchidGroupWorkHistory;
  onOpenCorrection?: (workOperationId: number) => void;
}) {
  return (
    <li className="rounded-md border border-[#dfe5dc] bg-[#fbfcfa] px-2 py-1.5 text-[11px]">
      <div className="flex items-center justify-between gap-2">
        <span className="truncate font-bold text-[#17251b]">{item.title}</span>
        <span className="shrink-0 text-[#5c6a60]">{item.workDate}</span>
      </div>
      {detailed ? (
        <p className="mt-1 text-[#435047]">{historySourceLabel(item)}</p>
      ) : null}
      {detailed && item.locationSnapshot ? (
        <p className="mt-1 text-[#6a766e]">
          당시 {formatLocation(item.locationSnapshot)}
          {item.locationSnapshot.houseId !== item.currentLocation.houseId ||
          item.locationSnapshot.bedZoneId !== item.currentLocation.bedZoneId
            ? ` · 현재 ${formatLocation(item.currentLocation)}`
            : ""}
        </p>
      ) : null}
      {detailed &&
      item.sourceKind === "WORK_OPERATION_EFFECT" &&
      item.workOperationId != null &&
      onOpenCorrection ? (
        <button
          className="mt-1 rounded-md border border-[#9dcaaa] bg-white px-2 py-0.5 font-semibold text-[#16713a]"
          type="button"
          onClick={() => onOpenCorrection(item.workOperationId!)}
        >
          결과 보정
        </button>
      ) : null}
    </li>
  );
}

function historyItemKey(item: OrchidGroupWorkHistory) {
  return `${item.sourceKind}-${item.workOperationId ?? item.legacyWorkRecordId}`;
}

function historySourceLabel(item: OrchidGroupWorkHistory) {
  if (item.sourceKind === "WORK_OPERATION_EFFECT") {
    return "구조 변경 작업으로 연결";
  }
  if (!item.propagated) return "직접 등록";
  return item.sourceScopeType === "HOUSE"
    ? "동 전체 작업에서 적용"
    : item.sourceScopeType === "DERIVED_GROUP"
      ? "자동 그룹 작업에서 적용"
      : item.sourceScopeType === "USER_COLLECTION"
        ? "사용자 그룹 작업에서 적용"
        : "범위 작업에서 적용";
}

function formatLocation(location: OrchidGroupWorkHistory["currentLocation"]) {
  return `${location.houseNumber}동 ${location.physicalBedNumber}다이 ${location.bedZoneName}`;
}

function WorkRecordSummaryView({
  loading,
  summary,
}: {
  loading: boolean;
  summary: WorkRecordSummary;
}) {
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
    <div className="mt-1 grid gap-2 xl:grid-cols-[210px_minmax(0,1fr)]">
      <dl className="hidden grid-cols-3 gap-1 text-[11px] text-[#5c6a60] xl:grid">
        {summaryRows}
      </dl>
      <RecentWorkList records={summary.latestRecords.slice(0, 2)} />
    </div>
  );
}

function RecentWorkList({ records }: { records: OrchidGroupWorkHistory[] }) {
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
          key={historyItemKey(record)}
          className="rounded-md border border-[#e1e6df] bg-[#fbfcfa] px-2 py-1.5 text-xs"
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
  record: OrchidGroupWorkHistory | null;
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
