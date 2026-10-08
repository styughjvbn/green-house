package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionFollowUpDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionFollowUpDecisionRepository
    extends JpaRepository<AuctionFollowUpDecision, Long> {
  Optional<AuctionFollowUpDecision> findFirstByLotIdOrderByIdDesc(Long lotId);
}
