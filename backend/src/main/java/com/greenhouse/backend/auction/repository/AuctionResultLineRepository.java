package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionResultLineRepository
		extends JpaRepository<AuctionResultLine, Long>, AuctionResultLineRepositoryCustom {
}
