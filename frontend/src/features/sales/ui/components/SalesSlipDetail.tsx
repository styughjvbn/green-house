"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { Copy, Printer } from "lucide-react";
import type { SalesSlip } from "@/entities/farm/types";

export function SalesSlipDetail({
  salesSlip,
}: {
  salesSlip: SalesSlip | null;
}) {
  if (!salesSlip) {
    return (
      <section className="rounded-md border border-[#dfe5dc] bg-white p-5 text-sm text-[#5c6a60] shadow-sm">
        선택한 전표가 없습니다.
      </section>
    );
  }

  const supplyAmount = Math.round(salesSlip.totalAmount / 1.1);
  const vatAmount = salesSlip.totalAmount - supplyAmount;

  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-bold text-[#17251b]">전표 상세</h2>
        <div className="flex gap-2">
          <Link
            className="inline-flex h-9 items-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm font-semibold text-[#344138]"
            href={`/print/sales-slips/${salesSlip.id}`}
          >
            <Printer className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            인쇄(미리보기)
          </Link>
          <button
            className="inline-flex h-9 items-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm font-semibold text-[#344138]"
            type="button"
          >
            <Copy className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            전표 복사
          </button>
        </div>
      </div>

      <div className="mt-4 rounded-md border border-[#dfe5dc] bg-[#fbfcfa] p-4">
        <div className="grid gap-4 md:grid-cols-4">
          <InfoLabel label="전표번호" strong value={salesSlip.slipNumber} />
          <InfoLabel label="판매일자" value={salesSlip.saleDate} />
          <InfoLabel label="입금 상태" value={salesSlip.paymentStatus} />
          <InfoLabel label="판매 상태" value={salesSlip.salesStatus} />
        </div>
      </div>

      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <InfoBox title="거래처 정보">
          <Description label="거래처명" value={salesSlip.customer.name} />
          <Description label="대표자명" value={salesSlip.customer.ownerName} />
          <Description label="연락처" value={salesSlip.customer.phone} />
          <Description label="주소" value={salesSlip.customer.address} />
          <Description label="메모" value={salesSlip.customer.memo} />
        </InfoBox>

        <InfoBox title="전표 정보">
          <Description label="결제 방식" value={salesSlip.paymentMethod} />
          <Description label="담당자" value="관리자" />
          <Description label="메모" value={salesSlip.memo} />
        </InfoBox>
      </div>

      <div className="mt-4">
        <h3 className="text-base font-bold text-[#17251b]">판매 품목</h3>
        <div className="mt-2 overflow-x-auto rounded-md border border-[#dfe5dc]">
          <table className="w-full min-w-[640px] border-collapse text-sm">
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
                <th className="px-3 py-3 text-left font-semibold">메모</th>
              </tr>
            </thead>
            <tbody>
              {salesSlip.items.map((item, index) => (
                <tr key={item.id} className="border-t border-[#edf0ec]">
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
                  <td className="px-3 py-3">{item.memo ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-3 items-center rounded-md border border-[#dfe5dc] bg-white p-4 text-sm">
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
    </section>
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
