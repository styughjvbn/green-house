"use client";

import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type { OrchidManagementMapProps } from "../model/types";
import HouseDetailMap from "./components/HouseDetailMap";
import HouseSelectorPanel from "./components/HouseSelectorPanel";
import OrchidSelectionPanel, {
  SelectedZoneInfo,
} from "./components/OrchidSelectionPanel";

export function OrchidManagementMap({
  mapData,
  house,
  workTypes,
}: OrchidManagementMapProps) {
  const orchidManagement = useOrchidManagementMap(house, workTypes);

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_330px]">
      <section className="space-y-3">
        <HouseSelectorPanel
          house={house}
          houses={mapData.houses}
          placementEditMode={orchidManagement.placementEditMode}
          selectedHouseId={house.id}
          onTogglePlacementEditMode={
            orchidManagement.actions.togglePlacementEditMode
          }
        />
        <HouseDetailMap
          dragState={orchidManagement.dragState}
          house={house}
          placementEditMode={orchidManagement.placementEditMode}
          saving={orchidManagement.saving}
          selection={orchidManagement.selection}
          onDragEnd={orchidManagement.actions.endDrag}
          onDragStart={orchidManagement.actions.startDrag}
          onDropOnBedZone={orchidManagement.actions.dropOnBedZone}
          onEnterDropZone={orchidManagement.actions.enterDropZone}
          onSelectBedZone={orchidManagement.actions.selectBedZone}
          onSelectOrchidGroup={orchidManagement.actions.selectOrchidGroup}
        />
        <SelectedZoneInfo
          house={house}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          workRecordSummary={orchidManagement.workRecordSummary}
          workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
        />
      </section>
      <OrchidSelectionPanel
        errorMessage={orchidManagement.errorMessage}
        house={house}
        houses={mapData.houses}
        mutationMode={orchidManagement.mutationMode}
        resolvedZone={orchidManagement.resolvedZone}
        saving={orchidManagement.saving}
        selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
        workRecordForm={orchidManagement.workRecordForm}
        workTypes={workTypes}
        onCancelMutation={orchidManagement.actions.cancelMutation}
        onCreate={orchidManagement.actions.create}
        onDelete={orchidManagement.actions.delete}
        onEdit={orchidManagement.actions.edit}
        onMove={orchidManagement.actions.move}
        onOpenCreate={orchidManagement.actions.openCreate}
        onOpenEdit={orchidManagement.actions.openEdit}
        onOpenMove={orchidManagement.actions.openMove}
        onOpenWorkRecord={orchidManagement.actions.openWorkRecord}
        onTogglePlacementEditMode={
          orchidManagement.actions.togglePlacementEditMode
        }
        onUpdateWorkRecordForm={orchidManagement.actions.updateWorkRecordForm}
        onWorkRecordCreate={orchidManagement.actions.workRecordCreate}
        placementEditMode={orchidManagement.placementEditMode}
      />
    </div>
  );
}
