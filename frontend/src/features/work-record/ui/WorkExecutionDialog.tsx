import { getWorkExecutionKind } from "../model/workTypeDefinition";
import type { WorkExecutionDialogProps } from "../model/workExecution";
import { DiscardExecutionDialog } from "./work-types/discard/DiscardExecutionDialog";
import { MovementExecutionDialog } from "./work-types/movement/MovementExecutionDialog";
import { PottingExecutionDialog } from "./work-types/potting/PottingExecutionDialog";
import { StructureChangeExecutionDialog } from "./work-types/structure-change/StructureChangeExecutionDialog";

export function WorkExecutionDialog(props: WorkExecutionDialogProps) {
  switch (getWorkExecutionKind(props.operation.workTypeCode)) {
    case "DISCARD":
      return <DiscardExecutionDialog {...props} />;
    case "MOVEMENT":
      return <MovementExecutionDialog {...props} />;
    case "POTTING":
      return <PottingExecutionDialog {...props} />;
    case "STRUCTURE_CHANGE":
      return <StructureChangeExecutionDialog {...props} />;
    default:
      return null;
  }
}
