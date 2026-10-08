package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.domain.direct.DirectSaleAmounts;
import com.greenhouse.backend.sales.repository.direct.DirectSaleAmountReconciliationRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectSaleReviewReader {
  private final DirectSaleAmountReconciliationRepository repository;

  public Set<Long> findRequired(Collection<Long> ids) {
    var ordered = ids.stream().distinct().sorted().toList();
    var required = new HashSet<Long>();
    for (int offset = 0; offset < ordered.size(); offset += 500) {
      for (var evidence :
          repository.findAll(ordered.subList(offset, Math.min(offset + 500, ordered.size())))) {
        if (evidence.requiresReview()) required.add(evidence.getDocumentId());
      }
    }
    return Set.copyOf(required);
  }

  public void requireClear(Long id) {
    DirectSaleAmounts.requireReviewCleared(findRequired(List.of(id)).contains(id));
  }
}
