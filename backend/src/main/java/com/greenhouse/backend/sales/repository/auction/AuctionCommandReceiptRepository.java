package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionCommandReceiptRepository
    extends JpaRepository<AuctionCommandReceipt, Long> {
  Optional<AuctionCommandReceipt> findByLotIdAndCommandTypeAndRequestKey(
      Long lotId, String commandType, String requestKey);
}
