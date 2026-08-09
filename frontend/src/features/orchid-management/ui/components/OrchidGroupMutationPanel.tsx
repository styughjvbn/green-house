"use client";

import { useState } from "react";
import type { BedZone, House, OrchidGroup } from "@/entities/farm/types";
import type {
  MapCellRangePick,
  MutationPayload,
  OrchidFormDraft,
} from "../../model/types";
import OrchidGroupForm from "./OrchidGroupForm";

export default function OrchidGroupMutationPanel({
  house,
  mapCellRangePick,
  mode,
  pasteSourceOrchidGroup,
  resolvedZone,
  saving,
  selectedOrchidGroup,
  onCancel,
  onCreate,
  onEdit,
  onStartMapCellRangePick,
  onSyncMapCellRangePick,
}: {
  house: House;
  mapCellRangePick: MapCellRangePick;
  mode: "CREATE" | "EDIT";
  pasteSourceOrchidGroup: OrchidGroup | null;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedOrchidGroup: OrchidGroup | null;
  onCancel: () => void;
  onCreate: (payload: MutationPayload) => Promise<void>;
  onEdit: (payload: MutationPayload) => Promise<void>;
  onStartMapCellRangePick: (options: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number | null;
  }) => void;
  onSyncMapCellRangePick: (options: {
    endCell: string;
    excludeOrchidGroupId?: number | null;
    maxCell: number;
    startCell: string;
    targetBedZoneId: number;
  }) => void;
}) {
  const [createDraft, setCreateDraft] = useState<OrchidFormDraft | null>(null);

  return (
    <OrchidGroupForm
      key={
        mode === "EDIT"
          ? `edit-${selectedOrchidGroup?.id ?? "none"}`
          : `create-${resolvedZone?.id ?? "none"}-${pasteSourceOrchidGroup?.id ?? "empty"}`
      }
      draft={mode === "CREATE" ? createDraft : null}
      house={house}
      initialValue={
        mode === "EDIT" ? selectedOrchidGroup : pasteSourceOrchidGroup
      }
      mode={mode}
      saving={saving}
      mapCellRangePick={mapCellRangePick}
      targetZone={resolvedZone}
      onCancel={onCancel}
      onDraftChange={mode === "CREATE" ? setCreateDraft : undefined}
      onStartMapCellRangePick={onStartMapCellRangePick}
      onSyncMapCellRangePick={onSyncMapCellRangePick}
      onSubmit={
        mode === "EDIT"
          ? onEdit
          : async (payload) => {
              await onCreate(payload);
              setCreateDraft(null);
            }
      }
    />
  );
}
