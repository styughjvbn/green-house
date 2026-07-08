package com.greenhouse.backend.analytics.repository;

import com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse;
import com.greenhouse.backend.work.domain.WorkRecord;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SalesAnalyticsRepository {

	private final EntityManager entityManager;

	public Long sumSales(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select coalesce(sum(s.totalAmount), 0)
				from SalesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				""", Long.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getSingleResult();
	}

	public Long sumShippedQuantity(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select coalesce(sum(i.quantity), 0)
				from SalesSlipItem i
				join i.salesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				""", Long.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getSingleResult();
	}

	public Long sumUnpaidAmount(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select coalesce(sum(s.remainingAmount), 0)
				from SalesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
					and s.remainingAmount > 0
				""", Long.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getSingleResult();
	}

	public List<Object[]> monthlySales(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select year(s.saleDate), month(s.saleDate), coalesce(sum(s.totalAmount), 0)
				from SalesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				group by year(s.saleDate), month(s.saleDate)
				order by year(s.saleDate), month(s.saleDate)
				""", Object[].class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getResultList();
	}

	public List<Object[]> varietySales(LocalDate from, LocalDate to, int limit) {
		return entityManager.createQuery("""
				select i.itemName, coalesce(sum(i.amount), 0)
				from SalesSlipItem i
				join i.salesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				group by i.itemName
				order by coalesce(sum(i.amount), 0) desc
				""", Object[].class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setMaxResults(limit)
				.getResultList();
	}

	public List<Object[]> partnerSales(LocalDate from, LocalDate to, int limit) {
		return entityManager.createQuery("""
				select p.name, coalesce(sum(s.totalAmount), 0)
				from SalesSlip s
				join s.partner p
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				group by p.name
				order by coalesce(sum(s.totalAmount), 0) desc
				""", Object[].class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setMaxResults(limit)
				.getResultList();
	}

	public List<Object[]> paymentBreakdown(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select s.paymentStatus, coalesce(sum(s.totalAmount), 0)
				from SalesSlip s
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				group by s.paymentStatus
				""", Object[].class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getResultList();
	}

	public List<AnalyticsSlipSummaryResponse> recentSlips(LocalDate from, LocalDate to, int limit) {
		return entityManager.createQuery("""
				select new com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse(
					s.id, s.slipNumber, s.saleDate, p.name, s.totalAmount, s.paidAmount, s.remainingAmount,
					s.paymentStatus, s.salesStatus)
				from SalesSlip s
				join s.partner p
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
				order by s.saleDate desc, s.id desc
				""", AnalyticsSlipSummaryResponse.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setMaxResults(limit)
				.getResultList();
	}

	public List<AnalyticsSlipSummaryResponse> unpaidSlips(LocalDate from, LocalDate to, int limit) {
		return entityManager.createQuery("""
				select new com.greenhouse.backend.analytics.dto.AnalyticsSlipSummaryResponse(
					s.id, s.slipNumber, s.saleDate, p.name, s.totalAmount, s.paidAmount, s.remainingAmount,
					s.paymentStatus, s.salesStatus)
				from SalesSlip s
				join s.partner p
				where s.saleDate between :from and :to
					and s.salesStatus <> '취소'
					and s.remainingAmount > 0
				order by s.remainingAmount desc, s.saleDate desc
				""", AnalyticsSlipSummaryResponse.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setMaxResults(limit)
				.getResultList();
	}

	public List<PartnerAnalyticsStatResponse> partnerStats(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select new com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse(
					p.id,
					p.name,
					p.partnerType,
					coalesce(sum(s.totalAmount), 0),
					count(s.id),
					coalesce(sum(s.remainingAmount), 0),
					coalesce(sum(s.paidAmount), 0),
					coalesce(b.receivableBalance, 0),
					coalesce(b.creditBalance, 0),
					coalesce(b.unappliedPaymentAmount, 0),
					max(s.saleDate))
				from BusinessPartner p
				left join SalesSlip s on s.partner = p and s.saleDate between :from and :to and s.salesStatus <> '취소'
				left join PartnerBalanceSummary b on b.partner = p
				group by p.id, p.name, p.partnerType, b.receivableBalance, b.creditBalance, b.unappliedPaymentAmount
				having count(s.id) > 0
					or coalesce(b.receivableBalance, 0) > 0
					or coalesce(b.creditBalance, 0) > 0
					or coalesce(b.unappliedPaymentAmount, 0) > 0
				order by coalesce(sum(s.totalAmount), 0) desc, count(s.id) desc
				""", PartnerAnalyticsStatResponse.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getResultList();
	}

	public Long countWorkRecords(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select count(w.id)
				from WorkRecord w
				where w.workDate between :from and :to
				""", Long.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getSingleResult();
	}

	public Long countWorkRecordsByTemplate(LocalDate from, LocalDate to, String template) {
		return entityManager.createQuery("""
				select count(w.id)
				from WorkRecord w
				left join w.workTypeRef wt
				where w.workDate between :from and :to
					and wt.template = :template
				""", Long.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setParameter("template", com.greenhouse.backend.work.domain.WorkTypeTemplate.valueOf(template))
				.getSingleResult();
	}

	public LocalDate latestWorkDate(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select max(w.workDate)
				from WorkRecord w
				where w.workDate between :from and :to
				""", LocalDate.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getSingleResult();
	}

	public List<Object[]> workTypeCounts(LocalDate from, LocalDate to) {
		return entityManager.createQuery("""
				select w.workType, count(w.id)
				from WorkRecord w
				where w.workDate between :from and :to
				group by w.workType
				order by count(w.id) desc
				""", Object[].class)
				.setParameter("from", from)
				.setParameter("to", to)
				.getResultList();
	}

	public List<WorkRecord> recentWorkRecords(LocalDate from, LocalDate to, int limit) {
		return entityManager.createQuery("""
				select w
				from WorkRecord w
				left join fetch w.workTypeRef wt
				where w.workDate between :from and :to
				order by w.workDate desc, w.id desc
				""", WorkRecord.class)
				.setParameter("from", from)
				.setParameter("to", to)
				.setMaxResults(limit)
				.getResultList();
	}
}
