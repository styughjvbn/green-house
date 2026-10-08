import { createUuid } from "@/shared/lib/id";
import { createReceiptRequests } from "../lib/receiptRequest";
export const paymentRequests = createReceiptRequests(createUuid, () =>
  typeof window === "undefined" ? null : window.sessionStorage,
);
