package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQueryApi;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupReader implements OrchidGroupQueryApi {

  private static final int ID_BATCH_SIZE = 500;

  private final OrchidGroupRepository orchidGroupRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public Map<Long, OrchidGroupState> lockStates(Collection<Long> orchidGroupIds) {
    return loadStates(orchidGroupIds, true);
  }

  /** Lock the complete use-case ID set without loading state/association snapshots. */
  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void lockGroups(Collection<Long> orchidGroupIds) {
    var ids = orchidGroupIds.stream().distinct().sorted().toList();
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      lockBatch(ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size())));
    }
  }

  @Override
  public Map<Long, OrchidGroupState> getStates(Collection<Long> orchidGroupIds) {
    return loadStates(orchidGroupIds, false);
  }

  private Map<Long, OrchidGroupState> loadStates(Collection<Long> orchidGroupIds, boolean lock) {
    var ids = orchidGroupIds.stream().distinct().sorted().toList();
    var states = new HashMap<Long, OrchidGroupState>();
    for (int start = 0; start < ids.size(); start += ID_BATCH_SIZE) {
      var batch = ids.subList(start, Math.min(start + ID_BATCH_SIZE, ids.size()));
      if (lock) lockBatch(batch);
      for (var group : orchidGroupRepository.findDetailsByIds(batch)) {
        states.put(group.getId(), OrchidGroupStateFactory.from(group));
      }
    }
    if (states.size() != ids.size()) {
      throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
    }
    return Map.copyOf(states);
  }

  private void lockBatch(List<Long> ids) {
    if (orchidGroupRepository.findAllForUpdateByIdIn(ids).size() != ids.size()) {
      throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
    }
  }

  @Override
  public List<OrchidGroupState> searchSellable(String keyword, Long varietyId, String status) {
    return orchidGroupRepository
        .searchSellable(
            keyword, varietyId, status, OrchidGroupStatusPolicy.unavailableForSaleStatuses())
        .stream()
        .map(OrchidGroupStateFactory::from)
        .toList();
  }
}
