package com.greenhouse.backend.sales.repository.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionLotStatusHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionLotStatusHistoryRepository
    extends JpaRepository<AuctionLotStatusHistory, Long> {

  @Query(
      """
			select history from AuctionLotStatusHistory history
			where history.shipmentLot.id in :lotIds
			order by history.shipmentLot.id asc, history.changedAt asc, history.id asc
			""")
  List<AuctionLotStatusHistory> findAllByLotIdIn(@Param("lotIds") Collection<Long> lotIds);
}
