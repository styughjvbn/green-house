package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionLotSearchCriteria;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionShipmentLotRepositoryCustom {

	Page<AuctionShipmentLot> search(AuctionLotSearchCriteria criteria, Pageable pageable);

	AuctionTrackingSummaryProjection summarize();

}
