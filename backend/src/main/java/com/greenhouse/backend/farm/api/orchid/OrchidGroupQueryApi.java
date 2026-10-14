package com.greenhouse.backend.farm.api.orchid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OrchidGroupQueryApi {

  Map<Long, OrchidGroupState> getStates(Collection<Long> orchidGroupIds);

  Map<Long, OrchidGroupState> lockStates(Collection<Long> orchidGroupIds);

  void lockGroups(Collection<Long> orchidGroupIds);

  List<OrchidGroupState> searchSellable(String keyword, Long varietyId, String status);
}
