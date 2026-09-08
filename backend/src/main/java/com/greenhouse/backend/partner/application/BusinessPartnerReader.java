package com.greenhouse.backend.partner.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.domain.PartnerTextMatch;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BusinessPartnerReader {

	private static final int ID_BATCH_SIZE = 500;

	private final BusinessPartnerRepository partnerRepository;

	/**
	 * All matching IDs, read in bounded batches; historical searches include inactive
	 * partners.
	 */
	public List<Long> findMatchingIds(PartnerTextMatch match, String value) {
		var matches = new ArrayList<Long>();
		long afterId = 0;
		while (true) {
			var batch = partnerRepository.findMatchingIds(match, value, afterId, ID_BATCH_SIZE);
			matches.addAll(batch);
			if (batch.size() < ID_BATCH_SIZE)
				return List.copyOf(matches);
			afterId = batch.getLast();
		}
	}

	public Map<Long, Identity> getIdentities(Collection<Long> partnerIds) {
		var ids = new ArrayList<>(new HashSet<>(partnerIds));
		var identities = new HashMap<Long, Identity>();
		for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
			var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
			for (var row : partnerRepository.findIdentities(batch)) {
				identities.put(row.getId(), new Identity(row.getId(), row.getName(), row.getPartnerType()));
			}
		}
		if (identities.size() != ids.size()) {
			throw new NotFoundException("거래처를 찾을 수 없습니다.");
		}
		return Map.copyOf(identities);
	}

	public record Identity(Long id, String name, PartnerType partnerType) {
	}

	public BusinessPartnerInfo getInfo(Long partnerId) {
		return partnerRepository.findById(partnerId)
			.map(BusinessPartnerInfo::from)
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
		return partners.stream()
			.map(BusinessPartnerInfo::from)
			.collect(Collectors.toUnmodifiableMap(BusinessPartnerInfo::id, Function.identity()));
	}

}
