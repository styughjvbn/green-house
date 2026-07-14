import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { useAuctionTracking } from "../../model/useAuctionTracking";
import { AuctionFilters } from "./AuctionFilters";
import { AuctionLotDetail } from "./AuctionLotDetail";
import { AuctionLotList } from "./AuctionLotList";
import { TabError, TabSplit, TabStack } from "@/shared/ui/TabLayout";

export function AuctionTrackingView({
  initialPage,
  initialSummary,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
}) {
  const tracking = useAuctionTracking(initialPage, initialSummary);

  return (
    <TabStack>
      <AuctionFilters
        filters={tracking.filters}
        loading={tracking.loading}
        summary={tracking.summary}
        onChange={tracking.updateFilter}
        onSearch={() => tracking.refresh()}
        onReset={tracking.resetFilters}
      />
      <TabError message={tracking.error} />
      <TabSplit
        columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(420px,0.85fr)]"
        gap="gap-3"
      >
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
          key={tracking.selectedLot?.id ?? "empty"}
          lot={tracking.selectedLot}
          loading={tracking.loading}
          onAddResult={tracking.addResult}
          onConfirmReturn={tracking.confirmReturn}
          onAdjust={tracking.adjustQuantity}
        />
      </TabSplit>
    </TabStack>
  );
}
