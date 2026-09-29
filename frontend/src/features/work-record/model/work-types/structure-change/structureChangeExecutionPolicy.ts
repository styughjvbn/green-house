export function isStructureChangeSourceLocked(recordMode: boolean) {
  return recordMode;
}

export function allowsStructureChangeIncrease(workTypeCode: string) {
  return workTypeCode === "REPOT" || workTypeCode === "DIVIDE";
}
