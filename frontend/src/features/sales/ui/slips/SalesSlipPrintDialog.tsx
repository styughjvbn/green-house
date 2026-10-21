"use client";

import { useQuery } from "@tanstack/react-query";
import { getDirectSaleChanges } from "../../api/salesApi";
import { salesQueryKeys } from "../../model/salesQueryKeys";
import type { DirectSaleChangeState } from "../../api/directChangeTypes";
import { salesDispatchLabel } from "../../lib/salesDisplay";
import { getPartnerSettlementSettings } from "../../api/salesApi";

import { useEffect, useState } from "react";
import { Printer, X } from "lucide-react";
import type {
  PartnerPrintTemplate,
  SalesSlip,
  SalesSlipItem,
} from "@/entities/farm/types";

export function SalesSlipPrintDialog({
  salesSlip,
  onClose,
}: {
  salesSlip: SalesSlip;
  onClose: () => void;
}) {
  const changes = useQuery({
    queryKey: salesQueryKeys.slips.changes(salesSlip.id, 0),
    queryFn: ({ signal }) => getDirectSaleChanges(salesSlip.id, 0, signal),
    enabled: salesSlip.salesType === "DIRECT",
  });
  const commercialLoading =
    salesSlip.salesType === "DIRECT" && changes.isPending;
  const commercialError = salesSlip.salesType === "DIRECT" && changes.error;
  const [printTemplate, setPrintTemplate] =
    useState<PartnerPrintTemplate>("STANDARD_A5");
  const [shipperCode, setShipperCode] = useState("");
  const [shipperName, setShipperName] = useState("");
  const [settingsMessage, setSettingsMessage] = useState<string | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(
    salesSlip.salesType === "AUCTION",
  );

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

  useEffect(() => {
    if (salesSlip.salesType !== "AUCTION") return;

    let active = true;
    void getPartnerSettlementSettings(salesSlip.partner.id)
      .then((settings) => {
        if (!active) return;
        setPrintTemplate(settings.printTemplate);
        setShipperCode(settings.shipperCode ?? "");
        setShipperName(settings.shipperName ?? "");
      })
      .catch((error: unknown) => {
        if (!active) return;
        setSettingsMessage(
          error instanceof Error
            ? error.message
            : "경매장 인쇄 기본값을 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (active) setSettingsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [salesSlip.partner.id, salesSlip.salesType]);

  return (
    <div
      className="sales-slip-print-dialog fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-4xl flex-col rounded-md bg-[#f4f6f3] shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="판매 전표 인쇄 미리보기"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[#d7ddd4] bg-white p-4">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">A5 출력</p>
            <h2 className="mt-1 text-xl font-semibold">
              {salesSlip.salesType === "AUCTION"
                ? "경매 출하 전표 미리보기"
                : "판매 전표 미리보기"}
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <button
              className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white hover:bg-[#117c3b] disabled:cursor-not-allowed disabled:opacity-50"
              type="button"
              disabled={
                settingsLoading || commercialLoading || !!commercialError
              }
              onClick={() => window.print()}
            >
              <Printer
                className="h-4 w-4"
                strokeWidth={1.8}
                aria-hidden="true"
              />
              {settingsLoading ? "불러오는 중" : "인쇄"}
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

        {salesSlip.salesType === "AUCTION" ? (
          <div className="sales-slip-print-controls grid shrink-0 gap-3 border-b border-[#d7ddd4] bg-white px-4 py-3 sm:grid-cols-3">
            <label className="text-xs font-semibold text-[#526057]">
              인쇄 양식
              <select
                className="mt-1 h-9 w-full rounded-md border border-[#ccd5ca] bg-white px-3 text-sm"
                value={printTemplate}
                onChange={(event) =>
                  setPrintTemplate(event.target.value as PartnerPrintTemplate)
                }
              >
                <option value="STANDARD_A5">기본 A5</option>
                <option value="YANGJAE_AUCTION_SHIPMENT">
                  양재 경매장 출하 양식
                </option>
              </select>
            </label>
            <label className="text-xs font-semibold text-[#526057]">
              출하자 코드
              <input
                className="mt-1 h-9 w-full rounded-md border border-[#ccd5ca] px-3 text-sm"
                maxLength={50}
                value={shipperCode}
                onChange={(event) => setShipperCode(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-[#526057]">
              출하자 성명
              <input
                className="mt-1 h-9 w-full rounded-md border border-[#ccd5ca] px-3 text-sm"
                maxLength={100}
                value={shipperName}
                onChange={(event) => setShipperName(event.target.value)}
              />
            </label>
            {settingsMessage ? (
              <p className="text-xs font-semibold text-red-700 sm:col-span-3">
                {settingsMessage}
              </p>
            ) : null}
          </div>
        ) : null}

        <div className="sales-slip-print-preview min-h-0 overflow-auto p-4">
          {commercialError ? (
            <div role="alert" className="bg-white p-4 text-red-700">
              <p>
                현재 거래금액을 불러오지 못했습니다. {commercialError.message}
              </p>
              <button type="button" onClick={() => void changes.refetch()}>
                다시 불러오기
              </button>
            </div>
          ) : commercialLoading ? (
            <div className="bg-white p-4">
              현재 거래금액을 불러오는 중입니다.
            </div>
          ) : settingsLoading ? (
            <div className="mx-auto flex min-h-[210mm] w-[148mm] items-center justify-center bg-white text-sm font-semibold text-[#68756c] shadow-sm">
              경매장 인쇄 기본값을 불러오는 중입니다.
            </div>
          ) : salesSlip.salesType === "AUCTION" &&
            printTemplate === "YANGJAE_AUCTION_SHIPMENT" ? (
            <YangjaeAuctionPrintSheets
              salesSlip={salesSlip}
              shipperCode={shipperCode}
              shipperName={shipperName}
            />
          ) : (
            <StandardSalesSlipPrintSheet
              salesSlip={salesSlip}
              changes={changes.data}
            />
          )}
        </div>
      </section>
    </div>
  );
}

function StandardSalesSlipPrintSheet({
  salesSlip,
  changes,
}: {
  salesSlip: SalesSlip;
  changes?: DirectSaleChangeState;
}) {
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
            <p className="font-semibold">전표 ID</p>
            <p className="mt-1 text-lg font-bold">{salesSlip.id}</p>
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
                  {item.unit != null || item.boxes != null ? (
                    <span className="block text-xs text-[#5b675e]">
                      {[
                        item.unit != null
                          ? `단위 ${item.unit.toLocaleString()}`
                          : null,
                        item.boxes != null
                          ? `${item.boxes.toLocaleString()}상자`
                          : null,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  ) : null}
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

      {changes && changes.history.totalElements > 0 ? (
        <section className="mt-3 text-xs">
          <p>
            위 품목은 원판매 내용입니다. 원판매 금액{" "}
            {changes.originalAmount.toLocaleString()}원
          </p>
          <p className="mt-1 font-bold">가격 조정·반품 반영 후 판매 구성</p>
          {changes.portions.map((portion) => (
            <p
              key={`${portion.itemId}:${portion.sourceChangeId ?? "original"}`}
              className="mt-1"
            >
              {portion.itemName} {portion.spec ?? ""} · {portion.quantity}개 ×{" "}
              {portion.unitPrice.toLocaleString()}원 ={" "}
              {portion.amount.toLocaleString()}원
            </p>
          ))}
          {changes.portions.length === 0 ? <p>전체 반품</p> : null}
        </section>
      ) : null}

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
            {(changes?.currentAmount ?? salesSlip.totalAmount).toLocaleString()}
            원
          </p>
        </div>
      </section>

      <footer className="mt-6 grid grid-cols-2 gap-3 text-sm">
        {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. */}
        <PrintInfoBox
          label="출고·출하"
          value={salesDispatchLabel(salesSlip.salesStatus, salesSlip.salesType)}
        />
        <div className="rounded-md border border-[#cfd8cc] p-3 text-center">
          <p className="font-bold">확인</p>
          <p className="mt-8 border-t border-[#1f2a24] pt-2">서명</p>
        </div>
      </footer>
    </article>
  );
}

function YangjaeAuctionPrintSheets({
  salesSlip,
  shipperCode,
  shipperName,
}: {
  salesSlip: SalesSlip;
  shipperCode: string;
  shipperName: string;
}) {
  const pages = chunk(salesSlip.items, 10);
  return (
    <div className="sales-slip-print-pages space-y-4 print:space-y-0">
      {pages.map((items, pageIndex) => (
        <YangjaeAuctionPrintSheet
          key={pageIndex}
          items={items}
          pageIndex={pageIndex}
          pageCount={pages.length}
          salesSlip={salesSlip}
          shipperCode={shipperCode}
          shipperName={shipperName}
        />
      ))}
    </div>
  );
}

function YangjaeAuctionPrintSheet({
  items,
  pageCount,
  pageIndex,
  salesSlip,
  shipperCode,
  shipperName,
}: {
  items: SalesSlipItem[];
  pageCount: number;
  pageIndex: number;
  salesSlip: SalesSlip;
  shipperCode: string;
  shipperName: string;
}) {
  const rows: Array<SalesSlipItem | null> = [
    ...items,
    ...Array<null>(Math.max(0, 10 - items.length)).fill(null),
  ];
  return (
    <article className="sales-slip-print-sheet yangjae-auction-sheet a5-sheet mx-auto bg-[#fffef4] p-[8mm] text-[#171b18] shadow-sm">
      <header className="relative pt-1 text-center">
        <h2 className="inline-block border-b-2 border-[#171b18] px-5 pb-1 text-[22px] font-bold tracking-[0.28em]">
          분화(난) 수탁증
        </h2>
        {pageCount > 1 ? (
          <p className="absolute top-0 right-0 text-[9px]">
            {pageIndex + 1}/{pageCount}
          </p>
        ) : null}
      </header>

      <div className="mt-5 grid grid-cols-[1fr_32mm] gap-3 text-[11px]">
        <div className="space-y-2">
          <PrintLine label="출하자 코드" value={shipperCode} mono />
          <div className="grid grid-cols-[1fr_1fr] gap-3">
            <PrintLine label="출하자 성명" value={shipperName} />
            <PrintLine
              label="출하일자"
              value={formatDate(salesSlip.saleDate)}
            />
          </div>
        </div>
        <div className="border border-[#171b18] text-center">
          <p className="border-b border-[#171b18] py-1.5 font-semibold tracking-[0.25em]">
            차량순번
          </p>
          <p className="h-8" />
        </div>
      </div>

      <div className="mt-2 flex border border-[#171b18] text-[10px]">
        <p className="w-[30mm] border-r border-[#171b18] py-1 text-center font-bold">
          운송방법
        </p>
        <p className="flex-1 px-3 py-1 tracking-wide">
          1. 자가　　5. 위탁　　7. 2.5ton　　8. 3.5ton　　9. 4.5ton
        </p>
      </div>

      <table className="mt-2 w-full table-fixed border-collapse border border-[#171b18] text-[9px]">
        <thead>
          <tr>
            <th className="w-[16%] border border-[#171b18] px-1 py-1.5">
              품목명
            </th>
            <th className="w-[30%] border border-[#171b18] px-1 py-1.5">
              품종명
            </th>
            <th className="w-[20%] border border-[#171b18] px-1 py-1.5">
              등급
            </th>
            <th className="w-[11%] border border-[#171b18] px-1 py-1.5">
              상자
            </th>
            <th className="w-[11%] border border-[#171b18] px-1 py-1.5">분</th>
            <th className="w-[12%] border border-[#171b18] px-1 py-1.5">
              비고
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item, rowIndex) => (
            <tr key={item?.id ?? `empty-${rowIndex}`} className="h-[11mm]">
              <td className="border border-[#171b18] px-1.5 py-1">
                {item ? "난" : ""}
              </td>
              <td className="border border-[#171b18] px-1.5 py-1 font-semibold">
                {item?.itemName ?? ""}
                {item?.genus ? (
                  <span className="block text-[8px] font-normal">
                    {item.genus}
                  </span>
                ) : null}
              </td>
              <td className="border border-[#171b18] px-1.5 py-1 text-center">
                {item?.spec ?? ""}
              </td>
              <td className="border border-[#171b18] px-1.5 py-1 text-right">
                {item?.boxes?.toLocaleString() ?? ""}
              </td>
              <td className="border border-[#171b18] px-1.5 py-1 text-right">
                {item?.quantity.toLocaleString() ?? ""}
              </td>
              <td className="border border-[#171b18] px-1.5 py-1">
                {item?.memo ?? ""}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <footer className="mt-3 grid grid-cols-[1fr_45mm] gap-4 text-[9px]">
        <div>
          <p className="text-[11px] font-bold">판매 및 정산 이용안내</p>
          <p className="mt-1">
            경매 결과와 정산 내역은 경매장 안내를 확인해 주세요.
          </p>
          <p className="mt-1">경매장: {salesSlip.partner.name}</p>
        </div>
        <div className="space-y-3 pt-1 text-[10px]">
          <p className="flex justify-between border-b border-[#171b18] pb-1">
            <span>운송자</span>
            <span>(인)</span>
          </p>
          <p className="flex justify-between border-b border-[#171b18] pb-1">
            <span>인수자</span>
            <span>(인)</span>
          </p>
        </div>
      </footer>
    </article>
  );
}

function PrintLine({
  label,
  mono = false,
  value,
}: {
  label: string;
  mono?: boolean;
  value: string;
}) {
  return (
    <p className="flex min-w-0 items-end gap-2">
      <span className="shrink-0 font-semibold">{label}:</span>
      <span
        className={`min-h-5 flex-1 border-b border-[#171b18] px-1 font-semibold ${
          mono ? "tracking-[0.18em]" : ""
        }`}
      >
        {value}
      </span>
    </p>
  );
}

function chunk<T>(items: T[], size: number): T[][] {
  if (items.length === 0) return [[]];
  return Array.from({ length: Math.ceil(items.length / size) }, (_, index) =>
    items.slice(index * size, (index + 1) * size),
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
