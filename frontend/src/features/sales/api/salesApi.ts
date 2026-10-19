import type {
  DirectSaleChangeState,
  DirectReturnArrivalState,
} from "./directChangeTypes";
import type { DirectChangePendingRequest } from "../lib/directChangeRequests";
import { fetchApi, requestApi, requestApiBlob } from "@/shared/api/client";
import type {
  BusinessPartner,
  BusinessPartnerOption,
  BusinessPartnerOptionPage,
  BusinessPartnerPage,
  PartnerPaymentEventPage,
  PaymentTargetType,
  PartnerSettlementSettings,
  SalesOrchidGroupOption,
  SalesSlip,
  SalesSlipPage,
} from "@/entities/farm/types";
import type {
  AuctionLot,
  AuctionLotPage,
  AuctionTrackingSummary,
  AuctionShipmentOption,
  AuctionProceeds,
  AuctionProceedsPage,
} from "@/entities/farm/types";
import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "../model/types";
import type {
  AuctionLotStatusPayload,
  AuctionQuantityAdjustmentPayload,
  AuctionReturnPayload,
  CreateAuctionResultPayload,
  CreateBusinessPartnerPayload,
  CreateSalesSlipPayload,
  ManualPaymentPayload,
  PartnerSettlementSettingsPayload,
  SalesCreationRequestKey,
  UpdateBusinessPartnerPayload,
} from "./types";

async function requestJson<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
): Promise<T> {
  return requestApi<T>(path, init, fallbackMessage);
}

export function getBusinessPartnerOptions(
  keyword: string,
  page: number,
  auctionHouse?: boolean,
  active?: boolean,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    keyword,
    page: String(page),
    size: "10",
  });
  if (auctionHouse != null) params.set("auctionHouse", String(auctionHouse));
  if (active != null) params.set("active", String(active));
  return fetchApi<BusinessPartnerOptionPage>(
    `/business-partners/options?${params}`,
    { signal },
  );
}

export function getBusinessPartnerOption(
  partnerId: number,
  signal?: AbortSignal,
) {
  return fetchApi<BusinessPartnerOption>(
    `/business-partners/${partnerId}/option`,
    { signal },
  );
}

export function getBusinessPartnerPage(
  filters?: Partial<BusinessPartnerFilterState>,
  page = 0,
  size = 10,
) {
  const params = new URLSearchParams();
  const keyword = filters?.keyword?.trim();
  if (keyword) {
    params.set("keyword", keyword);
  }
  if (filters?.partnerType) {
    params.set("partnerType", filters.partnerType);
  }
  if (filters?.active) {
    params.set("active", String(filters.active === "ACTIVE"));
  }
  params.set("page", String(page));
  params.set("size", String(size));
  return fetchApi<BusinessPartnerPage>(`/business-partners/page?${params}`);
}

export function getSalesSlipPage(
  filters?: Partial<SalesFilterState>,
  page = 0,
  size = 10,
) {
  const params = new URLSearchParams();
  Object.entries(filters ?? {}).forEach(([key, value]) => {
    if (value != null && value !== "") params.set(key, String(value));
  });
  params.set("page", String(page));
  params.set("size", String(size));
  return fetchApi<SalesSlipPage>(`/sales-slips/page?${params}`);
}

export function getSalesSlip(salesSlipId: number) {
  return fetchApi<SalesSlip>(`/sales-slips/${salesSlipId}`);
}

export function updateSalesSlip(
  salesSlipId: number,
  payload: CreateSalesSlipPayload,
) {
  return requestJson<SalesSlip>(
    `/sales-slips/${salesSlipId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "판매 전표를 수정하지 못했습니다.",
  );
}

export function searchSalesOrchidGroups(
  keyword: string,
  varietyId?: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams();
  if (keyword.trim()) params.set("keyword", keyword.trim());
  if (varietyId != null) params.set("varietyId", String(varietyId));
  const query = params.size > 0 ? `?${params}` : "";
  return fetchApi<SalesOrchidGroupOption[]>(
    `/sales/orchid-groups/search${query}`,
    { signal },
  );
}

export function getAuctionShipmentOptions() {
  return fetchApi<AuctionShipmentOption[]>("/sales-slips/auction-shipments");
}

export function getAuctionLots(
  filters?: Partial<AuctionFilterState>,
  page = 0,
  size = 20,
) {
  const params = new URLSearchParams();
  Object.entries(filters ?? {}).forEach(([key, value]) => {
    if (value !== "" && value !== false && value != null) {
      params.set(key, String(value));
    }
  });
  params.set("page", String(page));
  params.set("size", String(size));
  const query = params.size > 0 ? `?${params}` : "";
  return fetchApi<AuctionLotPage>(`/auction-lots${query}`);
}

export function getAuctionTrackingSummary() {
  return fetchApi<AuctionTrackingSummary>("/auction-tracking/summary");
}

export function getAuctionProceedsPage(
  page: number,
  size: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<AuctionProceedsPage>(`/auction-proceeds/page?${params}`, {
    signal,
  });
}

export function getAuctionProceeds(id: number, signal?: AbortSignal) {
  return fetchApi<AuctionProceeds>(`/auction-proceeds/${id}`, { signal });
}

export type { ManualPaymentPayload } from "./types";

export function confirmAuctionProceedsPayment(
  id: number,
  payload: ManualPaymentPayload,
) {
  return requestJson<AuctionProceeds>(
    `/auction-proceeds/${id}/confirm-payment`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입금을 확인하지 못했습니다.",
  );
}

export function confirmSalesSlipPayment(
  salesSlipId: number,
  payload: ManualPaymentPayload,
) {
  return requestJson<SalesSlip>(
    `/sales-slips/${salesSlipId}/confirm-payment`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입금을 확인하지 못했습니다.",
  );
}

export function getTargetPaymentHistoryPage(
  targetType: PaymentTargetType,
  targetId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return fetchApi<PartnerPaymentEventPage>(
    `/payment-targets/${targetType}/${targetId}/history?${params}`,
    { signal },
  );
}

export function confirmAuctionReturn(
  lotId: number,
  payload: AuctionReturnPayload,
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/confirm-return`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "반환 상태를 확정하지 못했습니다.",
  );
}

export function adjustAuctionQuantity(
  lotId: number,
  payload: AuctionQuantityAdjustmentPayload,
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/adjust-quantity`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "수량을 보정하지 못했습니다.",
  );
}

export function createAuctionResult(
  lotId: number,
  payload: CreateAuctionResultPayload,
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/results`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "경매 결과를 저장하지 못했습니다.",
  );
}

export function changeAuctionLotStatus(
  lotId: number,
  payload: AuctionLotStatusPayload,
) {
  return requestJson<AuctionLot>(
    `/auction-lots/${lotId}/status`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "상태를 변경하지 못했습니다.",
  );
}

export function createBusinessPartner(
  payload: CreateBusinessPartnerPayload,
): Promise<BusinessPartner> {
  return requestJson<BusinessPartner>(
    "/business-partners",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "거래처를 저장하지 못했습니다.",
  );
}

export function updateBusinessPartner(
  partnerId: number,
  payload: UpdateBusinessPartnerPayload,
): Promise<BusinessPartner> {
  return requestJson<BusinessPartner>(
    `/business-partners/${partnerId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "거래처를 수정하지 못했습니다.",
  );
}

export function getPartnerSettlementSettings(partnerId: number) {
  return fetchApi<PartnerSettlementSettings>(
    `/business-partners/${partnerId}/settlement-settings`,
  );
}

export function updatePartnerSettlementSettings(
  partnerId: number,
  payload: PartnerSettlementSettingsPayload,
) {
  return requestJson<PartnerSettlementSettings>(
    `/business-partners/${partnerId}/settlement-settings`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "정산 설정을 저장하지 못했습니다.",
  );
}

export function createSalesSlip(
  payload: CreateSalesSlipPayload,
  idempotencyKey: SalesCreationRequestKey,
): Promise<SalesSlip> {
  return requestJson<SalesSlip>(
    "/sales-slips",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(payload),
    },
    "판매 전표를 저장하지 못했습니다.",
  );
}

export function changeSalesSlipStatus(
  salesSlipId: number,
  payload: {
    salesStatus: string;
    memo: string | null;
  },
) {
  return requestJson<SalesSlip>(
    `/sales-slips/${salesSlipId}/sales-status`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "출고·출하 처리를 변경하지 못했습니다.",
  );
}

export function getAuctionLot(lotId: number, signal?: AbortSignal) {
  return fetchApi<AuctionLot>(`/auction-lots/${lotId}`, { signal });
}

export function getAuctionFollowUp(lotId: number, signal?: AbortSignal) {
  return fetchApi<import("./types").AuctionFollowUp>(
    `/auction-lots/${lotId}/follow-up`,
    { signal },
  );
}

export function getAuctionArrivals(
  lotId: number,
  page: number,
  signal?: AbortSignal,
) {
  return fetchApi<
    import("@/shared/api/page").Page<import("./types").AuctionArrival>
  >(`/auction-lots/${lotId}/arrivals?page=${page}&size=10`, { signal });
}

export function decideAuctionFollowUp(
  lotId: number,
  payload: import("./types").AuctionFollowUpPayload,
) {
  return requestJson<import("./types").AuctionFollowUpResult>(
    `/auction-lots/${lotId}/follow-up`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "후속 처리를 저장하지 못했습니다.",
  );
}

export function recordAuctionArrival(
  lotId: number,
  payload: import("./types").AuctionArrivalPayload,
) {
  return requestJson<import("./types").AuctionFollowUpResult>(
    `/auction-lots/${lotId}/arrivals`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "실제 도착을 저장하지 못했습니다.",
  );
}

export function cancelAuctionArrival(
  lotId: number,
  arrivalId: number,
  payload: import("./types").AuctionArrivalCancellationPayload,
) {
  return requestJson<import("./types").AuctionFollowUpResult>(
    `/auction-lots/${lotId}/arrivals/${arrivalId}/cancel`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "도착 기록을 취소하지 못했습니다.",
  );
}

export function getAuctionReturnHouses(signal?: AbortSignal) {
  return fetchApi<import("@/entities/farm/types").House[]>("/houses", {
    signal,
  });
}

export function getAuctionReturnVarieties(signal?: AbortSignal) {
  return fetchApi<
    Required<
      import("@/shared/api/generated/openapi").components["schemas"]["VarietyGeneraResponse"]
    >
  >("/varieties/genera", { signal });
}

export function getSalesRecordedVarieties(
  keyword: string,
  page: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    keyword: keyword.trim(),
    page: String(page),
    size: "20",
  });
  return fetchApi<
    import("@/shared/api/page").Page<
      import("@/entities/farm/types").VarietyOption
    >
  >(`/varieties?${params}`, { signal });
}

export function getUnassignedReceiptPage(
  partnerId: number,
  page: number,
  signal?: AbortSignal,
) {
  return fetchApi<
    import("@/shared/api/page").Page<import("./types").UnassignedReceipt>
  >(
    `/partner-payment-events/page?partnerId=${partnerId}&targetType=NONE&page=${page}&size=10`,
    { signal },
  );
}
export function getPaymentBalance(partnerId: number, signal?: AbortSignal) {
  return fetchApi<import("./types").PartnerPaymentBalance>(
    `/business-partners/${partnerId}/balance-summary`,
    { signal },
  );
}
export function receiveUnassignedPayment(
  partnerId: number,
  payload: ManualPaymentPayload,
) {
  return requestJson<import("./types").UnassignedReceipt>(
    `/business-partners/${partnerId}/payment-receipts`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "수납을 저장하지 못했습니다.",
  );
}
export function cancelUnassignedPayment(
  partnerId: number,
  receiptId: number,
  payload: import("./types").CancelUnassignedReceiptPayload,
) {
  return requestJson<import("./types").UnassignedReceipt>(
    `/business-partners/${partnerId}/payment-receipts/${receiptId}/cancel`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "수납 입력을 취소하지 못했습니다.",
  );
}

export function getAllocationReceipts(
  partnerId: number,
  page: number,
  signal?: AbortSignal,
) {
  return fetchApi<
    import("@/shared/api/page").Page<import("./types").PaymentReceipt>
  >(`/business-partners/${partnerId}/payment-receipts?page=${page}&size=10`, {
    signal,
  });
}
export function getAllocationReceipt(
  partnerId: number,
  id: number,
  signal?: AbortSignal,
) {
  return fetchApi<import("./types").PaymentReceipt>(
    `/business-partners/${partnerId}/payment-receipts/${id}`,
    { signal },
  );
}
export function getReceiptAllocations(
  partnerId: number,
  id: number,
  page: number,
  signal?: AbortSignal,
) {
  return fetchApi<
    import("@/shared/api/page").Page<import("./types").PaymentAllocation>
  >(
    `/business-partners/${partnerId}/payment-receipts/${id}/allocations?page=${page}&size=10`,
    { signal },
  );
}
export function getAllocationMetadata(partnerId: number, signal?: AbortSignal) {
  return fetchApi<import("./types").AllocationMetadata>(
    `/payment-allocation-metadata?partnerId=${partnerId}`,
    { signal },
  );
}
export function getAllocationTargets(
  partnerId: number,
  type: import("./types").AllocationTargetType,
  keyword: string,
  page: number,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    targetType: type,
    keyword,
    page: String(page),
    size: "10",
  });
  return fetchApi<
    import("@/shared/api/page").Page<import("./types").AllocationTarget>
  >(`/business-partners/${partnerId}/payment-allocation-targets?${query}`, {
    signal,
  });
}
export function submitPaymentRequest(
  partnerId: number,
  request: import("../lib/receiptRequest").ReceiptRequest,
) {
  if (request.operation === "RECEIVE")
    return receiveUnassignedPayment(partnerId, request.payload);
  if (request.operation === "CANCEL")
    return cancelUnassignedPayment(
      partnerId,
      request.receiptId,
      request.payload,
    );
  return requestJson<import("./types").AllocationResult>(
    `/business-partners/${partnerId}/${request.operation === "ALLOCATE" ? "payment-allocations" : "payment-allocation-corrections"}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request.payload),
    },
    "배분을 저장하지 못했습니다.",
  );
}

// Commercial changes keep original document items, stock and actual cash history intact.
export function getDirectSaleChanges(
  id: number,
  page: number,
  signal?: AbortSignal,
) {
  return requestApi<DirectSaleChangeState>(
    `/sales-slips/${id}/direct-changes?page=${page}&size=10`,
    { signal },
  );
}
export function submitDirectSaleChange(
  id: number,
  request: DirectChangePendingRequest,
) {
  const path =
    request.operation === "ARRIVAL"
      ? `${request.returnId}/arrivals`
      : request.operation === "ARRIVAL_CANCEL"
        ? `return-arrivals/${request.arrivalId}/cancel`
        : request.operation === "CANCEL"
          ? `${request.changeId}/cancel`
          : request.operation === "RETURN"
            ? "returns"
            : "price-adjustments";
  return requestApi(
    `/sales-slips/${id}/direct-changes/${path}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": request.key,
      },
      body: JSON.stringify(request.payload),
    },
    "가격 조정·반품을 기록하지 못했습니다.",
  );
}

export function getDirectReturnArrivals(
  id: number,
  page: number,
  signal?: AbortSignal,
) {
  return fetchApi<DirectReturnArrivalState>(
    `/sales-slips/${id}/direct-changes/return-arrivals?page=${page}&size=10`,
    { signal },
  );
}

export function readSalesSlipPhoto(
  photo: import("./types").SalesSlipPhotoInput,
  signal?: AbortSignal,
) {
  const bytes = Uint8Array.from(atob(photo.contentBase64), (char) =>
    char.charCodeAt(0),
  );
  const body = new FormData();
  body.append(
    "image",
    new Blob([bytes], { type: photo.contentType }),
    photo.fileName,
  );
  return requestApi<import("./types").SalesSlipPhotoExtraction>(
    "/ai/slips/extract",
    { method: "POST", body, signal },
  );
}
export function openSalesSlipPhotoSession(signal?: AbortSignal) {
  return requestApi<
    import("@/shared/api/generated/openapi").components["schemas"]["AiSlipSession"]
  >("/ai/slips/sessions", { method: "POST", signal });
}
export function closeSalesSlipPhotoSession(sessionId: string) {
  return requestApi<void>(
    `/ai/slips/sessions/${encodeURIComponent(sessionId)}`,
    { method: "DELETE" },
  );
}
export function removeSalesSlipSessionPhoto(
  sessionId: string,
  photoId: string,
) {
  return requestApi<void>(
    `/ai/slips/sessions/${encodeURIComponent(sessionId)}/photos/${encodeURIComponent(photoId)}`,
    { method: "DELETE" },
  );
}
export function cancelSalesSlipPhotoReading(
  sessionId: string,
  requestId: string,
) {
  return requestApi<void>(
    `/ai/slips/sessions/${encodeURIComponent(sessionId)}/requests/${encodeURIComponent(requestId)}/cancel`,
    { method: "POST" },
  );
}
export function readSalesSlipPhotos(
  sessionId: string,
  photos: {
    photoId: string;
    photo: import("./types").SalesSlipPhotoInput;
  }[],
  signal?: AbortSignal,
  requestId?: string,
) {
  const body = new FormData();
  body.append(
    "metadata",
    new Blob(
      [
        JSON.stringify({
          photos: photos.map((p) => ({
            photoId: p.photoId,
          })),
        }),
      ],
      { type: "application/json" },
    ),
  );
  for (const entry of photos) {
    const bytes = Uint8Array.from(atob(entry.photo.contentBase64), (char) =>
      char.charCodeAt(0),
    );
    body.append(
      "photos",
      new Blob([bytes], { type: entry.photo.contentType }),
      entry.photo.fileName,
    );
  }
  return requestApi<
    import("@/shared/api/generated/openapi").components["schemas"]["SlipConversationResult"]
  >(`/ai/slips/sessions/${encodeURIComponent(sessionId)}/extract`, {
    method: "POST",
    body,
    signal,
    headers: requestId ? { "X-AI-Request-Id": requestId } : undefined,
  });
}
export function getSalesSlipPhotoInfo(id: number) {
  return fetchApi<import("./types").SalesSlipPhotoInfo | null>(
    `/sales-slips/${id}/source-photo/info`,
  );
}
export function getSalesSlipPhoto(id: number, signal?: AbortSignal) {
  return fetchApi<import("./types").SalesSlipPhotoContent>(
    `/sales-slips/${id}/source-photo`,
    { signal },
  );
}

export function prepareSalesSlipPhoto(
  photo: import("./types").SalesSlipPhotoInput,
  rotationDegrees: number,
  signal?: AbortSignal,
) {
  const bytes = Uint8Array.from(atob(photo.contentBase64), (char) =>
    char.charCodeAt(0),
  );
  const body = new FormData();
  body.append(
    "image",
    new Blob([bytes], { type: photo.contentType }),
    photo.fileName,
  );
  body.append("rotationDegrees", String(rotationDegrees));
  return requestApi<
    import("@/shared/api/generated/openapi").components["schemas"]["SalesSlipImagePreview"]
  >(
    "/sales-slips/image-previews",
    { method: "POST", body, signal },
    "전표를 자동으로 찾지 못했습니다.",
  );
}

export function getSalesSlipPreparedImage(
  previewId: string,
  imageId: string,
  signal?: AbortSignal,
) {
  return requestApiBlob(
    `/sales-slips/image-previews/${encodeURIComponent(previewId)}/images/${encodeURIComponent(imageId)}`,
    { signal },
    "문서 사진을 가져오지 못했습니다.",
  );
}

export function removeSalesSlipImagePreview(id: string) {
  return requestApi<void>(
    `/sales-slips/image-previews/${encodeURIComponent(id)}`,
    { method: "DELETE" },
  );
}
