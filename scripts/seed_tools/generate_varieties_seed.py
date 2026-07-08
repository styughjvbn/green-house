#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate varieties master-data seed SQL from auction and direct-sales source CSV files.

The current final schema does not have a separate item master table. The closest
master table is `varieties`, so this generator collects distinct
(품목명, 품종명) pairs from the auction/direct-sales sources and creates initial
variety rows.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Iterable

from seed_common import (
    clean_text,
    nullable_text,
    parse_date,
    read_csv_dicts,
    row_value,
    sql_literal,
    values_rows,
)


@dataclass
class SeedVariety:
    genus: str
    name: str
    code: str
    auction_count: int = 0
    direct_count: int = 0
    first_seen: date | None = None
    last_seen: date | None = None
    source_labels: set[str] = field(default_factory=set)

    @property
    def source_label(self) -> str:
        return "+".join(sorted(self.source_labels))


def stable_variety_code(genus: str, name: str) -> str:
    """Return a deterministic code under varieties.code VARCHAR(50)."""
    key = f"{clean_text(genus).casefold()}|{clean_text(name).casefold()}"
    digest = hashlib.sha1(key.encode("utf-8")).hexdigest()[:12].upper()
    return f"VAR-{digest}"


def normalize_genus(value: str, variety_name: str, genus_lookup: dict[str, str] | None = None) -> str:
    text = clean_text(value)
    if text:
        return text
    if genus_lookup is not None:
        inferred = genus_lookup.get(clean_text(variety_name).casefold())
        if inferred:
            return inferred
    # Direct-sales CSV often has no 품목명. If it cannot be inferred from auction
    # data, keep such rows usable by putting them under a neutral group.
    return "미분류"


def parse_optional_source_date(row: dict[str, str], *aliases: str) -> date | None:
    text = row_value(row, *aliases)
    if not clean_text(text):
        return None
    try:
        return parse_date(text)
    except Exception:
        return None


def upsert_variety(
    varieties: dict[tuple[str, str], SeedVariety],
    *,
    genus: str,
    name: str,
    source_label: str,
    source_date: date | None,
) -> None:
    genus = clean_text(genus)
    name = clean_text(name)
    key = (genus.casefold(), name.casefold())
    current = varieties.get(key)
    if current is None:
        current = SeedVariety(
            genus=genus,
            name=name,
            code=stable_variety_code(genus, name),
        )
        varieties[key] = current

    if source_label == "AUCTION":
        current.auction_count += 1
    elif source_label == "DIRECT":
        current.direct_count += 1
    current.source_labels.add(source_label)

    if source_date is not None:
        if current.first_seen is None or source_date < current.first_seen:
            current.first_seen = source_date
        if current.last_seen is None or source_date > current.last_seen:
            current.last_seen = source_date


def collect_auction_varieties(path: Path | None) -> tuple[dict[tuple[str, str], SeedVariety], list[dict[str, object]]]:
    varieties: dict[tuple[str, str], SeedVariety] = {}
    issues: list[dict[str, object]] = []
    if not path:
        return varieties, issues

    for row_no, row in enumerate(read_csv_dicts(path), start=1):
        variety_name = clean_text(row_value(row, "품종명", "품종", "varietyName"))
        genus = normalize_genus(row_value(row, "품목명", "품목", "genus", "itemName"), variety_name)
        if not variety_name:
            issues.append({
                "row_no": row_no,
                "source": "AUCTION",
                "issue_type": "MISSING_VARIETY_NAME",
                "message": "품종명이 없어 varieties seed에서 제외했습니다.",
                "raw": row,
            })
            continue

        source_date = parse_optional_source_date(row, "일자", "출하일자", "판매일자")
        upsert_variety(
            varieties,
            genus=genus,
            name=variety_name,
            source_label="AUCTION",
            source_date=source_date,
        )

    return varieties, issues


def collect_direct_varieties(path: Path | None, genus_lookup: dict[str, str] | None = None, ambiguous_genus_names: set[str] | None = None) -> tuple[dict[tuple[str, str], SeedVariety], list[dict[str, object]]]:
    varieties: dict[tuple[str, str], SeedVariety] = {}
    issues: list[dict[str, object]] = []
    if not path:
        return varieties, issues

    for row_no, row in enumerate(read_csv_dicts(path), start=1):
        partner_name = clean_text(row_value(row, "구매자명", "거래처명", "partnerName", "customerName"))
        sale_date_text = row_value(row, "일자", "판매일자", "saleDate")
        variety_name = clean_text(row_value(row, "품종명", "품종", "varietyName"))

        # The direct-sales CSV has a final grand-total row with amount only.
        if not partner_name and not clean_text(sale_date_text) and not variety_name:
            continue

        raw_genus = row_value(row, "품목명", "품목", "genus", "itemName")
        genus = normalize_genus(raw_genus, variety_name, genus_lookup)
        if not clean_text(raw_genus) and ambiguous_genus_names and clean_text(variety_name).casefold() in ambiguous_genus_names:
            issues.append({
                "row_no": row_no,
                "source": "DIRECT",
                "issue_type": "AMBIGUOUS_GENUS_INFERENCE",
                "message": "동일 품종명이 경매 데이터에서 여러 품목명으로 등장해 미분류로 처리했습니다.",
                "raw": row,
            })
        if not variety_name:
            issues.append({
                "row_no": row_no,
                "source": "DIRECT",
                "issue_type": "MISSING_VARIETY_NAME",
                "message": "품종명이 없어 varieties seed에서 제외했습니다.",
                "raw": row,
            })
            continue

        source_date = parse_optional_source_date(row, "일자", "판매일자", "saleDate")
        upsert_variety(
            varieties,
            genus=genus,
            name=variety_name,
            source_label="DIRECT",
            source_date=source_date,
        )

    return varieties, issues



def build_unique_genus_lookup(varieties: dict[tuple[str, str], SeedVariety]) -> tuple[dict[str, str], set[str]]:
    by_name: dict[str, set[str]] = {}
    for variety in varieties.values():
        by_name.setdefault(variety.name.casefold(), set()).add(variety.genus)
    unique = {name: next(iter(genuses)) for name, genuses in by_name.items() if len(genuses) == 1}
    ambiguous = {name for name, genuses in by_name.items() if len(genuses) > 1}
    return unique, ambiguous

def merge_variety_maps(*maps: dict[tuple[str, str], SeedVariety]) -> list[SeedVariety]:
    merged: dict[tuple[str, str], SeedVariety] = {}
    for source_map in maps:
        for key, value in source_map.items():
            current = merged.get(key)
            if current is None:
                current = SeedVariety(
                    genus=value.genus,
                    name=value.name,
                    code=value.code,
                    auction_count=value.auction_count,
                    direct_count=value.direct_count,
                    first_seen=value.first_seen,
                    last_seen=value.last_seen,
                    source_labels=set(value.source_labels),
                )
                merged[key] = current
                continue

            current.auction_count += value.auction_count
            current.direct_count += value.direct_count
            current.source_labels.update(value.source_labels)
            if value.first_seen is not None and (current.first_seen is None or value.first_seen < current.first_seen):
                current.first_seen = value.first_seen
            if value.last_seen is not None and (current.last_seen is None or value.last_seen > current.last_seen):
                current.last_seen = value.last_seen

    return sorted(merged.values(), key=lambda v: (v.genus, v.name))


def find_review_issues(varieties: Iterable[SeedVariety], source_issues: list[dict[str, object]]) -> list[dict[str, object]]:
    issues = list(source_issues)

    by_name: dict[str, set[str]] = {}
    by_code: dict[str, set[tuple[str, str]]] = {}
    for variety in varieties:
        by_name.setdefault(variety.name.casefold(), set()).add(variety.genus)
        by_code.setdefault(variety.code, set()).add((variety.genus, variety.name))

    for name_key, genus_set in sorted(by_name.items()):
        if len(genus_set) > 1:
            issues.append({
                "row_no": None,
                "source": "MERGED",
                "issue_type": "SAME_VARIETY_NAME_DIFFERENT_GENUS",
                "message": f"같은 품종명이 여러 품목명으로 등장합니다: {', '.join(sorted(genus_set))}",
                "raw": {"variety_name_key": name_key},
            })

    for code, pairs in sorted(by_code.items()):
        if len(pairs) > 1:
            issues.append({
                "row_no": None,
                "source": "MERGED",
                "issue_type": "CODE_COLLISION",
                "message": f"동일 code가 여러 품종 조합에 할당되었습니다: {code}",
                "raw": {"pairs": sorted(pairs)},
            })

    return issues


def generate_sql(varieties: list[SeedVariety], source_names: list[str], delete_existing: bool) -> str:
    lines: list[str] = []
    lines.append("-- Generated by generate_varieties_seed.py")
    lines.append("-- Source files: " + ", ".join(source_names))
    lines.append(f"-- Varieties: {len(varieties)}")
    lines.append("--")
    lines.append("-- Notes:")
    lines.append("-- - This project has no separate item master table in the current schema.")
    lines.append("-- - Initial product master data is inserted into varieties using distinct (품목명, 품종명) pairs.")
    lines.append("-- - When direct-sales 품목명 is empty, genus is inferred from auction data if possible; otherwise '미분류'.")
    lines.append("BEGIN;")
    lines.append("")

    lines.append("CREATE TEMP TABLE _seed_varieties (")
    lines.append("    code TEXT NOT NULL,")
    lines.append("    genus TEXT NOT NULL,")
    lines.append("    name TEXT NOT NULL,")
    lines.append("    source_label TEXT NOT NULL,")
    lines.append("    auction_count INTEGER NOT NULL,")
    lines.append("    direct_count INTEGER NOT NULL,")
    lines.append("    first_seen DATE,")
    lines.append("    last_seen DATE")
    lines.append(") ON COMMIT DROP;")
    lines.append("")

    if varieties:
        rows = [
            (
                v.code,
                v.genus,
                v.name,
                v.source_label,
                v.auction_count,
                v.direct_count,
                v.first_seen,
                v.last_seen,
            )
            for v in varieties
        ]
        lines.append("INSERT INTO _seed_varieties (")
        lines.append("    code, genus, name, source_label, auction_count, direct_count, first_seen, last_seen")
        lines.append(") VALUES")
        lines.append(values_rows(rows) + ";")
        lines.append("")

    if delete_existing:
        lines.append("-- Dev reset: remove varieties that were generated by this seed code prefix and are not referenced yet.")
        lines.append("DELETE FROM varieties variety")
        lines.append("USING _seed_varieties seed")
        lines.append("WHERE variety.code = seed.code")
        lines.append("  AND NOT EXISTS (SELECT 1 FROM inbound_records inbound WHERE inbound.variety_id = variety.id)")
        lines.append("  AND NOT EXISTS (SELECT 1 FROM orchid_groups orchid WHERE orchid.variety_id = variety.id);")
        lines.append("")

    lines.append("INSERT INTO varieties (")
    lines.append("    created_at,")
    lines.append("    updated_at,")
    lines.append("    code,")
    lines.append("    genus,")
    lines.append("    name,")
    lines.append("    alias,")
    lines.append("    default_pot_size,")
    lines.append("    description,")
    lines.append("    sale_enabled,")
    lines.append("    is_active,")
    lines.append("    memo")
    lines.append(")")
    lines.append("SELECT")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    CURRENT_TIMESTAMP,")
    lines.append("    seed.code,")
    lines.append("    seed.genus,")
    lines.append("    seed.name,")
    lines.append("    NULL,")
    lines.append("    NULL,")
    lines.append("    '경매/일반 판매 초기 데이터에서 추출한 품종',")
    lines.append("    TRUE,")
    lines.append("    TRUE,")
    lines.append("    'source=' || seed.source_label || ', auction_count=' || seed.auction_count || ', direct_count=' || seed.direct_count")
    lines.append("FROM _seed_varieties seed")
    lines.append("WHERE NOT EXISTS (")
    lines.append("    SELECT 1")
    lines.append("    FROM varieties variety")
    lines.append("    WHERE LOWER(variety.genus) = LOWER(seed.genus)")
    lines.append("      AND LOWER(variety.name) = LOWER(seed.name)")
    lines.append(");")
    lines.append("")

    lines.append("UPDATE varieties variety")
    lines.append("SET")
    lines.append("    sale_enabled = TRUE,")
    lines.append("    is_active = TRUE,")
    lines.append("    updated_at = CURRENT_TIMESTAMP")
    lines.append("FROM _seed_varieties seed")
    lines.append("WHERE LOWER(variety.genus) = LOWER(seed.genus)")
    lines.append("  AND LOWER(variety.name) = LOWER(seed.name)")
    lines.append("  AND (variety.sale_enabled = FALSE OR variety.is_active = FALSE);")
    lines.append("")

    lines.append("SELECT setval(pg_get_serial_sequence('varieties', 'id'), COALESCE((SELECT MAX(id) FROM varieties), 1), true);")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    return "\n".join(lines)


def write_review_csv(path: Path, issues: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["row_no", "source", "issue_type", "message", "raw_json"])
        writer.writeheader()
        for issue in issues:
            writer.writerow({
                "row_no": issue.get("row_no"),
                "source": issue.get("source"),
                "issue_type": issue.get("issue_type"),
                "message": issue.get("message"),
                "raw_json": json.dumps(issue.get("raw", {}), ensure_ascii=False),
            })


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate varieties seed SQL from auction/direct-sales source CSV files.")
    parser.add_argument("--auction-csv", type=Path, help="Auction source CSV")
    parser.add_argument("--direct-csv", type=Path, help="Direct-sales source CSV")
    parser.add_argument("--output", required=True, type=Path, help="Output SQL path")
    parser.add_argument("--review", type=Path, default=Path("varieties_seed_review_required.csv"), help="Review CSV path")
    parser.add_argument("--delete-existing", action="store_true", help="Delete generated unreferenced varieties before inserting. Use only for dev reset.")
    args = parser.parse_args()

    auction_varieties, auction_issues = collect_auction_varieties(args.auction_csv)
    genus_lookup, ambiguous_genus_names = build_unique_genus_lookup(auction_varieties)
    direct_varieties, direct_issues = collect_direct_varieties(args.direct_csv, genus_lookup, ambiguous_genus_names)
    varieties = merge_variety_maps(auction_varieties, direct_varieties)
    issues = find_review_issues(varieties, auction_issues + direct_issues)
    source_names = [p.name for p in [args.auction_csv, args.direct_csv] if p]

    sql = generate_sql(varieties, source_names, args.delete_existing)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(sql, encoding="utf-8")
    write_review_csv(args.review, issues)

    print("Generated:", args.output)
    print("Review CSV:", args.review)
    print("Varieties:", len(varieties))
    print("Auction-linked varieties:", sum(1 for v in varieties if v.auction_count > 0))
    print("Direct-linked varieties:", sum(1 for v in varieties if v.direct_count > 0))
    print("Review issues:", len(issues))


if __name__ == "__main__":
    main()
