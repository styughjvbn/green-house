package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupReader {
	private static final int ID_BATCH_SIZE = 500;
	private final OrchidGroupRepository orchidGroupRepository;

	public Optional<OrchidGroup> findById(Long orchidGroupId) {
		return orchidGroupRepository.findById(orchidGroupId);
	}

	public Optional<OrchidGroup> findDetailById(Long orchidGroupId) {
		return orchidGroupRepository.findDetailById(orchidGroupId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Map<Long, OrchidGroupState> lockStates(Collection<Long> orchidGroupIds) {
		return loadStates(orchidGroupIds, true);
	}

	public Map<Long, OrchidGroupState> getStates(Collection<Long> orchidGroupIds) {
		return loadStates(orchidGroupIds, false);
	}

	private Map<Long, OrchidGroupState> loadStates(Collection<Long> orchidGroupIds, boolean lock) {
		var ids = orchidGroupIds.stream().distinct().sorted().toList();
		var states = new HashMap<Long, OrchidGroupState>();
		for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
			var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
			if (lock && orchidGroupRepository.findAllForUpdateByIdIn(batch).size() != batch.size()) {
				throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
			}
			for (var group : orchidGroupRepository.findDetailsByIds(batch)) {
				states.put(group.getId(), OrchidGroupState.from(group));
			}
		}
		if (states.size() != ids.size()) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		return Map.copyOf(states);
	}

	public List<OrchidGroupState> searchSellable(String keyword, Long varietyId, String status) {
		return orchidGroupRepository.searchSellable(keyword, varietyId, status).stream()
				.map(OrchidGroupState::from).toList();
	}
}
