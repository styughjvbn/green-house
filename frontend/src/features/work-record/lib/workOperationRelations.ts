export type WorkOperationRelationKind = "CREATION_BATCH" | "LINKED";

export type WorkOperationRelationSelection = {
  operationId: number;
  operationTitle: string;
  kind: WorkOperationRelationKind;
};
