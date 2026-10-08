package com.greenhouse.backend.sales.repository.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventStatus;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPaymentEventRepository extends JpaRepository<PartnerPaymentEvent, Long> {

  @Query(
      "select distinct e.parentEvent.id as receiptId, e.partnerId as partnerId, e.targetType as targetType, e.targetId as targetId from PartnerPaymentEvent e where e.parentEvent.id in :ids and e.eventType in (com.greenhouse.backend.sales.domain.payment.PaymentEventType.PAYMENT_ALLOCATED, com.greenhouse.backend.sales.domain.payment.PaymentEventType.MANUAL_MATCH_CONFIRMED)")
  List<AllocationReference> findAllocationReferences(Collection<Long> ids);

  interface AllocationReference {
    Long getReceiptId();

    Long getPartnerId();

    PaymentTargetType getTargetType();

    Long getTargetId();
  }

  @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select event from PartnerPaymentEvent event where event.partnerId = :partnerId and event.id in :ids order by event.id")
  List<PartnerPaymentEvent> findAllForUpdate(Long partnerId, Collection<Long> ids);

  @Query(
      "select event from PartnerPaymentEvent event where event.partnerId = :partnerId and event.parentEvent.id = :receiptId and event.eventType in (com.greenhouse.backend.sales.domain.payment.PaymentEventType.MANUAL_MATCH_CONFIRMED, com.greenhouse.backend.sales.domain.payment.PaymentEventType.PAYMENT_ALLOCATED) order by event.id desc")
  Page<PartnerPaymentEvent> findAllocationPage(Long partnerId, Long receiptId, Pageable pageable);

  @Query(
      "select event from PartnerPaymentEvent event where event.partnerId = :partnerId and event.id in :ids")
  List<PartnerPaymentEvent> findAllOwned(Long partnerId, Collection<Long> ids);

  @Query(
      "select count(event) > 0 from PartnerPaymentEvent event where event.parentEvent.id = :receiptId and event.eventType in (com.greenhouse.backend.sales.domain.payment.PaymentEventType.PAYMENT_ALLOCATED, com.greenhouse.backend.sales.domain.payment.PaymentEventType.MANUAL_MATCH_CONFIRMED) and event.status <> com.greenhouse.backend.sales.domain.payment.PaymentEventStatus.CANCELLED")
  boolean hasActiveAllocations(Long receiptId);

  boolean existsByParentEventIdAndStatusNot(Long parentEventId, PaymentEventStatus status);

  // PostgreSQL sums bigint as numeric; retain precision before checking the summary's bigint limit.
  @Query(
      value =
          """
      select coalesce(sum(cast(unapplied_amount as numeric)), 0)
      from partner_payment_events
      where partner_id = :partnerId and event_type = 'PAYMENT_RECEIVED'
        and status in ('UNAPPLIED', 'PARTIALLY_APPLIED')
      """,
      nativeQuery = true)
  BigDecimal sumUnassignedAmount(@Param("partnerId") Long partnerId);

  boolean existsByTargetTypeAndTargetId(PaymentTargetType targetType, Long targetId);

  @Query(
      """
			select distinct event.targetId from PartnerPaymentEvent event
			where event.targetType = :targetType
			  and event.targetId in :targetIds
			""")
  List<Long> findExistingTargetIds(
      @Param("targetType") PaymentTargetType targetType, @Param("targetIds") List<Long> targetIds);

  @Query(
      value =
          "select original_target_type as targetType, original_target_id as targetId from payment_target_aliases where target_type = :targetType and target_id = :targetId",
      nativeQuery = true)
  Optional<TargetAlias> findOriginalTarget(String targetType, Long targetId);

  interface TargetAlias {
    String getTargetType();

    Long getTargetId();
  }

  Optional<PartnerPaymentEvent> findByExternalUid(String externalUid);

  @Query(
      """
			select event from PartnerPaymentEvent event
			where (:partnerId is null or event.partnerId = :partnerId)
			  and (:targetType is null or event.targetType = :targetType)
			  and (:targetId is null or event.targetId = :targetId)
			  and (:eventType is null or event.eventType = :eventType)
			order by event.eventDate desc, event.id desc
			""")
  Page<PartnerPaymentEvent> search(
      @Param("partnerId") Long partnerId,
      @Param("targetType") PaymentTargetType targetType,
      @Param("targetId") Long targetId,
      @Param("eventType") PaymentEventType eventType,
      Pageable pageable);
}
