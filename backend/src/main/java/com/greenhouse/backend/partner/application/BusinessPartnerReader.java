package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BusinessPartnerReader {
	private final BusinessPartnerRepository partnerRepository;

	public BusinessPartner get(Long partnerId) {
		return partnerRepository.findById(partnerId)
				.orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
	}

	public BusinessPartner getActive(Long partnerId) {
		var partner = get(partnerId);
		if (!partner.isActive())
			throw new IllegalArgumentException("비활성 거래처는 사용할 수 없습니다.");
		return partner;
	}
}
