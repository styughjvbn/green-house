#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Export current orchid_groups from a running PostgreSQL DB as private seed SQL.

This generator intentionally does not dump raw IDs. It rebuilds foreign keys by
stable natural keys:

- variety_id: varieties.code first, then genus + name fallback
- bed_zone_id: house number + physical bed number + bed zone side

Usage from project root:

  python scripts/seed/generate_orchid_groups_seed_from_db.py \
    --output scripts/seed/generated-private/seed_orchid_groups.sql

It talks to PostgreSQL through `docker compose exec db psql`, so it does not
require psycopg/psycopg2.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional

from seed_common import clean_text, sql_literal


DEFAULT_QUERY = r"""
SELECT COALESCE(json_agg(row_to_json(t) ORDER BY t.house_number, t.physical_bed_number, t.bed_zone_side, t.sort_order, t.id), '[]'::json)
FROM (
    SELECT
        og.id,
        og.created_at,
        og.updated_at,
        og.age_year,
        og.genus,
        og.variety_name,
        v.code AS variety_code,
        v.genus AS master_genus,
        v.name AS master_name,
        og.quantity,
        COALESCE(og.reserved_quantity, 0) AS reserved_quantity,
        og.pot_size,
        og.pot_size_code,
        og.placement_type,
        og.tray_count,
        og.status,
        og.sort_order,
        og.memo,
        COALESCE(og.split_placement_allowed, FALSE) AS split_placement_allowed,
        og.start_position,
        og.end_position,
        og.inbound_record_id,
        h.number AS house_number,
        pb.number AS physical_bed_number,
        bz.side AS bed_zone_side,
        bz.name AS bed_zone_name
    FROM orchid_groups og
    JOIN bed_zones bz ON bz.id = og.bed_zone_id
    JOIN physical_beds pb ON pb.id = bz.physical_bed_id
    JOIN houses h ON h.id = pb.house_id
    LEFT JOIN varieties v ON v.id = og.variety_id
    ORDER BY h.number, pb.number, bz.sort_order, og.sort_order, og.id
) t;
"""


@dataclass(frozen=True)
class DbConfig:
    service: str
    user: str
    database: str
    compose_file: Optional[str]


def run_psql_json(config: DbConfig, query: str) -> list[dict[str, Any]]:
    cmd = ["docker", "compose"]
    if config.compose_file:
        cmd.extend(["-f", config.compose_file])
    cmd.extend([
        "exec",
        "-T",
        config.service,
        "psql",
        "-U",
        config.user,
        "-d",
        config.database,
        "-At",
        "-v",
        "ON_ERROR_STOP=1",
        "-c",
        query,
    ])

    env = os.environ.copy()
    env.setdefault("PGCLIENTENCODING", "UTF8")

    try:
        completed = subprocess.run(
            cmd,
            check=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=env,
        )
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr or ""
        stdout = exc.stdout or ""
        raise RuntimeError(
            "psql query failed while exporting orchid groups.\n"
            f"Command: {' '.join(cmd)}\n"
            f"STDOUT:\n{stdout}\n"
            f"STDERR:\n{stderr}"
        ) from exc

    output = (completed.stdout or "").strip()
    if not output:
        return []
    try:
        parsed = json.loads(output)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            "psql query did not return valid JSON. "
            "Check DB connection, client encoding, and query output.\n"
            f"Output preview: {output[:1000]}"
        ) from exc
    if not isinstance(parsed, list):
        raise RuntimeError("psql query did not return a JSON array")
    return parsed


def maybe_number_literal(value: Any) -> str:
    if value is None:
        return "NULL"
    return str(value)


def variety_lookup_sql(group: dict[str, Any]) -> str:
    code = clean_text(group.get("variety_code"))
    master_genus = clean_text(group.get("master_genus")) or clean_text(group.get("genus"))
    master_name = clean_text(group.get("master_name")) or clean_text(group.get("variety_name"))

    if code:
        return (
            "(SELECT id FROM varieties "
            f"WHERE code = {sql_literal(code)} "
            f"OR (genus = {sql_literal(master_genus)} AND name = {sql_literal(master_name)}) "
            "ORDER BY CASE WHEN code = " + sql_literal(code) + " THEN 0 ELSE 1 END "
            "LIMIT 1)"
        )
    return (
        "(SELECT id FROM varieties "
        f"WHERE genus = {sql_literal(master_genus)} AND name = {sql_literal(master_name)} "
        "LIMIT 1)"
    )


def bed_zone_lookup_sql(group: dict[str, Any]) -> str:
    return (
        "(SELECT bz.id "
        "FROM bed_zones bz "
        "JOIN physical_beds pb ON pb.id = bz.physical_bed_id "
        "JOIN houses h ON h.id = pb.house_id "
        f"WHERE h.number = {int(group['house_number'])} "
        f"AND pb.number = {int(group['physical_bed_number'])} "
        f"AND bz.side = {sql_literal(group['bed_zone_side'])} "
        "LIMIT 1)"
    )


def build_insert_row(group: dict[str, Any]) -> str:
    genus = clean_text(group.get("master_genus")) or clean_text(group.get("genus"))
    variety_name = clean_text(group.get("master_name")) or clean_text(group.get("variety_name"))
    values = [
        "CURRENT_TIMESTAMP",  # created_at
        "CURRENT_TIMESTAMP",  # updated_at
        variety_lookup_sql(group),
        sql_literal(genus),
        sql_literal(variety_name),
        maybe_number_literal(group.get("age_year")),
        sql_literal(group.get("pot_size")),
        sql_literal(group.get("pot_size_code") or "UNSPECIFIED"),
        sql_literal(group.get("placement_type")),
        maybe_number_literal(group.get("quantity")),
        maybe_number_literal(group.get("reserved_quantity") or 0),
        maybe_number_literal(group.get("tray_count")),
        sql_literal(group.get("status")),
        maybe_number_literal(group.get("sort_order") or 0),
        bed_zone_lookup_sql(group),
        sql_literal(group.get("memo")),
        sql_literal(bool(group.get("split_placement_allowed"))),
        maybe_number_literal(group.get("start_position")),
        maybe_number_literal(group.get("end_position")),
    ]
    return "  (" + ", ".join(values) + ")"


def build_sql(groups: list[dict[str, Any]], delete_existing: bool) -> str:
    lines: list[str] = []
    lines.append("-- Generated by generate_orchid_groups_seed_from_db.py")
    lines.append("-- Source: current PostgreSQL orchid_groups table")
    lines.append(f"-- Orchid groups: {len(groups)}")
    lines.append("-- This is private operational seed data. Do not commit if it contains real farm data.")
    lines.append("BEGIN;")
    lines.append("")

    lines.append("CREATE TEMP TABLE IF NOT EXISTS seed_orchid_group_warnings (")
    lines.append("    message TEXT")
    lines.append(") ON COMMIT DROP;")
    lines.append("")

    if delete_existing:
        lines.append("-- WARNING: development reset mode. This removes current orchid groups.")
        lines.append("DELETE FROM sales_inventory_movements;")
        lines.append("DELETE FROM sales_slip_item_allocations;")
        lines.append("DELETE FROM orchid_groups;")
        lines.append("")

    inbound_linked = [g for g in groups if g.get("inbound_record_id") is not None]
    if inbound_linked:
        lines.append(f"-- WARNING: {len(inbound_linked)} group(s) had inbound_record_id in source DB.")
        lines.append("-- inbound_record_id is intentionally not restored by this seed.")
        lines.append("")

    if groups:
        lines.append("INSERT INTO orchid_groups (")
        lines.append("    created_at,")
        lines.append("    updated_at,")
        lines.append("    variety_id,")
        lines.append("    genus,")
        lines.append("    variety_name,")
        lines.append("    age_year,")
        lines.append("    pot_size,")
        lines.append("    pot_size_code,")
        lines.append("    placement_type,")
        lines.append("    quantity,")
        lines.append("    reserved_quantity,")
        lines.append("    tray_count,")
        lines.append("    status,")
        lines.append("    sort_order,")
        lines.append("    bed_zone_id,")
        lines.append("    memo,")
        lines.append("    split_placement_allowed,")
        lines.append("    start_position,")
        lines.append("    end_position")
        lines.append(") VALUES")
        lines.append(",\n".join(build_insert_row(group) for group in groups) + ";")
        lines.append("")

    lines.append("-- Fail fast if a referenced variety or bed zone could not be resolved.")
    lines.append("DO $$")
    lines.append("BEGIN")
    lines.append("    IF EXISTS (SELECT 1 FROM orchid_groups WHERE variety_id IS NULL) THEN")
    lines.append("        RAISE EXCEPTION 'seed_orchid_groups.sql inserted rows with NULL variety_id';")
    lines.append("    END IF;")
    lines.append("    IF EXISTS (SELECT 1 FROM orchid_groups WHERE bed_zone_id IS NULL) THEN")
    lines.append("        RAISE EXCEPTION 'seed_orchid_groups.sql inserted rows with NULL bed_zone_id';")
    lines.append("    END IF;")
    lines.append("END $$;")
    lines.append("")
    lines.append("SELECT setval(pg_get_serial_sequence('orchid_groups', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM orchid_groups), 1), true);")
    lines.append("")
    lines.append("COMMIT;")
    lines.append("")
    return "\n".join(lines)


def write_review(path: Path, groups: list[dict[str, Any]]) -> None:
    lines = ["issue_type,orchid_group_id,message"]
    for group in groups:
        if not clean_text(group.get("variety_code")):
            lines.append(
                ",".join([
                    "MISSING_VARIETY_CODE",
                    str(group.get("id")),
                    sql_literal(f"품종 코드가 없어 genus/name으로 매칭합니다: {group.get('genus')} / {group.get('variety_name')}").strip("'"),
                ])
            )
        if group.get("inbound_record_id") is not None:
            lines.append(
                ",".join([
                    "INBOUND_LINK_OMITTED",
                    str(group.get("id")),
                    sql_literal("inbound_record_id는 seed에서 복원하지 않습니다.").strip("'"),
                ])
            )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8-sig")


def main() -> None:
    parser = argparse.ArgumentParser(description="Export orchid_groups from DB as private seed SQL.")
    parser.add_argument("--output", type=Path, default=Path("scripts/seed/generated-private/seed_orchid_groups.sql"))
    parser.add_argument("--review", type=Path, default=Path("scripts/seed/generated-private/orchid_groups_seed_review_required.csv"))
    parser.add_argument("--db-service", default=os.getenv("GREENHOUSE_DB_SERVICE", "db"))
    parser.add_argument("--db-user", default=os.getenv("GREENHOUSE_DB_USER", "greenhouse"))
    parser.add_argument("--db-name", default=os.getenv("GREENHOUSE_DB_NAME", "greenhouse"))
    parser.add_argument("--compose-file", default=os.getenv("GREENHOUSE_COMPOSE_FILE"))
    parser.add_argument("--delete-existing", action="store_true", help="Add DELETE statements before inserting orchid groups. Use only for dev reset.")
    args = parser.parse_args()

    config = DbConfig(
        service=args.db_service,
        user=args.db_user,
        database=args.db_name,
        compose_file=args.compose_file,
    )
    groups = run_psql_json(config, DEFAULT_QUERY)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.review.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(build_sql(groups, delete_existing=args.delete_existing), encoding="utf-8")
    write_review(args.review, groups)
    print(f"Generated: {args.output}")
    print(f"Review CSV: {args.review}")
    print(f"Orchid groups: {len(groups)}")


if __name__ == "__main__":
    main()
