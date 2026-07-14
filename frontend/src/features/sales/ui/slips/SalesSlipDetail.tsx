import type { ReactNode } from "react";
import { Ban, Copy, Pencil, Printer, Truck } from "lucide-react";
import type { SalesSlip } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { confirmSalesSlipPayment } from "../../api/salesApi";
import { ManualPaymentPanel } from "../auction/ManualPaymentPanel";
import {
  SalesDetailCard,
  SalesDetailActionButton,
  SalesDetailEmpty,
  SalesDetailHeader,
} from "../common/SalesDetailCard";

export function SalesSlipDetail({
  loading = false,
  salesSlip,
  updatingSalesStatus,
  onCancelSalesSlip,
  onEditSalesSlip,
  onCompleteSalesSlip,
  onPaymentConfirmed,
}: {
  loading?: boolean;
  salesSlip: SalesSlip | null;
  updatingSalesStatus: boolean;
  onCancelSalesSlip: (salesSlipId: number) => Promise<void>;
  onEditSalesSlip: (salesSlipId: number) => void;
  onCompleteSalesSlip: (salesSlipId: number) => Promise<void>;
  onPaymentConfirmed: (salesSlip: SalesSlip) => void;
}) {
  if (loading) {
    return <SalesDetailEmpty>전표 상세를 불러오는 중입니다.</SalesDetailEmpty>;
  }

  if (!salesSlip) {
    return <SalesDetailEmpty>선택한 전표가 없습니다.</SalesDetailEmpty>;
  }

  const supplyAmount = Math.round(salesSlip.totalAmount / 1.1);
  const vatAmount = salesSlip.totalAmount - supplyAmount;
  const canComplete =
    salesSlip.salesStatus !== "출고 완료" &&
    salesSlip.salesStatus !== "출하 완료" &&
    salesSlip.salesStatus !== "취소";
  const canEdit =
    salesSlip.salesType === "DIRECT" &&
    salesSlip.salesStatus === "작성중" &&
    salesSlip.paidAmount === 0;
  const canCancel = salesSlip.salesStatus !== "취소";

  return (
    <SalesDetailCard>
      <SalesDetailHeader
        eyebrow={`LOT #${salesSlip.slipNumber}`}
        title="전표 상세"
        actions={
          <>
            {canEdit ? (
              <SalesDetailActionButton
                icon={Pencil}
                onClick={() => onEditSalesSlip(salesSlip.id)}
              >
                전표 수정
              </SalesDetailActionButton>
            ) : null}
            {canComplete ? (
              <SalesDetailActionButton
                disabled={updatingSalesStatus}
                icon={Truck}
                tone="primary"
                onClick={() => void onCompleteSalesSlip(salesSlip.id)}
              >
                {salesSlip.salesType === "AUCTION" ? "출하 완료" : "출고 완료"}
              </SalesDetailActionButton>
            ) : null}
            {canCancel ? (
              <SalesDetailActionButton
                disabled={updatingSalesStatus}
                icon={Ban}
                tone="danger"
                onClick={() => {
                  if (
                    window.confirm(
                      "이 전표를 취소하시겠습니까? 취소 후 되돌릴 수 없습니다.",
                    )
                  ) {
                    void onCancelSalesSlip(salesSlip.id);
                  }
                }}
              >
                전표 취소
              </SalesDetailActionButton>
            ) : null}
            <SalesDetailActionButton
              href={`/print/sales-slips/${salesSlip.id}`}
              icon={Printer}
            >
              인쇄(미리보기)
            </SalesDetailActionButton>
            <SalesDetailActionButton icon={Copy}>
              전표 복사
            </SalesDetailActionButton>
          </>
        }
      />

      <div className="p-4">
        <div className="rounded-md border border-[#dfe5dc] bg-[#fbfcfa] p-4">
          <div className="grid gap-4 lg:grid-cols-4">
            <InfoLabel
              label="판매 유형"
              value={
                salesSlip.salesType === "AUCTION" ? "경매 판매" : "일반 판매"
              }
            />
            <InfoLabel
              label="판매일자"
              value={formatShortDate(salesSlip.saleDate)}
            />
            <InfoLabel label="입금 상태" value={salesSlip.paymentStatus} />
            <InfoLabel label="판매 상태" value={salesSlip.salesStatus} />
          </div>
        </div>

        <div className="mt-3 grid gap-3 lg:grid-cols-2">
          <InfoBox title="거래처 정보">
            <Description label="거래처명" value={salesSlip.partner.name} />
            <Description label="대표자명" value={salesSlip.partner.ownerName} />
            <Description label="연락처" value={salesSlip.partner.phone} />
            <Description label="주소" value={salesSlip.partner.address} />
            <Description label="메모" value={salesSlip.partner.memo} />
          </InfoBox>

          <InfoBox title="전표 정보">
            <Description label="경매장" value={salesSlip.auctionMarket} />
            <Description label="결제 방식" value={salesSlip.paymentMethod} />
            <Description label="담당자" value="관리자" />
            <Description label="메모" value={salesSlip.memo} />
          </InfoBox>
        </div>

        <div className="mt-4">
          <h3 className="text-base font-bold text-[#17251b]">판매 품목</h3>
          <div className="mt-2 overflow-x-auto rounded-md border border-[#dfe5dc]">
            <table className="w-full min-w-[760px] border-collapse text-sm">
              <thead className="bg-[#fbfcfa] text-[#435047]">
                <tr>
                  <th className="px-3 py-3 text-left font-semibold">No.</th>
                  <th className="px-3 py-3 text-left font-semibold">
                    품종명 / 속
                  </th>
                  <th className="px-3 py-3 text-left font-semibold">규격</th>
                  <th className="px-3 py-3 text-right font-semibold">수량</th>
                  <th className="px-3 py-3 text-right font-semibold">단가</th>
                  <th className="px-3 py-3 text-right font-semibold">금액</th>
                  <th className="px-3 py-3 text-left font-semibold">
                    배분 난 묶음
                  </th>
                  <th className="px-3 py-3 text-left font-semibold">메모</th>
                </tr>
              </thead>
              <tbody>
                {salesSlip.items.map((item, index) => (
                  <tr
                    key={item.id}
                    className="border-t border-[#edf0ec] align-top"
                  >
                    <td className="px-3 py-3">{index + 1}</td>
                    <td className="px-3 py-3">
                      <p className="font-semibold">{item.itemName}</p>
                      <p className="text-xs text-[#6a766e]">
                        {item.genus ?? "-"}
                      </p>
                    </td>
                    <td className="px-3 py-3">{item.spec ?? "-"}</td>
                    <td className="px-3 py-3 text-right">{item.quantity}</td>
                    <td className="px-3 py-3 text-right">
                      {item.unitPrice.toLocaleString()}
                    </td>
                    <td className="px-3 py-3 text-right">
                      {item.amount.toLocaleString()}
                    </td>
                    <td className="px-3 py-3">
                      <div className="space-y-2">
                        {item.allocations.map((allocation) => (
                          <div
                            key={allocation.id}
                            className="rounded-md bg-[#f6f8f5] px-2 py-2"
                          >
                            <p className="font-semibold text-[#17251b]">
                              {allocation.varietyName}{" "}
                              {allocation.allocatedQuantity}분
                            </p>
                            <p className="text-xs text-[#6a766e]">
                              {allocation.houseNumber}동{" "}
                              {allocation.physicalBedNumber}
                              배드 {allocation.bedZoneName}
                            </p>
                          </div>
                        ))}
                      </div>
                    </td>
                    <td className="px-3 py-3">{item.memo ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="mt-3 grid gap-3 rounded-md border border-[#dfe5dc] bg-white p-4 text-sm md:grid-cols-3 md:items-center">
          <Amount label="공급가액" value={supplyAmount} />
          <Amount label="부가세" value={vatAmount} />
          <div className="text-right">
            <p className="font-semibold text-[#344138]">총 금액</p>
            <p className="mt-1 text-3xl font-bold text-[#159447]">
              {salesSlip.totalAmount.toLocaleString()}
              <span className="ml-1 text-sm text-[#17251b]">원</span>
            </p>
          </div>
        </div>

        {salesSlip.salesType === "DIRECT" &&
        salesSlip.salesStatus !== "취소" ? (
          <div className="mt-3 rounded-md border border-[#dfe5dc]">
            <ManualPaymentPanel
              key={salesSlip.id}
              targetType="SALES_SLIP"
              targetId={salesSlip.id}
              remainingAmount={salesSlip.remainingAmount}
              expectedPaymentDate={salesSlip.expectedPaymentDate}
              onConfirm={async (payload) => {
                onPaymentConfirmed(
                  await confirmSalesSlipPayment(salesSlip.id, payload),
                );
              }}
            />
          </div>
        ) : null}
      </div>
    </SalesDetailCard>
  );
}

function InfoLabel({
  label,
  strong = false,
  value,
}: {
  label: string;
  strong?: boolean;
  value: string;
}) {
  return (
    <div>
      <p className="text-xs font-semibold text-[#6a766e]">{label}</p>
      <p className={`mt-1 ${strong ? "text-2xl font-bold" : "font-semibold"}`}>
        {value}
      </p>
    </div>
  );
}

function InfoBox({ children, title }: { children: ReactNode; title: string }) {
  return (
    <div className="rounded-md border border-[#dfe5dc] p-4">
      <h3 className="font-bold text-[#17251b]">{title}</h3>
      <dl className="mt-3 space-y-2 text-sm">{children}</dl>
    </div>
  );
}

function Description({
  label,
  value,
}: {
  label: string;
  value: string | null;
}) {
  return (
    <div className="grid grid-cols-[72px_minmax(0,1fr)] gap-3">
      <dt className="text-[#6a766e]">{label}</dt>
      <dd className="truncate font-medium text-[#344138]">{value ?? "-"}</dd>
    </div>
  );
}

function Amount({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="font-semibold text-[#6a766e]">{label}</p>
      <p className="mt-1 text-lg font-semibold text-[#344138]">
        {value.toLocaleString()}
      </p>
    </div>
  );
}
