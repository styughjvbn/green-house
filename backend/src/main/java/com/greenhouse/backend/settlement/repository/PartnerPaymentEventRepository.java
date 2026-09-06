package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPaymentEventRepository extends JpaRepository<PartnerPaymentEvent, Long> {
	boolean existsByTargetTypeAndTargetId(PaymentTargetType targetType, Long targetId);

	@Query("""
			select distinct event.targetId from PartnerPaymentEvent event
			where event.targetType = :targetType
			  and event.targetId in :targetIds
			""")
	List<Long> findExistingTargetIds(
			@Param("targetType") PaymentTargetType targetType,
			@Param("targetIds") List<Long> targetIds);

	Optional<PartnerPaymentEvent> findByExternalUid(String externalUid);

	@Query("""
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
