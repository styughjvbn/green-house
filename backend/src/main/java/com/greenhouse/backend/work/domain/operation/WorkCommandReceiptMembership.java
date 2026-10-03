package com.greenhouse.backend.work.domain.operation;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_command_receipt_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCommandReceiptMembership {

  @EmbeddedId private WorkCommandReceiptMembershipId id;

  public WorkCommandReceiptMembership(String receiptKey, Long operationId) {
    if (receiptKey == null || operationId == null) {
      throw new IllegalArgumentException("작업 요청과 결과 작업 ID가 필요합니다.");
    }
    this.id = new WorkCommandReceiptMembershipId(receiptKey, operationId);
  }
}
