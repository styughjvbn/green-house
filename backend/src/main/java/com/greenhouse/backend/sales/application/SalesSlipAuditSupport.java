package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.sales.domain.SalesSlip;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SalesSlipAuditSupport {
	private final AuditEventWriter auditWriter;

	public SalesSlipAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> snapshot(SalesSlip slip) {
		var data = new LinkedHashMap<String, Object>();
		data.put("slipNumber", slip.getSlipNumber());
		data.put("saleDate", slip.getSaleDate());
		data.put("salesType", slip.getSalesType());
		data.put("partnerId", slip.getPartner().getId());
		data.put("totalAmount", slip.getTotalAmount());
		data.put("expectedPaymentDate", slip.getExpectedPaymentDate());
		data.put("paidAmount", slip.getPaidAmount());
		data.put("remainingAmount", slip.getRemainingAmount());
		data.put("paymentStatus", slip.getPaymentStatus());
		data.put("salesStatus", slip.getSalesStatus());
		data.put("paymentMethod", slip.getPaymentMethod());
		data.put("memo", slip.getMemo());
		data.put("items", slip.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
				.map(item -> {
					var value = new LinkedHashMap<String, Object>();
					value.put("itemName", item.getItemName());
					value.put("genus", item.getGenus());
					value.put("spec", item.getSpec());
					value.put("quantity", item.getQuantity());
					value.put("unitPrice", item.getUnitPrice());
					value.put("amount", item.getAmount());
					value.put("memo", item.getMemo());
					value.put("allocations", item.getAllocations().stream()
							.sorted(Comparator.comparing(allocation -> allocation.getOrchidGroup().getId()))
							.map(allocation -> Map.of(
									"orchidGroupId", allocation.getOrchidGroup().getId(),
									"quantity", allocation.getAllocatedQuantity()))
							.toList());
					return value;
				}).toList());
		return data;
	}

	public Long record(AuditAction action, SalesSlip slip,
			Map<String, Object> before, Map<String, Object> after) {
		return auditWriter.record(action, AuditSource.SALES_MANAGEMENT, "SALES_SLIP", slip.getId(),
				before, after, Map.of("salesType", slip.getSalesType().name()));
	}
}
