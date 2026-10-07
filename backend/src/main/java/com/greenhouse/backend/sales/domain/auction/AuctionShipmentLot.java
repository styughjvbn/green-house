package com.greenhouse.backend.sales.domain.auction;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auction_shipment_lots")
public class AuctionShipmentLot extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auction_shipment_lots_id_seq")
  @SequenceGenerator(
      name = "auction_shipment_lots_id_seq",
      sequenceName = "auction_shipment_lots_id_seq",
      allocationSize = 50)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shipment_id", nullable = false)
  private AuctionShipment shipment;

  @Column(name = "item_name", nullable = false)
  private String itemName;

  @Column(name = "variety_name", nullable = false)
  private String varietyName;

  @Column(name = "shipment_grade")
  private String shipmentGrade;

  @Column private Integer boxes;

  @Column(name = "shipped_quantity", nullable = false)
  private Integer shippedQuantity;

  @Column(name = "sold_quantity", nullable = false)
  private Integer soldQuantity;

  @Column(name = "waiting_quantity", nullable = false)
  private Integer waitingQuantity;

  @Column(name = "returned_quantity", nullable = false)
  private Integer returnedQuantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "follow_up_method")
  private AuctionFollowUpMethod followUpMethod;

  @Column(name = "disposed_quantity", nullable = false)
  private Integer disposedQuantity = 0;

  @Column(name = "inferred_return_quantity", nullable = false)
  private Integer inferredReturnQuantity = 0;

  @Column(name = "return_confirmed_date")
  private LocalDate returnConfirmedDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", nullable = false)
  private AuctionLotStatus currentStatus;

  @Column(columnDefinition = "text")
  private String memo;

  @OneToMany(mappedBy = "shipmentLot", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("auctionDate ASC, attemptNo ASC")
  private List<AuctionAttempt> attempts = new ArrayList<>();

  @OneToMany(mappedBy = "shipmentLot", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("changedAt ASC, id ASC")
  private List<AuctionLotStatusHistory> statusHistory = new ArrayList<>();

  public AuctionShipmentLot(
      String itemName, String varietyName, String grade, Integer boxes, Integer quantity) {
    this.itemName = itemName;
    this.varietyName = varietyName;
    this.shipmentGrade = grade;
    this.boxes = boxes;
    this.shippedQuantity = quantity;
    this.soldQuantity = 0;
    this.waitingQuantity = quantity;
    this.returnedQuantity = 0;
    this.currentStatus = AuctionLotStatus.WAITING;
  }

  void setShipment(AuctionShipment shipment) {
    this.shipment = shipment;
  }

  public void addAttempt(AuctionAttempt attempt) {
    attempts.add(attempt);
    attempt.setShipmentLot(this);
  }

  public void applyResult(
      Integer sold,
      Integer returned,
      boolean failed,
      boolean returnInferred,
      LocalDateTime changedAt) {
    int previousSold = soldQuantity;
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    soldQuantity += sold;
    returnedQuantity += returned;
    waitingQuantity = Math.max(0, shippedQuantity - soldQuantity - returnedQuantity);
    AuctionLotStatus next;
    if (soldQuantity + returnedQuantity > shippedQuantity) {
      next = AuctionLotStatus.QUANTITY_MISMATCH;
    } else if (returnInferred) {
      next = AuctionLotStatus.RETURN_INFERRED;
    } else if (returnedQuantity > 0 && waitingQuantity == 0) {
      next = AuctionLotStatus.RETURNED;
    } else if (soldQuantity == shippedQuantity) {
      next = AuctionLotStatus.SOLD;
    } else if (soldQuantity > 0) {
      next = AuctionLotStatus.PARTIALLY_SOLD;
    } else if (failed) {
      next = AuctionLotStatus.REAUCTION_WAITING;
    } else {
      next = AuctionLotStatus.IN_PROGRESS;
    }
    recordChange(
        next, "경매 결과 반영", null, null, changedAt, previousSold, previousWaiting, previousReturned);
  }

  public void recordResult(
      LocalDate auctionDate,
      Integer requestedAttemptNo,
      AuctionAttemptStatus attemptStatus,
      List<AuctionResultLineInput> resultLines,
      String requestedFailedReason,
      String requestedMemo,
      LocalDateTime changedAt) {
    if (followUpMethod == AuctionFollowUpMethod.FARM_RETURN
        || followUpMethod == AuctionFollowUpMethod.AUCTION_DISPOSAL)
      throw new ConflictException(
          "AUCTION_RESULT_FOLLOW_UP_LOCKED", "현재 후속 처리 결정에서는 경매 결과를 추가할 수 없습니다.");
    if (getWaitingQuantity() <= 0)
      throw new IllegalArgumentException("대기 수량이 없는 lot에는 경매 결과를 추가할 수 없습니다.");
    int waitingQuantity = getWaitingQuantity();
    int attemptNo = resolveAttemptNo(requestedAttemptNo);
    validateAttemptNo(auctionDate, attemptNo);

    String failedReason = normalize(requestedFailedReason);
    String memo = normalize(requestedMemo);
    var attempt = new AuctionAttempt(auctionDate, attemptNo, attemptStatus, failedReason, memo);

    switch (attemptStatus) {
      case SOLD -> {
        int soldQuantity = addSoldLines(attempt, resultLines);
        if (soldQuantity != waitingQuantity)
          throw new IllegalArgumentException("낙찰 상태에서는 남은 대기 수량 전체를 입력해야 합니다.");
        attempt.recalculateStatus();
        addAttempt(attempt);
        applyResult(soldQuantity, 0, false, false, changedAt);
      }
      case PARTIALLY_SOLD -> {
        int soldQuantity = addSoldLines(attempt, resultLines);
        if (soldQuantity >= waitingQuantity)
          throw new IllegalArgumentException("부분 낙찰은 대기 수량보다 적어야 합니다.");
        attempt.addResultLine(
            new AuctionResultLine(
                auctionDate,
                null,
                waitingQuantity - soldQuantity,
                0,
                0,
                failedReason == null ? "잔량 유찰" : failedReason,
                AuctionInspectionStatus.NORMAL));
        attempt.recalculateStatus();
        addAttempt(attempt);
        applyResult(soldQuantity, 0, false, false, changedAt);
      }
      case FAILED -> {
        attempt.addResultLine(
            new AuctionResultLine(
                auctionDate,
                null,
                waitingQuantity,
                0,
                0,
                failedReason == null ? "유찰" : failedReason,
                AuctionInspectionStatus.NORMAL));
        attempt.recalculateStatus();
        addAttempt(attempt);
        applyResult(0, 0, true, false, changedAt);
      }
      case RETURN_INFERRED -> {
        attempt.addResultLine(
            new AuctionResultLine(
                auctionDate,
                null,
                waitingQuantity,
                0,
                0,
                failedReason == null ? "반환 추정" : failedReason,
                AuctionInspectionStatus.RETURN_INFERRED));
        attempt.recalculateStatus();
        addAttempt(attempt);
        applyResult(0, waitingQuantity, false, true, changedAt);
      }
      default -> throw new IllegalArgumentException("지원하지 않는 경매 결과 상태입니다.");
    }
  }

  private int resolveAttemptNo(Integer attemptNo) {
    if (attemptNo != null) return attemptNo;
    return getAttempts().stream()
            .map(AuctionAttempt::getAttemptNo)
            .max(Integer::compareTo)
            .orElse(0)
        + 1;
  }

  private void validateAttemptNo(LocalDate auctionDate, int attemptNo) {
    boolean duplicate =
        getAttempts().stream()
            .anyMatch(
                attempt ->
                    Objects.equals(attempt.getAttemptNo(), attemptNo)
                        && Objects.equals(attempt.getAuctionDate(), auctionDate));
    if (duplicate) throw new IllegalArgumentException("같은 경매일과 차수의 결과가 이미 등록되어 있습니다.");
  }

  private int addSoldLines(AuctionAttempt attempt, List<AuctionResultLineInput> lines) {
    if (lines == null || lines.isEmpty())
      throw new IllegalArgumentException("낙찰 결과 행을 1개 이상 입력해야 합니다.");
    int soldQuantity = 0;
    for (var line : lines) {
      if (line.unitPrice() < 1) throw new IllegalArgumentException("낙찰 결과 단가는 1원 이상이어야 합니다.");
      attempt.addResultLine(
          new AuctionResultLine(
              attempt.getAuctionDate(),
              normalize(line.auctionGrade()),
              line.quantity(),
              line.unitPrice(),
              soldAmount(line.quantity(), line.unitPrice()),
              normalize(line.note()),
              line.inspectionStatus() == null
                  ? AuctionInspectionStatus.NORMAL
                  : line.inspectionStatus()));
      soldQuantity += line.quantity();
    }
    return soldQuantity;
  }

  private int soldAmount(int quantity, int unitPrice) {
    try {
      return Math.multiplyExact(quantity, unitPrice);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("낙찰 결과 금액은 2,147,483,647원 이하여야 합니다.");
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean isQuantityBalanced() {
    return (long) soldQuantity + waitingQuantity + returnedQuantity + disposedQuantity
        == shippedQuantity;
  }

  public boolean isFollowUpDecisionChangeAllowed(boolean hasValidArrivals) {
    return isQuantityBalanced()
        && !hasValidArrivals
        && !(returnedQuantity > 0
            && returnConfirmedDate == null
            && currentStatus != AuctionLotStatus.RETURN_INFERRED)
        && !(returnConfirmedDate != null && returnedQuantity > 0)
        && List.of(
                AuctionLotStatus.REAUCTION_WAITING,
                AuctionLotStatus.PARTIALLY_SOLD,
                AuctionLotStatus.RETURN_INFERRED)
            .contains(currentStatus)
        && getReturnConfirmableQuantity() > 0;
  }

  public boolean isActualArrivalAllowed() {
    return isQuantityBalanced()
        && followUpMethod == AuctionFollowUpMethod.FARM_RETURN
        && waitingQuantity > 0
        && List.of(
                AuctionLotStatus.REAUCTION_WAITING,
                AuctionLotStatus.PARTIALLY_SOLD,
                AuctionLotStatus.PARTIALLY_RETURNED)
            .contains(currentStatus);
  }

  public int decideFollowUp(
      AuctionFollowUpMethod method,
      boolean hasValidArrivals,
      String worker,
      String reason,
      LocalDateTime at) {
    requireFollowUpDecisionChangeAllowed(hasValidArrivals);
    if (method == null || at == null || reason == null || reason.isBlank())
      throw new IllegalArgumentException("후속 처리 방법과 결정 사유, 시각이 필요합니다.");
    if (!isFollowUpDecisionChangeAllowed(hasValidArrivals))
      throw new ConflictException(
          "AUCTION_FOLLOW_UP_NOT_AVAILABLE", "유찰 잔량을 확인한 뒤 후속 처리를 결정해야 합니다.");
    int previousSold = soldQuantity;
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    if (currentStatus == AuctionLotStatus.RETURN_INFERRED && returnConfirmedDate == null) {
      inferredReturnQuantity = returnedQuantity;
      waitingQuantity = Math.addExact(waitingQuantity, returnedQuantity);
      returnedQuantity = 0;
    }
    if (waitingQuantity <= 0)
      throw new ConflictException("AUCTION_FOLLOW_UP_NO_REMAINDER", "처리할 유찰 잔량이 없습니다.");
    int quantity = waitingQuantity;
    followUpMethod = method;
    var next =
        soldQuantity > 0 ? AuctionLotStatus.PARTIALLY_SOLD : AuctionLotStatus.REAUCTION_WAITING;
    if (method == AuctionFollowUpMethod.AUCTION_DISPOSAL) {
      disposedQuantity = Math.addExact(disposedQuantity, waitingQuantity);
      waitingQuantity = 0;
      next = AuctionLotStatus.DISPOSED;
    }
    recordChange(
        next, reason.trim(), worker, null, at, previousSold, previousWaiting, previousReturned);
    return quantity;
  }

  public void recordActualArrival(int quantity, LocalDate date, String worker, LocalDateTime at) {
    if (!isActualArrivalAllowed())
      throw new ConflictException("AUCTION_ARRIVAL_DECISION_REQUIRED", "농장 반환 결정을 먼저 확인해야 합니다.");
    if (quantity < 1 || quantity > waitingQuantity || date == null || at == null)
      throw new IllegalArgumentException("실제 도착 수량은 반환 대기 잔량 이내이며 도착일이 필요합니다.");
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    waitingQuantity -= quantity;
    returnedQuantity = Math.addExact(returnedQuantity, quantity);
    returnConfirmedDate = date;
    recordChange(
        waitingQuantity == 0 ? AuctionLotStatus.RETURNED : AuctionLotStatus.PARTIALLY_RETURNED,
        "실제 농장 도착",
        worker,
        null,
        at,
        soldQuantity,
        previousWaiting,
        previousReturned);
  }

  public void cancelActualArrival(
      int quantity, LocalDate latestValidDate, String worker, String reason, LocalDateTime at) {
    if (followUpMethod != AuctionFollowUpMethod.FARM_RETURN
        || quantity < 1
        || quantity > returnedQuantity)
      throw new IllegalArgumentException("취소할 실제 도착 수량을 확인해야 합니다.");
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    returnedQuantity -= quantity;
    waitingQuantity = Math.addExact(waitingQuantity, quantity);
    returnConfirmedDate = latestValidDate;
    var next =
        returnedQuantity > 0
            ? AuctionLotStatus.PARTIALLY_RETURNED
            : soldQuantity > 0
                ? AuctionLotStatus.PARTIALLY_SOLD
                : AuctionLotStatus.REAUCTION_WAITING;
    recordChange(next, reason, worker, null, at, soldQuantity, previousWaiting, previousReturned);
  }

  public void requireFollowUpDecisionChangeAllowed() {
    requireFollowUpDecisionChangeAllowed(false);
  }

  public void requireFollowUpDecisionChangeAllowed(boolean hasValidArrivals) {
    if (hasValidArrivals || (returnConfirmedDate != null && returnedQuantity > 0))
      throw new ConflictException(
          "AUCTION_FOLLOW_UP_ARRIVAL_EXISTS", "유효한 도착 기록을 먼저 취소한 뒤 후속 처리 결정을 변경해야 합니다.");
  }

  public void requireReturnConfirmable() {
    if (followUpMethod != null)
      throw new ConflictException(
          "AUCTION_ACTUAL_ARRIVAL_REQUIRED", "후속 결정 이후 반환은 실제 도착·농장 배치 경로에서 확인해야 합니다.");
    if (!List.of(
            AuctionLotStatus.REAUCTION_WAITING,
            AuctionLotStatus.RETURN_INFERRED,
            AuctionLotStatus.PARTIALLY_RETURNED)
        .contains(getCurrentStatus()))
      throw new IllegalArgumentException("재경매대기, 반환추정 또는 부분반환 상태에서만 반환을 확인할 수 있습니다.");
    if (getReturnConfirmableQuantity() <= 0) throw new IllegalArgumentException("확인할 반환 수량이 없습니다.");
  }

  public void confirmReturn(
      Integer quantity, LocalDate returnDate, String worker, String memo, LocalDateTime changedAt) {
    requireReturnConfirmable();
    if (quantity == null || quantity < 1) {
      throw new IllegalArgumentException("반환 확인 수량은 1 이상이어야 합니다.");
    }
    if (returnDate == null) {
      throw new IllegalArgumentException("반환 확인 날짜는 필수입니다.");
    }
    int confirmableQuantity = getReturnConfirmableQuantity();
    if (quantity > confirmableQuantity) {
      throw new IllegalArgumentException("반환 확인 수량이 확인 가능한 수량보다 많습니다.");
    }
    int previousSold = soldQuantity;
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    if (currentStatus == AuctionLotStatus.RETURN_INFERRED && returnedQuantity > 0) {
      int unconfirmedQuantity = returnedQuantity - quantity;
      returnedQuantity = quantity;
      waitingQuantity += unconfirmedQuantity;
    } else {
      returnedQuantity += quantity;
      waitingQuantity -= quantity;
    }
    returnConfirmedDate = returnDate;
    AuctionLotStatus next =
        waitingQuantity == 0 ? AuctionLotStatus.RETURNED : AuctionLotStatus.PARTIALLY_RETURNED;
    recordChange(
        next,
        next == AuctionLotStatus.RETURNED ? "반환 완료" : "부분반환 확인",
        worker,
        memo,
        changedAt,
        previousSold,
        previousWaiting,
        previousReturned);
  }

  public Integer getReturnConfirmableQuantity() {
    if (currentStatus == AuctionLotStatus.RETURN_INFERRED && returnedQuantity > 0) {
      return returnedQuantity;
    }
    return waitingQuantity;
  }

  public void adjustQuantities(
      Integer sold,
      Integer waiting,
      Integer returned,
      String worker,
      String memo,
      LocalDateTime changedAt) {
    if (sold == null
        || waiting == null
        || returned == null
        || sold < 0
        || waiting < 0
        || returned < 0) {
      throw new IllegalArgumentException("판매/대기/반환 수량은 0 이상의 값이 필요합니다.");
    }
    if ((long) sold + waiting + returned != shippedQuantity) {
      throw new IllegalArgumentException("판매/대기/반환 수량 합계가 출하 수량과 일치해야 합니다.");
    }
    if (sold.equals(soldQuantity)
        && waiting.equals(waitingQuantity)
        && returned.equals(returnedQuantity)) {
      return;
    }
    if (!isQuantityAdjustmentAllowed()) {
      throw new ConflictException(
          "AUCTION_QUANTITY_ADJUSTMENT_LOCKED", "경매 결과 또는 반환 확인 이력이 있는 lot의 수량은 직접 보정할 수 없습니다.");
    }
    int previousSold = soldQuantity;
    int previousWaiting = waitingQuantity;
    int previousReturned = returnedQuantity;
    soldQuantity = sold;
    waitingQuantity = waiting;
    returnedQuantity = returned;
    AuctionLotStatus next =
        waiting > 0
            ? (sold > 0 ? AuctionLotStatus.PARTIALLY_SOLD : AuctionLotStatus.REAUCTION_WAITING)
            : returned > 0 ? AuctionLotStatus.RETURNED : AuctionLotStatus.SOLD;
    recordChange(
        next, "수량 보정", worker, memo, changedAt, previousSold, previousWaiting, previousReturned);
  }

  public boolean isQuantityAdjustmentAllowed() {
    return isQuantityAdjustmentAllowed(!attempts.isEmpty());
  }

  public boolean isQuantityAdjustmentAllowed(boolean hasRecordedAttempts) {
    return !hasRecordedAttempts && returnConfirmedDate == null && followUpMethod == null;
  }

  public void changeStatus(
      AuctionLotStatus next, String reason, String worker, String memo, LocalDateTime changedAt) {
    if (next == AuctionLotStatus.DISPOSED
        && (followUpMethod != AuctionFollowUpMethod.AUCTION_DISPOSAL || disposedQuantity <= 0))
      throw new ConflictException("AUCTION_DISPOSAL_DECISION_REQUIRED", "경매장 처리 결정을 먼저 기록해야 합니다.");
    recordChange(
        next, reason, worker, memo, changedAt, soldQuantity, waitingQuantity, returnedQuantity);
  }

  private void recordChange(
      AuctionLotStatus next,
      String reason,
      String worker,
      String memo,
      LocalDateTime changedAt,
      int previousSold,
      int previousWaiting,
      int previousReturned) {
    if (currentStatus == next
        && previousSold == soldQuantity
        && previousWaiting == waitingQuantity
        && previousReturned == returnedQuantity) {
      return;
    }
    var history =
        new AuctionLotStatusHistory(
            this,
            currentStatus,
            next,
            reason,
            worker,
            memo,
            changedAt,
            previousSold,
            previousWaiting,
            previousReturned);
    statusHistory.add(history);
    currentStatus = next;
  }
}
