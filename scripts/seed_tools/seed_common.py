#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Shared helpers for farm seed SQL generators."""

from __future__ import annotations

import csv
import re
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable, Optional


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).replace("\ufeff", "").replace("\u00a0", " ").strip()
    return re.sub(r"\s+", " ", text)


def nullable_text(value: Any) -> Optional[str]:
    text = clean_text(value)
    return text if text else None


def read_csv_dicts(path: Path) -> list[dict[str, str]]:
    last_error: Optional[Exception] = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            with path.open("r", encoding=encoding, newline="") as f:
                reader = csv.DictReader(f)
                rows: list[dict[str, str]] = []
                for raw in reader:
                    sanitized = {
                        clean_text(k): clean_text(v)
                        for k, v in raw.items()
                        if clean_text(k)
                    }
                    if any(sanitized.values()):
                        rows.append(sanitized)
                return rows
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"CSV encoding must be UTF-8/CP949/EUC-KR: {last_error}")


def row_value(row: dict[str, str], *aliases: str) -> str:
    for alias in aliases:
        for key, value in row.items():
            if clean_text(key).casefold() == alias.casefold():
                return clean_text(value)
    return ""


def parse_date(value: Any) -> date:
    text = clean_text(value).replace(".", "-").replace("/", "-")
    text = re.sub(r"\s+", "", text)
    if not text:
        raise ValueError("empty date")
    return datetime.strptime(text, "%Y-%m-%d").date()


def parse_optional_int(value: Any) -> Optional[int]:
    text = clean_text(value)
    if not text:
        return None
    text = text.replace(",", "")
    if re.fullmatch(r"-?\d+\.0", text):
        text = text[:-2]
    if not re.fullmatch(r"-?\d+", text):
        raise ValueError(f"invalid integer: {value!r}")
    return int(text)


def parse_required_int(value: Any) -> int:
    parsed = parse_optional_int(value)
    if parsed is None:
        raise ValueError("empty integer")
    return parsed


def sql_literal(value: Any) -> str:
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
    return "'" + str(value).replace("'", "''") + "'"


def values_rows(rows: Iterable[tuple[Any, ...]], indent: str = "  ") -> str:
    return ",\n".join(
        indent + "(" + ", ".join(sql_literal(v) for v in row) + ")"
        for row in rows
    )


def normalize_market(value: Any) -> str:
    text = clean_text(value)
    aliases = {
        "음성공판장": "음성",
        "양재공판장": "양재",
        "고양공판장": "고양",
        "부산공판장": "부산",
    }
    return aliases.get(text, text)


def partner_type_for_direct_sale(name: str) -> str:
    # 현재 CSV 기준 정책: 일반인은 소매, 나머지는 도매 거래처로 본다.
    return "RETAIL" if clean_text(name) == "일반인" else "WHOLESALE"


@dataclass(frozen=True)
class SeedPartner:
    name: str
    partner_type: str
    memo: str
