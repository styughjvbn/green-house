package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks belong to the calling write use case and remain held until its transaction ends.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class BusinessPartnerLock {

	private final BusinessPartnerRepository partnerRepository;

	/**
	 * Returns each requested partner once, in ascending ID order. Inactive partners are
	 * included.
	 */
	public List<BusinessPartnerInfo> lockAll(Collection<Long> partnerIds) {
		var requestedIds = new HashSet<>(partnerIds);
		if (requestedIds.isEmpty()) {
			return List.of();
		}
		var partners = partnerRepository.findAllForUpdateByIdIn(requestedIds);
		if (partners.size() != requestedIds.size()) {
			throw new NotFoundException("거래처를 찾을 수 없습니다.");
		}
		return partners.stream().map(BusinessPartnerInfo::from).toList();
	}

}
