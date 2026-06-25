"use client";

import { useState } from "react";
import type { OrchidManagementViewMode } from "@/entities/farm/types";
import { useOrchidManagementMap } from "../model/useOrchidManagementMap";
import type { OrchidManagementMapProps } from "../model/types";
import HouseDetailHeader from "./components/HouseDetailHeader";
import HouseDetailMap from "./components/HouseDetailMap";
import HouseSelectorPanel from "./components/HouseSelectorPanel";
import MapLegend from "./components/MapLegend";
import OrchidSelectionPanel from "./components/OrchidSelectionPanel";

export function OrchidManagementMap({ mapData, house }: OrchidManagementMapProps) {
  const [viewMode, setViewMode] = useState<OrchidManagementViewMode>("REAL_DIRECTION");
  const orchidManagement = useOrchidManagementMap(house);

  return (
    <div className="grid gap-4 2xl:grid-cols-[260px_minmax(0,1fr)_330px]">
      <HouseSelectorPanel houses={mapData.houses} selectedHouseId={house.id} />
      <section className="space-y-3">
        <HouseDetailHeader house={house} viewMode={viewMode} onViewModeChange={setViewMode} />
        <HouseDetailMap
          house={house}
          selection={orchidManagement.selection}
          onSelectBedZone={orchidManagement.actions.selectBedZone}
          onSelectOrchidGroup={orchidManagement.actions.selectOrchidGroup}
        />
        <MapLegend />
      </section>
      <OrchidSelectionPanel
        errorMessage={orchidManagement.errorMessage}
        house={house}
        houses={mapData.houses}
        mutationMode={orchidManagement.mutationMode}
        resolvedZone={orchidManagement.resolvedZone}
        saving={orchidManagement.saving}
        selectedBedZone={orchidManagement.selectedBedZone}
        selectedOrchidGroup={orchidManagement.selectedOrchidGroup}
        onCancelMutation={orchidManagement.actions.cancelMutation}
        onCreate={orchidManagement.actions.create}
        onDelete={orchidManagement.actions.delete}
        onEdit={orchidManagement.actions.edit}
        onMove={orchidManagement.actions.move}
        onOpenCreate={orchidManagement.actions.openCreate}
        onOpenEdit={orchidManagement.actions.openEdit}
        onOpenMove={orchidManagement.actions.openMove}
      />
    </div>
  );
}

