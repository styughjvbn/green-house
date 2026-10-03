package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionShipmentLifecycleService {

  private final AuctionShipmentRepository auctionShipmentRepository;

  private final AuctionShipmentLotRepository auctionShipmentLotRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteDraftShipment(Long shipmentId) {
    if (shipmentId == null) {
      return;
    }
    // Serialize deletion with result, return, adjustment and status writers before checking facts.
    auctionShipmentLotRepository.findAllForUpdateByShipmentId(shipmentId);
    if (!findNonCancelableShipmentIds(List.of(shipmentId)).isEmpty()) {
      throw new IllegalArgumentException("경매 처리 이력이 있는 출하 lot이 있어 전표를 취소할 수 없습니다.");
    }
    auctionShipmentRepository.deleteById(shipmentId);
  }

  public Set<Long> findNonCancelableShipmentIds(Collection<Long> shipmentIds) {
    if (shipmentIds.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(
        auctionShipmentLotRepository.findNonCancelableShipmentIds(
            shipmentIds, AuctionLotStatus.WAITING));
  }
}
