"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { SalesSlipPage } from "@/features/sales/model/types";
import { PaginationControls } from "@/shared/ui/PaginationControls";

type PrintPageProps = {
  salesSlipPage: SalesSlipPage;
};

export function PrintPage({ salesSlipPage }: PrintPageProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const totalPages = Math.max(1, salesSlipPage.totalPages);
  const visiblePage = Math.min(salesSlipPage.page, totalPages - 1);

  function updatePage(nextPage: number, nextSize = salesSlipPage.size) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", String(nextPage));
    params.set("size", String(nextSize));
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <main className="space-y-5">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">판매 전표 출력</h2>
          <span className="rounded-full bg-[#eef7ec] px-3 py-1 text-sm font-semibold text-[#246b38]">
            {salesSlipPage.totalElements}건
          </span>
        </div>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[700px] border-separate border-spacing-y-2 text-left text-sm">
            <thead className="text-[#637063]">
              <tr>
                <th className="px-3 font-semibold">전표번호</th>
                <th className="px-3 font-semibold">판매일</th>
                <th className="px-3 font-semibold">거래처</th>
                <th className="px-3 text-right font-semibold">합계</th>
                <th className="px-3 font-semibold">출력</th>
              </tr>
            </thead>
            <tbody>
              {salesSlipPage.content.map((slip) => (
                <tr key={slip.id} className="bg-[#f8faf7]">
                  <td className="rounded-l-md px-3 py-3 font-semibold">
                    {slip.slipNumber}
                  </td>
                  <td className="px-3 py-3">{slip.saleDate}</td>
                  <td className="px-3 py-3">{slip.partner.name}</td>
                  <td className="px-3 py-3 text-right">
                    {slip.totalAmount.toLocaleString()}원
                  </td>
                  <td className="rounded-r-md px-3 py-3">
                    <Link
                      className="inline-flex min-h-0 items-center rounded-md bg-[#159447] px-3 py-1.5 text-xs font-semibold text-white"
                      href={`/print/sales-slips/${slip.id}`}
                    >
                      A5 출력
                    </Link>
                  </td>
                </tr>
              ))}
              {salesSlipPage.content.length === 0 ? (
                <tr>
                  <td
                    className="rounded-md bg-[#f8faf7] px-3 py-8 text-center text-[#5c6a60]"
                    colSpan={5}
                  >
                    출력할 판매 전표가 없습니다.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        <div className="mt-4">
          <PaginationControls
            nextLabel="다음"
            pageCount={totalPages}
            pageIndex={visiblePage}
            pageSize={salesSlipPage.size}
            pageSizeOptions={[10, 20, 50]}
            previousLabel="이전"
            onPageChange={(nextPage) => updatePage(nextPage)}
            onPageSizeChange={(nextPageSize) => updatePage(0, nextPageSize)}
          />
        </div>
      </section>
    </main>
  );
}
