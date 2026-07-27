import type {
  BedZone,
  House,
  OrchidGroup,
  WorkOperation,
  WorkOperationTarget,
} from "@/entities/farm/types";

export type WorkExecutionDialogProps = {
  bedZones: BedZone[];
  houses: House[];
  orchidGroups: OrchidGroup[];
  operation: WorkOperation;
  source: OrchidGroup | null;
  target: WorkOperationTarget;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
};
