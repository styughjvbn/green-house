package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.auction.dto.AuctionTrackingSummaryResponse;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuctionTrackingService {
	private final AuctionShipmentLotRepository lotRepository;

	public AuctionTrackingService(AuctionShipmentLotRepository lotRepository) { this.lotRepository = lotRepository; }

	public List<AuctionLotResponse> getLots(LocalDate from, LocalDate to, String market, String variety, String grade, AuctionLotStatus status, Boolean reviewOnly, Boolean returnOnly, Boolean waitingOnly, String keyword) {
		String normalizedKeyword = normalize(keyword);
		return lotRepository.findAllByOrderByIdDesc().stream()
			.filter(lot -> from == null || !lot.getShipment().getShipmentDate().isBefore(from))
			.filter(lot -> to == null || !lot.getShipment().getShipmentDate().isAfter(to))
			.filter(lot -> blank(market) || lot.getShipment().getAuctionMarket().equalsIgnoreCase(market.trim()))
			.filter(lot -> blank(variety) || lot.getVarietyName().toLowerCase().contains(variety.trim().toLowerCase()))
			.filter(lot -> blank(grade) || grade.trim().equals(lot.getShipmentGrade()))
			.filter(lot -> status == null || lot.getCurrentStatus() == status)
			.filter(lot -> !Boolean.TRUE.equals(reviewOnly) || requiresReview(lot.getCurrentStatus(), AuctionLotResponse.from(lot).inspectionStatus()))
			.filter(lot -> !Boolean.TRUE.equals(returnOnly) || lot.getCurrentStatus() == AuctionLotStatus.RETURN_INFERRED)
			.filter(lot -> !Boolean.TRUE.equals(waitingOnly) || lot.getCurrentStatus() == AuctionLotStatus.REAUCTION_WAITING || lot.getCurrentStatus() == AuctionLotStatus.WAITING)
			.filter(lot -> normalizedKeyword == null || (lot.getItemName() + " " + lot.getVarietyName() + " " + lot.getShipment().getAuctionMarket()).toLowerCase().contains(normalizedKeyword))
			.map(AuctionLotResponse::from).toList();
	}

	public AuctionLotResponse getLot(Long id) { return AuctionLotResponse.from(findLot(id)); }

	public AuctionTrackingSummaryResponse getSummary() {
		List<AuctionLotResponse> lots = lotRepository.findAllByOrderByIdDesc().stream().map(AuctionLotResponse::from).toList();
		return new AuctionTrackingSummaryResponse(lots.size(), sum(lots, "shipped"), sum(lots, "sold"), sum(lots, "waiting"), sum(lots, "returned"), (int) lots.stream().filter(lot -> requiresReview(lot.currentStatus(), lot.inspectionStatus())).count(), lots.stream().mapToInt(AuctionLotResponse::totalAmount).sum());
	}

	@Transactional
	public AuctionLotResponse confirmReturn(Long id, AuctionLotReturnRequest request) {
		var lot = findLot(id);
		if (lot.getWaitingQuantity() <= 0) throw new IllegalArgumentException("반환할 대기 수량이 없습니다.");
		lot.confirmReturn(normalize(request.worker()), normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	@Transactional
	public AuctionLotResponse adjust(Long id, AuctionLotAdjustmentRequest request) {
		var lot = findLot(id);
		lot.adjustQuantities(request.soldQuantity(), request.waitingQuantity(), request.returnedQuantity(), normalize(request.worker()), normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	@Transactional
	public AuctionLotResponse changeStatus(Long id, AuctionLotStatusRequest request) {
		var lot = findLot(id);
		lot.changeStatus(request.status(), request.reason().trim(), normalize(request.worker()), normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	private com.greenhouse.backend.auction.domain.AuctionShipmentLot findLot(Long id) { return lotRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("경매 출하 lot을 찾을 수 없습니다.")); }
	private boolean requiresReview(AuctionLotStatus status, AuctionInspectionStatus inspection) { return List.of(AuctionLotStatus.REVIEW_REQUIRED, AuctionLotStatus.QUANTITY_MISMATCH, AuctionLotStatus.RETURN_INFERRED).contains(status) || inspection.ordinal() >= AuctionInspectionStatus.MANUAL_REVIEW.ordinal(); }
	private int sum(List<AuctionLotResponse> lots, String type) { return lots.stream().mapToInt(lot -> switch (type) { case "shipped" -> lot.shippedQuantity(); case "sold" -> lot.soldQuantity(); case "waiting" -> lot.waitingQuantity(); default -> lot.returnedQuantity(); }).sum(); }
	private boolean blank(String value) { return value == null || value.isBlank(); }
	private String normalize(String value) { return blank(value) ? null : value.trim(); }
}
