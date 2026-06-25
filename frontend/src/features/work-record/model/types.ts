import type {
  BedZone,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
  WorkRecordTargetType,
} from "@/entities/farm/types";

export type WorkRecordManagerProps = {
  initialRecords: WorkRecord[];
  houses: HouseStatusSummary[];
  workTypes: string[];
};

export type WorkRecordFormState = {
  workType: string;
  workDate: string;
  targetType: WorkRecordTargetType;
  houseId: string;
  physicalBedId: string;
  bedZoneId: string;
  orchidGroupId: string;
  materialName: string;
  dilutionRatio: string;
  quantity: string;
  worker: string;
  memo: string;
};

export type WorkRecordTargetOptions = {
  physicalBeds: PhysicalBed[];
  bedZones: BedZone[];
  orchidGroups: OrchidGroup[];
};

export type CreateWorkRecordPayload = {
  workType: string;
  workDate: string;
  targetType: WorkRecordTargetType;
  targetId: number | null;
  materialName: string | null;
  dilutionRatio: string | null;
  quantity: string | null;
  worker: string | null;
  memo: string | null;
};
