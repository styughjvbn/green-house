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

export function salesPaymentLabel(value: string) {
  return (
    (
      {
        미입금: "미수납",
        "부분 입금": "일부 수납",
        부분입금: "일부 수납",
        "입금 완료": "수납 완료",
      } as Record<string, string>
    )[value] ?? value
  );
}
