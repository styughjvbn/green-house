"use client";

import Link from "next/link";
import type { SalesSlip } from "@/entities/farm/types";

type SalesSlipPrintViewProps = {
  slip: SalesSlip;
};

export function SalesSlipPrintView({ slip }: SalesSlipPrintViewProps) {
  return (
    <main className="print-root mx-auto max-w-[900px] space-y-4">
      <div className="print-hidden flex flex-wrap items-center justify-between gap-3 rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <div>
          <p className="text-sm font-semibold text-[#3d6f91]">A5 출력</p>
          <h1 className="mt-1 text-2xl font-semibold">판매 전표 출력</h1>
          <p className="mt-1 text-sm text-[#5c6a60]">
            아래 전표 영역만 인쇄됩니다.
          </p>
        </div>
        <div className="flex gap-2">
          <Link
            className="inline-flex items-center rounded-md border border-[#cfd8cc] px-4 py-2 text-sm font-semibold text-[#435047]"
            href="/sales/slips"
          >
            판매 관리
          </Link>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            onClick={() => window.print()}
            type="button"
          >
            인쇄
          </button>
        </div>
      </div>

      <article className="a5-sheet mx-auto bg-white p-[12mm] text-[#1f2a24] shadow-sm">
        <header className="border-b-2 border-[#1f2a24] pb-4">
          <div className="flex items-start justify-between gap-6">
            <div>
              <p className="text-sm font-semibold text-[#4d6755]">
                Green House
              </p>
              <h2 className="mt-1 text-3xl font-bold tracking-normal">
                {slip.salesType === "AUCTION" ? "경매 출하 전표" : "판매 전표"}
              </h2>
            </div>
            <div className="text-right text-sm">
              <p className="font-semibold">전표번호</p>
              <p className="mt-1 text-lg font-bold">{slip.slipNumber}</p>
              <p className="mt-2 text-[#4d6755]">{formatDate(slip.saleDate)}</p>
            </div>
          </div>
        </header>

        <section className="mt-5 grid grid-cols-2 gap-3 text-sm">
          <InfoBox label="거래처" value={slip.partner.name} />
          <InfoBox label="대표자" value={slip.partner.ownerName ?? "-"} />
          <InfoBox label="전화번호" value={slip.partner.phone ?? "-"} />
          <InfoBox label="결제 방법" value={slip.paymentMethod ?? "-"} />
          <InfoBox
            className="col-span-2"
            label="주소"
            value={slip.partner.address ?? "-"}
          />
        </section>

        <section className="mt-5">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-y border-[#1f2a24] bg-[#eef3eb]">
                <th className="w-[34%] px-2 py-2 text-left font-bold">품목</th>
                <th className="w-[16%] px-2 py-2 text-left font-bold">속명</th>
                <th className="w-[14%] px-2 py-2 text-left font-bold">규격</th>
                <th className="w-[10%] px-2 py-2 text-right font-bold">수량</th>
                <th className="w-[13%] px-2 py-2 text-right font-bold">단가</th>
                <th className="w-[13%] px-2 py-2 text-right font-bold">금액</th>
              </tr>
            </thead>
            <tbody>
              {slip.items.map((item) => (
                <tr key={item.id} className="border-b border-[#d8ded5]">
                  <td className="px-2 py-2 font-semibold">{item.itemName}</td>
                  <td className="px-2 py-2">{item.genus ?? "-"}</td>
                  <td className="px-2 py-2">{item.spec ?? "-"}</td>
                  <td className="px-2 py-2 text-right">
                    {item.quantity.toLocaleString()}
                  </td>
                  <td className="px-2 py-2 text-right">
                    {item.unitPrice.toLocaleString()}
                  </td>
                  <td className="px-2 py-2 text-right font-semibold">
                    {item.amount.toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="mt-5 grid grid-cols-[1fr_190px] gap-4">
          <div className="rounded-md border border-[#cfd8cc] p-3 text-sm">
            <p className="font-bold">메모</p>
            <p className="mt-2 min-h-[46px] whitespace-pre-wrap text-[#405148]">
              {slip.memo ?? "-"}
            </p>
          </div>
          <div className="rounded-md border-2 border-[#1f2a24] p-3 text-right">
            <p className="text-sm font-bold">합계 금액</p>
            <p className="mt-3 text-2xl font-bold">
              {slip.totalAmount.toLocaleString()}원
            </p>
          </div>
        </section>

        <footer className="mt-6 grid grid-cols-3 gap-3 text-sm">
          <InfoBox label="입금 상태" value={slip.paymentStatus} />
          <InfoBox label="판매 상태" value={slip.salesStatus} />
          <div className="rounded-md border border-[#cfd8cc] p-3 text-center">
            <p className="font-bold">확인</p>
            <p className="mt-8 border-t border-[#1f2a24] pt-2">서명</p>
          </div>
        </footer>
      </article>
    </main>
  );
}

function InfoBox({
  className = "",
  label,
  value,
}: {
  className?: string;
  label: string;
  value: string;
}) {
  return (
    <div className={`rounded-md border border-[#cfd8cc] p-3 ${className}`}>
      <p className="text-xs font-bold text-[#4d6755]">{label}</p>
      <p className="mt-1 font-semibold">{value}</p>
    </div>
  );
}

function formatDate(value: string) {
  return value.replaceAll("-", ".");
}
