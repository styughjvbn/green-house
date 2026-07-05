package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesService {

	private final BusinessPartnerRepository partnerRepository;
	private final SalesSlipRepository salesSlipRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final AuctionShipmentRepository auctionShipmentRepository;

	public SalesService(
		BusinessPartnerRepository partnerRepository,
		SalesSlipRepository salesSlipRepository,
		OrchidGroupRepository orchidGroupRepository,
		AuctionShipmentRepository auctionShipmentRepository
	) {
		this.partnerRepository = partnerRepository;
		this.salesSlipRepository = salesSlipRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.auctionShipmentRepository = auctionShipmentRepository;
	}

	@Transactional(readOnly = true)
	public List<SalesSlipResponse> getSalesSlips(Long partnerId, LocalDate from, LocalDate to) {
		return salesSlipRepository.search(partnerId, from, to).stream()
			.map(SalesSlipResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public SalesSlipResponse getSalesSlip(Long salesSlipId) {
		return salesSlipRepository.findWithDetailsById(salesSlipId)
			.map(SalesSlipResponse::from)
			.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true)
	public List<AuctionShipmentOptionResponse> getAuctionShipmentOptions() {
		return auctionShipmentRepository.findAllByOrderByShipmentDateDescIdDesc().stream()
			.filter(shipment -> !salesSlipRepository.existsByAuctionShipmentId(shipment.getId()))
			.map(AuctionShipmentOptionResponse::from)
			.toList();
	}

	public SalesSlipResponse createSalesSlip(SalesSlipCreateRequest request) {
		SalesType salesType = request.salesType() == null ? SalesType.DIRECT : request.salesType();
		if (salesType == SalesType.AUCTION) return createAuctionSalesSlip(request);
		if (request.partnerId() == null) throw new IllegalArgumentException("일반 판매는 거래처를 선택해야 합니다.");
		if (request.items().isEmpty()) throw new IllegalArgumentException("일반 판매 품목을 한 개 이상 입력해야 합니다.");
		BusinessPartner partner = findPartner(request.partnerId());
		if (partner.getPartnerType() == PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 거래처는 경매 판매 전표에서 사용해야 합니다.");
		}
		SalesSlip salesSlip = new SalesSlip(
			createSlipNumber(request.saleDate(), salesType),
			request.saleDate(),
			salesType,
			null,
			partner,
			defaultText(request.paymentStatus(), "미입금"),
			defaultText(request.salesStatus(), "작성중"),
			normalize(request.paymentMethod()),
			normalize(request.memo())
		);
		request.items().forEach(item -> salesSlip.addItem(new SalesSlipItem(
			item.orchidGroupId() == null
				? null
				: orchidGroupRepository.findById(item.orchidGroupId())
					.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다.")),
			null,
			normalizeRequired(item.itemName()),
			normalize(item.genus()),
			normalize(item.spec()),
			item.quantity(),
			item.unitPrice(),
			normalize(item.memo())
		)));
		return SalesSlipResponse.from(salesSlipRepository.save(salesSlip));
	}

	private SalesSlipResponse createAuctionSalesSlip(SalesSlipCreateRequest request) {
		if (request.auctionShipmentId() == null) throw new IllegalArgumentException("경매 판매는 출하 기록을 선택해야 합니다.");
		if (salesSlipRepository.existsByAuctionShipmentId(request.auctionShipmentId())) throw new IllegalArgumentException("이미 판매 전표가 생성된 경매 출하 기록입니다.");
		AuctionShipment shipment = auctionShipmentRepository.findWithLotsById(request.auctionShipmentId())
			.orElseThrow(() -> new NotFoundException("경매 출하 기록을 찾을 수 없습니다."));
		if (shipment.getLots().isEmpty()) throw new IllegalArgumentException("출하 lot이 없는 경매 출하 기록입니다.");
		BusinessPartner partner = shipment.getAuctionHouse();
		if (request.partnerId() != null && !request.partnerId().equals(partner.getId())) {
			throw new IllegalArgumentException("선택한 거래처가 출하 기록의 경매장과 다릅니다.");
		}
		SalesSlip salesSlip = new SalesSlip(
			createSlipNumber(shipment.getShipmentDate(), SalesType.AUCTION),
			shipment.getShipmentDate(),
			SalesType.AUCTION,
			shipment,
			partner,
			"정산 대기",
			"출하 완료",
			"경매 정산",
			normalize(request.memo()));
		shipment.getLots().forEach(lot -> salesSlip.addItem(new SalesSlipItem(
			null,
			lot,
			lot.getVarietyName(),
			null,
			lot.getShipmentGrade(),
			lot.getShippedQuantity(),
			0,
			"경매 출하 lot #" + lot.getId())));
		return SalesSlipResponse.from(salesSlipRepository.save(salesSlip));
	}

	private BusinessPartner findPartner(Long partnerId) {
		BusinessPartner partner = partnerRepository.findById(partnerId)
			.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
		if (!partner.isActive()) throw new IllegalArgumentException("비활성 거래처는 사용할 수 없습니다.");
		return partner;
	}

	private String createSlipNumber(LocalDate saleDate, SalesType salesType) {
		long sequence = salesSlipRepository.countBySaleDate(saleDate) + 1;
		String prefix = salesType == SalesType.AUCTION ? "A" : "S";
		return prefix + saleDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
	}

	private String defaultText(String value, String defaultValue) {
		String normalized = normalize(value);
		return normalized == null ? defaultValue : normalized;
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}
}
