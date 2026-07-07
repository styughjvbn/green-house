"use client";

import type {
  BedZone,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
  WorkRecordTargetType,
} from "@/entities/farm/types";
import type { WorkRecordFormState } from "../../model/types";
import { SelectField } from "./FormFields";

type TargetSelectorFieldsProps = {
  bedZones: BedZone[];
  form: WorkRecordFormState;
  houses: HouseStatusSummary[];
  orchidGroups: OrchidGroup[];
  physicalBeds: PhysicalBed[];
  safeBedZoneId: string;
  safeOrchidGroupId: string;
  safePhysicalBedId: string;
  onChange: <K extends keyof WorkRecordFormState>(
    field: K,
    value: WorkRecordFormState[K],
  ) => void;
};

export function TargetSelectorFields({
  bedZones,
  form,
  houses,
  orchidGroups,
  physicalBeds,
  safeBedZoneId,
  safeOrchidGroupId,
  safePhysicalBedId,
  onChange,
}: TargetSelectorFieldsProps) {
  return (
    <>
      <SelectField
        label="대상 유형"
        value={form.targetType}
        onChange={(value) =>
          onChange("targetType", value as WorkRecordTargetType)
        }
      >
        <option value="FARM">전체 농장</option>
        <option value="HOUSE">동</option>
        <option value="PHYSICAL_BED">다이</option>
        <option value="BED_ZONE">논리 구역</option>
        <option value="ORCHID_GROUP">난 묶음</option>
      </SelectField>

      {form.targetType !== "FARM" ? (
        <SelectField
          label="동"
          value={form.houseId}
          onChange={(value) => onChange("houseId", value)}
        >
          {houses.map((house) => (
            <option key={house.houseId} value={house.houseId}>
              {house.houseNumber}동
            </option>
          ))}
        </SelectField>
      ) : null}

      {form.targetType === "PHYSICAL_BED" ? (
        <SelectField
          label="다이"
          value={safePhysicalBedId}
          onChange={(value) => onChange("physicalBedId", value)}
        >
          {physicalBeds.map((bed) => (
            <option key={bed.id} value={bed.id}>
              {bed.number}다이
            </option>
          ))}
        </SelectField>
      ) : null}

      {form.targetType === "BED_ZONE" ? (
        <SelectField
          label="구역"
          value={safeBedZoneId}
          onChange={(value) => onChange("bedZoneId", value)}
        >
          {bedZones.map((zone) => (
            <option key={zone.id} value={zone.id}>
              {zone.physicalBedNumber}다이 {zone.side === "LEFT" ? "좌" : "우"}
            </option>
          ))}
        </SelectField>
      ) : null}

      {form.targetType === "ORCHID_GROUP" ? (
        <SelectField
          label="난 묶음"
          value={safeOrchidGroupId}
          onChange={(value) => onChange("orchidGroupId", value)}
        >
          {orchidGroups.map((group) => (
            <option key={group.id} value={group.id}>
              {group.physicalBedNumber}다이 {group.bedZoneName} /{" "}
              {group.varietyName}
            </option>
          ))}
        </SelectField>
      ) : null}
    </>
  );
}
