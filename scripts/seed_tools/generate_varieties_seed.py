#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate varieties master-data JSON and seed SQL.

Recommended workflow:
  1) Extract initial varieties from sales/auction source CSV files into JSON.
  2) Manually edit the JSON when real farm/orchid-group input reveals new varieties.
  3) Generate private seed SQL from the curated JSON.

The current final schema does not have a separate item master table. The closest
master table is `varieties`, so this tool creates/updates rows in `varieties`.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any, Iterable

from seed_common import (
    clean_text,
    parse_date,
    read_csv_dicts,
    row_value,
    values_rows,
)


JSON_VERSION = 1


@dataclass
class SeedVariety:
    genus: str
    name: str
    code: str
    alias: str | None = None
    default_pot_size: str | None = None
    description: str | None = None
    sale_enabled: bool = True
    is_active: bool = True
    memo: str | None = None
    auction_count: int = 0
    direct_count: int = 0
    first_seen: date | None = None
    last_seen: date | None = None
    source_labels: set[str] = field(default_factory=set)
    review_required: bool = False
    review_note: str | None = None

    @property
    def source_label(self) -> str:
        return "+".join(sorted(self.source_labels)) if self.source_labels else "MANUAL"


def stable_variety_code(genus: str, name: str) -> str:
    """Return a deterministic code under varieties.code VARCHAR(50)."""
    key = f"{clean_text(genus).casefold()}|{clean_text(name).casefold()}"
    digest = hashlib.sha1(key.encode("utf-8")).hexdigest()[:12].upper()
    return f"VAR-{digest}"


def compact_key(value: str) -> str:
    return clean_text(value).replace(" ", "").casefold()


def canonicalize_source_pair(genus: str, name: str) -> tuple[str, str, str | None]:
    """Normalize obvious source-data variants without hiding review-worthy cases."""
    genus = clean_text(genus)
    name = clean_text(name)
    note: str | None = None

    # 일반 판매 CSV에 스프링파루 대/소가 품종명처럼 들어오는 경우가 있다.
    springfaru_key = compact_key(name)
    if springfaru_key in {"스프링파루대", "스프링파루소"}:
        name = "스프링파루"
        if not genus or genus == "미분류":
            genus = "동양심비"
        note = "스프링파루 대/소 표기는 품종명이 아니라 등급/비고로 보고 품종명을 통합했습니다."

    # 띄어쓰기 차이.
    if compact_key(name) == "트리포라레이디":
        name = "트리포라 레이디"
        if not genus or genus == "미분류":
            genus = "온시디움"
        note = "트리포라레이디 띄어쓰기를 트리포라 레이디로 통일했습니다."

    # 홍화는 실제 출하 기준에서 긴기아남으로 보는 쪽이 현재 데이터에 더 안전하다.
    if compact_key(name) == "홍화" and (not genus or genus == "미분류" or genus == "카틀레야"):
        genus = "긴기아남"
        note = "홍화는 출하 기준 품목명인 긴기아남으로 통일했습니다."

    return genus, name, note


def normalize_genus(value: str, variety_name: str, genus_lookup: dict[str, str] | None = None) -> str:
    text = clean_text(value)
    if text:
        return text
    if genus_lookup is not None:
        inferred = genus_lookup.get(clean_text(variety_name).casefold())
        if inferred:
            return inferred
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
    review_note: str | None = None,
) -> None:
    genus, name, normalization_note = canonicalize_source_pair(genus, name)
    review_note = review_note or normalization_note
    key = (genus.casefold(), name.casefold())
    current = varieties.get(key)
    if current is None:
        current = SeedVariety(
            genus=genus,
            name=name,
            code=stable_variety_code(genus, name),
            description="경매/일반 판매 초기 데이터에서 추출한 품종",
        )
        varieties[key] = current

    if source_label == "AUCTION":
        current.auction_count += 1
    elif source_label == "DIRECT":
        current.direct_count += 1
    else:
        current.source_labels.add(source_label)
    current.source_labels.add(source_label)

    if review_note:
        current.review_required = True
        if current.review_note:
            if review_note not in current.review_note:
                current.review_note = f"{current.review_note} / {review_note}"
        else:
            current.review_note = review_note

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
        row_type = clean_text(row_value(row, "분류 (출하, 경매)", "분류", "구분", "type"))
        # 품종 마스터는 출하 행 기준으로 만든다. 경매 결과 행의 오기입이 품종 마스터에 들어가는 것을 막는다.
        if "경매" in row_type:
            continue

        variety_name = clean_text(row_value(row, "품종명", "품종", "varietyName"))
        genus = normalize_genus(row_value(row, "품목명", "품목", "genus", "itemName"), variety_name)
        if not variety_name:
            issues.append({
                "row_no": row_no,
                "source": "AUCTION",
                "issue_type": "MISSING_VARIETY_NAME",
                "message": "품종명이 없어 varieties JSON에서 제외했습니다.",
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


def build_unique_genus_lookup(varieties: dict[tuple[str, str], SeedVariety]) -> tuple[dict[str, str], set[str]]:
    by_name: dict[str, set[str]] = {}
    for variety in varieties.values():
        by_name.setdefault(variety.name.casefold(), set()).add(variety.genus)
    unique = {name: next(iter(genuses)) for name, genuses in by_name.items() if len(genuses) == 1}
    ambiguous = {name for name, genuses in by_name.items() if len(genuses) > 1}
    return unique, ambiguous


def collect_direct_varieties(
    path: Path | None,
    genus_lookup: dict[str, str] | None = None,
    ambiguous_genus_names: set[str] | None = None,
) -> tuple[dict[tuple[str, str], SeedVariety], list[dict[str, object]]]:
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
        review_note = None
        if not clean_text(raw_genus) and ambiguous_genus_names and clean_text(variety_name).casefold() in ambiguous_genus_names:
            genus = "미분류"
            review_note = "동일 품종명이 경매 출하 데이터에서 여러 품목명으로 등장해 자동 추정하지 못했습니다."
            issues.append({
                "row_no": row_no,
                "source": "DIRECT",
                "issue_type": "AMBIGUOUS_GENUS_INFERENCE",
                "message": review_note,
                "raw": row,
            })
        if not variety_name:
            issues.append({
                "row_no": row_no,
                "source": "DIRECT",
                "issue_type": "MISSING_VARIETY_NAME",
                "message": "품종명이 없어 varieties JSON에서 제외했습니다.",
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
            review_note=review_note,
        )

    return varieties, issues


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
                    alias=value.alias,
                    default_pot_size=value.default_pot_size,
                    description=value.description,
                    sale_enabled=value.sale_enabled,
                    is_active=value.is_active,
                    memo=value.memo,
                    auction_count=value.auction_count,
                    direct_count=value.direct_count,
                    first_seen=value.first_seen,
                    last_seen=value.last_seen,
                    source_labels=set(value.source_labels),
                    review_required=value.review_required,
                    review_note=value.review_note,
                )
                merged[key] = current
                continue

            current.auction_count += value.auction_count
            current.direct_count += value.direct_count
            current.source_labels.update(value.source_labels)
            if value.review_required:
                current.review_required = True
                if value.review_note:
                    current.review_note = value.review_note if not current.review_note else f"{current.review_note} / {value.review_note}"
            if value.first_seen is not None and (current.first_seen is None or value.first_seen < current.first_seen):
                current.first_seen = value.first_seen
            if value.last_seen is not None and (current.last_seen is None or value.last_seen > current.last_seen):
                current.last_seen = value.last_seen

    return sorted(merged.values(), key=lambda v: (v.genus, v.name))


def find_review_issues(varieties: Iterable[SeedVariety], source_issues: list[dict[str, object]]) -> list[dict[str, object]]:
    issues = list(source_issues)

    by_name: dict[str, set[str]] = {}
    by_compact_name: dict[str, set[tuple[str, str]]] = {}
    by_code: dict[str, set[tuple[str, str]]] = {}
    for variety in varieties:
        by_name.setdefault(variety.name.casefold(), set()).add(variety.genus)
        by_compact_name.setdefault(compact_key(variety.name), set()).add((variety.genus, variety.name))
        by_code.setdefault(variety.code, set()).add((variety.genus, variety.name))
        if variety.review_required:
            issues.append({
                "row_no": None,
                "source": "VARIETY_JSON",
                "issue_type": "REVIEW_REQUIRED_VARIETY",
                "message": variety.review_note or "수동 검토가 필요한 품종입니다.",
                "raw": {"code": variety.code, "genus": variety.genus, "name": variety.name},
            })

    for name_key, genus_set in sorted(by_name.items()):
        if len(genus_set) > 1:
            issues.append({
                "row_no": None,
                "source": "MERGED",
                "issue_type": "SAME_VARIETY_NAME_DIFFERENT_GENUS",
                "message": f"같은 품종명이 여러 품목명으로 등장합니다: {', '.join(sorted(genus_set))}",
                "raw": {"variety_name_key": name_key},
            })

    for compact, pairs in sorted(by_compact_name.items()):
        names = {name for _, name in pairs}
        if len(names) > 1:
            issues.append({
                "row_no": None,
                "source": "MERGED",
                "issue_type": "SIMILAR_VARIETY_NAME_SPACING",
                "message": "공백만 다른 품종명이 있습니다. 통합 여부를 확인하세요.",
                "raw": {"compact_key": compact, "pairs": sorted(pairs)},
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


def variety_to_json_obj(v: SeedVariety) -> dict[str, Any]:
    return {
        "code": v.code,
        "genus": v.genus,
        "name": v.name,
        "alias": v.alias,
        "defaultPotSize": v.default_pot_size,
        "description": v.description,
        "saleEnabled": v.sale_enabled,
        "active": v.is_active,
        "memo": v.memo,
        "reviewRequired": v.review_required,
        "reviewNote": v.review_note,
        "source": {
            "labels": sorted(v.source_labels),
            "auctionCount": v.auction_count,
            "directCount": v.direct_count,
            "firstSeen": v.first_seen.isoformat() if v.first_seen else None,
            "lastSeen": v.last_seen.isoformat() if v.last_seen else None,
        },
    }


def write_varieties_json(path: Path, varieties: list[SeedVariety], source_files: list[str]) -> None:
    payload = {
        "version": JSON_VERSION,
        "description": "수동 편집 가능한 품종 마스터 후보 목록입니다. 실제 난 묶음 입력 중 새 품종이 생기면 varieties 배열에 추가하세요.",
        "sourceFiles": source_files,
        "rules": [
            "code는 varieties.code에 들어가는 안정 식별자입니다. 수동 추가 시 VAR-로 시작하는 고유 값을 사용하세요.",
            "genus/name 조합은 실제 품종 마스터 기준입니다.",
            "reviewRequired=true인 항목은 SQL 생성 전 확인을 권장합니다.",
            "source는 참고용 메타데이터이며 SQL 생성에는 필수값이 아닙니다.",
        ],
        "varieties": [variety_to_json_obj(v) for v in varieties],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def date_from_json(value: Any) -> date | None:
    if value in (None, ""):
        return None
    return parse_date(str(value))


def load_varieties_json(path: Path) -> list[SeedVariety]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    raw_varieties = payload.get("varieties")
    if not isinstance(raw_varieties, list):
        raise ValueError("varieties JSON must contain a list field: varieties")

    seen_codes: set[str] = set()
    seen_pairs: set[tuple[str, str]] = set()
    result: list[SeedVariety] = []
    for index, raw in enumerate(raw_varieties, start=1):
        if not isinstance(raw, dict):
            raise ValueError(f"varieties[{index}] must be an object")
        genus = clean_text(raw.get("genus"))
        name = clean_text(raw.get("name"))
        if not genus or not name:
            raise ValueError(f"varieties[{index}] must have non-empty genus and name")
        code = clean_text(raw.get("code")) or stable_variety_code(genus, name)
        if code in seen_codes:
            raise ValueError(f"duplicate variety code in JSON: {code}")
        seen_codes.add(code)
        pair_key = (genus.casefold(), name.casefold())
        if pair_key in seen_pairs:
            raise ValueError(f"duplicate variety pair in JSON: {genus} / {name}")
        seen_pairs.add(pair_key)

        source = raw.get("source") if isinstance(raw.get("source"), dict) else {}
        labels = source.get("labels") if isinstance(source.get("labels"), list) else []
        result.append(SeedVariety(
            genus=genus,
            name=name,
            code=code,
            alias=clean_text(raw.get("alias")) or None,
            default_pot_size=clean_text(raw.get("defaultPotSize")) or None,
            description=clean_text(raw.get("description")) or None,
            sale_enabled=bool(raw.get("saleEnabled", True)),
            is_active=bool(raw.get("active", True)),
            memo=clean_text(raw.get("memo")) or None,
            auction_count=int(source.get("auctionCount") or 0),
            direct_count=int(source.get("directCount") or 0),
            first_seen=date_from_json(source.get("firstSeen")),
            last_seen=date_from_json(source.get("lastSeen")),
            source_labels={clean_text(label) for label in labels if clean_text(label)},
            review_required=bool(raw.get("reviewRequired", False)),
            review_note=clean_text(raw.get("reviewNote")) or None,
        ))
    return sorted(result, key=lambda v: (v.genus, v.name))


def collect_varieties_from_sources(auction_csv: Path | None, direct_csv: Path | None) -> tuple[list[SeedVariety], list[dict[str, object]]]:
    auction_varieties, auction_issues = collect_auction_varieties(auction_csv)
    genus_lookup, ambiguous_genus_names = build_unique_genus_lookup(auction_varieties)
    direct_varieties, direct_issues = collect_direct_varieties(direct_csv, genus_lookup, ambiguous_genus_names)
    varieties = merge_variety_maps(auction_varieties, direct_varieties)
    issues = find_review_issues(varieties, auction_issues + direct_issues)
    return varieties, issues


def generate_sql(varieties: list[SeedVariety], source_names: list[str], delete_existing: bool) -> str:
    lines: list[str] = []
    lines.append("-- Generated by generate_varieties_seed.py")
    lines.append("-- Source files: " + ", ".join(source_names))
    lines.append(f"-- Varieties: {len(varieties)}")
    lines.append("--")
    lines.append("-- Notes:")
    lines.append("-- - This seed is generated from curated varieties JSON when --varieties-json is used.")
    lines.append("-- - Manually add new varieties to JSON, then regenerate this SQL.")
    lines.append("BEGIN;")
    lines.append("")

    lines.append("CREATE TEMP TABLE _seed_varieties (")
    lines.append("    code TEXT NOT NULL,")
    lines.append("    genus TEXT NOT NULL,")
    lines.append("    name TEXT NOT NULL,")
    lines.append("    alias TEXT,")
    lines.append("    default_pot_size TEXT,")
    lines.append("    description TEXT,")
    lines.append("    sale_enabled BOOLEAN NOT NULL,")
    lines.append("    is_active BOOLEAN NOT NULL,")
    lines.append("    memo TEXT,")
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
                v.alias,
                v.default_pot_size,
                v.description or "경매/일반 판매 초기 데이터에서 추출한 품종",
                v.sale_enabled,
                v.is_active,
                v.memo,
                v.source_label,
                v.auction_count,
                v.direct_count,
                v.first_seen,
                v.last_seen,
            )
            for v in varieties
        ]
        lines.append("INSERT INTO _seed_varieties (")
        lines.append("    code, genus, name, alias, default_pot_size, description, sale_enabled, is_active, memo,")
        lines.append("    source_label, auction_count, direct_count, first_seen, last_seen")
        lines.append(") VALUES")
        lines.append(values_rows(rows) + ";")
        lines.append("")

    if delete_existing:
        lines.append("-- Dev reset: remove varieties generated by this seed when they are not referenced yet.")
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
    lines.append("    seed.alias,")
    lines.append("    seed.default_pot_size,")
    lines.append("    seed.description,")
    lines.append("    seed.sale_enabled,")
    lines.append("    seed.is_active,")
    lines.append("    COALESCE(seed.memo, 'source=' || seed.source_label || ', auction_count=' || seed.auction_count || ', direct_count=' || seed.direct_count)")
    lines.append("FROM _seed_varieties seed")
    lines.append("WHERE NOT EXISTS (")
    lines.append("    SELECT 1")
    lines.append("    FROM varieties variety")
    lines.append("    WHERE LOWER(variety.code) = LOWER(seed.code)")
    lines.append("       OR (LOWER(variety.genus) = LOWER(seed.genus) AND LOWER(variety.name) = LOWER(seed.name))")
    lines.append(");")
    lines.append("")

    lines.append("UPDATE varieties variety")
    lines.append("SET")
    lines.append("    genus = seed.genus,")
    lines.append("    name = seed.name,")
    lines.append("    alias = seed.alias,")
    lines.append("    default_pot_size = seed.default_pot_size,")
    lines.append("    description = seed.description,")
    lines.append("    sale_enabled = seed.sale_enabled,")
    lines.append("    is_active = seed.is_active,")
    lines.append("    memo = COALESCE(seed.memo, variety.memo),")
    lines.append("    updated_at = CURRENT_TIMESTAMP")
    lines.append("FROM _seed_varieties seed")
    lines.append("WHERE LOWER(variety.code) = LOWER(seed.code);")
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
    parser = argparse.ArgumentParser(description="Generate varieties seed SQL from curated JSON or source CSV files.")
    parser.add_argument("--auction-csv", type=Path, help="Auction source CSV. Used only when --varieties-json is not supplied.")
    parser.add_argument("--direct-csv", type=Path, help="Direct-sales source CSV. Used only when --varieties-json is not supplied.")
    parser.add_argument("--varieties-json", type=Path, help="Curated varieties JSON path")
    parser.add_argument("--output", required=True, type=Path, help="Output SQL path")
    parser.add_argument("--review", type=Path, default=Path("varieties_seed_review_required.csv"), help="Review CSV path")
    parser.add_argument("--delete-existing", action="store_true", help="Delete generated unreferenced varieties before inserting. Use only for dev reset.")
    args = parser.parse_args()

    if args.varieties_json:
        varieties = load_varieties_json(args.varieties_json)
        issues = find_review_issues(varieties, [])
        source_names = [args.varieties_json.name]
    else:
        varieties, issues = collect_varieties_from_sources(args.auction_csv, args.direct_csv)
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
