import type { WorkCorrectionDetail } from "../model/types";

export type ResultCorrection = {
  event: WorkCorrectionDetail;
  adjustment: NonNullable<WorkCorrectionDetail["adjustments"]>[number];
};

export function groupResultCorrections(
  events: readonly WorkCorrectionDetail[],
) {
  const byGroup = new Map<number, ResultCorrection[]>();
  for (const event of events) {
    for (const adjustment of event.adjustments ?? []) {
      const id = adjustment.orchidGroupId;
      if (id == null) continue;
      const rows = byGroup.get(id) ?? [];
      rows.push({ event, adjustment });
      byGroup.set(id, rows);
    }
  }
  return byGroup;
}
