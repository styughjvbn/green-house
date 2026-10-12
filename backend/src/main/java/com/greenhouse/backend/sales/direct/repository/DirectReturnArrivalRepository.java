package com.greenhouse.backend.sales.direct.repository;

import com.greenhouse.backend.sales.direct.domain.DirectReturnArrival;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectReturnArrivalRepository extends JpaRepository<DirectReturnArrival, Long> {
  List<DirectReturnArrival> findByDocumentIdAndCanceledAtIsNull(Long documentId);

  Page<DirectReturnArrival> findByDocumentId(Long documentId, Pageable pageable);

  Optional<DirectReturnArrival> findByIdAndDocumentId(Long id, Long documentId);

  boolean existsByReturnChangeIdAndCanceledAtIsNull(Long returnChangeId);
}
