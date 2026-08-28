#!/usr/bin/env python3
"""Normalize the profiler output into the engine state-chain import contract.

This is a cutover-only data tool. It never infers legacy causes again. It only:
- removes representation-only Flyway changes,
- converts snapshots to the current canonical field names,
- rebuilds BASELINE/CREATE/CHANGE/DELETE revisions from the profiler order, and
- validates the resulting complete chain.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo


FARM_TIME_ZONE = ZoneInfo("Asia/Seoul")
INVALID_POT_CODES = {None, "UNMAPPED"}
MUTATION_TYPE_MAP = {
    "CHANGE": "UPDATE_DETAILS",
    "CORRECTION": "CORRECTION",
    "DELETE": "DELETE",
    "DISCARD": "DISCARD",
    "MOVE": "MOVE",
    "MULTI_CREATE": "CREATE",
    "TRANSFORM": "TRANSFORM",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def instant(value: str) -> str:
    normalized = value.strip()
    if "." in normalized:
        prefix, suffix = normalized.split(".", 1)
        zone_index = next((index for index, char in enumerate(suffix) if char in "+-Z"), len(suffix))
        fraction = suffix[:zone_index].ljust(6, "0")[:6]
        normalized = prefix + "." + fraction + suffix[zone_index:]
    if normalized.endswith("Z") or "+" in normalized[10:]:
        parsed = datetime.fromisoformat(normalized.replace("Z", "+00:00"))
    else:
        parsed = datetime.fromisoformat(normalized).replace(tzinfo=ZoneInfo("UTC"))
    return parsed.isoformat(timespec="microseconds").replace("+00:00", "Z")


def business_date(value: str) -> str:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.astimezone(FARM_TIME_ZONE).date().isoformat()


def group_entries(mutations: list[dict]) -> dict[str, list[tuple[dict, dict]]]:
    result: dict[str, list[tuple[dict, dict]]] = defaultdict(list)
    for mutation in mutations:
        for entry in mutation["entries"]:
            result[entry["orchid_group_id"]].append((mutation, entry))
    for entries in result.values():
        entries.sort(key=lambda item: item[1]["revision_after"])
    return result


def canonical_pot_codes(mutations: list[dict]) -> dict[str, list[str | None]]:
    result: dict[str, list[str | None]] = {}
    for group_id, entries in group_entries(mutations).items():
        snapshots = []
        for _, entry in entries:
            snapshots.extend([entry.get("before_state"), entry.get("after_state")])
        codes = [snapshot.get("potSizeCode") if snapshot else None for snapshot in snapshots]
        canonical = []
        for index, code in enumerate(codes):
            if code not in INVALID_POT_CODES:
                canonical.append(code)
                continue
            replacement = next(
                (candidate for candidate in codes[index + 1 :] if candidate not in INVALID_POT_CODES),
                None,
            )
            if replacement is None:
                replacement = next(
                    (candidate for candidate in reversed(codes[:index]) if candidate not in INVALID_POT_CODES),
                    "UNSPECIFIED",
                )
            canonical.append(replacement)
        result[group_id] = canonical
    return result


def normalize_snapshot(snapshot: dict | None, pot_code: str | None) -> dict | None:
    if snapshot is None:
        return None
    normalized = copy.deepcopy(snapshot)
    normalized["ageYear"] = normalized.pop("initialAgeYear", None)
    normalized.pop("exists", None)
    normalized["potSizeCode"] = pot_code
    return normalized


def normalize_manifest(source: dict, source_fingerprint: str) -> dict:
    if source.get("manifest_schema_version") != 1:
        raise ValueError("지원하지 않는 profiler manifest schema입니다.")
    if source.get("generated_from") != "orchid_state_chain_profiler":
        raise ValueError("orchid_state_chain_profiler 산출물만 정규화할 수 있습니다.")
    if not source.get("migration_ready") or source.get("blocking_issues"):
        raise ValueError("migration_ready profiler manifest만 정규화할 수 있습니다.")

    source_mutations = source["mutations"]
    pot_codes = canonical_pot_codes(source_mutations)
    pot_indexes: dict[str, int] = defaultdict(int)
    normalized_mutations = []
    source_to_normalized: dict[int, dict] = {}

    for mutation in source_mutations:
        for entry in mutation["entries"]:
            group_id = entry["orchid_group_id"]
            before_index = pot_indexes[group_id]
            after_index = before_index + 1
            entry["_normalized_before_state"] = normalize_snapshot(
                entry.get("before_state"), pot_codes[group_id][before_index]
            )
            entry["_normalized_after_state"] = normalize_snapshot(
                entry.get("after_state"), pot_codes[group_id][after_index]
            )
            pot_indexes[group_id] += 2

        if mutation["source_type"] == "FLYWAY":
            continue

        occurred_at = instant(mutation["event_time"])
        source_type = mutation["source_type"]
        mutation_kind = mutation["mutation_kind"]
        if mutation_kind == "ORIGIN":
            mutation_type = (
                "BASELINE_IMPORT"
                if source_type == "EARLIEST_TRUSTWORTHY_BASELINE"
                else "CREATE"
            )
        else:
            mutation_type = MUTATION_TYPE_MAP[mutation_kind]
        normalized = {
            "mutation_key": mutation["mutation_key"],
            "mutation_type": mutation_type,
            "source_type": source_type,
            "source_reference": mutation["source_reference"],
            "occurred_at": occurred_at,
            "effective_business_date": business_date(occurred_at),
            "reason": mutation.get("evidence", {}).get("match_reason"),
            "evidence": copy.deepcopy(mutation.get("evidence", {})),
            "entries": [],
        }
        normalized_mutations.append(normalized)
        source_to_normalized[id(mutation)] = normalized

    for group_id, entries in group_entries(source_mutations).items():
        retained = [(mutation, entry) for mutation, entry in entries if mutation["source_type"] != "FLYWAY"]
        previous_state = None
        previous_revision = None
        for index, (mutation, entry) in enumerate(retained):
            target = source_to_normalized[id(mutation)]
            after_state = entry["_normalized_after_state"]
            if index == 0:
                if mutation["source_type"] == "EARLIEST_TRUSTWORTHY_BASELINE":
                    entry_kind = "BASELINE"
                    revision_after = 0
                    entry_role = "AFFECTED"
                else:
                    entry_kind = "CREATE"
                    revision_after = 1
                    entry_role = entry["role"]
                revision_before = None
                before_state = None
            else:
                revision_before = previous_revision
                revision_after = previous_revision + 1
                before_state = previous_state
                entry_kind = "DELETE" if mutation["mutation_kind"] == "DELETE" else "CHANGE"
                entry_role = entry["role"]

            if entry_kind == "DELETE":
                after_state = None
            normalized_entry = {
                "orchid_group_id": group_id,
                "entry_kind": entry_kind,
                "role": entry_role,
                "revision_before": revision_before,
                "revision_after": revision_after,
                "before_state": before_state,
                "after_state": after_state,
            }
            target["entries"].append(normalized_entry)
            previous_revision = revision_after
            previous_state = after_state

    normalized_mutations.sort(key=lambda mutation: mutation["mutation_key"])
    for mutation in normalized_mutations:
        mutation["entries"].sort(key=lambda entry: (int(entry["orchid_group_id"]), entry["role"]))

    result = {
        "manifest_schema_version": 2,
        "generated_from": "orchid_state_chain_manifest_normalizer",
        "migration_ready": True,
        "blocking_issues": [],
        "provenance": {
            **copy.deepcopy(source.get("provenance", {})),
            "normalized_from_sha256": source_fingerprint,
            "normalizer_version": "1.0.0",
        },
        "operational_profile": copy.deepcopy(source.get("operational_profile", {})),
        "mutations": normalized_mutations,
    }
    validate_manifest(result)
    return result


def validate_manifest(manifest: dict) -> None:
    mutations = manifest["mutations"]
    mutation_keys = [mutation["mutation_key"] for mutation in mutations]
    if len(mutation_keys) != len(set(mutation_keys)):
        raise ValueError("mutation_key가 중복됩니다.")

    entries_by_group: dict[str, list[dict]] = defaultdict(list)
    seen_mutation_groups = set()
    for mutation in mutations:
        if not mutation["entries"]:
            raise ValueError(f"Entry가 없는 Mutation입니다: {mutation['mutation_key']}")
        for entry in mutation["entries"]:
            identity = (mutation["mutation_key"], entry["orchid_group_id"])
            if identity in seen_mutation_groups:
                raise ValueError(f"같은 Mutation에 난 묶음이 중복됩니다: {identity}")
            seen_mutation_groups.add(identity)
            entries_by_group[entry["orchid_group_id"]].append(entry)

    existing_count = 0
    deleted_count = 0
    for group_id, entries in entries_by_group.items():
        entries.sort(key=lambda entry: entry["revision_after"])
        previous = None
        for index, entry in enumerate(entries):
            if index == 0:
                expected_after = 0 if entry["entry_kind"] == "BASELINE" else 1
                if entry["revision_before"] is not None or entry["revision_after"] != expected_after:
                    raise ValueError(f"최초 revision이 올바르지 않습니다: {group_id}")
                if entry["before_state"] is not None or entry["after_state"] is None:
                    raise ValueError(f"최초 snapshot이 올바르지 않습니다: {group_id}")
            else:
                if entry["revision_before"] != previous["revision_after"]:
                    raise ValueError(f"revision gap이 있습니다: {group_id}")
                if entry["revision_after"] != entry["revision_before"] + 1:
                    raise ValueError(f"revision 증가가 올바르지 않습니다: {group_id}")
                if entry["before_state"] != previous["after_state"]:
                    raise ValueError(f"snapshot chain이 불연속입니다: {group_id}")
            if entry["entry_kind"] == "DELETE" and index != len(entries) - 1:
                raise ValueError(f"DELETE는 terminal Entry여야 합니다: {group_id}")
            if entry["entry_kind"] == "DELETE" and entry["after_state"] is not None:
                raise ValueError(f"DELETE after_state는 null이어야 합니다: {group_id}")
            for snapshot in (entry.get("before_state"), entry.get("after_state")):
                if snapshot and snapshot.get("potSizeCode") in INVALID_POT_CODES:
                    raise ValueError(f"canonical potSizeCode가 아닙니다: {group_id}")
            previous = entry
        if entries[-1]["entry_kind"] == "DELETE":
            deleted_count += 1
        else:
            existing_count += 1

    expected_count = manifest.get("operational_profile", {}).get("anchor", {}).get(
        "expected_orchid_group_count"
    )
    if expected_count is not None and existing_count != expected_count:
        raise ValueError(
            f"최종 현존 난 묶음 수가 다릅니다: expected={expected_count} actual={existing_count}"
        )
    if deleted_count == 0:
        raise ValueError("terminal DELETE 검증 대상이 없습니다.")


def main() -> None:
    args = parse_args()
    source_bytes = args.input.read_bytes()
    source = json.loads(source_bytes)
    normalized = normalize_manifest(source, sha256_bytes(source_bytes))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(normalized, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(sha256_bytes(args.output.read_bytes()))


if __name__ == "__main__":
    main()
