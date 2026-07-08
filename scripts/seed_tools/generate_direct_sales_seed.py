#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate final-schema PostgreSQL seed SQL for direct sales slips/items from CSV."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal, InvalidOperation
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from seed_common import (
    clean_text,
    nullable_text,
    parse_date,
    parse_optional_int,
    parse_required_int,
    partner_type_for_direct_sale,
    read_csv_dicts,
    row_value,
    sql_literal,
    values_rows,
)


@dataclass
class DirectSaleRow:
    row_no: int
    partner_name: str
    sale_date: date
    genus: str | None
    variety_name: str
    spec: str | None
    quantity: int
    unit_price: int
    amount: int
    memo: str | None


def parse_unit_price(value: str, amount: int, quantity: int) -> int:
    text = clean_text(value).replace(",", "")
    if not text:
        raise ValueError("empty unit price")
    try:
        parsed = Decimal(text)
    except InvalidOperation as exc:
        raise ValueError(f"invalid unit price: {value!r}") from exc

    # CSV 단가가 8, 10.5처럼 천원 단위로 적힌다. 금액/수량과 대조해서 자동 보정한다.
    if quantity != 0 and parsed != 0:
        expected = parsed * Decimal(quantity)
        expected_thousand = parsed * Decimal(1000) * Decimal(quantity)
        if Decimal(amount) == expected_thousand:
            return int(parsed * Decimal(1000))
        if Decimal(amount) == expected:
            return int(parsed)

    # 대부분의 원본이 천원 단위이므로 fallback도 천원 단위로 둔다.
    if abs(parsed) < Decimal(1000):
        return int(parsed * Decimal(1000))
    return int(parsed)


def parse_rows(path: Path) -> tuple[list[DirectSaleRow], list[dict[str, object]], int | None]:
    raw_rows = read_csv_dicts(path)
    rows: list[DirectSaleRow] = []
    issues: list[dict[str, object]] = []
    source_total_amount: int | None = None

    for row_no, raw in enumerate(raw_rows, start=1):
        partner_name = clean_text(row_value(raw, "구매자명", "거래처명", "partnerName", "customerName"))
        sale_date_text = row_value(raw, "일자", "판매일자", "saleDate")
        variety_name = clean_text(row_value(raw, "품종명", "품종", "varietyName"))
        amount_text = row_value(raw, "금액", "amount")

        # The last row in the current CSV is a grand-total row with amount only.
        if not partner_name and not sale_date_text and not variety_name:
            amount = parse_optional_int(amount_text)
            if amount is not None:
                source_total_amount = amount
            continue

        try:
            if not partner_name:
                raise ValueError("구매자명이 없습니다.")
            if not variety_name:
                raise ValueError("품종명이 없습니다.")
            sale_date = parse_date(sale_date_text)
            quantity = parse_required_int(row_value(raw, "수량", "분수량", "quantity"))
            amount = parse_required_int(amount_text)
            unit_price = parse_unit_price(row_value(raw, "단가", "unitPrice"), amount, quantity)
            memo = nullable_text(row_value(raw, "비고", "메모", "note"))

            rows.append(DirectSaleRow(
                row_no=row_no,
                partner_name=partner_name,
                sale_date=sale_date,
                genus=nullable_text(row_value(raw, "품목명", "품목", "genus", "itemGroup")),
                variety_name=variety_name,
                spec=nullable_text(row_value(raw, "등급", "규격", "spec", "grade")),
                quantity=quantity,
                unit_price=unit_price,
                amount=amount,
                memo=memo,
            ))
        except Exception as exc:
            issues.append({"row_no": row_no, "issue_type": "SOURCE_ERROR", "message": str(exc), "raw": raw})

    return rows, issues, source_total_amount


def slip_key(row: DirectSaleRow) -> tuple[date, str]:
    return (row.sale_date, row.partner_name)


def generate_partner_guard(rows: list[DirectSaleRow]) -> str:
    partners = sorted({row.partner_name for row in rows})
    if not partners:
        return ""
    values = [(name, partner_type_for_direct_sale(name)) for name in partners]
    lines: list[str] = []
    lines.append("-- Ensure direct-sales partners exist.")
    lines.append("INSERT INTO business_partners (")
    lines.append("    created_at, updated_at, name, partner_type, is_active, memo")
    lines.append(")")
    lines.append("SELECT")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    seed.name,")
    lines.append("    seed.partner_type,")
    lines.append("    TRUE,")
    lines.append("    '일반 판매 seed 데이터용 거래처'")
    lines.append("FROM (VALUES")
    lines.append(values_rows(values, indent="    "))
    lines.append(") AS seed(name, partner_type)")
    lines.append("WHERE NOT EXISTS (")
    lines.append("    SELECT 1 FROM business_partners partner")
    lines.append("    WHERE LOWER(partner.name) = LOWER(seed.name)")
    lines.append(");")
    lines.append("")
    return "\n".join(lines)


def write_review_csv(path: Path, issues: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["row_no", "issue_type", "message", "raw_json"])
        writer.writeheader()
        for issue in issues:
            writer.writerow({
                "row_no": issue.get("row_no"),
                "issue_type": issue.get("issue_type"),
                "message": issue.get("message"),
                "raw_json": json.dumps(issue.get("raw", {}), ensure_ascii=False),
            })


def generate_sql(
    input_file: Path,
    rows: list[DirectSaleRow],
    source_total_amount: int | None,
    ensure_partners: bool,
    delete_existing: bool,
    slip_prefix: str,
) -> str:
    partners = sorted({row.partner_name for row in rows})
    keys = sorted({slip_key(row) for row in rows})
    row_total = sum(row.amount for row in rows)
    min_date = min((row.sale_date for row in rows), default=None)
    max_date = max((row.sale_date for row in rows), default=None)

    lines: list[str] = []
    lines.append("-- Generated by generate_direct_sales_seed.py")
    lines.append(f"-- Source file: {input_file.name}")
    lines.append(f"-- Seed item rows: {len(rows)}")
    lines.append(f"-- Direct sales slips: {len(keys)}")
    lines.append(f"-- Business partners: {len(partners)}")
    if min_date and max_date:
        lines.append(f"-- Sale date range: {min_date.isoformat()} ~ {max_date.isoformat()}")
    lines.append(f"-- Total amount: {row_total}")
    if source_total_amount is not None:
        lines.append(f"-- Source grand total amount: {source_total_amount}")
        lines.append(f"-- Total check: {'OK' if source_total_amount == row_total else 'MISMATCH'}")
    lines.append("--")
    lines.append("-- Notes:")
    lines.append("-- - DIRECT sales slips are grouped by (구매자명, 일자).")
    lines.append("-- - CSV unit price is normalized by amount/quantity; 8 -> 8000 when CSV amount matches thousand-unit price.")
    lines.append("-- - Return rows are kept as negative quantity/amount lines.")
    lines.append("BEGIN;")
    lines.append("")

    lines.append("CREATE TEMP TABLE _seed_direct_sales_rows (")
    lines.append("    row_no INTEGER NOT NULL,")
    lines.append("    partner_name TEXT NOT NULL,")
    lines.append("    sale_date DATE NOT NULL,")
    lines.append("    genus TEXT,")
    lines.append("    variety_name TEXT NOT NULL,")
    lines.append("    spec TEXT,")
    lines.append("    quantity INTEGER NOT NULL,")
    lines.append("    unit_price INTEGER NOT NULL,")
    lines.append("    amount INTEGER NOT NULL,")
    lines.append("    memo TEXT")
    lines.append(") ON COMMIT DROP;")
    lines.append("")

    if rows:
        values = [
            (row.row_no, row.partner_name, row.sale_date, row.genus, row.variety_name, row.spec,
             row.quantity, row.unit_price, row.amount, row.memo)
            for row in rows
        ]
        lines.append("INSERT INTO _seed_direct_sales_rows (")
        lines.append("    row_no, partner_name, sale_date, genus, variety_name, spec, quantity, unit_price, amount, memo")
        lines.append(") VALUES")
        lines.append(values_rows(values) + ";")
        lines.append("")

    if delete_existing:
        lines.append("-- Dev reset: remove existing direct-sale rows generated by this seed prefix.")
        lines.append("DELETE FROM sales_inventory_movements movement")
        lines.append("USING sales_slips slip")
        lines.append(f"WHERE movement.sales_slip_id = slip.id AND slip.slip_number LIKE {sql_literal(slip_prefix + '%')};")
        lines.append("")
        lines.append("DELETE FROM sales_slip_item_allocations allocation")
        lines.append("USING sales_slip_items item, sales_slips slip")
        lines.append("WHERE allocation.sales_slip_item_id = item.id")
        lines.append("  AND item.sales_slip_id = slip.id")
        lines.append(f"  AND slip.slip_number LIKE {sql_literal(slip_prefix + '%')};")
        lines.append("")
        lines.append("DELETE FROM sales_slip_items item")
        lines.append("USING sales_slips slip")
        lines.append("WHERE item.sales_slip_id = slip.id")
        lines.append(f"  AND slip.slip_number LIKE {sql_literal(slip_prefix + '%')};")
        lines.append("")
        lines.append("DELETE FROM sales_slips")
        lines.append(f"WHERE slip_number LIKE {sql_literal(slip_prefix + '%')};")
        lines.append("")

    if ensure_partners:
        lines.append(generate_partner_guard(rows))

    lines.append("CREATE TEMP TABLE _seed_direct_sales_slips ON COMMIT DROP AS")
    lines.append("SELECT")
    lines.append("    ROW_NUMBER() OVER (ORDER BY sale_date, partner_name) AS seed_slip_no,")
    lines.append("    partner_name,")
    lines.append("    sale_date,")
    lines.append("    SUM(amount)::INTEGER AS total_amount,")
    lines.append("    MIN(row_no) AS first_row_no")
    lines.append("FROM _seed_direct_sales_rows")
    lines.append("GROUP BY partner_name, sale_date;")
    lines.append("")

    lines.append("INSERT INTO sales_slips (")
    lines.append("    created_at,")
    lines.append("    updated_at,")
    lines.append("    memo,")
    lines.append("    payment_method,")
    lines.append("    payment_status,")
    lines.append("    sale_date,")
    lines.append("    sales_status,")
    lines.append("    slip_number,")
    lines.append("    total_amount,")
    lines.append("    partner_id,")
    lines.append("    sales_type,")
    lines.append("    auction_shipment_id,")
    lines.append("    expected_payment_date,")
    lines.append("    paid_amount,")
    lines.append("    remaining_amount")
    lines.append(")")
    lines.append("SELECT")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    '일반 판매 CSV seed',")
    lines.append("    NULL,")
    lines.append("    '입금 완료',")
    lines.append("    slips.sale_date,")
    lines.append("    '출고 완료',")
    lines.append(f"    {sql_literal(slip_prefix)} || LPAD(slips.seed_slip_no::TEXT, 4, '0'),")
    lines.append("    slips.total_amount,")
    lines.append("    partner.id,")
    lines.append("    'DIRECT',")
    lines.append("    NULL,")
    lines.append("    slips.sale_date,")
    lines.append("    slips.total_amount,")
    lines.append("    0")
    lines.append("FROM _seed_direct_sales_slips slips")
    lines.append("JOIN business_partners partner ON LOWER(partner.name) = LOWER(slips.partner_name);")
    lines.append("")

    lines.append("INSERT INTO sales_slip_items (")
    lines.append("    sales_slip_id,")
    lines.append("    item_name,")
    lines.append("    genus,")
    lines.append("    spec,")
    lines.append("    quantity,")
    lines.append("    unit_price,")
    lines.append("    amount,")
    lines.append("    memo,")
    lines.append("    auction_shipment_lot_id")
    lines.append(")")
    lines.append("SELECT")
    lines.append("    slip.id,")
    lines.append("    rows.variety_name,")
    lines.append("    rows.genus,")
    lines.append("    rows.spec,")
    lines.append("    rows.quantity,")
    lines.append("    rows.unit_price,")
    lines.append("    rows.amount,")
    lines.append("    rows.memo,")
    lines.append("    NULL")
    lines.append("FROM _seed_direct_sales_rows rows")
    lines.append("JOIN _seed_direct_sales_slips seeded_slip")
    lines.append("  ON seeded_slip.partner_name = rows.partner_name")
    lines.append(" AND seeded_slip.sale_date = rows.sale_date")
    lines.append("JOIN sales_slips slip")
    lines.append(f"  ON slip.slip_number = {sql_literal(slip_prefix)} || LPAD(seeded_slip.seed_slip_no::TEXT, 4, '0')")
    lines.append("ORDER BY rows.row_no;")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate DIRECT sales seed SQL from CSV.")
    parser.add_argument("--input", required=True, type=Path, help="Source direct-sales CSV")
    parser.add_argument("--output", required=True, type=Path, help="Output SQL path")
    parser.add_argument("--review", type=Path, default=Path("direct_sales_seed_review_required.csv"), help="Review CSV path")
    parser.add_argument("--slip-prefix", default="DIRECT-CSV-2026-", help="Generated slip_number prefix")
    parser.add_argument("--delete-existing", action="store_true", help="Delete rows with the same slip prefix before inserting. Use for dev reset, not normal Flyway seeds.")
    parser.add_argument("--no-ensure-partners", action="store_true", help="Do not emit business_partners guard insert block")
    args = parser.parse_args()

    rows, issues, source_total_amount = parse_rows(args.input)
    sql = generate_sql(
        input_file=args.input,
        rows=rows,
        source_total_amount=source_total_amount,
        ensure_partners=not args.no_ensure_partners,
        delete_existing=args.delete_existing,
        slip_prefix=args.slip_prefix,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(sql, encoding="utf-8")
    write_review_csv(args.review, issues)

    print("Generated:", args.output)
    print("Review CSV:", args.review)
    print("Rows:", len(rows))
    print("Slips:", len({slip_key(row) for row in rows}))
    print("Partners:", len({row.partner_name for row in rows}))
    print("Total amount:", sum(row.amount for row in rows))
    if source_total_amount is not None:
        print("Source total:", source_total_amount, "OK" if source_total_amount == sum(row.amount for row in rows) else "MISMATCH")
    print("Review issues:", len(issues))


if __name__ == "__main__":
    main()
