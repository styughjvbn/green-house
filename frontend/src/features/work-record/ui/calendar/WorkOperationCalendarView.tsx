"use client";

import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { WorkOperation, WorkOperationStatus } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import type { WorkRecordUrlState } from "../../lib/workRecordUrlState";
import { useWorkOperationActions } from "../../model/useWorkOperationActions";
import { workOperationCalendarQueryOptions } from "../../model/workRecordQueryOptions";
import { TabError, TabLayout, TabSplit } from "@/shared/ui/TabLayout";
import { WorkCalendarFilters } from "./WorkCalendarFilters";
import { WorkOperationDetailPanel } from "../WorkOperationDetailPanel";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];

export function WorkOperationCalendarView({
  headerActions,
  routeState,
}: {
  headerActions?: ReactNode;
  routeState: WorkRecordUrlState;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();
  const operationsQuery = useQuery(
    workOperationCalendarQueryOptions(routeState),
  );
  const operations = operationsQuery.data ?? [];
  const actions = useWorkOperationActions(operations);
  const month = routeState.month;
  const status = routeState.filters.status;
  const loading = operationsQuery.isFetching || actions.loading;
  const error =
    actions.error ??
    (operationsQuery.error instanceof Error
      ? operationsQuery.error.message
      : null);

  function changeMonth(offset: number) {
    const [year, monthNumber] = month.split("-").map(Number);
    const next = new Date(Date.UTC(year, monthNumber - 1 + offset, 1));
    const nextMonth = next.toISOString().slice(0, 7);
    writeUrlParams((params) => params.set("month", nextMonth));
  }

  function changeStatus(nextStatus: WorkOperationStatus | "") {
    writeUrlParams((params) => {
      if (nextStatus) params.set("status", nextStatus);
      else params.delete("status");
    });
  }

  const cells = calendarCells(month);

  return (
    <TabLayout>
      <WorkCalendarFilters status={status} onStatusChange={changeStatus} />

      <TabError message={error} />

      <TabSplit columns="grid-rows-[minmax(32rem,1fr)_minmax(20rem,auto)] lg:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)] lg:grid-rows-1">
        <section
          aria-busy={loading}
          className="flex h-full min-h-0 min-w-0 flex-col rounded-md border border-[#dfe5dc] bg-white shadow-sm"
        >
          <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[#e7ebe5] px-4 py-3">
            <div className="flex items-center gap-1">
              <button
                aria-label="이전 달"
                className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-[#d7ded5] bg-white text-[#435047] hover:bg-[#f3f6f2]"
                type="button"
                onClick={() => changeMonth(-1)}
              >
                <ChevronLeft
                  className="h-4 w-4"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
              </button>
              <h2 className="min-w-32 text-center text-base font-bold text-[#17251b]">
                {month}
              </h2>
              <button
                aria-label="다음 달"
                className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-[#d7ded5] bg-white text-[#435047] hover:bg-[#f3f6f2]"
                type="button"
                onClick={() => changeMonth(1)}
              >
                <ChevronRight
                  className="h-4 w-4"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
              </button>
            </div>
            <div className="ml-auto flex max-w-full flex-wrap items-center justify-end gap-3">
              <span className="text-xs font-semibold whitespace-nowrap text-[#159447]">
                {loading
                  ? "확인 중"
                  : `총 ${operations.length.toLocaleString()}건`}
              </span>
              {headerActions}
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-auto bg-white">
            <div className="grid min-w-[700px] grid-cols-7">
              {WEEKDAYS.map((day) => (
                <div
                  className="sticky top-0 z-10 border-r border-b border-[#e6ebe3] bg-[#f7f9f6] px-2 py-2.5 text-center text-xs font-semibold text-[#4b584f] last:border-r-0"
                  key={day}
                >
                  {day}
                </div>
              ))}
              {cells.map((date, index) => {
                const dayOperations = date
                  ? operations.filter((operation) =>
                      includesDate(operation, date),
                    )
                  : [];
                return (
                  <div
                    className="min-h-24 border-r border-b border-[#edf0ec] p-1.5 last:border-r-0"
                    key={date ?? `blank-${index}`}
                  >
                    {date ? (
                      <p className="text-xs font-semibold text-[#6a766e]">
                        {Number(date.slice(-2))}
                      </p>
                    ) : null}
                    <div className="mt-1 space-y-1">
                      {dayOperations.slice(0, 3).map((operation) => (
                        <button
                          className={`block w-full truncate rounded px-1.5 py-1 text-left text-[11px] font-semibold ${actions.selectedId === operation.id ? "bg-[#16713a] text-white" : statusClass(operation.status)}`}
                          key={operation.id}
                          title={operation.title}
                          type="button"
                          onClick={() => actions.select(operation.id)}
                        >
                          {operation.title}
                        </button>
                      ))}
                      {dayOperations.length > 3 ? (
                        <p className="text-[10px] text-[#6a766e]">
                          +{dayOperations.length - 3}건
                        </p>
                      ) : null}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        <WorkOperationDetailPanel
          actions={actions}
          emptyMessage="상세를 확인할 캘린더 작업을 선택하세요."
        />
      </TabSplit>
    </TabLayout>
  );
}

function calendarCells(month: string): Array<string | null> {
  const [year, monthNumber] = month.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  const sundayBased = new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay();
  const leading = (sundayBased + 6) % 7;
  const cells: Array<string | null> = Array.from(
    { length: leading },
    () => null,
  );
  for (let day = 1; day <= lastDay; day += 1) {
    cells.push(`${month}-${String(day).padStart(2, "0")}`);
  }
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

function includesDate(operation: WorkOperation, date: string) {
  return (
    operation.plannedStartDate <= date &&
    (operation.plannedEndDate ?? operation.plannedStartDate) >= date
  );
}

function statusClass(status: WorkOperationStatus) {
  if (status === "COMPLETED" || status === "CORRECTED")
    return "bg-[#e7f6eb] text-[#10783a]";
  if (status === "IN_PROGRESS") return "bg-[#e6f0ff] text-[#246df2]";
  if (status === "PAUSED") return "bg-[#fff3d8] text-[#8a5a12]";
  if (status === "CANCELED") return "bg-[#f2eeee] text-[#765f5a]";
  return "bg-[#eef2ed] text-[#435047]";
}
