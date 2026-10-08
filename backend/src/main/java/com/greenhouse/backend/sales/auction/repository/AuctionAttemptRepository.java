package com.greenhouse.backend.sales.auction.repository;

import com.greenhouse.backend.sales.auction.domain.AuctionAttempt;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionAttemptRepository extends JpaRepository<AuctionAttempt, Long> {

  @Query(
      """
			select distinct attempt from AuctionAttempt attempt
			left join fetch attempt.resultLines line
			where attempt.shipmentLot.id in :lotIds
			order by attempt.shipmentLot.id asc, attempt.auctionDate asc, attempt.attemptNo asc, line.id asc
			""")
  List<AuctionAttempt> findAllWithResultLinesByLotIdIn(@Param("lotIds") Collection<Long> lotIds);
}
