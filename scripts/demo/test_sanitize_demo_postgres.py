"""Opt-in end-to-end tests using an isolated disposable PostgreSQL container."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import time
import unittest
from unittest.mock import patch
import uuid

import sanitize_demo


@unittest.skipUnless(os.environ.get("DEMO_SANITIZE_POSTGRES_TEST") == "1", "opt-in Docker test")
class SanitizationPostgresTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        import psycopg

        cls.container = "demo-sanitize-test-" + uuid.uuid4().hex[:12]
        cls.password = uuid.uuid4().hex
        cls.driver = psycopg
        subprocess.run([
            "docker", "run", "--detach", "--name", cls.container,
            "--publish", "127.0.0.1::5432",
            "--env", "POSTGRES_DB=" + sanitize_demo.EXPECTED_DATABASE,
            "--env", "POSTGRES_PASSWORD=" + cls.password,
            "postgres:14-alpine",
        ], check=True, capture_output=True)
        cls.addClassCleanup(subprocess.run, ["docker", "rm", "--force", cls.container],
                            check=True, capture_output=True)
        address = subprocess.check_output([
            "docker", "port", cls.container, "5432/tcp",
        ], text=True).strip()
        cls.url = f"postgresql://postgres:{cls.password}@{address}/{sanitize_demo.EXPECTED_DATABASE}"
        deadline = time.monotonic() + 60
        while True:
            try:
                with psycopg.connect(cls.url):
                    break
            except psycopg.OperationalError:
                if time.monotonic() >= deadline:
                    raise RuntimeError("Isolated test PostgreSQL did not start") from None
                time.sleep(0.5)
        root = Path(__file__).resolve().parents[2]
        migrations = sorted((root / "backend/src/main/resources/db/migration").glob("V*.sql"),
                            key=lambda path: int(path.name.split("__")[0][1:]))
        with psycopg.connect(cls.url) as connection:
            for migration in migrations:
                with connection.transaction():
                    connection.execute(migration.read_text())
            # No Flyway process is used in this fixture. The SQL validators only
            # require this table's presence; production scripts still validate
            # real Flyway history/checksums before sanitizing.
            connection.execute("CREATE TABLE flyway_schema_history (version varchar(50))")

    def test_latest_schema_and_complete_history_sanitization(self) -> None:
        with self.driver.connect(self.url) as connection:
            connection.execute("""
                INSERT INTO varieties (id, code, genus, name, sale_enabled, is_active, created_at, updated_at)
                VALUES (9001, 'VAR-DEMO-TEST', '개인속이름', '개인품종이름', true, true, now(), now());
                INSERT INTO inbound_records (id, variety_id, inbound_date, inbound_type, status,
                    estimated_quantity, worker, memo, created_at, updated_at)
                VALUES (9001, 9001, '2026-09-02', 'BOTTLE', 'POTTING_PENDING', 200,
                    '개인작업자', '입고개인메모', now(), now());
                INSERT INTO orchid_groups (id, bed_zone_id, variety_id, genus, variety_name, quantity,
                    reserved_quantity, sort_order, status, pot_size_code, version, state_revision, created_at, updated_at)
                VALUES (348, (SELECT min(id) FROM bed_zones), 9001, '개인속이름', '개인품종이름',
                    120, 0, 1, '정상', 'POT_3', 0, 2, now(), now());
                INSERT INTO orchid_group_ledger_coverages (cutover_key, status, engine_schema_version,
                    snapshot_schema_version, effective_business_date, baseline_group_count,
                    baseline_fingerprint, import_fingerprint, minimum_writer_version)
                VALUES ('00000000-0000-0000-0000-000000000001', 'ACTIVE', 1, 1, '2026-09-01', 1,
                    repeat('a',64), repeat('b',64), '2.0.0');
                INSERT INTO orchid_group_mutations (id, mutation_type, source_domain, source_type,
                    source_reference_id, source_operation_key, correlation_id, command_fingerprint,
                    recorded_at, effective_business_date, reason, schema_version)
                VALUES (901, 'BASELINE_IMPORT', 'MIGRATION', 'BASELINE',
                    '00000000-0000-0000-0000-000000000001', 'baseline',
                    '00000000-0000-0000-0000-000000000011', repeat('c',64), now(), '2026-09-01', '원장개인사유', 1),
                    (902, 'CORRECTION', 'WORK', 'CORRECTION', '447', 'correction',
                    '00000000-0000-0000-0000-000000000012', repeat('d',64), now(), '2026-09-02', '보정개인사유', 1),
                    (903, 'STOCK_COUNT', 'FARM', 'STOCK_COUNT', '348', 'stock',
                    '00000000-0000-0000-0000-000000000013', repeat('e',64), now(), '2026-09-02', '실사개인사유', 1);
                INSERT INTO orchid_group_mutation_entries (mutation_id, orchid_group_id, entry_kind, role,
                    state_revision_before, state_revision_after, before_state, after_state)
                SELECT 901, id, 'BASELINE', 'AFFECTED', NULL, 0, NULL, jsonb_build_object(
                    'quantity',100,'reservedQuantity',0,'status',status,'varietyId',variety_id,
                    'genus',genus,'varietyName',variety_name,'memo',NULL) FROM orchid_groups;
                INSERT INTO orchid_group_mutation_entries (mutation_id, orchid_group_id, entry_kind, role,
                    state_revision_before, state_revision_after, before_state, after_state)
                SELECT 902, orchid_group_id, 'CHANGE', 'AFFECTED', 0, 1, after_state,
                    jsonb_set(after_state, '{quantity}', '120') FROM orchid_group_mutation_entries WHERE mutation_id=901;
                INSERT INTO orchid_group_mutation_entries (mutation_id, orchid_group_id, entry_kind, role,
                    state_revision_before, state_revision_after, before_state, after_state)
                SELECT 903, orchid_group_id, 'CHANGE', 'AFFECTED', 1, 2, after_state, after_state
                    FROM orchid_group_mutation_entries WHERE mutation_id=902;
                INSERT INTO work_operations (id, work_type_id, title, status, planned_start_date,
                    source_scope_type, target_snapshot_at, details, worker, memo, void_reason, version, created_at, updated_at)
                SELECT 447, id, '개인작업제목', 'VOIDED', '2026-09-02', 'NONE', now(),
                    '{"workDate":"2026-09-02","inputQuantity":100,"lossQuantity":10}',
                    '개인작업자', '작업개인메모', '취소개인사유', 0, now(), now()
                    FROM work_types WHERE code='MEMO';
                INSERT INTO work_operation_corrections (id, original_work_operation_id, reason,
                    worker, memo, result_details, mutation_id, correlation_id, created_at)
                VALUES (900, 447, '보정개인사유', '개인작업자', '보정개인메모',
                    '{"beforeWorkDate":"2026-09-01","afterWorkDate":"2026-09-02",
                      "adjustments":[{"orchidGroupId":348,"beforeQuantity":100,"afterQuantity":120,
                                      "beforeStatus":"정상","afterStatus":"정상"}],
                      "quantityBalances":[{"sourceInputQuantities":{"348":100},"resultQuantities":{"348":120}}]}',
                    902, '00000000-0000-0000-0000-000000000012', now());
                INSERT INTO work_correction_receipts (request_key, request_fingerprint, correction_id, created_at)
                VALUES ('correction-test', repeat('f',64), 900, now());
                INSERT INTO orchid_stock_counts (request_key, request_fingerprint, orchid_group_id, recorded_at,
                    business_date, worker, reason, memo, before_quantity, actual_quantity, mutation_id)
                VALUES ('stock-test', repeat('a',64), 348, now(), '2026-09-02', '개인작업자',
                    '실사개인사유', '실사개인메모', 120, 120, 903);
                INSERT INTO orchid_group_identity_migrations (work_operation_id, mutation_id,
                    preserved_orchid_group_id, removed_orchid_group_id, removed_group_snapshot)
                SELECT 447, 902, id, 999, to_jsonb(orchid_groups) ||
                    '{"quantity":70,"memo":"이관개인메모"}'::jsonb FROM orchid_groups;
                INSERT INTO work_command_receipts (receipt_key, result_operation_ids, created_at)
                VALUES ('chain-test', '[447]', now());
                INSERT INTO work_command_receipt_memberships VALUES ('chain-test', 447);
                """)
            with connection.cursor() as cursor:
                self.original_counts = sanitize_demo.capture_table_counts(cursor)
        with patch.dict(os.environ, {
            "SANITIZE_DB_URL": self.url, "DEMO_ANONYMIZATION_KEY": "test-key-" * 8,
            "DEMO_DATE_SHIFT_DAYS": "10", "DEMO_QUANTITY_FACTOR": "3", "DEMO_PRICE_FACTOR": "2",
        }):
            sanitize_demo.run()
            with self.assertRaisesRegex(sanitize_demo.SanitizationError, "already sanitized"):
                sanitize_demo.run()
        with self.driver.connect(self.url) as connection, connection.cursor() as cursor:
            sanitize_demo.assert_table_counts_preserved(cursor, self.original_counts)
            cursor.execute("SELECT quantity FROM orchid_groups WHERE id=348")
            self.assertEqual(cursor.fetchone()[0], 360)
            cursor.execute("SELECT estimated_quantity, inbound_date::text FROM inbound_records WHERE id=9001")
            self.assertEqual(cursor.fetchone(), (600, '2026-09-12'))
            cursor.execute("SELECT before_quantity, actual_quantity, worker, memo FROM orchid_stock_counts")
            self.assertEqual(cursor.fetchone(), (360, 360, '작업자 001', None))
            cursor.execute("SELECT result_details FROM work_operation_corrections")
            details = cursor.fetchone()[0]
            self.assertEqual(details['beforeWorkDate'], '2026-09-11')
            self.assertEqual(details['afterWorkDate'], '2026-09-12')
            self.assertEqual(details['adjustments'][0]['afterQuantity'], 360)
            self.assertEqual(details['adjustments'][0]['afterStatus'], '정상')
            self.assertEqual(details['quantityBalances'][0]['sourceInputQuantities'], {'348': 300})
            cursor.execute("SELECT removed_group_snapshot FROM orchid_group_identity_migrations")
            removed = cursor.fetchone()[0]
            self.assertEqual(removed['quantity'], 210)
            self.assertIsNone(removed['memo'])
            self.assertNotIn('개인', removed['variety_name'])
            cursor.execute("SELECT request_key, correction_id FROM work_correction_receipts")
            self.assertEqual(cursor.fetchone(), ('correction-test', 900))
            cursor.execute("SELECT receipt_key, operation_id FROM work_command_receipt_memberships")
            self.assertEqual(cursor.fetchone(), ('chain-test', 447))
            cursor.execute("SELECT state_revision_after, (after_state->>'quantity')::int "
                           "FROM orchid_group_mutation_entries ORDER BY state_revision_after")
            self.assertEqual(cursor.fetchall(), [(0, 300), (1, 360), (2, 360)])
            directory = Path(__file__).parent
            allowlist = [line.split('\t') for line in (directory / 'schema-allowlist.tsv').read_text().splitlines()]
            values = ','.join("('%s','%s')" % tuple(row) for row in allowlist)
            for script in sorted((directory / 'validate').glob('*.sql')):
                cursor.execute(script.read_text().replace(':allowlist_values', values))
            for table, column, raw in (
                ('work_operations', 'void_reason', '누락취소사유'),
                ('work_operation_corrections', 'worker', '누락보정작업자'),
                ('orchid_stock_counts', 'memo', '누락실사메모'),
            ):
                with self.assertRaises(self.driver.errors.RaiseException):
                    with connection.transaction():
                        cursor.execute(f"UPDATE {table} SET {column}=%s", (raw,))
                        cursor.execute((directory / 'validate/sensitive-data-check.sql').read_text())
            for free_text in ('2026-09-12', '00000000-0000-0000-0000-000000000001',
                              'private@example.com', '010-1234-5678'):
                with self.assertRaises(self.driver.errors.RaiseException):
                    with connection.transaction():
                        cursor.execute("UPDATE work_operations SET details=details || "
                                       "jsonb_build_object('memo', %s::text)", (free_text,))
                        cursor.execute((directory / 'validate/sensitive-data-check.sql').read_text())


if __name__ == "__main__":
    unittest.main()
