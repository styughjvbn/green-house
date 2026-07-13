"use client";

import type { WorkRecordManagerProps } from "../model/types";
import { useWorkRecordManager } from "../model/useWorkRecordManager";
import { WorkRecordCreateForm } from "./components/WorkRecordCreateForm";
import { WorkRecordDetail } from "./components/WorkRecordDetail";
import { WorkRecordFilters } from "./components/WorkRecordFilters";
import { WorkRecordList } from "./components/WorkRecordList";

export function WorkRecordManager(props: WorkRecordManagerProps) {
  const manager = useWorkRecordManager(props);

  return (
    <div className="flex h-full min-h-0 flex-col gap-4">
      <WorkRecordFilters
        filters={manager.filters}
        records={manager.records}
        workTypes={props.workTypes}
        onChange={manager.updateFilters}
        onReset={manager.resetFilters}
      />
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
          onChange={manager.updateForm}
          onSubmit={manager.submitWorkRecord}
        />
      ) : null}

      <div
        className={`grid min-h-0 flex-1 gap-4 ${
          manager.detailOpen
            ? "xl:grid-cols-[minmax(0,1fr)_400px]"
            : "xl:grid-cols-1"
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
          onCreate={() => manager.setShowCreateForm((current) => !current)}
          onPageChange={manager.changePage}
          onPageSizeChange={manager.changePageSize}
          onSelect={manager.selectRecord}
        />
        {manager.detailOpen ? (
          <WorkRecordDetail
            record={manager.selectedRecord}
            workTypes={props.workTypes}
            onClose={manager.closeDetail}
          />
        ) : null}
      </div>
    </div>
  );
}
