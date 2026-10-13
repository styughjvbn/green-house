"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { CalendarDays, List } from "lucide-react";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { DataTableCreateButton } from "@/shared/ui/DataTableCreateButton";
import {
  readWorkRecordUrlState,
  setWorkWorkspaceScope,
  setWorkWorkspaceView,
  type WorkWorkspaceScope,
  type WorkWorkspaceView,
} from "../lib/workRecordUrlState";
import { useWorkRecordInvalidation } from "../model/useWorkRecordInvalidation";
import { WorkOperationCalendarView } from "./calendar/WorkOperationCalendarView";
import { WorkOperationListView } from "./list/WorkOperationListView";
import { WorkOperationRegistrationDialog } from "./registration/WorkOperationRegistrationDialog";
import { WorkManagementFilter } from "./WorkManagementFilter";

export function WorkRecordPage() {
  const { invalidateWorkData } = useWorkRecordInvalidation();
  const [showOperationForm, setShowOperationForm] = useState(false);
  const searchParams = useSearchParams();
  const writeUrlParams = useUrlSearchParamsWriter();
  const urlState = readWorkRecordUrlState(searchParams);

  function changeScope(scope: WorkWorkspaceScope) {
    writeUrlParams((params) => setWorkWorkspaceScope(params, scope));
  }

  function changeView(view: WorkWorkspaceView) {
    writeUrlParams((params) => {
      setWorkWorkspaceView(params, view, {
        defaultMonth: urlState.month,
      });
    });
  }

  const headerActions = (
    <DataTableCreateButton
      onClick={() => setShowOperationForm(true)}
    >
      작업 등록
    </DataTableCreateButton>
  );

  const viewControl = (
    <SegmentedControl
      label="보기 방식"
      options={[
        {
          label: "목록",
          value: "LIST",
          accessibleLabel: "목록 보기",
          icon: List,
        },
        {
          label: "캘린더",
          value: "CALENDAR",
          accessibleLabel: "캘린더 보기",
          icon: CalendarDays,
        },
      ]}
      value={urlState.view}
      onChange={changeView}
    />
  );

  const scopeControl = (
    <WorkManagementFilter scope={urlState.scope} onChange={changeScope} />
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      {showOperationForm ? (
        <WorkOperationRegistrationDialog
          onClose={() => setShowOperationForm(false)}
          onSaved={() => void invalidateWorkData()}
        />
      ) : null}

      {urlState.view === "LIST" ? (
        <WorkOperationListView
          headerActions={headerActions}
          routeState={urlState}
          viewControl={viewControl}
        />
      ) : (
        <WorkOperationCalendarView
          headerActions={
            <>
              {viewControl}
              {headerActions}
            </>
          }
          routeState={urlState}
          scopeControl={scopeControl}
        />
      )}
    </div>
  );
}

function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{
    description?: string;
    accessibleLabel?: string;
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
      <div
        className={`inline-flex rounded-md border ${
          options.some((option) => option.icon)
            ? "h-8 border-[#dfe5dc] bg-white"
            : "border-[#d7ded5] bg-[#f5f7f4]"
        }`}
      >
        {options.map((option) => {
          const Icon = option.icon;
          const selected = option.value === value;
          return (
            <button
              aria-pressed={selected}
              aria-label={option.accessibleLabel}
              className={`inline-flex items-center justify-center rounded text-xs font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#16713a] ${Icon ? "h-full w-8 px-0" : "h-8 px-2"} ${
                selected
                  ? Icon
                    ? "bg-[#eef5ed] text-[#16713a]"
                    : "bg-white text-[#16713a]"
                  : "text-[#5c6960] hover:text-[#26352b]"
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
