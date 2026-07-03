package com.greenhouse.backend.auction.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "import_batches")
public class ImportBatch extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@Column(name = "file_name", nullable = false) private String fileName;
	@Column(name = "row_count", nullable = false) private Integer rowCount;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private ImportBatchStatus status;
	@Column(columnDefinition = "text") private String memo;
	@OneToMany(mappedBy = "importBatch", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ImportRow> rows = new ArrayList<>();

	protected ImportBatch() { }
	public ImportBatch(String fileName) { this.fileName = fileName; this.rowCount = 0; this.status = ImportBatchStatus.PROCESSING; }
	public void addRow(ImportRow row) { rows.add(row); row.setImportBatch(this); rowCount = rows.size(); }
	public void complete(boolean hasErrors) { status = hasErrors ? ImportBatchStatus.COMPLETED_WITH_ERRORS : ImportBatchStatus.COMPLETED; }
	public void fail(String memo) { status = ImportBatchStatus.FAILED; this.memo = memo; }
	public Long getId() { return id; }
	public String getFileName() { return fileName; }
	public Integer getRowCount() { return rowCount; }
	public ImportBatchStatus getStatus() { return status; }
	public String getMemo() { return memo; }
	public List<ImportRow> getRows() { return rows; }
}
