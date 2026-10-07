package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionProceeds;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuctionProceedsRepository extends JpaRepository<AuctionProceeds, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select proceeds from AuctionProceeds proceeds where proceeds.id = :id")
  Optional<AuctionProceeds> findForUpdate(Long id);
}
