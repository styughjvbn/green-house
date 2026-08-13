package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import java.util.Collection;
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

	@Transactional
	public List<OrchidGroup> findAllForUpdateByIds(Collection<Long> orchidGroupIds) {
		if (orchidGroupIds == null || orchidGroupIds.isEmpty()) {
			return List.of();
		}
		return orchidGroupRepository.findAllForUpdateByIdIn(orchidGroupIds.stream().sorted().toList());
	}

	@Transactional
	public List<OrchidGroup> findAllDetailsForUpdateByIds(Collection<Long> orchidGroupIds) {
		List<Long> sortedIds = orchidGroupIds == null
				? List.of()
				: orchidGroupIds.stream().distinct().sorted().toList();
		if (sortedIds.isEmpty()) {
			return List.of();
		}
		List<OrchidGroup> locked = orchidGroupRepository.findAllForUpdateByIdIn(sortedIds);
		if (locked.size() != sortedIds.size()) {
			return locked;
		}
		return orchidGroupRepository.findDetailsByIds(sortedIds);
	}

	public List<OrchidGroup> searchSellable(String keyword, Long varietyId, String status) {
		return orchidGroupRepository.searchSellable(keyword, varietyId, status);
	}
}
