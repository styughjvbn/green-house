package com.greenhouse.backend.sales.document.repository;

import com.greenhouse.backend.sales.document.domain.SalesSlipPhoto;
import com.greenhouse.backend.sales.document.domain.SalesSlipPhotoInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipPhotoRepository extends JpaRepository<SalesSlipPhoto, Long> {
  @Query(
      "select new com.greenhouse.backend.sales.document.domain.SalesSlipPhotoInfo(p.fileName, p.contentType) from SalesSlipPhoto p where p.salesSlipId = :id")
  Optional<SalesSlipPhotoInfo> findInfo(@Param("id") Long id);
}
