#!/usr/bin/env python3
"""Split docs/api/openapi.yaml into lightweight Codex slices.

Run from repository root:
    python scripts/split_openapi_slices.py
"""
from pathlib import Path
from collections import OrderedDict
import yaml

OPENAPI_PATH = Path("docs/api/openapi.yaml")
SLICE_DIR = Path("docs/api/slices")

DOMAIN_MAP = OrderedDict([
    ("farm-structure", ["farm-structure-controller"]),
    ("farm-status", ["farm-status-controller", "dashboard-controller"]),
    ("orchid-command", ["orchid-group-command-controller", "bed-placement-controller"]),
    ("work", ["work-record-controller"]),
    ("partner", ["business-partner-controller", "partner-settlement-settings-controller"]),
    ("sales", ["sales-controller"]),
    ("auction", ["auction-tracking-controller", "auction-settlement-controller"]),
    ("payment", ["payment-controller"]),
])

def ref_name(ref: str) -> str:
    return ref.split("/")[-1]

def collect_refs(obj, refs=None):
    if refs is None:
        refs = set()
    if isinstance(obj, dict):
        ref = obj.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/schemas/"):
            refs.add(ref_name(ref))
        for value in obj.values():
            collect_refs(value, refs)
    elif isinstance(obj, list):
        for value in obj:
            collect_refs(value, refs)
    return refs

def schema_closure(schemas, seed_refs):
    all_refs = set(seed_refs)
    changed = True
    while changed:
        changed = False
        for name in list(all_refs):
            schema = schemas.get(name)
            if not schema:
                continue
            nested = collect_refs(schema)
            if not nested.issubset(all_refs):
                all_refs |= nested
                changed = True
    return all_refs

def main():
    openapi = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    schemas = openapi.get("components", {}).get("schemas", {})
    SLICE_DIR.mkdir(parents=True, exist_ok=True)

    for slice_name, tags in DOMAIN_MAP.items():
        selected_paths = OrderedDict()
        seed_refs = set()

        for path, operations in openapi.get("paths", {}).items():
            selected_operations = OrderedDict()
            for method, operation in operations.items():
                if method == "$ref" or method.startswith("x-"):
                    continue
                if any(tag in operation.get("tags", []) for tag in tags):
                    selected_operations[method] = operation
                    collect_refs(operation, seed_refs)
            if selected_operations:
                selected_paths[path] = dict(selected_operations)

        refs = schema_closure(schemas, seed_refs)
        slice_doc = {
            "openapi": openapi.get("openapi"),
            "info": {
                "title": f"Greenhouse API Slice - {slice_name}",
                "version": openapi.get("info", {}).get("version", "v0"),
            },
            "servers": openapi.get("servers", []),
            "paths": selected_paths,
            "components": {
                "schemas": {name: schemas[name] for name in sorted(refs) if name in schemas}
            },
        }

        out = SLICE_DIR / f"{slice_name}.openapi.yaml"
        out.write_text(
            yaml.safe_dump(slice_doc, allow_unicode=True, sort_keys=False),
            encoding="utf-8",
        )

if __name__ == "__main__":
    main()
