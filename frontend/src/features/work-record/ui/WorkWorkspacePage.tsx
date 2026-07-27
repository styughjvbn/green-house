"use client";

import { useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { CalendarDays, List, Plus } from "lucide-react";
import type { BedZone, House, OrchidGroup } from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import {
  readWorkRecordUrlState,
  setWorkWorkspaceScope,
  setWorkWorkspaceView,
  type WorkWorkspaceScope,
  type WorkWorkspaceView,
} from "../lib/workRecordUrlState";
import { WorkOperationCalendarView } from "./calendar/WorkOperationCalendarView";
import { WorkOperationListView } from "./WorkOperationListView";

export function WorkWorkspacePage({
  bedZones,
  houses,
  orchidGroups,
  refreshKey,
  onCreateWork,
}: {
  bedZones: BedZone[];
  houses: House[];
  orchidGroups: OrchidGroup[];
  refreshKey: number;
  onCreateWork: () => void;
}) {
  const searchParams = useSearchParams();
  const writeUrlParams = useUrlSearchParamsWriter();
  const urlState = readWorkRecordUrlState(searchParams);
  const routeStateKey = JSON.stringify(urlState);

  useEffect(() => {
    const scope = searchParams.get("scope");
    const view = searchParams.get("view");
    const month = searchParams.get("month");
    const page = searchParams.get("page");
    const size = searchParams.get("size");
    const status = searchParams.get("status");
    if (
      (scope === "MANAGEMENT" || scope === "ALL") &&
      (view === "LIST" || view === "CALENDAR") &&
      (urlState.view !== "CALENDAR" || month === urlState.month) &&
      (page == null || page === String(urlState.page)) &&
      (size == null || size === String(urlState.size)) &&
      (status == null || status === urlState.filters.status)
    ) {
      return;
    }
    writeUrlParams((params) => {
      params.set("scope", urlState.scope);
      params.set("view", urlState.view);
      if (urlState.view === "CALENDAR") {
        params.set("month", urlState.month);
      }
      if (page != null) params.set("page", String(urlState.page));
      if (size != null) params.set("size", String(urlState.size));
      if (status && !urlState.filters.status) params.delete("status");
    });
  }, [
    searchParams,
    urlState.filters.status,
    urlState.month,
    urlState.scope,
    urlState.view,
    writeUrlParams,
  ]);

  function changeScope(scope: WorkWorkspaceScope) {
    writeUrlParams((params) => setWorkWorkspaceScope(params, scope));
  }

  function changeView(view: WorkWorkspaceView) {
    writeUrlParams((params) => {
      setWorkWorkspaceView(params, view);
      if (view === "CALENDAR" && !params.get("month")) {
        params.set("month", urlState.month);
      }
    });
  }

  const headerActions = (
    <WorkspaceHeaderActions
      scope={urlState.scope}
      view={urlState.view}
      onCreateWork={onCreateWork}
      onScopeChange={changeScope}
      onViewChange={changeView}
    />
  );

  return (
    <main className="h-full min-h-0">
      {urlState.view === "LIST" ? (
        <WorkOperationListView
          key={`list:${urlState.scope}:${routeStateKey}`}
          bedZones={bedZones}
          headerActions={headerActions}
          houses={houses}
          initialFilters={urlState.filters}
          initialPage={urlState.page}
          initialSize={urlState.size}
          orchidGroups={orchidGroups}
          queryView={urlState.scope}
          refreshKey={refreshKey}
          showCreateAction={false}
          onCreateWork={onCreateWork}
        />
      ) : (
        <WorkOperationCalendarView
          key={`calendar:${urlState.scope}:${routeStateKey}`}
          bedZones={bedZones}
          headerActions={headerActions}
          houses={houses}
          initialMonth={urlState.month}
          initialStatus={urlState.filters.status}
          orchidGroups={orchidGroups}
          refreshKey={refreshKey}
          view={urlState.scope}
        />
      )}
    </main>
  );
}

function WorkspaceHeaderActions({
  scope,
  view,
  onCreateWork,
  onScopeChange,
  onViewChange,
}: {
  scope: WorkWorkspaceScope;
  view: WorkWorkspaceView;
  onCreateWork: () => void;
  onScopeChange: (scope: WorkWorkspaceScope) => void;
  onViewChange: (view: WorkWorkspaceView) => void;
}) {
  return (
    <>
      <SegmentedControl
        compact
        label="표시 범위"
        options={[
          {
            label: "현황",
            value: "MANAGEMENT",
            description: "미완료 작업과 오늘 변경된 작업",
          },
          {
            label: "전체",
            value: "ALL",
            description: "상태와 관계없이 모든 작업",
          },
        ]}
        value={scope}
        onChange={onScopeChange}
      />
      <SegmentedControl
        compact
        iconOnly
        label="보기 방식"
        options={[
          {
            icon: List,
            label: "목록 보기",
            value: "LIST",
          },
          {
            icon: CalendarDays,
            label: "캘린더 보기",
            value: "CALENDAR",
          },
        ]}
        value={view}
        onChange={onViewChange}
      />
      <button
        className="inline-flex h-8 items-center gap-1.5 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
        type="button"
        onClick={onCreateWork}
      >
        <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
        작업 등록
      </button>
    </>
  );
}

function SegmentedControl<T extends string>({
  compact = false,
  iconOnly = false,
  label,
  options,
  value,
  onChange,
}: {
  compact?: boolean;
  iconOnly?: boolean;
  label: string;
  options: Array<{
    description?: string;
    icon?: typeof List;
    label: string;
    value: T;
  }>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <fieldset>
      <legend className="sr-only">{label}</legend>
      <div className="inline-flex rounded-md border border-[#d7ded5] bg-[#f5f7f4] p-1">
        {options.map((option) => {
          const Icon = option.icon;
          const selected = option.value === value;
          return (
            <button
              aria-pressed={selected}
              aria-label={iconOnly ? option.label : undefined}
              className={`inline-flex items-center justify-center gap-1.5 rounded font-semibold transition-colors ${
                selected
                  ? "bg-white text-[#16713a] shadow-sm"
                  : "text-[#5c6960] hover:text-[#26352b]"
              } ${compact ? "h-7 px-2 text-xs" : "h-8 px-3 text-sm"} ${
                iconOnly ? "w-7 px-0" : ""
              }`}
              key={option.value}
              title={option.description}
              type="button"
              onClick={() => onChange(option.value)}
            >
              {Icon ? (
                <Icon
                  className="h-4 w-4"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
              ) : null}
              {iconOnly ? (
                <span className="sr-only">{option.label}</span>
              ) : (
                option.label
              )}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
