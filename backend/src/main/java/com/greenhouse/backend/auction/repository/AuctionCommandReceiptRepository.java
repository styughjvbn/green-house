package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionCommandReceiptRepository
    extends JpaRepository<AuctionCommandReceipt, Long> {
  Optional<AuctionCommandReceipt> findByLotIdAndCommandTypeAndRequestKey(
      Long lotId, String commandType, String requestKey);
}
