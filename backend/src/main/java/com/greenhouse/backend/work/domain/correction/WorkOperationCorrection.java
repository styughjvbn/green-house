package com.greenhouse.backend.work.domain.correction;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "work_operation_corrections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOperationCorrection {

  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "work_operation_corrections_id_seq")
  @SequenceGenerator(
      name = "work_operation_corrections_id_seq",
      sequenceName = "work_operation_corrections_id_seq",
      allocationSize = 50)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "original_work_operation_id", nullable = false)
  private WorkOperation originalWorkOperation;

  @Column(nullable = false, columnDefinition = "text")
  private String reason;

  @Column(length = 100)
  private String worker;

  @Column(length = 1000)
  private String memo;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> resultDetails;

  private Long mutationId;

  private UUID correlationId;

  public WorkOperationCorrection(
      WorkOperation original, String reason, String worker, String memo, LocalDateTime createdAt) {
    if (!original.isStructureResultCorrectable() || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("완료된 구조 변경 작업과 보정 사유가 필요합니다.");
    }
    this.originalWorkOperation = original;
    this.reason = reason.trim();
    this.worker = worker;
    this.memo = memo;
    this.createdAt = createdAt;
    this.resultDetails = Map.of();
  }

  public void complete(Map<String, Object> resultDetails, Long mutationId, UUID correlationId) {
    if (!this.resultDetails.isEmpty()
        || resultDetails == null
        || resultDetails.isEmpty()
        || (mutationId == null) != (correlationId == null)) {
      throw new IllegalStateException("보정 결과와 Mutation 연결은 한 번만 확정해야 합니다.");
    }
    this.resultDetails = resultDetails;
    this.mutationId = mutationId;
    this.correlationId = correlationId;
  }
}
