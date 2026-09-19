import type { components } from "@/shared/api/generated/openapi";
import type { Page } from "@/shared/api/page";

type ApiSchemas = components["schemas"];

export type MutationType = NonNullable<
  ApiSchemas["OrchidGroupMutationResponse"]["mutationType"]
>;
export type MutationSourceDomain = NonNullable<
  ApiSchemas["OrchidGroupMutationResponse"]["sourceDomain"]
>;
export type MutationEntryKind = NonNullable<
  ApiSchemas["OrchidGroupMutationEntryResponse"]["entryKind"]
>;
export type MutationEntryRole = NonNullable<
  ApiSchemas["OrchidGroupMutationEntryResponse"]["role"]
>;
export type MutationRelationType = NonNullable<
  ApiSchemas["OrchidGroupMutationRelationResponse"]["relationType"]
>;

export type MutationState = {
  quantity?: number | null;
  reservedQuantity?: number | null;
  status?: string | null;
  bedZoneId?: number | null;
  sortOrder?: number | null;
  startPosition?: number | null;
  endPosition?: number | null;
  varietyId?: number | null;
  genus?: string | null;
  varietyName?: string | null;
  ageYear?: number | null;
  potSizeCode?: string | null;
  placementType?: string | null;
  trayCount?: number | null;
  splitPlacementAllowed?: boolean | null;
  inboundRecordId?: number | null;
  memo?: string | null;
};

export type MutationEntry = {
  id: number;
  orchidGroupId: number;
  entryKind: MutationEntryKind;
  role: MutationEntryRole;
  stateRevisionBefore?: number | null;
  stateRevisionAfter: number;
  beforeState?: MutationState | null;
  afterState?: MutationState | null;
};

export type MutationRelation = {
  id: number;
  mutationId: number;
  relatedMutationId: number;
  relationType: MutationRelationType;
};

export type OrchidGroupMutation = {
  id: number;
  mutationType: MutationType;
  sourceDomain: MutationSourceDomain;
  sourceType: string;
  sourceReferenceId: string;
  sourceOperationKey: string;
  correlationId: string;
  commandFingerprint: string;
  occurredAt: string;
  recordedAt: string;
  effectiveBusinessDate: string;
  reason?: string | null;
  schemaVersion: number;
  entries: MutationEntry[];
  relations: MutationRelation[];
};

export type MutationPage = Page<OrchidGroupMutation>;

export type MutationLabFilters = {
  orchidGroupId: number | null;
  mutationType: MutationType | null;
  sourceDomain: MutationSourceDomain | null;
  page: number;
  size: number;
};
