#!/usr/bin/env python3
"""Create realistic, non-identifying demo data in the isolated temporary DB.

The script intentionally keeps transformation rules in Python. PostgreSQL is
used only for reading and writing the data inside one transaction.
"""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import urlparse


SCRIPT_DIR = Path(__file__).resolve().parent
CATALOG_PATH = SCRIPT_DIR / "item-varieties.json"
EXPECTED_DATABASE = "greenhouse_demo_sanitize_tmp"
INT_MAX = 2_147_483_647
BIGINT_MAX = 9_223_372_036_854_775_807
IDENTIFIER = re.compile(r"^[a-z_][a-z0-9_]*$")
SAFE_CODE = re.compile(r"^[A-Z][A-Z0-9_:-]{1,49}$")

SAFE_WORK_TYPE_NAMES = {
    "INBOUND": "입고",
    "POTTING": "포트 작업",
    "PESTICIDE": "농약",
    "FERTILIZER": "비료",
    "REPOT": "분갈이",
    "STATUS": "상태 기록",
    "MEMO": "일반 메모",
    "LEAF_CLEANUP": "잎 정리",
    "WEED_CLEANUP": "잡초 정리",
    "FLOWER_CLEANUP": "단화/꽃 정리",
    "MOVEMENT": "위치 이동",
    "MULTI_CREATE": "난 묶음 다중 생성",
    "CORRECTION": "구조 변경 보정",
    "DIVIDE": "분주",
    "MERGE": "합식",
    "DISCARD": "폐기",
}

ACTOR_COLUMNS = (
    ("auction_lot_status_history", "worker"),
    ("auction_settlements", "confirmed_by"),
    ("inbound_records", "worker"),
    ("partner_payment_events", "created_by"),
    ("work_records", "worker"),
    ("orchid_group_collections", "created_by"),
    ("orchid_group_collection_members", "created_by"),
    ("work_operations", "worker"),
    ("work_target_executions", "worker"),
    ("work_applied_effects", "worker"),
)

JSON_COLUMNS = {
    "auction_settlement_lines": ("line_meta_json",),
    "auction_settlements": ("payment_meta_json",),
    "partner_balance_summaries": ("summary_json",),
    "partner_payment_events": (
        "allocation_payload",
        "balance_snapshot_json",
        "match_payload",
        "raw_payload",
    ),
    "partner_settlement_settings": ("depositor_aliases", "rule_json"),
    "work_records": ("details",),
    "work_operations": ("source_condition_snapshot", "details"),
    "work_operation_targets": ("location_snapshot",),
    "work_target_executions": ("result_details",),
    "work_applied_effects": ("command_details", "result_details"),
}


class SanitizationError(RuntimeError):
    pass


@dataclass(frozen=True)
class CatalogPair:
    item: str
    variety: str


def load_driver() -> Any:
    try:
        import psycopg  # type: ignore

        return psycopg
    except ImportError:
        try:
            import psycopg2  # type: ignore

            return psycopg2
        except ImportError as exc:
            raise SanitizationError(
                "PostgreSQL Python driver is missing. Run: "
                "python3 -m pip install 'psycopg[binary]>=3.1,<4'"
            ) from exc


def required_env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise SanitizationError(f"{name} is required")
    return value


def parse_int_env(name: str, minimum: int, maximum: int | None = None) -> int:
    raw = required_env(name)
    try:
        value = int(raw)
    except ValueError as exc:
        raise SanitizationError(f"{name} must be an integer") from exc
    if value < minimum or (maximum is not None and value > maximum):
        suffix = f"..{maximum}" if maximum is not None else f" or greater"
        raise SanitizationError(f"{name} must be {minimum}{suffix}")
    return value


def load_catalog(path: Path = CATALOG_PATH) -> list[CatalogPair]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SanitizationError(f"Cannot read variety catalog: {path}: {exc}") from exc
    if not isinstance(payload, list):
        raise SanitizationError("item-varieties.json root must be an array")

    pairs: list[CatalogPair] = []
    seen: set[tuple[str, str]] = set()
    for index, entry in enumerate(payload):
        if not isinstance(entry, dict):
            raise SanitizationError(f"Catalog entry {index} must be an object")
        item = str(entry.get("item", "")).strip()
        varieties = entry.get("varieties")
        if not item or not isinstance(varieties, list):
            raise SanitizationError(f"Catalog entry {index} needs item and varieties")
        for raw_variety in varieties:
            variety = str(raw_variety).strip()
            if not variety:
                continue
            pair = (item, variety)
            if pair in seen:
                continue
            if len(item) > 100 or len(variety) > 150:
                raise SanitizationError(f"Catalog value exceeds DB length: {item} / {variety}")
            seen.add(pair)
            pairs.append(CatalogPair(*pair))
    if not pairs:
        raise SanitizationError("item-varieties.json contains no usable pairs")
    return sorted(pairs, key=lambda pair: (pair.item.casefold(), pair.variety.casefold()))


def keyed_digest(key: str, namespace: str, value: str) -> str:
    return hmac.new(
        key.encode("utf-8"),
        f"{namespace}\0{value}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def token(key: str, namespace: str, value: str, prefix: str) -> str:
    return prefix + keyed_digest(key, namespace, value)[:12]


def catalog_pair_for(
    catalog: Sequence[CatalogPair], key: str, namespace: str, source: str
) -> CatalogPair:
    digest = keyed_digest(key, namespace, source)
    return catalog[int(digest, 16) % len(catalog)]


def unique_catalog_mapping(
    rows: Sequence[tuple[int, str, str]],
    catalog: Sequence[CatalogPair],
    key: str,
) -> dict[int, CatalogPair]:
    if len(rows) > len(catalog):
        raise SanitizationError(
            f"Variety master has {len(rows)} rows but catalog has only {len(catalog)} unique pairs"
        )
    ordered_rows = sorted(
        rows,
        key=lambda row: keyed_digest(key, "variety-master", f"{row[0]}|{row[1]}|{row[2]}"),
    )
    offset = int(keyed_digest(key, "catalog-offset", str(len(rows))), 16) % len(catalog)
    rotated = list(catalog[offset:]) + list(catalog[:offset])
    return {row[0]: pair for row, pair in zip(ordered_rows, rotated)}


def sanitize_json(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: sanitize_json(item) for key, item in value.items()}
    if isinstance(value, list):
        return [sanitize_json(item) for item in value]
    if isinstance(value, str) and not re.fullmatch(r"[A-Z][A-Z0-9_:-]{0,49}", value):
        return "데모"
    return value


def validated_identifier(value: str) -> str:
    if not IDENTIFIER.fullmatch(value):
        raise SanitizationError(f"Unsafe database identifier: {value}")
    return value


def fetch_all(cursor: Any, query: str, params: Sequence[Any] = ()) -> list[tuple[Any, ...]]:
    cursor.execute(query, params)
    return list(cursor.fetchall())


def collect_original_sensitive_values(cursor: Any) -> set[str]:
    sources = (
        ("business_partners", "address"),
        ("business_partners", "memo"),
        ("business_partners", "name"),
        ("business_partners", "owner_name"),
        ("business_partners", "phone"),
        ("auction_lot_status_history", "worker"),
        ("auction_settlements", "confirmed_by"),
        ("inbound_records", "worker"),
        ("partner_payment_events", "created_by"),
        ("partner_payment_events", "depositor_name"),
        ("partner_payment_events", "description"),
        ("partner_payment_events", "memo"),
        ("partner_payment_events", "external_uid"),
        ("work_records", "worker"),
        ("orchid_group_collections", "created_by"),
        ("orchid_group_collection_members", "created_by"),
        ("work_operations", "worker"),
        ("work_target_executions", "worker"),
        ("work_applied_effects", "worker"),
        ("materials", "manufacturer"),
        ("materials", "storage_location"),
        ("materials", "usage"),
        ("auction_attempts", "memo"),
        ("auction_result_lines", "note"),
        ("auction_settlements", "memo"),
        ("auction_shipment_lots", "memo"),
        ("auction_shipments", "memo"),
        ("inbound_records", "memo"),
        ("orchid_groups", "memo"),
        ("sales_slips", "memo"),
        ("sales_slip_items", "memo"),
        ("work_records", "memo"),
        ("work_operations", "memo"),
        ("work_records", "cancel_reason"),
        ("work_operation_targets", "exclusion_reason"),
        ("work_operation_corrections", "reason"),
    )
    values: set[str] = set()
    safe_names = set(SAFE_WORK_TYPE_NAMES.values())
    for table, column in sources:
        query = f"SELECT DISTINCT {column}::text FROM {table} WHERE {column} IS NOT NULL"
        for (raw,) in fetch_all(cursor, query):
            value = raw.strip()
            if len(value) >= 2 and not SAFE_CODE.fullmatch(value) and value not in safe_names:
                values.add(value)
    for code, name in fetch_all(cursor, "SELECT code, name FROM work_types"):
        if SAFE_WORK_TYPE_NAMES.get(code) != name and len(name.strip()) >= 2:
            values.add(name.strip())
    return values


def catalog_without_original_varieties(
    cursor: Any, catalog: Sequence[CatalogPair], original_sensitive_values: set[str]
) -> list[CatalogPair]:
    """Exclude catalog names that could reproduce original source text.

    The catalog contains real auction-market names. A real catalog name can
    nevertheless be one used by this farm or one mentioned in free text, so
    reusing it would retain an original identifier in the demo data.
    """
    sources = (
        ("varieties", "name"),
        ("orchid_groups", "variety_name"),
        ("work_operation_targets", "variety_name_snapshot"),
        ("auction_shipment_lots", "variety_name"),
        ("sales_slip_items", "item_name"),
    )
    original_names = set(original_sensitive_values)
    for table, column in sources:
        query = (
            f"SELECT DISTINCT btrim({column}) FROM {table} "
            f"WHERE {column} IS NOT NULL AND btrim({column})<>''"
        )
        for (value,) in fetch_all(cursor, query):
            if len(value) >= 2:
                original_names.add(value)

    # assert_original_values_removed() intentionally uses substring matching,
    # so an original such as "드림" must also rule out "화이트 드림".
    filtered = [
        pair
        for pair in catalog
        if not any(original_name in pair.variety for original_name in original_names)
    ]
    if not filtered:
        raise SanitizationError("No catalog varieties remain after excluding source varieties")
    return filtered


def clear_sensitive_data(cursor: Any) -> None:
    statements = (
        "UPDATE auction_attempts SET memo=NULL, failed_reason=CASE WHEN failed_reason IS NULL THEN NULL ELSE '데모 사유' END",
        "UPDATE auction_lot_status_history SET memo=NULL, reason='데모 상태 변경'",
        "UPDATE auction_result_lines SET note=NULL",
        "UPDATE auction_settlements SET memo=NULL",
        "UPDATE auction_shipment_lots SET memo=NULL",
        "UPDATE auction_shipments SET memo=NULL",
        "UPDATE bed_zone_capacities SET memo=NULL",
        "UPDATE bed_zones SET memo=NULL",
        "UPDATE houses SET memo=NULL",
        "UPDATE inbound_records SET memo=NULL",
        "UPDATE orchid_groups SET memo=NULL",
        "UPDATE physical_beds SET memo=NULL",
        "UPDATE sales_inventory_movements SET memo=NULL",
        "UPDATE sales_slip_items SET memo=NULL",
        "UPDATE sales_slips SET memo=NULL",
        "UPDATE work_records SET memo=NULL, cancel_reason=CASE WHEN cancel_reason IS NULL THEN NULL ELSE '데모 취소 사유' END",
        "UPDATE work_operations SET memo=NULL",
        "UPDATE work_operation_targets SET exclusion_reason=CASE WHEN exclusion_reason IS NULL THEN NULL ELSE '데모 제외 사유' END",
        "UPDATE work_operation_corrections SET reason='데모 보정 사유'",
        "UPDATE partner_payment_events SET description=NULL, memo=NULL, external_uid=NULL, raw_payload='{}'::jsonb, match_payload='{}'::jsonb",
        "UPDATE partner_settlement_settings SET memo=NULL, depositor_aliases='[]'::jsonb",
        "UPDATE varieties SET description=NULL, memo=NULL",
        "UPDATE orchid_group_collections SET description=NULL, purpose=CASE WHEN purpose IS NULL THEN NULL ELSE '데모 묶음' END",
    )
    for statement in statements:
        cursor.execute(statement)


def anonymize_actors(cursor: Any, key: str) -> None:
    actors: set[str] = set()
    for table, column in ACTOR_COLUMNS:
        for (value,) in fetch_all(
            cursor, f"SELECT DISTINCT {column} FROM {table} WHERE {column} IS NOT NULL"
        ):
            if value.strip():
                actors.add(value)
    ordered = sorted(actors, key=lambda value: keyed_digest(key, "actor", value))
    mapping = {value: f"작업자 {index:03d}" for index, value in enumerate(ordered, 1)}
    for table, column in ACTOR_COLUMNS:
        cursor.executemany(
            f"UPDATE {table} SET {column}=%s WHERE {column}=%s",
            [(demo, original) for original, demo in mapping.items()],
        )


def anonymize_partners(cursor: Any, key: str) -> None:
    cursor.execute(
        """
        WITH numbered_partners AS (
          SELECT id,
                 row_number() OVER (
                   PARTITION BY partner_type
                   ORDER BY id
                 ) AS type_sequence
          FROM business_partners
        )
        UPDATE business_partners partner
        SET name=CASE partner.partner_type
              WHEN 'RETAIL' THEN
                '데모 소매 거래처 ' || lpad(numbered.type_sequence::text, 3, '0')
              WHEN 'WHOLESALE' THEN
                '데모 도매 거래처 ' || lpad(numbered.type_sequence::text, 3, '0')
              WHEN 'AUCTION_HOUSE' THEN
                '데모 경매장 ' || lpad(numbered.type_sequence::text, 3, '0')
            END,
            owner_name=CASE
              WHEN partner.owner_name IS NULL THEN NULL
              ELSE '데모 대표 ' || lpad(numbered.type_sequence::text, 3, '0')
            END,
            phone=CASE WHEN partner.phone IS NULL THEN NULL ELSE '010-0000-0000' END,
            address=CASE WHEN partner.address IS NULL THEN NULL ELSE '데모 주소' END,
            memo=NULL
        FROM numbered_partners numbered
        WHERE partner.id=numbered.id
        """
    )
    depositors = [
        row[0]
        for row in fetch_all(
            cursor,
            "SELECT DISTINCT depositor_name FROM partner_payment_events "
            "WHERE depositor_name IS NOT NULL AND btrim(depositor_name)<>''",
        )
    ]
    ordered = sorted(depositors, key=lambda value: keyed_digest(key, "depositor", value))
    cursor.executemany(
        "UPDATE partner_payment_events SET depositor_name=%s WHERE depositor_name=%s",
        [(f"입금자 {index:03d}", original) for index, original in enumerate(ordered, 1)],
    )


def shift_dates(cursor: Any, days: int) -> None:
    columns = fetch_all(
        cursor,
        """
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema='public'
          AND table_name<>'flyway_schema_history'
          AND data_type IN ('date', 'timestamp without time zone', 'timestamp with time zone')
        """,
    )
    table_columns: dict[str, list[str]] = {}
    for table, column in columns:
        table_columns.setdefault(validated_identifier(table), []).append(
            validated_identifier(column)
        )

    # A direct date shift can transiently collide with a non-deferrable unique
    # constraint (for example auction house + auction date). Move each table to
    # a distant intermediate range first, then apply the requested final shift.
    intermediate_days = 36_500
    for table, date_columns in table_columns.items():
        for offset in (intermediate_days, days - intermediate_days):
            assignments = ", ".join(
                f"{column}={column} + (%s * interval '1 day')"
                for column in date_columns
            )
            cursor.execute(
                f"UPDATE {table} SET {assignments}",
                tuple(offset for _ in date_columns),
            )
    cursor.execute(
        "UPDATE work_operations SET planned_end_date=planned_start_date "
        "WHERE planned_end_date<planned_start_date"
    )
    cursor.execute(
        "UPDATE work_operations SET actual_end_at=actual_start_at "
        "WHERE actual_end_at<actual_start_at"
    )


def transform_varieties(cursor: Any, key: str, catalog: Sequence[CatalogPair]) -> None:
    master_rows = [
        (int(row_id), genus, name)
        for row_id, genus, name in fetch_all(
            cursor, "SELECT id, genus, name FROM varieties ORDER BY id"
        )
    ]
    master_mapping = unique_catalog_mapping(master_rows, catalog, key)

    # Avoid transient collisions with the unique(genus, name) constraint.
    cursor.executemany(
        "UPDATE varieties SET genus=%s, name=%s, alias=NULL WHERE id=%s",
        [(f"임시속-{row_id}", f"임시품종-{row_id}", row_id) for row_id, _, _ in master_rows],
    )
    cursor.executemany(
        "UPDATE varieties SET genus=%s, name=%s WHERE id=%s",
        [(pair.item, pair.variety, row_id) for row_id, pair in master_mapping.items()],
    )

    group_rows = fetch_all(
        cursor, "SELECT id, variety_id, genus, variety_name FROM orchid_groups ORDER BY id"
    )
    group_updates = []
    for row_id, variety_id, genus, variety_name in group_rows:
        pair = master_mapping.get(variety_id)
        if pair is None:
            pair = catalog_pair_for(
                catalog, key, "orchid-group", f"{genus or ''}|{variety_name}"
            )
        group_updates.append((pair.item, pair.variety, row_id))
    cursor.executemany(
        "UPDATE orchid_groups SET genus=%s, variety_name=%s WHERE id=%s", group_updates
    )

    target_rows = fetch_all(
        cursor,
        "SELECT id, variety_id_snapshot, variety_name_snapshot "
        "FROM work_operation_targets ORDER BY id",
    )
    target_updates = []
    for row_id, variety_id, variety_name in target_rows:
        pair = master_mapping.get(variety_id)
        if pair is None:
            pair = catalog_pair_for(catalog, key, "work-target", variety_name)
        target_updates.append((pair.variety, row_id))
    cursor.executemany(
        "UPDATE work_operation_targets SET variety_name_snapshot=%s WHERE id=%s",
        target_updates,
    )

    for table, item_column, variety_column, namespace in (
        ("auction_shipment_lots", "item_name", "variety_name", "auction-lot"),
        ("sales_slip_items", "genus", "item_name", "sales-item"),
    ):
        rows = fetch_all(
            cursor,
            f"SELECT id, {item_column}, {variety_column} FROM {table} ORDER BY id",
        )
        updates = []
        for row_id, item, variety in rows:
            pair = catalog_pair_for(catalog, key, namespace, f"{item or ''}|{variety}")
            updates.append((pair.item, pair.variety, row_id))
        cursor.executemany(
            f"UPDATE {table} SET {item_column}=%s, {variety_column}=%s WHERE id=%s",
            updates,
        )


def transform_work_data(cursor: Any, key: str) -> None:
    cursor.execute(
        "UPDATE orchid_group_collections SET name='데모 묶음 ' || lpad(id::text, 3, '0')"
    )
    custom_types = fetch_all(cursor, "SELECT id, code, name FROM work_types")
    for row_id, code, original_name in custom_types:
        if SAFE_WORK_TYPE_NAMES.get(code) == original_name:
            continue
        demo_name = token(key, "work-type", original_name, "작업유형-")
        cursor.execute(
            "UPDATE work_records SET work_type=%s "
            "WHERE work_type_id=%s OR (work_type_id IS NULL AND work_type=%s)",
            (demo_name, row_id, original_name),
        )
        cursor.execute("UPDATE work_types SET name=%s WHERE id=%s", (demo_name, row_id))
    cursor.execute(
        "UPDATE work_operations SET title='데모 작업 ' || lpad(id::text, 4, '0'), "
        "request_key=CASE WHEN request_key IS NULL THEN NULL ELSE 'demo-request-' || id END"
    )
    material_rows = fetch_all(cursor, "SELECT id, name FROM materials")
    cursor.executemany(
        """
        UPDATE materials
        SET name=%s,
            manufacturer=CASE WHEN manufacturer IS NULL THEN NULL ELSE '데모 제조사' END,
            storage_location=CASE WHEN storage_location IS NULL THEN NULL ELSE '데모 보관 위치' END,
            usage=CASE WHEN usage IS NULL THEN NULL ELSE '데모 용도' END
        WHERE id=%s
        """,
        [(token(key, "material", name, "데모 자재-"), row_id) for row_id, name in material_rows],
    )
    cursor.execute(
        "UPDATE inbound_records SET temp_location=CASE WHEN temp_location IS NULL "
        "THEN NULL ELSE '데모 임시 위치' END"
    )
    cursor.execute("UPDATE sales_slips SET slip_number='DEMO-SALE-' || lpad(id::text, 8, '0')")
    work_materials = fetch_all(
        cursor,
        "SELECT id, material_name, quantity FROM work_records "
        "WHERE material_name IS NOT NULL OR quantity IS NOT NULL",
    )
    updates = []
    for row_id, material_name, quantity in work_materials:
        demo_material = (
            token(key, "material", material_name, "데모 자재-") if material_name else None
        )
        if quantity is None:
            demo_quantity = None
        elif re.fullmatch(r"[0-9]+", quantity):
            demo_quantity = quantity  # Scaled after an overflow-safe factor is selected.
        else:
            demo_quantity = "데모 수량"
        updates.append((demo_material, demo_quantity, row_id))
    cursor.executemany(
        "UPDATE work_records SET material_name=%s, quantity=%s WHERE id=%s", updates
    )


def scaling_fits(cursor: Any, quantity_factor: int, price_factor: int) -> bool:
    combined = quantity_factor * price_factor
    checks = (
        (
            "SELECT coalesce(max(abs(value::numeric)),0) FROM ("
            "SELECT bottle_count value FROM inbound_records UNION ALL "
            "SELECT estimated_quantity FROM inbound_records UNION ALL "
            "SELECT actual_quantity FROM inbound_records UNION ALL "
            "SELECT tray_count FROM inbound_records UNION ALL "
            "SELECT quantity FROM orchid_groups UNION ALL SELECT tray_count FROM orchid_groups UNION ALL "
            "SELECT reserved_quantity FROM orchid_groups UNION ALL "
            "SELECT source_quantity FROM orchid_group_lineage UNION ALL "
            "SELECT result_quantity FROM orchid_group_lineage UNION ALL "
            "SELECT quantity_snapshot FROM work_operation_targets UNION ALL "
            "SELECT processed_quantity FROM work_target_executions UNION ALL "
            "SELECT allocated_quantity FROM sales_slip_item_allocations UNION ALL "
            "SELECT quantity_delta FROM sales_inventory_movements UNION ALL "
            "SELECT quantity FROM sales_slip_items UNION ALL SELECT boxes FROM auction_shipment_lots UNION ALL "
            "SELECT returned_quantity FROM auction_shipment_lots UNION ALL "
            "SELECT shipped_quantity FROM auction_shipment_lots UNION ALL "
            "SELECT sold_quantity FROM auction_shipment_lots UNION ALL "
            "SELECT waiting_quantity FROM auction_shipment_lots UNION ALL "
            "SELECT quantity FROM auction_result_lines UNION ALL "
            "SELECT quantity FROM auction_settlement_lines"
            ") values",
            quantity_factor,
            INT_MAX,
        ),
        (
            "SELECT coalesce(max(abs(value::numeric)),0) FROM ("
            "SELECT unit_price value FROM sales_slip_items UNION ALL "
            "SELECT unit_price FROM auction_result_lines UNION ALL "
            "SELECT unit_price FROM auction_settlement_lines"
            ") values",
            price_factor,
            INT_MAX,
        ),
        (
            "SELECT coalesce(max(abs(value::numeric)),0) FROM ("
            "SELECT quantity::numeric * unit_price value FROM sales_slip_items UNION ALL "
            "SELECT quantity::numeric * unit_price FROM auction_result_lines UNION ALL "
            "SELECT paid_amount FROM sales_slips UNION ALL "
            "SELECT coalesce(sum(item.quantity::numeric * item.unit_price),0) "
            "FROM sales_slips slip LEFT JOIN sales_slip_items item ON item.sales_slip_id=slip.id "
            "GROUP BY slip.id"
            ") values",
            combined,
            INT_MAX,
        ),
        (
            "SELECT coalesce(max(abs(value::numeric)),0) FROM ("
            "SELECT quantity::numeric * unit_price value FROM auction_settlement_lines UNION ALL "
            "SELECT deduction_amount FROM auction_settlements UNION ALL "
            "SELECT fee_amount FROM auction_settlements UNION ALL "
            "SELECT paid_amount FROM auction_settlements UNION ALL "
            "SELECT amount FROM partner_payment_events UNION ALL "
            "SELECT unapplied_amount FROM partner_payment_events UNION ALL "
            "SELECT credit_balance FROM partner_balance_summaries UNION ALL "
            "SELECT receivable_balance FROM partner_balance_summaries UNION ALL "
            "SELECT unapplied_payment_amount FROM partner_balance_summaries UNION ALL "
            "SELECT amount_tolerance FROM partner_settlement_settings UNION ALL "
            "SELECT coalesce(sum(line.quantity::numeric * line.unit_price),0) "
            "FROM auction_settlements settlement "
            "LEFT JOIN auction_settlement_lines line ON line.settlement_id=settlement.id "
            "GROUP BY settlement.id"
            ") values",
            combined,
            BIGINT_MAX,
        ),
    )
    for query, factor, limit in checks:
        maximum = fetch_all(cursor, query)[0][0]
        if maximum * factor > limit:
            return False
    return True


def choose_scaling_factors(
    cursor: Any, requested_quantity: int, requested_price: int
) -> tuple[int, int]:
    candidates = [
        (quantity, price)
        for quantity in range(requested_quantity, 0, -1)
        for price in range(requested_price, 0, -1)
    ]
    candidates.sort(
        key=lambda pair: (
            pair[0] * pair[1],
            pair[0] + pair[1],
            pair[0],
        ),
        reverse=True,
    )
    for quantity, price in candidates:
        if scaling_fits(cursor, quantity, price):
            return quantity, price
    raise SanitizationError("Existing numeric data is already outside PostgreSQL integer ranges")


def scale_business_values(cursor: Any, quantity_factor: int, price_factor: int) -> None:
    combined = quantity_factor * price_factor
    quantity_statements = (
        "UPDATE inbound_records SET bottle_count=bottle_count*%s, estimated_quantity=estimated_quantity*%s, actual_quantity=actual_quantity*%s, tray_count=tray_count*%s",
        "UPDATE orchid_groups SET quantity=quantity*%s, tray_count=tray_count*%s, reserved_quantity=reserved_quantity*%s",
        "UPDATE orchid_group_lineage SET source_quantity=source_quantity*%s, result_quantity=result_quantity*%s",
        "UPDATE work_operation_targets SET quantity_snapshot=quantity_snapshot*%s",
        "UPDATE work_target_executions SET processed_quantity=processed_quantity*%s",
        "UPDATE sales_slip_item_allocations SET allocated_quantity=allocated_quantity*%s",
        "UPDATE sales_inventory_movements SET quantity_delta=quantity_delta*%s",
        "UPDATE auction_shipment_lots SET boxes=boxes*%s, returned_quantity=returned_quantity*%s, shipped_quantity=shipped_quantity*%s, sold_quantity=sold_quantity*%s, waiting_quantity=waiting_quantity*%s",
    )
    for statement in quantity_statements:
        cursor.execute(statement, tuple(quantity_factor for _ in range(statement.count("%s"))))

    numeric_work_records = fetch_all(
        cursor,
        "SELECT id, quantity FROM work_records WHERE quantity ~ '^[0-9]+$'",
    )
    cursor.executemany(
        "UPDATE work_records SET quantity=%s WHERE id=%s",
        [(str(int(value) * quantity_factor), row_id) for row_id, value in numeric_work_records],
    )

    cursor.execute(
        """
        UPDATE sales_slip_items
        SET quantity=quantity*%s,
            unit_price=unit_price*%s,
            amount=(quantity::bigint*unit_price*%s)::integer
        """,
        (quantity_factor, price_factor, combined),
    )
    cursor.execute(
        """
        UPDATE sales_slips slip
        SET total_amount=coalesce((
              SELECT sum(item.amount)::integer FROM sales_slip_items item
              WHERE item.sales_slip_id=slip.id
            ),0),
            paid_amount=coalesce(paid_amount,0)*%s
        """,
        (combined,),
    )
    cursor.execute(
        "UPDATE sales_slips SET remaining_amount=greatest(0,total_amount::bigint-paid_amount)"
    )
    cursor.execute(
        """
        UPDATE auction_result_lines
        SET quantity=quantity*%s,
            unit_price=unit_price*%s,
            amount=(quantity::bigint*unit_price*%s)::integer
        """,
        (quantity_factor, price_factor, combined),
    )
    cursor.execute(
        """
        UPDATE auction_settlement_lines
        SET quantity=quantity*%s,
            unit_price=unit_price*%s,
            amount=quantity::bigint*unit_price*%s
        """,
        (quantity_factor, price_factor, combined),
    )
    for statement in (
        "UPDATE auction_settlements SET deduction_amount=deduction_amount*%s, fee_amount=fee_amount*%s, paid_amount=paid_amount*%s",
        "UPDATE partner_payment_events SET amount=amount*%s, unapplied_amount=unapplied_amount*%s",
        "UPDATE partner_balance_summaries SET credit_balance=credit_balance*%s, receivable_balance=receivable_balance*%s, unapplied_payment_amount=unapplied_payment_amount*%s",
        "UPDATE partner_settlement_settings SET amount_tolerance=amount_tolerance*%s",
    ):
        cursor.execute(statement, tuple(combined for _ in range(statement.count("%s"))))
    cursor.execute(
        """
        UPDATE auction_settlements settlement
        SET gross_amount=coalesce((
          SELECT sum(line.amount) FROM auction_settlement_lines line
          WHERE line.settlement_id=settlement.id
        ),0)
        """
    )
    cursor.execute(
        "UPDATE auction_settlements SET expected_deposit_amount="
        "greatest(0,gross_amount-fee_amount-deduction_amount)"
    )
    cursor.execute(
        "UPDATE auction_settlements SET remaining_amount="
        "greatest(0,expected_deposit_amount-paid_amount)"
    )


def sanitize_json_columns(cursor: Any) -> None:
    for table, columns in JSON_COLUMNS.items():
        rows = fetch_all(cursor, f"SELECT id, {', '.join(columns)} FROM {table} ORDER BY id")
        if not rows:
            continue
        assignments = ", ".join(f"{column}=%s::jsonb" for column in columns)
        updates = []
        for row in rows:
            values = [
                json.dumps(sanitize_json(value), ensure_ascii=False) if value is not None else None
                for value in row[1:]
            ]
            updates.append((*values, row[0]))
        cursor.executemany(
            f"UPDATE {table} SET {assignments} WHERE id=%s",
            updates,
        )


def assert_original_values_removed(cursor: Any, originals: set[str]) -> None:
    columns = fetch_all(
        cursor,
        """
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema='public'
          AND table_name<>'flyway_schema_history'
          AND data_type IN ('character varying','text','json','jsonb')
        """,
    )
    for table, column in columns:
        table = validated_identifier(table)
        column = validated_identifier(column)
        for (raw,) in fetch_all(
            cursor, f"SELECT {column}::text FROM {table} WHERE {column} IS NOT NULL"
        ):
            matches = [value for value in originals if value in raw]
            if matches:
                sample = matches[0][:30]
                raise SanitizationError(
                    f"Original sensitive value remains in {table}.{column}: {sample!r}"
                )


def create_marker(cursor: Any) -> None:
    cursor.execute("CREATE SCHEMA demo_internal")
    cursor.execute("REVOKE ALL ON SCHEMA demo_internal FROM PUBLIC")
    cursor.execute(
        """
        CREATE TABLE demo_internal.sanitization_marker (
          singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
          sanitized_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
          pipeline_version integer NOT NULL
        )
        """
    )
    cursor.execute(
        "INSERT INTO demo_internal.sanitization_marker(pipeline_version) VALUES (2)"
    )


def validate_configuration() -> tuple[str, str, int, int]:
    database_url = required_env("SANITIZE_DB_URL")
    key = required_env("DEMO_ANONYMIZATION_KEY")
    if len(key) < 32:
        raise SanitizationError("DEMO_ANONYMIZATION_KEY must contain at least 32 characters")
    date_shift = parse_int_env("DEMO_DATE_SHIFT_DAYS", -100_000, 100_000)
    if date_shift == 0:
        raise SanitizationError("DEMO_DATE_SHIFT_DAYS must not be zero")
    quantity_factor = parse_int_env("DEMO_QUANTITY_FACTOR", 2, 9)
    price_factor = parse_int_env("DEMO_PRICE_FACTOR", 2, 9)
    parsed = urlparse(database_url)
    if parsed.path.lstrip("/") != EXPECTED_DATABASE:
        raise SanitizationError(f"SANITIZE_DB_URL must target {EXPECTED_DATABASE}")
    return database_url, key, date_shift, quantity_factor, price_factor


def run() -> None:
    database_url, key, date_shift, requested_quantity, requested_price = (
        validate_configuration()
    )
    catalog = load_catalog()
    driver = load_driver()

    with driver.connect(database_url) as connection:
        with connection.cursor() as cursor:
            current_database = fetch_all(cursor, "SELECT current_database()")[0][0]
            if current_database != EXPECTED_DATABASE:
                raise SanitizationError(
                    f"Connected to {current_database}, expected {EXPECTED_DATABASE}"
                )
            if fetch_all(
                cursor, "SELECT to_regclass('demo_internal.sanitization_marker')"
            )[0][0] is not None:
                raise SanitizationError(
                    "Temporary database is already sanitized; restore it from the source dump"
                )

            originals = collect_original_sensitive_values(cursor)
            catalog = catalog_without_original_varieties(cursor, catalog, originals)
            clear_sensitive_data(cursor)
            anonymize_actors(cursor, key)
            anonymize_partners(cursor, key)
            shift_dates(cursor, date_shift)
            transform_varieties(cursor, key, catalog)
            transform_work_data(cursor, key)
            quantity_factor, price_factor = choose_scaling_factors(
                cursor, requested_quantity, requested_price
            )
            scale_business_values(cursor, quantity_factor, price_factor)
            sanitize_json_columns(cursor)
            assert_original_values_removed(cursor, originals)
            create_marker(cursor)

    print(f"Sanitized with {len(catalog)} real item-variety pairs.")
    print(
        "Applied scaling factors: "
        f"quantity={quantity_factor}, price={price_factor}"
        + (
            " (reduced to stay within PostgreSQL integer ranges)"
            if (quantity_factor, price_factor) != (requested_quantity, requested_price)
            else ""
        )
    )


def main() -> int:
    try:
        run()
        return 0
    except SanitizationError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"[ERROR] Sanitization failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
