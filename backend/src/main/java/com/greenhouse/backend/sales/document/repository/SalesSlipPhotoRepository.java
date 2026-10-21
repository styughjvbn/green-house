package com.greenhouse.backend.sales.document.repository;

import com.greenhouse.backend.sales.document.domain.SalesPhotoReference;
import com.greenhouse.backend.sales.document.domain.SalesSlipPhoto;
import com.greenhouse.backend.sales.document.domain.SalesSlipPhotoInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesSlipPhotoRepository extends JpaRepository<SalesSlipPhoto, Long> {
  @Query(
      "select new com.greenhouse.backend.sales.document.domain.SalesSlipPhotoInfo(p.fileName, p.contentType) from SalesSlipPhoto p where p.salesSlipId = :id")
  Optional<SalesSlipPhotoInfo> findInfo(@Param("id") Long id);

  @Query(
      "select new com.greenhouse.backend.sales.document.domain.SalesPhotoReference(p.salesSlipId,p.storageKey,p.byteSize,p.sha256) from SalesSlipPhoto p where p.salesSlipId > :after order by p.salesSlipId")
  List<SalesPhotoReference> findReferencesAfter(@Param("after") long after, Pageable page);

  @Query("select p.storageKey from SalesSlipPhoto p where p.storageKey in :keys")
  List<String> findReferencedKeys(@Param("keys") Collection<String> keys);
}
