package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.sales.domain.Customer;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
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

	public SalesService(
		CustomerRepository customerRepository,
		SalesSlipRepository salesSlipRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.customerRepository = customerRepository;
		this.salesSlipRepository = salesSlipRepository;
		this.orchidGroupRepository = orchidGroupRepository;
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

	public SalesSlipResponse createSalesSlip(SalesSlipCreateRequest request) {
		Customer customer = customerRepository.findById(request.customerId())
			.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
		SalesSlip salesSlip = new SalesSlip(
			createSlipNumber(request.saleDate()),
			request.saleDate(),
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
			normalizeRequired(item.itemName()),
			normalize(item.genus()),
			normalize(item.spec()),
			item.quantity(),
			item.unitPrice(),
			normalize(item.memo())
		)));
		return SalesSlipResponse.from(salesSlipRepository.save(salesSlip));
	}

	private String createSlipNumber(LocalDate saleDate) {
		long sequence = salesSlipRepository.countBySaleDate(saleDate) + 1;
		return "S" + saleDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
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
