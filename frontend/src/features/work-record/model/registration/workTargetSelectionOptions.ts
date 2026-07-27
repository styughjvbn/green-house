import type { House } from "@/entities/farm/types";
import type { WorkTargetSelectionOptions } from "../types";

const EXCLUDED_GROUP_STATUSES = new Set(["종료", "폐기", "판매 완료"]);

export function deriveWorkTargetSelectionOptions(
  houses: House[],
): WorkTargetSelectionOptions {
  const bedZones = houses.flatMap((house) =>
    house.physicalBeds.flatMap((bed) => bed.bedZones),
  );
  return {
    bedZones: bedZones.filter((zone) => zone.active),
    orchidGroups: bedZones
      .flatMap((zone) => zone.orchidGroups)
      .filter(
        (group) =>
          group.quantity > 0 && !EXCLUDED_GROUP_STATUSES.has(group.status),
      ),
  };
}
