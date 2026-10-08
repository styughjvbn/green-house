package com.greenhouse.backend.sales.direct.repository;

import com.greenhouse.backend.sales.direct.domain.DirectSaleAmountReconciliation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface DirectSaleAmountReconciliationRepository
    extends Repository<DirectSaleAmountReconciliation, Long> {
  @Query("select review from DirectSaleAmountReconciliation review where review.documentId in :ids")
  List<DirectSaleAmountReconciliation> findAll(@Param("ids") Collection<Long> ids);
}
