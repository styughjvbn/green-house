import { FileUp } from "lucide-react";
import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { useAuctionTracking } from "../model/useAuctionTracking";
import { AuctionFilters } from "./components/AuctionFilters";
import { AuctionImportPanel } from "./components/AuctionImportPanel";
import { AuctionLotDetail } from "./components/AuctionLotDetail";
import { AuctionLotList } from "./components/AuctionLotList";
import { AuctionSummaryCards } from "./components/AuctionSummaryCards";

export function AuctionTrackingView({
  initialPage,
  initialSummary,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
}) {
  const tracking = useAuctionTracking(initialPage, initialSummary);

  return (
    <div className="space-y-3">
      <div className="flex justify-end">
        <button
          className="inline-flex h-9 items-center gap-2 rounded-md border border-[#d8e0d7] bg-white px-3 text-sm font-semibold shadow-sm"
          type="button"
          onClick={() => tracking.setImportOpen(!tracking.importOpen)}
        >
          <FileUp className="h-4 w-4" />
          CSV 가져오기
        </button>
      </div>
      {tracking.importOpen ? (
        <AuctionImportPanel
          batch={tracking.importBatch}
          rows={tracking.importRows}
          loading={tracking.loading}
          onClose={() => tracking.setImportOpen(false)}
          onImport={tracking.importCsv}
        />
      ) : null}
      <AuctionSummaryCards summary={tracking.summary} />
      <AuctionFilters
        filters={tracking.filters}
        loading={tracking.loading}
        onChange={tracking.updateFilter}
        onSearch={() => tracking.refresh()}
        onReset={tracking.resetFilters}
      />
      {tracking.error ? (
        <p className="rounded-md border border-[#f0c7c3] bg-[#fff1ef] px-3 py-2 text-sm font-semibold text-[#b83e35]">
          {tracking.error}
        </p>
      ) : null}
      <div className="grid min-w-0 gap-3 2xl:grid-cols-[minmax(0,1.15fr)_minmax(420px,0.85fr)]">
        <AuctionLotList
          lots={tracking.lots}
          page={tracking.page}
          pageSize={tracking.pageSize}
          totalElements={tracking.totalElements}
          totalPages={tracking.totalPages}
          selectedId={tracking.selectedLot?.id ?? null}
          onSelect={tracking.setSelectedId}
          onPageChange={tracking.changePage}
          onPageSizeChange={tracking.changePageSize}
        />
        <AuctionLotDetail
          lot={tracking.selectedLot}
          loading={tracking.loading}
          onConfirmReturn={tracking.confirmReturn}
          onAdjust={tracking.adjustQuantity}
        />
      </div>
    </div>
  );
}
