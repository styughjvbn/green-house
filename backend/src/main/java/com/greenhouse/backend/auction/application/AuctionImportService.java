package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.domain.ImportBatch;
import com.greenhouse.backend.auction.domain.ImportRow;
import com.greenhouse.backend.auction.dto.ImportBatchResponse;
import com.greenhouse.backend.auction.dto.ImportRowResponse;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.auction.repository.ImportBatchRepository;
import com.greenhouse.backend.auction.repository.ImportRowRepository;
import com.greenhouse.backend.common.exception.NotFoundException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AuctionImportService {
	private final ImportBatchRepository batchRepository;
	private final ImportRowRepository rowRepository;
	private final AuctionShipmentRepository shipmentRepository;
	private final AuctionShipmentLotRepository lotRepository;
	private final ObjectMapper objectMapper;

	public AuctionImportService(ImportBatchRepository batchRepository, ImportRowRepository rowRepository, AuctionShipmentRepository shipmentRepository, AuctionShipmentLotRepository lotRepository, ObjectMapper objectMapper) {
		this.batchRepository = batchRepository; this.rowRepository = rowRepository; this.shipmentRepository = shipmentRepository; this.lotRepository = lotRepository; this.objectMapper = objectMapper;
	}

	public ImportBatchResponse importCsv(MultipartFile file) {
		if (file.isEmpty()) throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
		ImportBatch batch = new ImportBatch(Objects.requireNonNullElse(file.getOriginalFilename(), "auction.csv"));
		List<NormalizedImportRow> normalizedRows = new ArrayList<>();
		boolean hasErrors = false;
		try (var reader = new StringReader(decodeCsv(file.getBytes()))) {
			var format = CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.setAllowMissingColumnNames(true)
				.setIgnoreEmptyLines(true)
				.setTrim(true)
				.get();
			for (CSVRecord record : format.parse(reader)) {
				Map<String, String> raw = sanitize(record.toMap());
				ImportRow row = new ImportRow((int) record.getRecordNumber() + 1, json(raw));
				batch.addRow(row);
				try {
					AuctionImportData data = normalize(raw);
					row.normalized(json(data));
					normalizedRows.add(new NormalizedImportRow(row, data));
				} catch (IllegalArgumentException exception) {
					row.error(AuctionInspectionStatus.SOURCE_ERROR, exception.getMessage());
					hasErrors = true;
				}
			}
			batchRepository.saveAndFlush(batch);
			Map<String, AuctionShipment> shipments = new LinkedHashMap<>();
			for (NormalizedImportRow normalized : normalizedRows) {
				if (normalized.data().type() != RowType.SHIPMENT) continue;
				AuctionImportData data = normalized.data();
				String key = data.shipmentDate() + "|" + data.market();
				AuctionShipment shipment = shipments.computeIfAbsent(key, ignored -> new AuctionShipment(data.shipmentDate(), data.market(), batch));
				AuctionShipmentLot lot = new AuctionShipmentLot(data.itemName(), data.varietyName(), data.grade(), data.boxes(), data.quantity(), normalized.row());
				shipment.addLot(lot);
				shipmentRepository.save(shipment);
			}
			shipmentRepository.flush();
			List<AuctionShipmentLot> lots = lotRepository.findAllByOrderByIdDesc();
			for (NormalizedImportRow normalized : normalizedRows) {
				if (normalized.data().type() != RowType.AUCTION) continue;
				if (!matchAuctionRow(normalized, lots)) hasErrors = true;
			}
			batch.complete(hasErrors);
			return ImportBatchResponse.from(batch);
		} catch (Exception exception) {
			batch.fail(exception.getMessage());
			batchRepository.save(batch);
			if (exception instanceof IllegalArgumentException illegal) throw illegal;
			throw new IllegalArgumentException("CSV 파일을 처리하지 못했습니다: " + exception.getMessage(), exception);
		}
	}

	@Transactional(readOnly = true)
	public ImportBatchResponse getBatch(Long id) { return batchRepository.findById(id).map(ImportBatchResponse::from).orElseThrow(() -> new NotFoundException("가져오기 배치를 찾을 수 없습니다.")); }

	@Transactional(readOnly = true)
	public List<ImportRowResponse> getRows(Long id) {
		if (!batchRepository.existsById(id)) throw new NotFoundException("가져오기 배치를 찾을 수 없습니다.");
		return rowRepository.findByImportBatchIdOrderByRowNumberAsc(id).stream().map(ImportRowResponse::from).toList();
	}

	private boolean matchAuctionRow(NormalizedImportRow normalized, List<AuctionShipmentLot> lots) {
		AuctionImportData data = normalized.data();
		List<AuctionShipmentLot> base = lots.stream().filter(lot -> lot.getShipment().getShipmentDate().equals(data.shipmentDate()) && lot.getShipment().getAuctionMarket().equalsIgnoreCase(data.market()) && lot.getVarietyName().equalsIgnoreCase(data.varietyName())).toList();
		List<AuctionShipmentLot> exact = data.grade() == null ? List.of() : base.stream().filter(lot -> Objects.equals(lot.getShipmentGrade(), data.grade())).toList();
		AuctionShipmentLot lot;
		AuctionInspectionStatus inspection;
		if (exact.size() == 1) { lot = exact.getFirst(); inspection = AuctionInspectionStatus.AUTO_MATCHED; }
		else if (base.size() == 1) { lot = base.getFirst(); inspection = AuctionInspectionStatus.CORRECTED_MATCH; }
		else {
			normalized.row().error(base.isEmpty() ? AuctionInspectionStatus.MATCH_FAILED : AuctionInspectionStatus.MANUAL_REVIEW, base.isEmpty() ? "일치하는 출하 lot이 없습니다." : "복수 출하 lot 후보가 있어 수동 확인이 필요합니다.");
			return false;
		}
		boolean returnInferred = contains(data.note(), "반환");
		boolean failed = !returnInferred && (data.amount() == 0 || contains(data.note(), "유찰"));
		int sold = !failed && !returnInferred ? data.quantity() : 0;
		int returned = returnInferred ? data.quantity() : 0;
		AuctionAttempt attempt = lot.getAttempts().stream().filter(value -> value.getAuctionDate().equals(data.auctionDate())).findFirst().orElse(null);
		if (attempt == null) {
			attempt = new AuctionAttempt(data.auctionDate(), lot.getAttempts().size() + 1, attemptStatus(sold, failed, returnInferred), failed ? data.note() : null, null);
			lot.addAttempt(attempt);
		} else if (sold > 0 && attempt.getAttemptStatus() == AuctionAttemptStatus.FAILED) {
			attempt.updateStatus(AuctionAttemptStatus.PARTIALLY_SOLD);
		}
		attempt.addResultLine(new AuctionResultLine(data.auctionDate(), data.grade(), data.quantity(), data.unitPrice(), data.amount(), data.note(), returnInferred ? AuctionInspectionStatus.RETURN_INFERRED : inspection, normalized.row()));
		lot.applyResult(sold, returned, failed, returnInferred);
		normalized.row().matched("AUCTION_LOT", lot.getId(), returnInferred ? AuctionInspectionStatus.RETURN_INFERRED : inspection);
		return true;
	}

	private AuctionAttemptStatus attemptStatus(int sold, boolean failed, boolean returned) { if (returned) return AuctionAttemptStatus.RETURN_INFERRED; if (failed) return AuctionAttemptStatus.FAILED; return sold > 0 ? AuctionAttemptStatus.SOLD : AuctionAttemptStatus.FAILED; }

	private AuctionImportData normalize(Map<String, String> row) {
		String typeValue = value(row, "구분", "유형", "분류", "분류 (출하, 경매)", "type");
		LocalDate shipmentDate = parseDate(required(row, "출하일자", "출하일", "shipmentDate"));
		String market = required(row, "경매장", "auctionMarket", "market");
		String variety = required(row, "품종명", "품종", "varietyName");
		String item = value(row, "품목명", "품목", "itemName");
		LocalDate auctionDate = parseOptionalDate(value(row, "경매일자", "경매일", "일자", "auctionDate"));
		RowType type = contains(typeValue, "출하")
			? RowType.SHIPMENT
			: contains(typeValue, "경매") || auctionDate != null ? RowType.AUCTION : RowType.SHIPMENT;
		if (type == RowType.AUCTION && auctionDate == null) throw new IllegalArgumentException("경매 행의 경매일자가 없습니다.");
		int quantity = parseInt(required(row, "분수량", "수량", "quantity"));
		int unitPrice = parseOptionalInt(value(row, "단가", "unitPrice"));
		int amount = parseOptionalInt(value(row, "금액", "amount"));
		if (amount == 0 && unitPrice > 0) amount = quantity * unitPrice;
		return new AuctionImportData(type, shipmentDate, auctionDate, market, item.isBlank() ? variety : item, variety, nullable(value(row, "등급", "출하등급", "경매등급", "grade")), parseOptionalInt(value(row, "상자", "상자수", "boxes")), quantity, unitPrice, amount, nullable(value(row, "비고", "메모", "note")));
	}

	private Map<String, String> sanitize(Map<String, String> values) {
		Map<String, String> result = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			String normalizedKey = key.replace("\ufeff", "").trim();
			if (!normalizedKey.isBlank()) result.put(normalizedKey, value == null ? "" : value.trim());
		});
		return result;
	}
	private String decodeCsv(byte[] bytes) {
		try {
			return decode(bytes, StandardCharsets.UTF_8);
		} catch (CharacterCodingException ignored) {
			try {
				return decode(bytes, Charset.forName("MS949"));
			} catch (CharacterCodingException exception) {
				throw new IllegalArgumentException("CSV 문자 인코딩을 확인해주세요. UTF-8 또는 CP949 파일만 지원합니다.");
			}
		}
	}
	private String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
		return charset.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString();
	}
	private String value(Map<String, String> row, String... aliases) { for (String alias : aliases) { for (var entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(alias)) return entry.getValue().trim(); } return ""; }
	private String required(Map<String, String> row, String... aliases) { String result = value(row, aliases); if (result.isBlank()) throw new IllegalArgumentException(aliases[0] + " 값이 없습니다."); return result; }
	private LocalDate parseDate(String value) { String normalized = value.trim().replaceAll("\\s", "").replace('.', '-').replace('/', '-'); for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy-M-d", Locale.KOREA))) { try { return LocalDate.parse(normalized, formatter); } catch (DateTimeParseException ignored) { } } throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다: " + value); }
	private LocalDate parseOptionalDate(String value) { return value.isBlank() ? null : parseDate(value); }
	private int parseInt(String value) { try { return Integer.parseInt(value.replace(",", "").trim()); } catch (NumberFormatException exception) { throw new IllegalArgumentException("숫자 형식이 올바르지 않습니다: " + value); } }
	private int parseOptionalInt(String value) { return value.isBlank() ? 0 : parseInt(value); }
	private boolean contains(String value, String token) { return value != null && value.contains(token); }
	private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalArgumentException("원본 행을 JSON으로 변환하지 못했습니다.", exception); } }

	private enum RowType { SHIPMENT, AUCTION }
	private record NormalizedImportRow(ImportRow row, AuctionImportData data) { }
	private record AuctionImportData(RowType type, LocalDate shipmentDate, LocalDate auctionDate, String market, String itemName, String varietyName, String grade, Integer boxes, Integer quantity, Integer unitPrice, Integer amount, String note) { }
}
