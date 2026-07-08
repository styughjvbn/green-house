#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate seed SQL files.

Policy:
- Public Flyway migration output contains only non-sensitive base master data.
- Real sales/auction-derived data is generated as private SQL without Flyway version prefixes.

Recommended repo layout:
  backend/src/main/resources/db/migration/
    V1__initial_schema.sql
    V2__seed_base_master_data.sql

  scripts/seed/generated-private/   # .gitignore
    seed_varieties_from_sales_sources.sql
    seed_business_partners.sql
    seed_auction_2025_2026.sql
    seed_direct_sales_2026.sql
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


PRIVATE_SQL_FILES = [
    "seed_varieties_from_sales_sources.sql",
    "seed_business_partners.sql",
    "seed_auction_2025_2026.sql",
    "seed_direct_sales_2026.sql",
]


def run(args: list[str]) -> None:
    print("$", " ".join(args))
    subprocess.run(args, check=True)


def write_apply_scripts(private_output_dir: Path) -> None:
    """Write small helper scripts for applying private seeds outside Flyway."""
    ps1 = private_output_dir / "apply_private_seeds.ps1"
    sh = private_output_dir / "apply_private_seeds.sh"

    ps1.write_text(
        """# Apply private seed SQL files outside Flyway.\n"
        "# Usage from project root:\n"
        "#   powershell -ExecutionPolicy Bypass -File scripts/seed/generated-private/apply_private_seeds.ps1\n"
        "\n"
        "$ErrorActionPreference = 'Stop'\n"
        "$DbUser = $env:GREENHOUSE_DB_USER\n"
        "$DbName = $env:GREENHOUSE_DB_NAME\n"
        "if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = 'greenhouse' }\n"
        "if ([string]::IsNullOrWhiteSpace($DbName)) { $DbName = 'greenhouse' }\n"
        "$Dir = Split-Path -Parent $MyInvocation.MyCommand.Path\n"
        "$Files = @(\n"
        "  'seed_varieties_from_sales_sources.sql',\n"
        "  'seed_business_partners.sql',\n"
        "  'seed_auction_2025_2026.sql',\n"
        "  'seed_direct_sales_2026.sql'\n"
        ")\n"
        "foreach ($File in $Files) {\n"
        "  $Path = Join-Path $Dir $File\n"
        "  Write-Host \"Applying $Path\"\n"
        "  Get-Content $Path | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U $DbUser -d $DbName\n"
        "}\n"
        """,
        encoding="utf-8",
    )

    sh.write_text(
        """#!/usr/bin/env bash\n"
        "set -euo pipefail\n"
        "\n"
        "# Apply private seed SQL files outside Flyway.\n"
        "# Usage from project root:\n"
        "#   bash scripts/seed/generated-private/apply_private_seeds.sh\n"
        "\n"
        "DB_USER=\"${GREENHOUSE_DB_USER:-greenhouse}\"\n"
        "DB_NAME=\"${GREENHOUSE_DB_NAME:-greenhouse}\"\n"
        "DIR=\"$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)\"\n"
        "FILES=(\n"
        "  seed_varieties_from_sales_sources.sql\n"
        "  seed_business_partners.sql\n"
        "  seed_auction_2025_2026.sql\n"
        "  seed_direct_sales_2026.sql\n"
        ")\n"
        "\n"
        "for file in \"${FILES[@]}\"; do\n"
        "  echo \"Applying ${DIR}/${file}\"\n"
        "  docker compose exec -T db psql -v ON_ERROR_STOP=1 -U \"${DB_USER}\" -d \"${DB_NAME}\" < \"${DIR}/${file}\"\n"
        "done\n"
        """,
        encoding="utf-8",
    )
    sh.chmod(0o755)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Generate public Flyway base seed and private sales/auction seed SQL files. "
            "Private files are intentionally not versioned as Flyway migrations."
        )
    )
    parser.add_argument("--auction-csv", required=True, type=Path, help="2026 내수 출하 CSV")
    parser.add_argument("--direct-csv", required=True, type=Path, help="2026 일반 판매 CSV")
    parser.add_argument(
        "--migration-output-dir",
        type=Path,
        default=Path("backend/src/main/resources/db/migration"),
        help="Public Flyway migration output directory. Only V2 base master seed is generated here.",
    )
    parser.add_argument(
        "--private-output-dir",
        type=Path,
        default=Path("scripts/seed/generated-private"),
        help="Private output directory for real sales/auction-derived seed SQL. Add this to .gitignore.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Deprecated. Use --private-output-dir. Kept only for old command compatibility.",
    )
    parser.add_argument("--skip-base-master", action="store_true", help="Do not generate V2__seed_base_master_data.sql")
    parser.add_argument("--dev-reset", action="store_true", help="Emit destructive reset clauses for local reruns of private seeds")
    parser.add_argument("--no-apply-scripts", action="store_true", help="Do not write apply_private_seeds helper scripts")
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    migration_output_dir = args.migration_output_dir
    private_output_dir = args.output_dir or args.private_output_dir

    migration_output_dir.mkdir(parents=True, exist_ok=True)
    private_output_dir.mkdir(parents=True, exist_ok=True)

    # Public Flyway seed: safe to commit.
    base_sql = migration_output_dir / "V2__seed_base_master_data.sql"

    # Private SQL files: do not put in Flyway migration folder; do not commit real data.
    varieties_sql = private_output_dir / "seed_varieties_from_sales_sources.sql"
    business_sql = private_output_dir / "seed_business_partners.sql"
    auction_sql = private_output_dir / "seed_auction_2025_2026.sql"
    direct_sql = private_output_dir / "seed_direct_sales_2026.sql"
    varieties_review = private_output_dir / "varieties_seed_review_required.csv"
    auction_review = private_output_dir / "auction_seed_review_required.csv"
    direct_review = private_output_dir / "direct_sales_seed_review_required.csv"

    if not args.skip_base_master:
        run([
            sys.executable,
            str(script_dir / "generate_base_master_seed.py"),
            "--output",
            str(base_sql),
        ])

    varieties_cmd = [
        sys.executable,
        str(script_dir / "generate_varieties_seed.py"),
        "--auction-csv",
        str(args.auction_csv),
        "--direct-csv",
        str(args.direct_csv),
        "--output",
        str(varieties_sql),
        "--review",
        str(varieties_review),
    ]
    if args.dev_reset:
        varieties_cmd.append("--delete-existing")
    run(varieties_cmd)

    run([
        sys.executable,
        str(script_dir / "generate_business_partners_seed.py"),
        "--auction-csv",
        str(args.auction_csv),
        "--direct-csv",
        str(args.direct_csv),
        "--output",
        str(business_sql),
    ])

    auction_cmd = [
        sys.executable,
        str(script_dir / "generate_auction_seed.py"),
        "--input",
        str(args.auction_csv),
        "--output",
        str(auction_sql),
        "--review",
        str(auction_review),
        "--no-ensure-partners",
    ]
    if args.dev_reset:
        auction_cmd.append("--truncate")
    run(auction_cmd)

    direct_cmd = [
        sys.executable,
        str(script_dir / "generate_direct_sales_seed.py"),
        "--input",
        str(args.direct_csv),
        "--output",
        str(direct_sql),
        "--review",
        str(direct_review),
        "--no-ensure-partners",
    ]
    if args.dev_reset:
        direct_cmd.append("--delete-existing")
    run(direct_cmd)

    if not args.no_apply_scripts:
        write_apply_scripts(private_output_dir)

    print("\nPublic Flyway seed files:")
    if args.skip_base_master:
        print("- skipped base master seed")
    else:
        print("-", base_sql)

    print("\nPrivate seed files. Do not commit these if they contain real data:")
    print("-", varieties_sql)
    print("-", business_sql)
    print("-", auction_sql)
    print("-", direct_sql)

    print("\nReview files. Do not commit these if they contain real data:")
    print("-", varieties_review)
    print("-", auction_review)
    print("-", direct_review)

    if not args.no_apply_scripts:
        print("\nApply helpers:")
        print("-", private_output_dir / "apply_private_seeds.ps1")
        print("-", private_output_dir / "apply_private_seeds.sh")


if __name__ == "__main__":
    main()
