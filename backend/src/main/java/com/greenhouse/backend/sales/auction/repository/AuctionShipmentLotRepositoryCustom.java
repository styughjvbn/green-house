package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionLotSearchCriteria;
import com.greenhouse.backend.sales.auction.domain.AuctionShipmentLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionShipmentLotRepositoryCustom {

  Page<AuctionShipmentLot> search(AuctionLotSearchCriteria criteria, Pageable pageable);

  AuctionTrackingSummaryProjection summarize();
}
