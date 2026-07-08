"use client";

import ReactPaginate from "react-paginate";

type PaginationControlsProps = {
  pageCount: number;
  pageIndex: number;
  pageSize?: number;
  pageSizeOptions?: number[];
  previousLabel?: string;
  nextLabel?: string;
  onPageChange: (pageIndex: number) => void;
  onPageSizeChange?: (pageSize: number) => void;
};

export function PaginationControls({
  pageCount,
  pageIndex,
  pageSize,
  pageSizeOptions,
  previousLabel = "이전",
  nextLabel = "다음",
  onPageChange,
  onPageSizeChange,
}: PaginationControlsProps) {
  return (
    <div className="flex w-full flex-wrap items-center justify-between gap-3">
      <ReactPaginate
        breakLabel={"..."}
        containerClassName="flex items-center gap-1"
        disabledClassName="pointer-events-none opacity-40"
        forcePage={Math.max(0, Math.min(pageIndex, Math.max(pageCount - 1, 0)))}
        nextClassName="h-8"
        nextLabel={nextLabel}
        nextLinkClassName="inline-flex h-8 min-w-8 items-center justify-center rounded border border-[#d7ddd8] px-2 text-xs font-semibold text-[#344138]"
        pageClassName="h-8"
        pageCount={pageCount}
        pageLinkClassName="inline-flex h-8 min-w-8 items-center justify-center rounded border border-[#d7ddd8] px-2 text-xs text-[#344138]"
        marginPagesDisplayed={1}
        pageRangeDisplayed={5}
        previousClassName="h-8"
        previousLabel={previousLabel}
        previousLinkClassName="inline-flex h-8 min-w-8 items-center justify-center rounded border border-[#d7ddd8] px-2 text-xs font-semibold text-[#344138]"
        renderOnZeroPageCount={null}
        activeClassName="[&>a]:border-[#159447] [&>a]:bg-[#159447] [&>a]:font-bold [&>a]:text-white"
        onPageChange={({ selected }) => onPageChange(selected)}
      />

      {pageSize && pageSizeOptions && onPageSizeChange ? (
        <label className="inline-flex items-center gap-2 rounded-md border border-[#dfe5dc] px-3 py-2 text-xs font-semibold text-[#344138]">
          <span>페이지당</span>
          <select
            className="bg-white font-bold outline-none"
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number(event.target.value))}
          >
            {pageSizeOptions.map((size) => (
              <option key={size} value={size}>
                {size}개
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </div>
  );
}
