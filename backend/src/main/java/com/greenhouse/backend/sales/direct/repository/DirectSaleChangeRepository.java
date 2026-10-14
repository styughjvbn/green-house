package com.greenhouse.backend.sales.direct.repository;

import com.greenhouse.backend.sales.direct.domain.DirectSaleChange;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectSaleChangeRepository extends JpaRepository<DirectSaleChange, Long> {
  @org.springframework.data.jpa.repository.Query(
      """
      select change.itemId, sum(change.amountDelta) from DirectSaleChange change, DirectSale sale
      where change.documentId = sale.documentId
        and sale.saleDate >= :from and sale.saleDate <= :to
      group by change.itemId
      having sum(change.amountDelta) <> 0
      """)
  List<Object[]> sumItemChanges(
      @org.springframework.data.repository.query.Param("from") LocalDate from,
      @org.springframework.data.repository.query.Param("to") LocalDate to);

  List<DirectSaleChange> findByDocumentIdInOrderByIdAsc(Collection<Long> documentIds);

  Optional<DirectSaleChange> findByDocumentIdAndRequestKey(Long documentId, String requestKey);
}
