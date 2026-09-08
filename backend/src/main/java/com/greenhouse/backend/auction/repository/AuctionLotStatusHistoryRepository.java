package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.AuctionLotStatusHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionLotStatusHistoryRepository extends JpaRepository<AuctionLotStatusHistory, Long> {

	@Query("""
			select history from AuctionLotStatusHistory history
			where history.shipmentLot.id in :lotIds
			order by history.shipmentLot.id asc, history.changedAt asc
			""")
	List<AuctionLotStatusHistory> findAllByLotIdIn(@Param("lotIds") Collection<Long> lotIds);

}
