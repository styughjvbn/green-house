package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import java.time.LocalDate;

public record AuctionResultLineResponse(Long id, LocalDate auctionDate, String auctionGrade, Integer quantity, Integer unitPrice, Integer amount, String note, AuctionInspectionStatus inspectionStatus, Long rawRowId) {
	public static AuctionResultLineResponse from(AuctionResultLine line) { return new AuctionResultLineResponse(line.getId(), line.getAuctionDate(), line.getAuctionGrade(), line.getQuantity(), line.getUnitPrice(), line.getAmount(), line.getNote(), line.getInspectionStatus(), line.getRawRow() == null ? null : line.getRawRow().getId()); }
}
