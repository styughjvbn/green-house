package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.sales.domain.Customer;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.CustomerCreateRequest;
import com.greenhouse.backend.sales.dto.CustomerResponse;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.CustomerRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesService {

	private final CustomerRepository customerRepository;
	private final SalesSlipRepository salesSlipRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final AuctionShipmentRepository auctionShipmentRepository;

	public SalesService(
		CustomerRepository customerRepository,
		SalesSlipRepository salesSlipRepository,
		OrchidGroupRepository orchidGroupRepository,
		AuctionShipmentRepository auctionShipmentRepository
	) {
		this.customerRepository = customerRepository;
		this.salesSlipRepository = salesSlipRepository;
		this.orchidGroupRepository = orchidGroupRepository;
		this.auctionShipmentRepository = auctionShipmentRepository;
	}

	@Transactional(readOnly = true)
	public List<CustomerResponse> getCustomers(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return customerRepository.findAll().stream()
				.map(CustomerResponse::from)
				.toList();
		}
		return customerRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword.trim()).stream()
			.map(CustomerResponse::from)
			.toList();
	}

	public CustomerResponse createCustomer(CustomerCreateRequest request) {
		Customer customer = new Customer(
			normalizeRequired(request.name()),
			normalize(request.ownerName()),
			normalize(request.phone()),
			normalize(request.address()),
			normalize(request.memo())
		);
		return CustomerResponse.from(customerRepository.save(customer));
	}

	@Transactional(readOnly = true)
	public List<SalesSlipResponse> getSalesSlips(Long customerId, LocalDate from, LocalDate to) {
		return salesSlipRepository.search(customerId, from, to).stream()
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
		if (request.customerId() == null) throw new IllegalArgumentException("일반 판매는 거래처를 선택해야 합니다.");
		if (request.items().isEmpty()) throw new IllegalArgumentException("일반 판매 품목을 한 개 이상 입력해야 합니다.");
		Customer customer = findCustomer(request.customerId());
		SalesSlip salesSlip = new SalesSlip(
			createSlipNumber(request.saleDate(), salesType),
			request.saleDate(),
			salesType,
			null,
			customer,
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
		Customer customer = request.customerId() == null
			? customerRepository.findByNameIgnoreCase(shipment.getAuctionMarket())
				.orElseGet(() -> customerRepository.save(new Customer(shipment.getAuctionMarket(), null, null, null, "경매장 자동 생성")))
			: findCustomer(request.customerId());
		SalesSlip salesSlip = new SalesSlip(
			createSlipNumber(shipment.getShipmentDate(), SalesType.AUCTION),
			shipment.getShipmentDate(),
			SalesType.AUCTION,
			shipment,
			customer,
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

	private Customer findCustomer(Long customerId) {
		return customerRepository.findById(customerId)
			.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
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
