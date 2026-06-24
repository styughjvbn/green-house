"use client";

import type { WorkRecordManagerProps } from "../model/types";
import { useWorkRecordManager } from "../model/useWorkRecordManager";
import { WorkRecordCreateForm } from "./components/WorkRecordCreateForm";
import { WorkRecordList } from "./components/WorkRecordList";

export function WorkRecordManager(props: WorkRecordManagerProps) {
  const manager = useWorkRecordManager(props);

  return (
    <div className="grid gap-5 xl:grid-cols-[420px_minmax(0,1fr)]">
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

      <WorkRecordList records={manager.records} />
    </div>
  );
}
