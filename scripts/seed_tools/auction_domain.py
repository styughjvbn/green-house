#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate PostgreSQL seed SQL for auction shipment/result tracking from the farm CSV.

Usage:
  python generate_auction_seed_sql.py \
    --input "2026 내수 출하 완 - 추출용.csv" \
    --output V20__seed_auction_2025_2026.sql \
    --review review_required.csv \
    --truncate

Assumptions:
- CSV has columns: 분류 (출하, 경매), 일자, 출하일자, 유찰횟수, 품목명, 품종명, 등급, 상자, 분수량, 단가, 금액, 비고, 경매장, 검수
- Shipment rows use `일자` as shipment date.
- Auction rows use `일자` as auction date and `출하일자` as original shipment date.
- The generated SQL is intended for initial seed/migration use on PostgreSQL.
- If your entity column names differ, edit the TABLE_* or generated INSERT blocks below.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Optional

# -----------------------------------------------------------------------------
# Table names
# -----------------------------------------------------------------------------
TABLE_SHIPMENTS = "auction_shipments"
TABLE_LOTS = "auction_shipment_lots"
TABLE_ATTEMPTS = "auction_attempts"
TABLE_RESULT_LINES = "auction_result_lines"
TABLE_STATUS_HISTORY = "auction_lot_status_history"

# If your import_rows table has unknown/different columns, keep this False.
# auction_shipment_lots.source_row_id and auction_result_lines.source_row_id will be NULL.
INCLUDE_IMPORT_ROWS = False
TABLE_IMPORT_ROWS = "import_rows"

# -----------------------------------------------------------------------------
# Status values. Adjust these strings if your Java enum names differ.
# -----------------------------------------------------------------------------
LOT_STATUS_WAITING = "WAITING"
LOT_STATUS_SOLD = "SOLD"
LOT_STATUS_PARTIALLY_SOLD = "PARTIALLY_SOLD"
LOT_STATUS_REAUCTION_WAITING = "REAUCTION_WAITING"
LOT_STATUS_RETURN_INFERRED = "RETURN_INFERRED"
LOT_STATUS_RETURNED = "RETURNED"
LOT_STATUS_QUANTITY_MISMATCH = "QUANTITY_MISMATCH"
LOT_STATUS_NEEDS_REVIEW = "NEEDS_REVIEW"

ATTEMPT_STATUS_SOLD = "SOLD"
ATTEMPT_STATUS_FAILED = "FAILED"
ATTEMPT_STATUS_PARTIALLY_SOLD = "PARTIALLY_SOLD"
ATTEMPT_STATUS_RETURN_INFERRED = "RETURN_INFERRED"

INSPECTION_AUTO_MATCHED = "AUTO_MATCHED"
INSPECTION_CORRECTED_MATCH = "CORRECTED_MATCH"
INSPECTION_MANUAL_REVIEW = "MANUAL_REVIEW"
INSPECTION_MATCH_FAILED = "MATCH_FAILED"
INSPECTION_RETURN_INFERRED = "RETURN_INFERRED"

# -----------------------------------------------------------------------------
# Data classes
# -----------------------------------------------------------------------------
@dataclass
class SourceRow:
    row_number: int
    raw: dict[str, str]

@dataclass
class NormalizedRow:
    source: SourceRow
    row_type: str  # SHIPMENT / AUCTION
    shipment_date: date
    auction_date: Optional[date]
    market: str
    item_name: str
    variety_name: str
    grade: Optional[str]
    boxes: Optional[int]
    quantity: int
    unit_price: int
    amount: int
    note: Optional[str]
    inspection: Optional[str]
    failed_history: Optional[str]
    is_synthetic: bool = False

@dataclass
class Shipment:
    id: int
    shipment_date: date
    market: str
    status: str = LOT_STATUS_WAITING
    memo: Optional[str] = None
    lots: list["Lot"] = field(default_factory=list)

@dataclass
class Lot:
    id: int
    shipment: Shipment
    item_name: str
    variety_name: str
    grade: Optional[str]
    boxes: Optional[int]
    shipped_quantity: int
    source_row: SourceRow
    sold_quantity: int = 0
    returned_quantity: int = 0
    waiting_quantity: int = 0
    current_status: str = LOT_STATUS_WAITING
    memo: Optional[str] = None
    attempts: dict[date, "Attempt"] = field(default_factory=dict)
    match_reviews: list[str] = field(default_factory=list)

    @property
    def remaining_quantity(self) -> int:
        # Failed quantity is still considered waiting. Sold/returned reduce the open quantity.
        return max(0, self.shipped_quantity - self.sold_quantity - self.returned_quantity)

@dataclass
class Attempt:
    id: int
    lot: Lot
    auction_date: date
    attempt_no: int
    status: str = ATTEMPT_STATUS_FAILED
    failed_reason: Optional[str] = None
    memo: Optional[str] = None
    result_lines: list["ResultLine"] = field(default_factory=list)

@dataclass
class ResultLine:
    id: int
    attempt: Attempt
    row: NormalizedRow
    inspection_status: str

@dataclass
class StatusHistory:
    id: int
    lot: Lot
    changed_at: datetime
    reason: str
    new_status: str
    previous_status: str
    memo: Optional[str] = None
    worker: Optional[str] = None

@dataclass
class ReviewIssue:
    row_number: int
    issue_type: str
    message: str
    raw: dict[str, str]
    candidate_lot_ids: list[int] = field(default_factory=list)

# -----------------------------------------------------------------------------
# CSV normalization helpers
# -----------------------------------------------------------------------------
def clean_text(value: object) -> str:
    if value is None:
        return ""
    text = str(value).replace("\ufeff", "").replace("\u00a0", " ").strip()
    return re.sub(r"\s+", " ", text)

def nullable_text(value: object) -> Optional[str]:
    text = clean_text(value)
    return text if text else None

def normalize_market(value: object) -> str:
    text = clean_text(value)
    aliases = {
        "음성공판장": "음성",
        "양재공판장": "양재",
        "고양공판장": "고양",
        "부산공판장": "부산",
    }
    return aliases.get(text, text)

def normalize_grade(value: object) -> Optional[str]:
    # Keep grades such as 2-3, 4-, 특, 대, 소 as pure text.
    return nullable_text(value)

def is_springfaru(value: object) -> bool:
    return clean_text(value).casefold() == "스프링파루".casefold()

def springfaru_size(value: object) -> Optional[str]:
    """Return the Springfaru size marker from note text.

    스프링파루는 같은 출하일/경매장/품종/등급 안에서도 비고의 대/소가
    서로 다른 lot을 의미한다. 이 값은 일반 등급이 아니라 비고 구분값이므로
    스프링파루 매칭에서만 최우선 비교 기준으로 사용한다.
    """
    text = clean_text(value)
    if not text:
        return None
    has_large = "대" in text
    has_small = "소" in text
    if has_large and not has_small:
        return "대"
    if has_small and not has_large:
        return "소"
    return None

def parse_date(value: object) -> date:
    text = clean_text(value).replace(".", "-").replace("/", "-")
    text = re.sub(r"\s+", "", text)
    if not text:
        raise ValueError("empty date")
    for fmt in ("%Y-%m-%d", "%Y-%m-%d", "%Y-%m-%d"):
        try:
            # Python's strptime accepts non-zero-padded month/day for %m/%d.
            return datetime.strptime(text, fmt).date()
        except ValueError:
            pass
    raise ValueError(f"invalid date: {value!r}")

def parse_optional_int(value: object) -> Optional[int]:
    text = clean_text(value)
    if not text:
        return None
    text = text.replace(",", "")
    # OCR/CSV cleanup can sometimes leave .0.
    if re.fullmatch(r"-?\d+\.0", text):
        text = text[:-2]
    if not re.fullmatch(r"-?\d+", text):
        raise ValueError(f"invalid integer: {value!r}")
    return int(text)

def parse_required_int(value: object) -> int:
    parsed = parse_optional_int(value)
    if parsed is None:
        raise ValueError("empty integer")
    return parsed

def contains(value: Optional[str], token: str) -> bool:
    return bool(value and token in value)

def row_value(row: dict[str, str], *aliases: str) -> str:
    for alias in aliases:
        for key, value in row.items():
            if clean_text(key).lower() == alias.lower():
                return clean_text(value)
    return ""

def read_csv_rows(path: Path) -> list[SourceRow]:
    last_error: Optional[Exception] = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            with path.open("r", encoding=encoding, newline="") as f:
                reader = csv.DictReader(f)
                rows: list[SourceRow] = []
                for index, raw in enumerate(reader, start=2):
                    sanitized = {clean_text(k): clean_text(v) for k, v in raw.items() if clean_text(k)}
                    if any(v for v in sanitized.values()):
                        rows.append(SourceRow(index, sanitized))
                return rows
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"CSV encoding must be UTF-8 or CP949: {last_error}")

def normalize_row(source: SourceRow) -> NormalizedRow:
    raw = source.raw
    type_text = row_value(raw, "분류 (출하, 경매)", "분류", "구분", "type")
    row_type = "AUCTION" if "경매" in type_text else "SHIPMENT"

    if row_type == "SHIPMENT":
        shipment_date = parse_date(row_value(raw, "일자", "출하일자", "출하일"))
        auction_date = None
    else:
        auction_date = parse_date(row_value(raw, "일자", "경매일자", "경매일"))
        shipment_date = parse_date(row_value(raw, "출하일자", "출하일"))

    market = normalize_market(row_value(raw, "경매장", "auctionMarket", "market"))
    item_name = clean_text(row_value(raw, "품목명", "품목", "itemName"))
    variety_name = clean_text(row_value(raw, "품종명", "품종", "varietyName"))
    if not item_name:
        item_name = variety_name
    if not market:
        raise ValueError("경매장 값이 없습니다.")
    if not variety_name:
        raise ValueError("품종명 값이 없습니다.")

    boxes = parse_optional_int(row_value(raw, "상자", "상자수", "boxes"))
    quantity = parse_required_int(row_value(raw, "분수량", "수량", "quantity"))
    unit_price = parse_optional_int(row_value(raw, "단가", "unitPrice")) or 0
    amount = parse_optional_int(row_value(raw, "금액", "amount")) or 0
    if amount == 0 and unit_price > 0:
        amount = quantity * unit_price

    return NormalizedRow(
        source=source,
        row_type=row_type,
        shipment_date=shipment_date,
        auction_date=auction_date,
        market=market,
        item_name=item_name,
        variety_name=variety_name,
        grade=normalize_grade(row_value(raw, "등급", "출하등급", "경매등급", "grade")),
        boxes=boxes,
        quantity=quantity,
        unit_price=unit_price,
        amount=amount,
        note=nullable_text(row_value(raw, "비고", "메모", "note")),
        inspection=nullable_text(row_value(raw, "검수", "inspection")),
        failed_history=nullable_text(row_value(raw, "유찰횟수", "failedHistory")),
    )

# -----------------------------------------------------------------------------
# Matching and aggregate logic
# -----------------------------------------------------------------------------
def same_text(a: Optional[str], b: Optional[str]) -> bool:
    return clean_text(a).casefold() == clean_text(b).casefold()

def same_nullable_text(a: Optional[str], b: Optional[str]) -> bool:
    return nullable_text(a) == nullable_text(b)

def match_lot(row: NormalizedRow, lots: list[Lot]) -> tuple[Optional[Lot], str, list[Lot], str]:
    assert row.row_type == "AUCTION"

    def extract_uk(value: object) -> Optional[str]:
        text = clean_text(value)
        match = re.search(r"\buk\s*=\s*(\d+)\b", text, flags=re.IGNORECASE)
        return match.group(1) if match else None

    def lot_uk(lot: Lot) -> Optional[str]:
        # 출하 행의 검수 컬럼에 적힌 uk를 사용한다.
        return extract_uk(row_value(lot.source_row.raw, "검수", "inspection"))

    row_uk = extract_uk(row.inspection)

    # 0) uk 강제 매칭
    # 검수 컬럼에 uk=<숫자>가 있으면 같은 uk를 가진 출하 lot을 최우선으로 사용한다.
    if row_uk:
        uk_candidates = [lot for lot in lots if lot_uk(lot) == row_uk]

        if not uk_candidates:
            return (
                None,
                INSPECTION_MATCH_FAILED,
                [],
                f"uk={row_uk}와 일치하는 출하 lot이 없습니다",
            )

        selected = pick_best_lot(uk_candidates, row)

        if selected is None:
            return (
                None,
                INSPECTION_MANUAL_REVIEW,
                uk_candidates,
                f"uk={row_uk} 후보가 있으나 수량 기준으로 선택하지 못했습니다",
            )

        status = INSPECTION_AUTO_MATCHED if len(uk_candidates) == 1 else INSPECTION_CORRECTED_MATCH
        return selected, status, uk_candidates, f"uk={row_uk} matched"

    # 1) 기본 후보: 출하일자 + 경매장 + 품종명
    candidates = [
        lot for lot in lots
        if lot.shipment.shipment_date == row.shipment_date
        and same_text(lot.shipment.market, row.market)
        and same_text(lot.variety_name, row.variety_name)
    ]

    if not candidates:
        return None, INSPECTION_MATCH_FAILED, [], "same shipment_date + market + variety candidate not found"

    # 2) 스프링파루는 비고의 대/소를 최우선으로 비교한다.
    if is_springfaru(row.variety_name):
        row_size = springfaru_size(row.note)
        candidate_sizes = {
            springfaru_size(lot.memo)
            for lot in candidates
            if springfaru_size(lot.memo)
        }

        if row_size:
            size_matched = [
                lot for lot in candidates
                if springfaru_size(lot.memo) == row_size
            ]

            if not size_matched:
                return (
                    None,
                    INSPECTION_MANUAL_REVIEW,
                    candidates,
                    f"스프링파루 비고({row_size})와 일치하는 출하 lot이 없습니다",
                )

            candidates = size_matched

        elif len(candidate_sizes) > 1:
            return (
                None,
                INSPECTION_MANUAL_REVIEW,
                candidates,
                "스프링파루 경매 행에 비고 대/소가 없어 후보를 확정할 수 없습니다",
            )

    # 3) 품목명 + 등급 일치
    item_grade = [
        lot for lot in candidates
        if same_text(lot.item_name, row.item_name)
        and same_nullable_text(lot.grade, row.grade)
    ]

    selected = pick_best_lot(item_grade, row)
    if selected:
        status = INSPECTION_AUTO_MATCHED if len(item_grade) == 1 else INSPECTION_CORRECTED_MATCH
        return selected, status, item_grade, "item+grade matched"

    # 4) 등급 일치
    grade_only = [
        lot for lot in candidates
        if same_nullable_text(lot.grade, row.grade)
    ]

    selected = pick_best_lot(grade_only, row)
    if selected:
        status = INSPECTION_AUTO_MATCHED if len(grade_only) == 1 else INSPECTION_CORRECTED_MATCH
        return selected, status, grade_only, "grade matched"

    # 5) 품목명 일치
    item_only = [
        lot for lot in candidates
        if same_text(lot.item_name, row.item_name)
    ]

    selected = pick_best_lot(item_only, row)
    if selected:
        return selected, INSPECTION_CORRECTED_MATCH, item_only, "item matched, grade corrected/ignored"

    # 6) 출하일자 + 경매장 + 품종명만 일치
    selected = pick_best_lot(candidates, row)
    if selected:
        return selected, INSPECTION_MANUAL_REVIEW, candidates, "variety matched only; check manually"

    return None, INSPECTION_MANUAL_REVIEW, candidates, "multiple candidates but no safe quantity candidate"

def pick_best_lot(candidates: list[Lot], row: NormalizedRow) -> Optional[Lot]:
    if not candidates:
        return None

    # Prefer candidates that still have enough open quantity.
    enough = [lot for lot in candidates if lot.remaining_quantity >= row.quantity]
    pool = enough or candidates

    if len(pool) == 1:
        return pool[0]

    # If several lots are identical, greedily take the earliest lot with largest remaining quantity.
    # This is deterministic, but review_required.csv will still record ambiguity when candidates > 1.
    pool = sorted(pool, key=lambda lot: (-lot.remaining_quantity, lot.id))
    return pool[0]

def classify_result(row: NormalizedRow) -> tuple[int, int, bool, bool, str]:
    return_inferred = contains(row.inspection, "반환") or contains(row.note, "반환") or contains(row.note, "반송")
    failed = (not return_inferred) and (row.amount == 0 or contains(row.note, "유찰"))
    sold_quantity = 0 if failed or return_inferred else row.quantity
    returned_quantity = row.quantity if return_inferred else 0
    inspection_status = INSPECTION_RETURN_INFERRED if return_inferred else ""
    return sold_quantity, returned_quantity, failed, return_inferred, inspection_status

def recalc_attempt_status(attempt: Attempt) -> str:
    has_return = any(line.inspection_status == INSPECTION_RETURN_INFERRED for line in attempt.result_lines)
    has_sold = any(line.row.amount > 0 and line.inspection_status != INSPECTION_RETURN_INFERRED for line in attempt.result_lines)
    has_failed = any((line.row.amount == 0 or contains(line.row.note, "유찰")) and line.inspection_status != INSPECTION_RETURN_INFERRED for line in attempt.result_lines)
    if has_return:
        return ATTEMPT_STATUS_RETURN_INFERRED
    if has_sold and has_failed:
        return ATTEMPT_STATUS_PARTIALLY_SOLD
    if has_sold:
        return ATTEMPT_STATUS_SOLD
    return ATTEMPT_STATUS_FAILED

def recalc_lot_status(lot: Lot) -> str:
    lot.waiting_quantity = max(0, lot.shipped_quantity - lot.sold_quantity - lot.returned_quantity)
    if lot.sold_quantity + lot.returned_quantity > lot.shipped_quantity:
        return LOT_STATUS_QUANTITY_MISMATCH
    if lot.returned_quantity > 0:
        return LOT_STATUS_RETURNED
    if lot.waiting_quantity == 0 and lot.sold_quantity >= lot.shipped_quantity:
        return LOT_STATUS_SOLD
    if lot.sold_quantity > 0 and lot.waiting_quantity > 0:
        return LOT_STATUS_PARTIALLY_SOLD
    if any(attempt.status == ATTEMPT_STATUS_FAILED for attempt in lot.attempts.values()):
        return LOT_STATUS_REAUCTION_WAITING
    return LOT_STATUS_WAITING

def next_market_shipment_date(market_dates: dict[str, list[date]], market: str, current_date: date) -> Optional[date]:
    """Return the next shipment date for the same market after current_date.

    반환은 경매가 끝난 뒤 경매장에 남아 있던 물건을 다음 출하 차량으로
    농장에 가져온 것으로 본다. 따라서 반환 일자는 다음 경매일이 아니라
    같은 경매장의 다음 출하일을 사용한다.
    """
    for candidate in market_dates.get(market, []):
        if candidate > current_date:
            return candidate
    return None

def create_synthetic_return_row(row: NormalizedRow, auction_date: date, quantity: int) -> NormalizedRow:
    """Create a synthetic result row that represents an inferred farm return on the next shipment date."""
    raw = dict(row.source.raw)
    raw["분류"] = "경매"
    raw["일자"] = auction_date.isoformat()
    raw["비고"] = "반환"
    raw["검수"] = "반환추정"
    raw["분수량"] = str(quantity)
    raw["단가"] = "0"
    raw["금액"] = "0"
    raw["synthetic"] = "true"
    raw["synthetic_reason"] = "RETURN_INFERRED_NEXT_MARKET_SHIPMENT_DATE"

    return NormalizedRow(
        source=SourceRow(row.source.row_number, raw),
        row_type="AUCTION",
        shipment_date=row.shipment_date,
        auction_date=auction_date,
        market=row.market,
        item_name=row.item_name,
        variety_name=row.variety_name,
        grade=row.grade,
        boxes=row.boxes,
        quantity=quantity,
        unit_price=0,
        amount=0,
        note="반환",
        inspection="반환추정",
        failed_history=row.failed_history,
        is_synthetic=True,
    )


def replace_return_inferred_row_as_failed(row: NormalizedRow) -> NormalizedRow:
    """Convert the original return-inferred auction row into a failed auction line.

    The original auction date represents the date the lot failed to sell and was
    marked for return. The actual farm return is represented by a separate
    synthetic RETURN_INFERRED line on the return date.
    """
    raw = dict(row.source.raw)
    original_note = nullable_text(row.note)
    if original_note and "유찰" not in original_note:
        failed_note = f"{original_note} / 유찰"
    else:
        failed_note = original_note or "유찰"

    raw["비고"] = failed_note
    raw["검수"] = ""
    raw["단가"] = "0"
    raw["금액"] = "0"
    raw["return_inferred_original"] = "true"

    return NormalizedRow(
        source=SourceRow(row.source.row_number, raw),
        row_type=row.row_type,
        shipment_date=row.shipment_date,
        auction_date=row.auction_date,
        market=row.market,
        item_name=row.item_name,
        variety_name=row.variety_name,
        grade=row.grade,
        boxes=row.boxes,
        quantity=row.quantity,
        unit_price=0,
        amount=0,
        note=failed_note,
        inspection=None,
        failed_history=row.failed_history,
        is_synthetic=row.is_synthetic,
    )

def return_confirmed_at(return_date: date) -> datetime:
    """Return confirmation history is recorded at 8 PM on the return date."""
    return datetime.combine(return_date, time(hour=20, minute=0))

def get_or_create_attempt(
    lot: Lot,
    auction_date: date,
    attempts: list[Attempt],
    next_attempt_id: int,
    failed_reason: Optional[str] = None,
) -> tuple[Attempt, int]:
    attempt = lot.attempts.get(auction_date)
    if attempt is not None:
        return attempt, next_attempt_id

    # The final attempt_no is recalculated after all normal/synthetic rows are attached.
    attempt = Attempt(
        id=next_attempt_id,
        lot=lot,
        auction_date=auction_date,
        attempt_no=0,
        failed_reason=failed_reason,
    )
    next_attempt_id += 1
    lot.attempts[auction_date] = attempt
    attempts.append(attempt)
    return attempt, next_attempt_id

def add_result_line(
    lot: Lot,
    row: NormalizedRow,
    inspection_status: str,
    attempts: list[Attempt],
    result_lines: list[ResultLine],
    next_attempt_id: int,
    next_result_line_id: int,
    failed: bool = False,
) -> tuple[int, int, ResultLine]:
    if row.auction_date is None:
        raise ValueError("auction row must have auction_date")

    attempt, next_attempt_id = get_or_create_attempt(
        lot=lot,
        auction_date=row.auction_date,
        attempts=attempts,
        next_attempt_id=next_attempt_id,
        failed_reason=row.note if failed else None,
    )

    line = ResultLine(
        id=next_result_line_id,
        attempt=attempt,
        row=row,
        inspection_status=inspection_status,
    )
    next_result_line_id += 1
    attempt.result_lines.append(line)
    result_lines.append(line)
    attempt.status = recalc_attempt_status(attempt)
    return next_attempt_id, next_result_line_id, line

def renumber_attempts(lots: list[Lot]) -> None:
    """Ensure attempt_no follows chronological auction dates after synthetic rows are added."""
    for lot in lots:
        for index, auction_date in enumerate(sorted(lot.attempts), start=1):
            lot.attempts[auction_date].attempt_no = index

def build_domain(rows: list[NormalizedRow]) -> tuple[list[Shipment], list[Lot], list[Attempt], list[ResultLine], list[StatusHistory], list[ReviewIssue]]:
    shipments_by_key: dict[tuple[date, str], Shipment] = {}
    lots: list[Lot] = []
    attempts: list[Attempt] = []
    result_lines: list[ResultLine] = []
    status_histories: list[StatusHistory] = []
    reviews: list[ReviewIssue] = []
    pending_return_rows: list[tuple[Lot, NormalizedRow, str, list[Lot], str]] = []

    market_shipment_dates: dict[str, list[date]] = defaultdict(list)
    for row in rows:
        if row.row_type == "SHIPMENT":
            market_shipment_dates[row.market].append(row.shipment_date)
    market_shipment_dates = {
        market: sorted(set(dates))
        for market, dates in market_shipment_dates.items()
    }

    next_shipment_id = 1
    next_lot_id = 1
    next_attempt_id = 1
    next_result_line_id = 1
    next_status_history_id = 1

    # Create shipments/lots from shipment rows only.
    for row in rows:
        if row.row_type != "SHIPMENT":
            continue
        key = (row.shipment_date, row.market)
        shipment = shipments_by_key.get(key)
        if shipment is None:
            shipment = Shipment(id=next_shipment_id, shipment_date=row.shipment_date, market=row.market)
            next_shipment_id += 1
            shipments_by_key[key] = shipment

        lot = Lot(
            id=next_lot_id,
            shipment=shipment,
            item_name=row.item_name,
            variety_name=row.variety_name,
            grade=row.grade,
            boxes=row.boxes,
            shipped_quantity=row.quantity,
            source_row=row.source,
            waiting_quantity=row.quantity,
            memo=row.note if is_springfaru(row.variety_name) else None,
        )
        next_lot_id += 1
        shipment.lots.append(lot)
        lots.append(lot)

    # Match auction rows to existing lots.
    for row in rows:
        if row.row_type != "AUCTION":
            continue

        lot, match_status, candidates, reason = match_lot(row, lots)
        if lot is None:
            reviews.append(ReviewIssue(
                row_number=row.source.row_number,
                issue_type=match_status,
                message=reason,
                raw=row.source.raw,
                candidate_lot_ids=[c.id for c in candidates],
            ))
            continue

        if len(candidates) > 1 or match_status in {INSPECTION_MANUAL_REVIEW, INSPECTION_CORRECTED_MATCH}:
            reviews.append(ReviewIssue(
                row_number=row.source.row_number,
                issue_type=match_status,
                message=reason,
                raw=row.source.raw,
                candidate_lot_ids=[c.id for c in candidates],
            ))

        sold_qty, returned_qty, failed, returned, result_inspection = classify_result(row)

        if returned:
            # 반환추정 원본 경매 행은 실제 반환일이 아니라 "팔리지 않고 반환 대상으로 잡힌 날"이다.
            # 따라서 원본 경매일에는 유찰/대기 auction_result_lines를 남기고,
            # 실제 반환은 다음 출하일 또는 fallback 반환일에 synthetic "반환" line으로 따로 추가한다.
            failed_row = replace_return_inferred_row_as_failed(row)
            next_attempt_id, next_result_line_id, _ = add_result_line(
                lot=lot,
                row=failed_row,
                inspection_status=match_status,
                attempts=attempts,
                result_lines=result_lines,
                next_attempt_id=next_attempt_id,
                next_result_line_id=next_result_line_id,
                failed=True,
            )
            lot.current_status = recalc_lot_status(lot)
            pending_return_rows.append((lot, row, match_status, candidates, reason))
            continue

        inspection_status = result_inspection or match_status
        next_attempt_id, next_result_line_id, _ = add_result_line(
            lot=lot,
            row=row,
            inspection_status=inspection_status,
            attempts=attempts,
            result_lines=result_lines,
            next_attempt_id=next_attempt_id,
            next_result_line_id=next_result_line_id,
            failed=failed,
        )

        lot.sold_quantity += sold_qty
        lot.returned_quantity += returned_qty
        lot.current_status = recalc_lot_status(lot)

    # Add synthetic return result lines at the next shipment date of the same market.
    for lot, original_row, match_status, candidates, reason in pending_return_rows:
        assert original_row.auction_date is not None
        return_date = next_market_shipment_date(market_shipment_dates, original_row.market, original_row.auction_date)

        if return_date is None:
            # No later shipment date exists in the seed source. In this case, infer
            # the return date as one week after the return-inferred auction date.
            return_date = original_row.auction_date + timedelta(days=7)
            reviews.append(ReviewIssue(
                row_number=original_row.source.row_number,
                issue_type="RETURN_DATE_FALLBACK_PLUS_7_DAYS",
                message="해당 경매장의 다음 출하일을 찾지 못해 반환추정 경매일로부터 7일 후를 반환일로 생성했습니다.",
                raw=original_row.source.raw,
                candidate_lot_ids=[lot.id],
            ))

        synthetic_row = create_synthetic_return_row(
            row=original_row,
            auction_date=return_date,
            quantity=original_row.quantity,
        )

        next_attempt_id, next_result_line_id, _ = add_result_line(
            lot=lot,
            row=synthetic_row,
            inspection_status=INSPECTION_RETURN_INFERRED,
            attempts=attempts,
            result_lines=result_lines,
            next_attempt_id=next_attempt_id,
            next_result_line_id=next_result_line_id,
            failed=False,
        )

        lot.returned_quantity += synthetic_row.quantity
        lot.current_status = recalc_lot_status(lot)

        status_histories.append(StatusHistory(
            id=next_status_history_id,
            lot=lot,
            changed_at=return_confirmed_at(return_date),
            reason="판매 관리 화면에서 반환 확인",
            new_status=LOT_STATUS_RETURNED,
            previous_status=LOT_STATUS_RETURN_INFERRED,
            memo="반환 완료",
            worker=None,
        ))
        next_status_history_id += 1

        reviews.append(ReviewIssue(
            row_number=original_row.source.row_number,
            issue_type=INSPECTION_RETURN_INFERRED,
            message=f"반환추정 행의 반환 수량을 {return_date.isoformat()} {original_row.market} 다음 출하일의 synthetic 반환 result_line으로 생성했습니다.",
            raw=synthetic_row.source.raw,
            candidate_lot_ids=[lot.id],
        ))

    # Final aggregate status and chronological attempt numbers.
    for lot in lots:
        lot.current_status = recalc_lot_status(lot)
        if lot.current_status == LOT_STATUS_QUANTITY_MISMATCH:
            reviews.append(ReviewIssue(
                row_number=lot.source_row.row_number,
                issue_type=LOT_STATUS_QUANTITY_MISMATCH,
                message=f"sold({lot.sold_quantity}) + returned({lot.returned_quantity}) exceeds shipped({lot.shipped_quantity})",
                raw=lot.source_row.raw,
                candidate_lot_ids=[lot.id],
            ))

    renumber_attempts(lots)
    attempts.sort(key=lambda attempt: (attempt.lot.id, attempt.auction_date, attempt.id))
    result_lines.sort(key=lambda line: (line.attempt.id, line.id))

    return list(shipments_by_key.values()), lots, attempts, result_lines, status_histories, reviews

# -----------------------------------------------------------------------------
# SQL generation
# -----------------------------------------------------------------------------
def sql_literal(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, datetime):
        return f"'{value.strftime('%Y-%m-%d %H:%M:%S')}'"
    if isinstance(value, date):
        return f"'{value.isoformat()}'"
    text = str(value)
    return "'" + text.replace("'", "''") + "'"

def json_literal(value: object) -> str:
    return sql_literal(json.dumps(value, ensure_ascii=False, separators=(",", ":")))

def values_rows(rows: list[tuple], indent: str = "  ") -> str:
    return ",\n".join(indent + "(" + ", ".join(sql_literal(v) for v in row) + ")" for row in rows)

def generate_sql(
    input_file: Path,
    normalized_rows: list[NormalizedRow],
    shipments: list[Shipment],
    lots: list[Lot],
    attempts: list[Attempt],
    result_lines: list[ResultLine],
    status_histories: list[StatusHistory],
    include_truncate: bool,
) -> str:
    now_expr = "CURRENT_TIMESTAMP"
    lines: list[str] = []
    lines.append("-- Generated by generate_auction_seed_sql.py")
    lines.append(f"-- Source file: {input_file.name}")
    lines.append(f"-- Source rows: {len(normalized_rows)}")
    lines.append(f"-- Shipments: {len(shipments)}, Lots: {len(lots)}, Attempts: {len(attempts)}, Result lines: {len(result_lines)}, Status histories: {len(status_histories)}")
    lines.append("BEGIN;")
    lines.append("")

    if include_truncate:
        lines.append("-- WARNING: This removes existing auction seed data.")
        lines.append(
            "TRUNCATE TABLE "
            f"{TABLE_RESULT_LINES}, {TABLE_ATTEMPTS}, {TABLE_STATUS_HISTORY}, {TABLE_LOTS}, {TABLE_SHIPMENTS} "
            "RESTART IDENTITY CASCADE;"
        )
        lines.append("")

    # auction_shipments
    if shipments:
        rows = [(s.id, s.shipment_date, s.market, s.status, s.memo) for s in shipments]
        lines.append(f"INSERT INTO {TABLE_SHIPMENTS} (id, shipment_date, auction_market, status, memo, created_at, updated_at) VALUES")
        lines.append(",\n".join(
            f"  ({sid}, {sql_literal(dt)}, {sql_literal(market)}, {sql_literal(status)}, {sql_literal(memo)}, {now_expr}, {now_expr})"
            for sid, dt, market, status, memo in rows
        ) + ";")
        lines.append("")

    # lots
    if lots:
        lines.append(f"INSERT INTO {TABLE_LOTS} (id, shipment_id, item_name, variety_name, shipment_grade, boxes, shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, current_status, memo, created_at, updated_at) VALUES")
        lines.append(",\n".join(
            f"  ({lot.id}, {lot.shipment.id}, {sql_literal(lot.item_name)}, {sql_literal(lot.variety_name)}, {sql_literal(lot.grade)}, {sql_literal(lot.boxes)}, "
            f"{lot.shipped_quantity}, {lot.sold_quantity}, {lot.waiting_quantity}, {lot.returned_quantity}, {sql_literal(lot.current_status)}, {sql_literal(lot.memo)}, {now_expr}, {now_expr})"
            for lot in lots
        ) + ";")
        lines.append("")

    # attempts
    if attempts:
        lines.append(f"INSERT INTO {TABLE_ATTEMPTS} (id, shipment_lot_id, auction_date, attempt_no, attempt_status, failed_reason, memo, created_at, updated_at) VALUES")
        lines.append(",\n".join(
            f"  ({a.id}, {a.lot.id}, {sql_literal(a.auction_date)}, {a.attempt_no}, {sql_literal(a.status)}, {sql_literal(a.failed_reason)}, {sql_literal(a.memo)}, {now_expr}, {now_expr})"
            for a in attempts
        ) + ";")
        lines.append("")

    # result lines
    if result_lines:
        lines.append(f"INSERT INTO {TABLE_RESULT_LINES} (id, auction_attempt_id, auction_date, auction_grade, quantity, unit_price, amount, note, inspection_status, created_at, updated_at) VALUES")
        lines.append(",\n".join(
            f"  ({line.id}, {line.attempt.id}, {sql_literal(line.row.auction_date)}, {sql_literal(line.row.grade)}, {line.row.quantity}, {line.row.unit_price}, {line.row.amount}, {sql_literal(line.row.note)}, {sql_literal(line.inspection_status)}, {now_expr}, {now_expr})"
            for line in result_lines
        ) + ";")
        lines.append("")

    # status history: only explicit return confirmations are inserted for seed data.
    if status_histories:
        lines.append(f"INSERT INTO {TABLE_STATUS_HISTORY} (id, changed_at, reason, new_status, previous_status, memo, worker, shipment_lot_id) VALUES")
        lines.append(",\n".join(
            f"  ({history.id}, {sql_literal(history.changed_at)}, {sql_literal(history.reason)}, {sql_literal(history.new_status)}, {sql_literal(history.previous_status)}, {sql_literal(history.memo)}, {sql_literal(history.worker)}, {history.lot.id})"
            for history in status_histories
        ) + ";")
        lines.append("")

    # Set sequences safely.
    seq_targets = [
        (TABLE_SHIPMENTS, len(shipments)),
        (TABLE_LOTS, len(lots)),
        (TABLE_ATTEMPTS, len(attempts)),
        (TABLE_RESULT_LINES, len(result_lines)),
        (TABLE_STATUS_HISTORY, len(status_histories)),
    ]
    lines.append("-- Keep bigserial sequences after explicit IDs.")
    for table, max_id in seq_targets:
        if max_id > 0:
            lines.append(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM {table}), {max_id}), true);")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    return "\n".join(lines)

def write_review_csv(path: Path, reviews: list[ReviewIssue]) -> None:
    fieldnames = ["row_number", "issue_type", "message", "candidate_lot_ids", "raw_json"]
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for issue in reviews:
            writer.writerow({
                "row_number": issue.row_number,
                "issue_type": issue.issue_type,
                "message": issue.message,
                "candidate_lot_ids": ",".join(map(str, issue.candidate_lot_ids)),
                "raw_json": json.dumps(issue.raw, ensure_ascii=False),
            })

def main() -> None:
    parser = argparse.ArgumentParser(description="Generate auction seed SQL from CSV.")
    parser.add_argument("--input", required=True, type=Path, help="Source CSV path")
    parser.add_argument("--output", required=True, type=Path, help="Output SQL path")
    parser.add_argument("--review", type=Path, default=Path("review_required.csv"), help="Review CSV path")
    parser.add_argument("--truncate", action="store_true", help="Add TRUNCATE ... RESTART IDENTITY CASCADE at the beginning")
    args = parser.parse_args()

    source_rows = read_csv_rows(args.input)
    normalized_rows: list[NormalizedRow] = []
    normalize_errors: list[ReviewIssue] = []
    for source in source_rows:
        try:
            normalized_rows.append(normalize_row(source))
        except Exception as exc:
            normalize_errors.append(ReviewIssue(
                row_number=source.row_number,
                issue_type="SOURCE_ERROR",
                message=str(exc),
                raw=source.raw,
            ))

    shipments, lots, attempts, result_lines, status_histories, reviews = build_domain(normalized_rows)
    reviews = normalize_errors + reviews

    sql = generate_sql(args.input, normalized_rows, shipments, lots, attempts, result_lines, status_histories, args.truncate)
    args.output.write_text(sql, encoding="utf-8")
    write_review_csv(args.review, reviews)

    shipment_count = sum(1 for r in normalized_rows if r.row_type == "SHIPMENT")
    auction_count = sum(1 for r in normalized_rows if r.row_type == "AUCTION")
    print("Generated:", args.output)
    print("Review CSV:", args.review)
    print(f"Rows: total={len(source_rows)}, normalized={len(normalized_rows)}, shipment={shipment_count}, auction={auction_count}")
    print(f"Domain: shipments={len(shipments)}, lots={len(lots)}, attempts={len(attempts)}, result_lines={len(result_lines)}, status_histories={len(status_histories)}")
    print(f"Review issues: {len(reviews)}")

if __name__ == "__main__":
    main()
