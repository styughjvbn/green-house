package com.greenhouse.backend.auction.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AuctionLotSearchCriteria(LocalDate from, LocalDate to, List<Long> marketIds, String variety, String grade,
		AuctionLotStatus status, boolean reviewOnly, boolean returnOnly, boolean waitingOnly, String keyword,
		List<Long> keywordMarketIds, Map<String, List<Long>> boundaryMarketIds) {
}
