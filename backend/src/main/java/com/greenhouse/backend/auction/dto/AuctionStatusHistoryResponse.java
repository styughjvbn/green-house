package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatusHistory;
import java.time.LocalDateTime;

public record AuctionStatusHistoryResponse(Long id, AuctionLotStatus previousStatus, AuctionLotStatus newStatus,
		LocalDateTime changedAt, String reason, String worker, String memo) {
	public static AuctionStatusHistoryResponse from(AuctionLotStatusHistory history) {
		return new AuctionStatusHistoryResponse(history.getId(), history.getPreviousStatus(), history.getNewStatus(),
				TimeConfig.toFarmTime(history.getChangedAt()), history.getReason(), history.getWorker(), history.getMemo());
	}
}
