import type { SalesSlip } from "@/entities/farm/types";

export function salesDispatchLabel(
  value: string,
  salesType?: SalesSlip["salesType"],
) {
  if (value !== "작성중") return value;
  if (salesType === "AUCTION") return "출하 대기";
  if (salesType === "DIRECT") return "출고 대기";
  return "출고·출하 대기";
}
