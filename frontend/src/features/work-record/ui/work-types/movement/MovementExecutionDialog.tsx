"use client";

import type { WorkExecutionDialogProps } from "../../../model/operation/workExecution";
import { StructureChangeExecutionDialog } from "../structure-change/StructureChangeExecutionDialog";

export function MovementExecutionDialog({
  houses,
  orchidGroups,
  operation,
  onClose,
  onSaved,
}: WorkExecutionDialogProps) {
  return (
    <StructureChangeExecutionDialog
      houses={houses}
      orchidGroups={orchidGroups}
      operation={operation}
      onClose={onClose}
      onSaved={onSaved}
    />
  );
}
