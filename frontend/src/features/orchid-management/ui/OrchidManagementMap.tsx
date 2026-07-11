"use client";

import { useState } from "react";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type { OrchidManagementMapProps } from "../model/types";
import BedPrecisionSettings from "./components/BedPrecisionSettings";
import HouseDetailMap from "./components/HouseDetailMap";
import HouseSelectorPanel from "./components/HouseSelectorPanel";
import OrchidSearchPanel from "./components/OrchidSearchPanel";
import OrchidSelectionPanel, {
  SelectedZoneInfo,
} from "./components/OrchidSelectionPanel";

const VARIETY_COLOR_STORAGE_KEY =
  "orchid-management:distinguish-variety-colors";

export function OrchidManagementMap({
  initialSelectedOrchidGroupId,
  initialSelectedPhysicalBedId,
  initialSelectedBedZoneId,
  initialSearchFilters,
  mapData,
  house,
  workTypes,
}: OrchidManagementMapProps) {
  const orchidManagement = useOrchidManagementMap(
    house,
    workTypes,
    initialSelectedOrchidGroupId,
    initialSelectedPhysicalBedId ?? null,
    initialSelectedBedZoneId ?? null,
    initialSearchFilters,
  );
  const [showScale, setShowScale] = useState(true);
  const [distinguishVarietyColors, setDistinguishVarietyColors] = useState(
    () =>
      typeof window !== "undefined" &&
      window.localStorage.getItem(VARIETY_COLOR_STORAGE_KEY) === "true",
  );

  function toggleVarietyColors() {
    setDistinguishVarietyColors((current) => {
      const next = !current;
      window.localStorage.setItem(VARIETY_COLOR_STORAGE_KEY, String(next));
      return next;
    });
  }

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_440px]">
      <section className="space-y-3">
        <HouseSelectorPanel
          distinguishVarietyColors={distinguishVarietyColors}
          house={house}
          houses={mapData.houses}
          placementEditMode={orchidManagement.placementEditMode}
          selectedHouseId={house.id}
          showScale={showScale}
          onTogglePlacementEditMode={
            orchidManagement.actions.togglePlacementEditMode
          }
          onToggleVarietyColors={toggleVarietyColors}
          onToggleScale={() => setShowScale((current) => !current)}
        />
        <HouseDetailMap
          distinguishVarietyColors={distinguishVarietyColors}
          dragState={orchidManagement.dragState}
          filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
          house={house}
          placementEditMode={orchidManagement.placementEditMode}
          saving={orchidManagement.saving}
          selection={orchidManagement.selection}
          showScale={showScale}
          onDragEnd={orchidManagement.actions.endDrag}
          onDragStart={orchidManagement.actions.startDrag}
          onDropOnBedZone={orchidManagement.actions.dropOnBedZone}
          onEnterDropZone={orchidManagement.actions.enterDropZone}
          onSelectBedZone={orchidManagement.actions.selectBedZone}
          onSelectOrchidGroup={
            orchidManagement.actions.selectOrchidGroupForEdit
          }
        />
        <SelectedZoneInfo
          house={house}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          workRecordSummary={orchidManagement.workRecordSummary}
          workRecordSummaryLoading={orchidManagement.workRecordSummaryLoading}
        />
        {/* <BedPrecisionSettings zone={orchidManagement.resolvedZone} /> 26.07.11 비활성화*/}
      </section>
      <div className="space-y-3">
        <OrchidSearchPanel
          currentHouseId={house.id}
          currentSelectedOrchidGroupId={
            orchidManagement.selectedOrchidGroup?.id ?? null
          }
          filteredCount={orchidManagement.searchResults.length}
          filters={orchidManagement.searchFilters}
          hasActiveSearch={orchidManagement.hasActiveSearch}
          loading={orchidManagement.searchLoading}
          results={orchidManagement.searchResults}
          onClear={orchidManagement.actions.resetSearch}
          onSelectResult={orchidManagement.actions.moveToOrchidGroup}
          onUpdateFilter={orchidManagement.actions.updateSearchFilter}
        />
        <OrchidSelectionPanel
          copiedOrchidGroup={orchidManagement.copiedOrchidGroup}
          errorMessage={orchidManagement.errorMessage}
          filteredOrchidGroupIds={orchidManagement.filteredOrchidGroupIds}
          hasActiveSearch={orchidManagement.hasActiveSearch}
          house={house}
          mutationMode={orchidManagement.mutationMode}
          pasteSourceOrchidGroup={orchidManagement.pasteSourceOrchidGroup}
          preferredMoveZoneId={orchidManagement.preferredMoveZoneId}
          resolvedZone={orchidManagement.resolvedZone}
          saving={orchidManagement.saving}
          selectedBedZone={orchidManagement.selectedBedZone}
          selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
          workRecordForm={orchidManagement.workRecordForm}
          workTypes={workTypes}
          onCancelMutation={orchidManagement.actions.cancelMutation}
          onClearCopiedOrchidGroup={
            orchidManagement.actions.clearCopiedOrchidGroup
          }
          onCopyOrchidGroup={orchidManagement.actions.copyOrchidGroup}
          onCreate={orchidManagement.actions.create}
          onDelete={orchidManagement.actions.delete}
          onEdit={orchidManagement.actions.edit}
          onMove={orchidManagement.actions.move}
          onOpenCreate={orchidManagement.actions.openCreate}
          onOpenEdit={orchidManagement.actions.openEdit}
          onOpenMove={orchidManagement.actions.openMove}
          onOpenPaste={orchidManagement.actions.openPaste}
          onOpenWorkRecord={orchidManagement.actions.openWorkRecord}
          onSelectOrchidGroup={orchidManagement.actions.selectOrchidGroup}
          onTogglePlacementEditMode={
            orchidManagement.actions.togglePlacementEditMode
          }
          onUpdateWorkRecordForm={orchidManagement.actions.updateWorkRecordForm}
          onWorkRecordCreate={orchidManagement.actions.workRecordCreate}
          placementEditMode={orchidManagement.placementEditMode}
        />
      </div>
    </div>
  );
}
