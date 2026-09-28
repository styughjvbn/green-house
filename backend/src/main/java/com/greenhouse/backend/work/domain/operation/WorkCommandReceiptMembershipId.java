package com.greenhouse.backend.work.domain.operation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@EqualsAndHashCode
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCommandReceiptMembershipId implements Serializable {

	@Column(name = "receipt_key", length = 255)
	private String receiptKey;

	@Column(name = "operation_id")
	private Long operationId;

	public WorkCommandReceiptMembershipId(String receiptKey, Long operationId) {
		this.receiptKey = receiptKey;
		this.operationId = operationId;
	}

}
