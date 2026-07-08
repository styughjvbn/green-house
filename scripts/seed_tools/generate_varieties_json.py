#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extract initial varieties from source CSV files into an editable JSON file."""

from __future__ import annotations

import argparse
from pathlib import Path

from generate_varieties_seed import (
    collect_varieties_from_sources,
    write_review_csv,
    write_varieties_json,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract varieties from sales/auction sources into editable JSON.")
    parser.add_argument("--auction-csv", required=True, type=Path, help="2026 내수 출하 CSV")
    parser.add_argument("--direct-csv", required=True, type=Path, help="2026 일반 판매 CSV")
    parser.add_argument("--output", required=True, type=Path, help="Output varieties JSON path")
    parser.add_argument("--review", type=Path, default=Path("varieties_seed_review_required.csv"), help="Review CSV path")
    args = parser.parse_args()

    varieties, issues = collect_varieties_from_sources(args.auction_csv, args.direct_csv)
    write_varieties_json(args.output, varieties, [args.auction_csv.name, args.direct_csv.name])
    write_review_csv(args.review, issues)

    print("Generated JSON:", args.output)
    print("Review CSV:", args.review)
    print("Varieties:", len(varieties))
    print("Review issues:", len(issues))


if __name__ == "__main__":
    main()
