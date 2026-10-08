package com.greenhouse.backend.sales.repository.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPaymentEventRepository extends JpaRepository<PartnerPaymentEvent, Long> {

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
