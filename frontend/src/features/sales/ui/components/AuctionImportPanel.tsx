import { useState } from "react";
import { FileUp, X } from "lucide-react";
import type {
  AuctionImportBatch,
  AuctionImportRow,
} from "@/entities/farm/types";
import { auctionInspectionLabel } from "../../lib/auctionDisplay";

export function AuctionImportPanel({
  batch,
  rows,
  loading,
  onClose,
  onImport,
}: {
  batch: AuctionImportBatch | null;
  rows: AuctionImportRow[];
  loading: boolean;
  onClose: () => void;
  onImport: (file: File) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const problemRows = rows.filter((row) =>
    [
      "MANUAL_REVIEW",
      "MATCH_FAILED",
      "QUANTITY_MISMATCH",
      "RETURN_INFERRED",
      "SOURCE_ERROR",
    ].includes(row.validationStatus),
  );

  return (
    <section className="rounded-md border border-[#bcd9c2] bg-[#f7fbf7] p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-bold">출하·경매 CSV 가져오기</h2>
          <p className="mt-1 text-xs text-[#627067]">
            원본 행을 보존하고 날짜·숫자를 정규화한 뒤 출하 lot과 경매 결과를
            자동 매칭합니다.
          </p>
        </div>
        <button type="button" title="닫기" onClick={onClose}>
          <X className="h-5 w-5" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input
          className="min-w-0 flex-1 rounded-md border border-[#d2dbd1] bg-white p-2 text-sm"
          type="file"
          accept=".csv,text/csv"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
        <button
          className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-bold text-white disabled:opacity-50"
          type="button"
          disabled={!file || loading}
          onClick={() => file && onImport(file)}
        >
          <FileUp className="h-4 w-4" />
          가져오기
        </button>
      </div>
      {batch ? (
        <div className="mt-3 rounded-md border border-[#dfe5dc] bg-white p-3 text-sm">
          <div className="flex flex-wrap gap-x-5 gap-y-1">
            <strong>{batch.fileName}</strong>
            <span>전체 {batch.rowCount.toLocaleString()}행</span>
            <span>확인 필요 {problemRows.length.toLocaleString()}행</span>
            <span>{batch.status}</span>
          </div>
          {problemRows.length > 0 ? (
            <div className="mt-2 max-h-28 overflow-auto border-t border-[#edf0ec] pt-2 text-xs">
              {problemRows.slice(0, 20).map((row) => (
                <p key={row.id} className="py-0.5">
                  <strong>{row.rowNumber}행</strong> ·{" "}
                  {auctionInspectionLabel(row.validationStatus)} ·{" "}
                  {row.errorMessage || "검수가 필요합니다."}
                </p>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
