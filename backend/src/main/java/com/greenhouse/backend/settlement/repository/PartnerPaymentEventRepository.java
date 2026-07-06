package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerPaymentEventRepository extends JpaRepository<PartnerPaymentEvent, Long> {
	@EntityGraph(attributePaths = {"partner", "parentEvent"})
	@Query("""
		select event from PartnerPaymentEvent event
		where (:partnerId is null or event.partner.id = :partnerId)
		  and (:targetType is null or event.targetType = :targetType)
		  and (:targetId is null or event.targetId = :targetId)
		order by event.eventDate desc, event.id desc
		""")
	List<PartnerPaymentEvent> search(
		@Param("partnerId") Long partnerId,
		@Param("targetType") PaymentTargetType targetType,
		@Param("targetId") Long targetId);
}
