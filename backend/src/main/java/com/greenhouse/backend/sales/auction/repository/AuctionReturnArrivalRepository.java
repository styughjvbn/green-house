package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionReturnArrival;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuctionReturnArrivalRepository extends JpaRepository<AuctionReturnArrival, Long> {
  Optional<AuctionReturnArrival> findByIdAndLotId(Long id, Long lotId);

  boolean existsByLotIdAndCanceledAtIsNull(Long lotId);

  Page<AuctionReturnArrival> findByLotId(Long lotId, Pageable pageable);

  @Query(
      "select max(arrival.arrivalDate) from AuctionReturnArrival arrival where arrival.lotId = :lotId and arrival.canceledAt is null")
  LocalDate latestValidArrivalDate(Long lotId);
}
