package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionLotSearchCriteria;
import com.greenhouse.backend.sales.domain.auction.AuctionShipmentLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionShipmentLotRepositoryCustom {

  Page<AuctionShipmentLot> search(AuctionLotSearchCriteria criteria, Pageable pageable);

  AuctionTrackingSummaryProjection summarize();
}
