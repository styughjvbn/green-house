package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;

import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BusinessPartnerReader {
	private final BusinessPartnerRepository partnerRepository;

	public BusinessPartnerInfo getInfo(Long partnerId) {
		return partnerRepository.findById(partnerId).map(BusinessPartnerInfo::from)
				.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
	}

	public BusinessPartnerInfo getActiveInfo(Long partnerId) {
		var partner = getInfo(partnerId);
		if (!partner.active()) {
			throw new IllegalArgumentException("비활성 거래처는 사용할 수 없습니다.");
		}
		return partner;
	}

	public Map<Long, BusinessPartnerInfo> getAllInfo(Collection<Long> partnerIds) {
		var requestedIds = new HashSet<>(partnerIds);
		if (requestedIds.isEmpty()) {
			return Map.of();
		}
		var partners = partnerRepository.findAllById(requestedIds);
		if (partners.size() != requestedIds.size()) {
			throw new NotFoundException("거래처를 찾을 수 없습니다.");
		}
		return partners.stream().map(BusinessPartnerInfo::from)
				.collect(Collectors.toUnmodifiableMap(BusinessPartnerInfo::id, Function.identity()));
	}

}
