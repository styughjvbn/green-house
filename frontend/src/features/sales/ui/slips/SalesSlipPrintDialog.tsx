"use client";

import { useEffect } from "react";
import { Printer, X } from "lucide-react";
import type { SalesSlip } from "@/entities/farm/types";

export function SalesSlipPrintDialog({
  salesSlip,
  onClose,
}: {
  salesSlip: SalesSlip;
  onClose: () => void;
}) {
  useEffect(() => {
    const previousOverflow = document.body.style.overflow;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  return (
    <div
      className="sales-slip-print-dialog fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col rounded-md bg-[#f4f6f3] shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="판매 전표 인쇄 미리보기"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[#d7ddd4] bg-white p-4">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">A5 출력</p>
            <h2 className="mt-1 text-xl font-semibold">판매 전표 미리보기</h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white hover:bg-[#117c3b]"
              type="button"
              onClick={() => window.print()}
            >
              <Printer
                className="h-4 w-4"
                strokeWidth={1.8}
                aria-hidden="true"
              />
              인쇄
            </button>
            <button
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
              type="button"
              onClick={onClose}
              aria-label="닫기"
            >
              <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="sales-slip-print-preview min-h-0 overflow-auto p-4">
          <SalesSlipPrintSheet salesSlip={salesSlip} />
        </div>
      </section>
    </div>
  );
}

function SalesSlipPrintSheet({ salesSlip }: { salesSlip: SalesSlip }) {
  return (
    <article className="sales-slip-print-sheet a5-sheet mx-auto bg-white p-[12mm] text-[#1f2a24] shadow-sm">
      <div className="border-b-2 border-[#1f2a24] pb-4">
        <div className="flex items-start justify-between gap-6">
          <div>
            <p className="text-sm font-semibold text-[#4d6755]">Green House</p>
            <h2 className="mt-1 text-3xl font-bold tracking-normal">
              {salesSlip.salesType === "AUCTION"
                ? "경매 출하 전표"
                : "판매 전표"}
            </h2>
          </div>
          <div className="text-right text-sm">
            <p className="font-semibold">전표번호</p>
            <p className="mt-1 text-lg font-bold">{salesSlip.slipNumber}</p>
            <p className="mt-2 text-[#4d6755]">
              {formatDate(salesSlip.saleDate)}
            </p>
          </div>
        </div>
      </div>

      <section className="mt-5 grid grid-cols-2 gap-3 text-sm">
        <PrintInfoBox label="거래처" value={salesSlip.partner.name} />
        <PrintInfoBox
          label="대표자"
          value={salesSlip.partner.ownerName ?? "-"}
        />
        <PrintInfoBox label="전화번호" value={salesSlip.partner.phone ?? "-"} />
        <PrintInfoBox
          label="결제 방법"
          value={salesSlip.paymentMethod ?? "-"}
        />
        <PrintInfoBox
          className="col-span-2"
          label="주소"
          value={salesSlip.partner.address ?? "-"}
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
            {salesSlip.items.map((item) => (
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
            {salesSlip.memo ?? "-"}
          </p>
        </div>
        <div className="rounded-md border-2 border-[#1f2a24] p-3 text-right">
          <p className="text-sm font-bold">합계 금액</p>
          <p className="mt-3 text-2xl font-bold">
            {salesSlip.totalAmount.toLocaleString()}원
          </p>
        </div>
      </section>

      <footer className="mt-6 grid grid-cols-3 gap-3 text-sm">
        <PrintInfoBox label="입금 상태" value={salesSlip.paymentStatus} />
        <PrintInfoBox label="판매 상태" value={salesSlip.salesStatus} />
        <div className="rounded-md border border-[#cfd8cc] p-3 text-center">
          <p className="font-bold">확인</p>
          <p className="mt-8 border-t border-[#1f2a24] pt-2">서명</p>
        </div>
      </footer>
    </article>
  );
}

function PrintInfoBox({
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
