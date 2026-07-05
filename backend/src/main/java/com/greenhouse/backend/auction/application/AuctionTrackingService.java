package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotPageResponse;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.auction.dto.AuctionTrackingSummaryResponse;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuctionTrackingService {
	private final AuctionShipmentLotRepository lotRepository;

	public AuctionTrackingService(AuctionShipmentLotRepository lotRepository) { this.lotRepository = lotRepository; }

	public AuctionLotPageResponse getLots(LocalDate from, LocalDate to, String market, String variety, String grade, AuctionLotStatus status, Boolean reviewOnly, Boolean returnOnly, Boolean waitingOnly, String keyword, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		var result = lotRepository.search(
			from,
			to,
			normalizeOrEmpty(market),
			normalizeOrEmpty(variety),
			normalizeOrEmpty(grade),
			status,
			Boolean.TRUE.equals(reviewOnly),
			Boolean.TRUE.equals(returnOnly),
			Boolean.TRUE.equals(waitingOnly),
			normalizeOrEmpty(keyword),
			List.of(AuctionLotStatus.RETURN_INFERRED, AuctionLotStatus.PARTIALLY_RETURNED),
			List.of(AuctionLotStatus.REAUCTION_WAITING, AuctionLotStatus.WAITING),
			List.of(AuctionLotStatus.REVIEW_REQUIRED, AuctionLotStatus.QUANTITY_MISMATCH, AuctionLotStatus.RETURN_INFERRED, AuctionLotStatus.PARTIALLY_RETURNED),
			List.of(AuctionInspectionStatus.MANUAL_REVIEW, AuctionInspectionStatus.MATCH_FAILED, AuctionInspectionStatus.QUANTITY_MISMATCH, AuctionInspectionStatus.RETURN_INFERRED, AuctionInspectionStatus.SOURCE_ERROR),
			pageable)
			.map(AuctionLotResponse::from);
		return AuctionLotPageResponse.from(result);
	}

	public AuctionLotResponse getLot(Long id) { return AuctionLotResponse.from(findLot(id)); }

	public AuctionTrackingSummaryResponse getSummary() {
		var summary = lotRepository.summarize();
		return new AuctionTrackingSummaryResponse(
			summary.getLotCount().intValue(),
			summary.getShippedQuantity().intValue(),
			summary.getSoldQuantity().intValue(),
			summary.getWaitingQuantity().intValue(),
			summary.getReturnedQuantity().intValue(),
			summary.getReviewRequiredCount().intValue(),
			summary.getTotalAmount().intValue());
	}

	@Transactional
	public AuctionLotResponse confirmReturn(Long id, AuctionLotReturnRequest request) {
		var lot = findLot(id);
		if (!List.of(AuctionLotStatus.RETURN_INFERRED, AuctionLotStatus.PARTIALLY_RETURNED).contains(lot.getCurrentStatus())) throw new IllegalArgumentException("반환추정 또는 부분반환 상태에서만 반환을 확인할 수 있습니다.");
		if (lot.getWaitingQuantity() <= 0) throw new IllegalArgumentException("반환할 대기 수량이 없습니다.");
		int quantity = request.returnedQuantity() == null ? lot.getWaitingQuantity() : request.returnedQuantity();
		lot.confirmReturn(quantity, normalize(request.worker()), normalize(request.memo()));
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
	private boolean blank(String value) { return value == null || value.isBlank(); }
	private String normalize(String value) { return blank(value) ? null : value.trim(); }
	private String normalizeOrEmpty(String value) { return blank(value) ? "" : value.trim(); }
}
