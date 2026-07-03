package com.greenhouse.backend.auction.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
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
import jakarta.persistence.Table;

@Entity
@Table(name = "import_rows")
public class ImportRow extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_batch_id", nullable = false) private ImportBatch importBatch;
	@Column(name = "row_number", nullable = false) private Integer rowNumber;
	@Column(name = "raw_data_json", nullable = false, columnDefinition = "text") private String rawDataJson;
	@Column(name = "normalized_data_json", columnDefinition = "text") private String normalizedDataJson;
	@Enumerated(EnumType.STRING) @Column(name = "validation_status", nullable = false) private AuctionInspectionStatus validationStatus;
	@Column(name = "matched_entity_type") private String matchedEntityType;
	@Column(name = "matched_entity_id") private Long matchedEntityId;
	@Column(name = "error_message", columnDefinition = "text") private String errorMessage;

	protected ImportRow() { }
	public ImportRow(Integer rowNumber, String rawDataJson) { this.rowNumber = rowNumber; this.rawDataJson = rawDataJson; this.validationStatus = AuctionInspectionStatus.NORMAL; }
	void setImportBatch(ImportBatch batch) { this.importBatch = batch; }
	public void normalized(String json) { normalizedDataJson = json; }
	public void matched(String type, Long id, AuctionInspectionStatus status) { matchedEntityType = type; matchedEntityId = id; validationStatus = status; errorMessage = null; }
	public void error(AuctionInspectionStatus status, String message) { validationStatus = status; errorMessage = message; }
	public Long getId() { return id; }
	public ImportBatch getImportBatch() { return importBatch; }
	public Integer getRowNumber() { return rowNumber; }
	public String getRawDataJson() { return rawDataJson; }
	public String getNormalizedDataJson() { return normalizedDataJson; }
	public AuctionInspectionStatus getValidationStatus() { return validationStatus; }
	public String getMatchedEntityType() { return matchedEntityType; }
	public Long getMatchedEntityId() { return matchedEntityId; }
	public String getErrorMessage() { return errorMessage; }
}
