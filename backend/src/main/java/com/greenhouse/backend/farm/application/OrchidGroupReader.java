package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupReader {
	private final OrchidGroupRepository orchidGroupRepository;

	public Optional<OrchidGroup> findById(Long orchidGroupId) {
		return orchidGroupRepository.findById(orchidGroupId);
	}

	public Optional<OrchidGroup> findDetailById(Long orchidGroupId) {
		return orchidGroupRepository.findDetailById(orchidGroupId);
	}

	public List<OrchidGroup> searchSellable(String keyword, Long varietyId, String status) {
		return orchidGroupRepository.searchSellable(keyword, varietyId, status);
	}
}
