package com.greenhouse.backend.sales.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Identifies the actual auction result; quantities and amounts stay with that result. */
@Entity
@Table(name = "auction_proceeds_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionProceedsResult {
  @Id
  @Column(name = "auction_result_line_id")
  private Long resultLineId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "auction_proceeds_id", nullable = false)
  private AuctionProceeds proceeds;

  AuctionProceedsResult(AuctionProceeds proceeds, Long resultLineId) {
    this.proceeds = proceeds;
    this.resultLineId = resultLineId;
  }
}
