package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResultLineRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResultRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.auction.dto.AuctionTrackingSummaryResponse;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionTrackingService {
	private final AuctionShipmentLotRepository lotRepository;

	public PageResponse<AuctionLotResponse> getLots(LocalDate from, LocalDate to, String market, String variety, String grade,
			AuctionLotStatus status, Boolean reviewOnly, Boolean returnOnly, Boolean waitingOnly, String keyword,
			int page, int size) {
		if (page < 0)
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		if (size < 1 || size > 100)
			throw new IllegalArgumentException("페이지 크기는 1~100이어야 합니다.");
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
				List.of(AuctionLotStatus.REVIEW_REQUIRED, AuctionLotStatus.QUANTITY_MISMATCH,
						AuctionLotStatus.RETURN_INFERRED, AuctionLotStatus.PARTIALLY_RETURNED),
				List.of(AuctionInspectionStatus.MANUAL_REVIEW, AuctionInspectionStatus.MATCH_FAILED,
						AuctionInspectionStatus.QUANTITY_MISMATCH, AuctionInspectionStatus.RETURN_INFERRED,
						AuctionInspectionStatus.SOURCE_ERROR),
				pageable)
				.map(AuctionLotResponse::from);
		return PageResponse.from(result);
	}

	public AuctionLotResponse getLot(Long id) {
		return AuctionLotResponse.from(findLot(id));
	}

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
		if (!List.of(AuctionLotStatus.REAUCTION_WAITING, AuctionLotStatus.RETURN_INFERRED,
				AuctionLotStatus.PARTIALLY_RETURNED).contains(lot.getCurrentStatus()))
			throw new IllegalArgumentException("재경매대기, 반환추정 또는 부분반환 상태에서만 반환을 확인할 수 있습니다.");
		if (lot.getReturnConfirmableQuantity() <= 0)
			throw new IllegalArgumentException("확인할 반환 수량이 없습니다.");
		int quantity = request.returnedQuantity() == null ? lot.getReturnConfirmableQuantity()
				: request.returnedQuantity();
		lot.confirmReturn(quantity, request.returnDate(), normalize(request.worker()), normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	@Transactional
	public AuctionLotResponse adjust(Long id, AuctionLotAdjustmentRequest request) {
		var lot = findLot(id);
		lot.adjustQuantities(request.soldQuantity(), request.waitingQuantity(), request.returnedQuantity(),
				normalize(request.worker()), normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	@Transactional
	public AuctionLotResponse addResult(Long id, AuctionLotResultRequest request) {
		var lot = findLot(id);
		if (lot.getWaitingQuantity() <= 0)
			throw new IllegalArgumentException("대기 수량이 없는 lot에는 경매 결과를 추가할 수 없습니다.");
		int waitingQuantity = lot.getWaitingQuantity();
		int attemptNo = resolveAttemptNo(lot, request.attemptNo());
		validateAttemptNo(lot, request.auctionDate(), attemptNo);

		String failedReason = normalize(request.failedReason());
		String memo = normalize(request.memo());
		var attempt = new AuctionAttempt(
				request.auctionDate(),
				attemptNo,
				request.attemptStatus(),
				failedReason,
				memo);

		switch (request.attemptStatus()) {
			case SOLD -> {
				int soldQuantity = addSoldLines(attempt, request.resultLines(), lot.getShipmentGrade());
				if (soldQuantity != waitingQuantity)
					throw new IllegalArgumentException("낙찰 상태에서는 남은 대기 수량 전체를 입력해야 합니다.");
				attempt.recalculateStatus();
				lot.addAttempt(attempt);
				lot.applyResult(soldQuantity, 0, false, false);
			}
			case PARTIALLY_SOLD -> {
				int soldQuantity = addSoldLines(attempt, request.resultLines(), lot.getShipmentGrade());
				if (soldQuantity >= waitingQuantity)
					throw new IllegalArgumentException("부분 낙찰은 대기 수량보다 적어야 합니다.");
				attempt.addResultLine(new AuctionResultLine(
						request.auctionDate(),
						lot.getShipmentGrade(),
						waitingQuantity - soldQuantity,
						0,
						0,
						failedReason == null ? "잔량 유찰" : failedReason,
						AuctionInspectionStatus.NORMAL));
				attempt.recalculateStatus();
				lot.addAttempt(attempt);
				lot.applyResult(soldQuantity, 0, false, false);
			}
			case FAILED -> {
				attempt.addResultLine(new AuctionResultLine(
						request.auctionDate(),
						lot.getShipmentGrade(),
						waitingQuantity,
						0,
						0,
						failedReason == null ? "유찰" : failedReason,
						AuctionInspectionStatus.NORMAL));
				attempt.recalculateStatus();
				lot.addAttempt(attempt);
				lot.applyResult(0, 0, true, false);
			}
			case RETURN_INFERRED -> {
				attempt.addResultLine(new AuctionResultLine(
						request.auctionDate(),
						lot.getShipmentGrade(),
						waitingQuantity,
						0,
						0,
						failedReason == null ? "반환 추정" : failedReason,
						AuctionInspectionStatus.RETURN_INFERRED));
				attempt.recalculateStatus();
				lot.addAttempt(attempt);
				lot.applyResult(0, waitingQuantity, false, true);
			}
			default -> throw new IllegalArgumentException("지원하지 않는 경매 결과 상태입니다.");
		}

		return AuctionLotResponse.from(lot);
	}

	@Transactional
	public AuctionLotResponse changeStatus(Long id, AuctionLotStatusRequest request) {
		var lot = findLot(id);
		lot.changeStatus(request.status(), request.reason().trim(), normalize(request.worker()),
				normalize(request.memo()));
		return AuctionLotResponse.from(lot);
	}

	private com.greenhouse.backend.auction.domain.AuctionShipmentLot findLot(Long id) {
		return lotRepository.findWithDetailsById(id)
				.orElseThrow(() -> new NotFoundException("경매 출하 lot를 찾을 수 없습니다."));
	}

	private int resolveAttemptNo(com.greenhouse.backend.auction.domain.AuctionShipmentLot lot, Integer attemptNo) {
		if (attemptNo != null)
			return attemptNo;
		return lot.getAttempts().stream().map(AuctionAttempt::getAttemptNo).max(Integer::compareTo).orElse(0) + 1;
	}

	private void validateAttemptNo(com.greenhouse.backend.auction.domain.AuctionShipmentLot lot, LocalDate auctionDate,
			int attemptNo) {
		boolean duplicate = lot.getAttempts().stream()
				.anyMatch(attempt -> Objects.equals(attempt.getAttemptNo(), attemptNo)
						&& Objects.equals(attempt.getAuctionDate(), auctionDate));
		if (duplicate)
			throw new IllegalArgumentException("같은 경매일과 차수의 결과가 이미 등록되어 있습니다.");
	}

	private int addSoldLines(AuctionAttempt attempt, List<AuctionLotResultLineRequest> lines, String defaultGrade) {
		if (lines == null || lines.isEmpty())
			throw new IllegalArgumentException("낙찰 결과 행을 1개 이상 입력해야 합니다.");
		int soldQuantity = 0;
		for (var line : lines) {
			if (line.unitPrice() < 1)
				throw new IllegalArgumentException("낙찰 결과 단가는 1원 이상이어야 합니다.");
			attempt.addResultLine(new AuctionResultLine(
					attempt.getAuctionDate(),
					normalize(line.auctionGrade()) == null ? defaultGrade : normalize(line.auctionGrade()),
					line.quantity(),
					line.unitPrice(),
					line.quantity() * line.unitPrice(),
					normalize(line.note()),
					line.inspectionStatus() == null ? AuctionInspectionStatus.NORMAL : line.inspectionStatus()));
			soldQuantity += line.quantity();
		}
		return soldQuantity;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String normalize(String value) {
		return blank(value) ? null : value.trim();
	}

	private String normalizeOrEmpty(String value) {
		return blank(value) ? "" : value.trim();
	}
}
