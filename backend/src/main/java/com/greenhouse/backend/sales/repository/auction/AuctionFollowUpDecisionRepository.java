package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionFollowUpDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionFollowUpDecisionRepository
    extends JpaRepository<AuctionFollowUpDecision, Long> {
  Optional<AuctionFollowUpDecision> findFirstByLotIdOrderByIdDesc(Long lotId);
}
