"use client";

import { useState } from "react";
import type { WorkRecordManagerProps } from "../model/types";
import { useWorkRecordManager } from "../model/useWorkRecordManager";
import { WorkRecordCreateForm } from "./components/WorkRecordCreateForm";
import { WorkRecordDetail } from "./components/WorkRecordDetail";
import { WorkRecordFilters } from "./components/WorkRecordFilters";
import { WorkRecordList } from "./components/WorkRecordList";
import { HouseWorkOperationPanel } from "./components/HouseWorkOperationPanel";

export function WorkRecordManager(props: WorkRecordManagerProps) {
  const manager = useWorkRecordManager(props);
  const [showOperationForm, setShowOperationForm] = useState(false);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <WorkRecordFilters
        filters={manager.filters}
        records={manager.records}
        workTypes={props.workTypes}
        onChange={manager.updateFilters}
        onReset={manager.resetFilters}
      />
      {manager.errorMessage && !manager.showCreateForm ? (
        <div className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-3 text-sm text-[#8f2f19]">
          {manager.errorMessage}
        </div>
      ) : null}
      {manager.showCreateForm ? (
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
      {showOperationForm ? (
        <HouseWorkOperationPanel
          houses={props.houses}
          workTypes={props.workTypes}
          onClose={() => setShowOperationForm(false)}
        />
      ) : null}

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
          onCreate={() => manager.setShowCreateForm(true)}
          onCreateOperation={() => setShowOperationForm(true)}
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
    </div>
  );
}
