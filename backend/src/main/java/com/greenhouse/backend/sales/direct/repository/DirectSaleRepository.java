package com.greenhouse.backend.sales.direct.repository;

import com.greenhouse.backend.sales.direct.domain.DirectSale;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectSaleRepository extends JpaRepository<DirectSale, Long> {
  @Query(
      "select distinct sale from DirectSale sale left join fetch sale.prices where sale.documentId in :ids")
  List<DirectSale> findAllWithPrices(@Param("ids") Collection<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select sale from DirectSale sale where sale.documentId = :id")
  Optional<DirectSale> findForUpdate(@Param("id") Long id);
}
