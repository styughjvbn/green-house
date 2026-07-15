"use client";

import Link from "next/link";
import { useState } from "react";
import type { WorkRecordManagerProps } from "../model/types";
import { useWorkRecordManager } from "../model/useWorkRecordManager";
import { WorkRecordCreateForm } from "./components/WorkRecordCreateForm";
import { WorkRecordDetail } from "./components/WorkRecordDetail";
import { WorkRecordFilters } from "./components/WorkRecordFilters";
import { WorkRecordList } from "./components/WorkRecordList";
import { WorkOperationPanel } from "./components/HouseWorkOperationPanel";
import { WorkOperationSchedule } from "./components/WorkOperationSchedule";
import { WorkOperationList } from "./components/WorkOperationList";

export type WorkManagementTab = "LIST" | "CALENDAR" | "HISTORY";

export function WorkRecordManager(
  props: WorkRecordManagerProps & { activeTab: WorkManagementTab },
) {
  const manager = useWorkRecordManager(props);
  const [showOperationForm, setShowOperationForm] = useState(false);
  const [operationSavedVersion, setOperationSavedVersion] = useState(0);
  const { activeTab } = props;
  const refreshKey = manager.operationCreatedVersion + operationSavedVersion;

  function prepareTabChange() {
    setShowOperationForm(false);
    manager.setShowCreateForm(false);
  }

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <div className="flex shrink-0 gap-1 overflow-x-auto rounded-md border border-[#d7ddd4] bg-white p-1 shadow-sm">
        {(
          [
            ["LIST", "작업 목록"],
            ["CALENDAR", "캘린더"],
            ["HISTORY", "작업 이력"],
          ] as const
        ).map(([tab, label]) => (
          <Link
            className={`min-w-28 rounded-md px-4 py-2.5 text-center text-sm font-bold transition ${activeTab === tab ? "bg-[#2f8f4e] text-white" : "text-[#435047] hover:bg-[#eef5ed]"}`}
            href={
              {
                LIST: "/work-records/list",
                CALENDAR: "/work-records/calendar",
                HISTORY: "/work-records/history",
              }[tab]
            }
            key={tab}
            onClick={prepareTabChange}
          >
            {label}
          </Link>
        ))}
      </div>
      {manager.errorMessage && !manager.showCreateForm ? (
        <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
          {manager.errorMessage}
        </div>
      ) : null}
      {activeTab === "LIST" && manager.showCreateForm ? (
        <WorkRecordCreateForm
          bedZones={manager.bedZones}
          errorMessage={manager.errorMessage}
          form={manager.form}
          houses={props.houses}
          orchidGroups={manager.orchidGroups}
          physicalBeds={manager.physicalBeds}
          safeBedZoneId={manager.safeBedZoneId}
          safeOrchidGroupId={manager.safeOrchidGroupId}
          safePhysicalBedId={manager.safePhysicalBedId}
          saving={manager.saving}
          workTypes={props.workTypes}
          onClose={() => manager.setShowCreateForm(false)}
          onChange={manager.updateForm}
          onSubmit={manager.submitWorkRecord}
        />
      ) : null}
      {activeTab === "LIST" && showOperationForm ? (
        <WorkOperationPanel
          houses={props.houses}
          workTypes={props.workTypes}
          onClose={() => setShowOperationForm(false)}
          onSaved={() => setOperationSavedVersion((current) => current + 1)}
        />
      ) : null}

      {activeTab === "LIST" ? (
        <WorkOperationList
          refreshKey={refreshKey}
          onCreateOperation={() => {
            manager.setShowCreateForm(false);
            setShowOperationForm(true);
          }}
          onCreateRecord={() => {
            setShowOperationForm(false);
            manager.setShowCreateForm(true);
          }}
        />
      ) : null}

      {activeTab === "CALENDAR" ? (
        <div className="min-h-0 flex-1 overflow-auto">
          <WorkOperationSchedule refreshKey={refreshKey} />
        </div>
      ) : null}

      {activeTab === "HISTORY" ? (
        <>
          <WorkRecordFilters
            filters={manager.filters}
            records={manager.records}
            workTypes={props.workTypes}
            onChange={manager.updateFilters}
            onReset={manager.resetFilters}
          />
          <div
            className={`grid min-h-0 flex-1 gap-4 ${
              manager.detailOpen
                ? "lg:grid-cols-[minmax(0,1fr)_400px]"
                : "lg:grid-cols-1"
            }`}
          >
            <WorkRecordList
              currentPage={manager.currentPage}
              pageSize={manager.pageSize}
              records={manager.paginatedRecords}
              selectedRecordId={
                manager.detailOpen ? (manager.selectedRecord?.id ?? null) : null
              }
              totalPages={manager.totalPages}
              totalRecords={manager.filteredRecords.length}
              workTypes={props.workTypes}
              onPageChange={manager.changePage}
              onPageSizeChange={manager.changePageSize}
              onSelect={manager.selectRecord}
            />
            {manager.detailOpen ? (
              <WorkRecordDetail
                canceling={manager.canceling}
                record={manager.selectedRecord}
                workTypes={props.workTypes}
                onCancel={manager.cancelSelectedRecord}
                onClose={manager.closeDetail}
              />
            ) : null}
          </div>
        </>
      ) : null}
    </div>
  );
}
