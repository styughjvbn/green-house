package com.greenhouse.backend.work.operation.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_creation_batch_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Groups operations created by one request; replay protection belongs to WorkCommandReceipt.
public class WorkCreationBatchMembership {

  @EmbeddedId private WorkCreationBatchMembershipId id;

  public WorkCreationBatchMembership(String receiptKey, Long operationId) {
    if (receiptKey == null || operationId == null) {
      throw new IllegalArgumentException("작업 요청과 결과 작업 ID가 필요합니다.");
    }
    this.id = new WorkCreationBatchMembershipId(receiptKey, operationId);
  }
}
