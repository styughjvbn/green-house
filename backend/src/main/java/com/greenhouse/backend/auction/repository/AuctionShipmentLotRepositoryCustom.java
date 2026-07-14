package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionShipmentLotRepositoryCustom {

	Page<AuctionShipmentLot> search(
			LocalDate from,
			LocalDate to,
			String market,
			String variety,
			String grade,
			AuctionLotStatus status,
			boolean reviewOnly,
			boolean returnOnly,
			boolean waitingOnly,
			String keyword,
			List<AuctionLotStatus> returnStatuses,
			List<AuctionLotStatus> waitingStatuses,
			List<AuctionLotStatus> reviewStatuses,
			List<AuctionInspectionStatus> reviewInspections,
			Pageable pageable);

	AuctionTrackingSummaryProjection summarize();
}
