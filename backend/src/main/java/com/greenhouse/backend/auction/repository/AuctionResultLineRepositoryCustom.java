package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.time.LocalDate;
import java.util.List;

public interface AuctionResultLineRepositoryCustom {

	List<AuctionResultLine> findSoldLines(Long auctionHouseId, LocalDate auctionDate);

	List<AuctionResultLine> findAllSoldLines();
}
