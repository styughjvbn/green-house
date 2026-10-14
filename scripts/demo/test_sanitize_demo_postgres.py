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
                    (902, 'CORRECTION', 'WORK', 'CORRECTION', '447', 'BATCH_VOID:cancel-447-chain-11-12-300-400-20260902-10',
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
                INSERT INTO work_creation_batch_memberships VALUES ('chain-test', 447);
                """)
            connection.execute("""
                INSERT INTO business_partners (id,created_at,updated_at,name,partner_type)
                VALUES (9001,now(),now(),'판매개인거래처','RETAIL'),(9002,now(),now(),'경매개인거래처','AUCTION_HOUSE');
                INSERT INTO sales_slips (id,created_at,updated_at,sale_date,sales_type,sales_status,payment_status,total_amount,paid_amount,remaining_amount,partner_id)
                VALUES (9101,now(),now(),'2026-09-02','DIRECT','출고 완료','입금 완료',800,1200,0,9001),
                 (9102,now(),now(),'2026-09-02','DIRECT','출고 완료','미입금',-100,0,0,9001);
                INSERT INTO sales_slip_items (id,sales_slip_id,item_name,genus,quantity,unit_price,amount,boxes)
                VALUES (9101,9101,'판매개인품종','판매개인속',10,100,1000,2),
                 (9102,9102,'판매개인품종','판매개인속',-1,100,-100,NULL);
                INSERT INTO direct_sales (sales_slip_id,partner_id,sale_date,total_amount,created_at,updated_at)
                VALUES (9101,9001,'2026-09-02',800,now(),now()), (9102,9001,'2026-09-02',-100,now(),now());
                INSERT INTO direct_sale_prices (sales_slip_id,sales_slip_item_id,priced_quantity,unit_price,amount)
                VALUES (9101,9101,10,100,1000),(9102,9102,-1,100,-100);
                INSERT INTO direct_sale_changes (id,sales_slip_id,sales_slip_item_id,type,quantity,previous_unit_price,next_unit_price,amount_delta,allocated_before,change_date,reason,request_key,fingerprint,created_at,canceled_change_id)
                VALUES (9201,9101,9101,'PRICE_ADJUSTMENT',2,100,50,-100,1200,'2026-09-03','가격개인사유','change-1',repeat('a',64),now(),NULL),
                 (9202,9101,9101,'RETURN',1,100,0,-100,1200,'2026-09-04','반품개인사유','change-2',repeat('a',64),now(),NULL),
                 (9203,9101,9101,'PRICE_ADJUSTMENT',1,100,200,100,1200,'2026-09-05','가격개인사유','change-3',repeat('a',64),now(),NULL),
                 (9204,9101,9101,'CANCEL',1,200,100,-100,1200,'2026-09-06','정정개인사유','change-4',repeat('a',64),now(),9203);
                INSERT INTO direct_return_arrivals (id,sales_slip_id,return_change_id,quantity,arrival_date,created_at,worker,canceled_at,correction_date,cancellation_reason)
                VALUES (9205,9101,9202,1,'2026-09-05',now(),'개인작업자',now(),'2026-09-06','도착개인사유');
                INSERT INTO direct_return_command_receipts (sales_slip_id,request_key,fingerprint,arrival_id,created_at)
                VALUES (9101,'direct-arrival-test',repeat('b',64),9205,now());
                INSERT INTO sales_creation_receipts (request_key,request_fingerprint,sales_slip_id,response_snapshot,created_at)
                VALUES ('sale-test',repeat('c',64),9101,'{"id":9101,"saleDate":"2026-09-02","totalAmount":1000,"paidAmount":1200,"remainingAmount":0,"items":[{"id":9101,"quantity":10,"boxes":2,"unitPrice":100,"amount":1000,"itemName":"판매개인품종"}]}',now());
                INSERT INTO inbound_creation_receipts (request_key,request_fingerprint,inbound_record_id,response_snapshot,created_at)
                VALUES ('inbound-test',repeat('d',64),9001,'{"id":9001,"inboundDate":[2026,9,2],"estimatedQuantity":200,"worker":"개인작업자"}',now());
                UPDATE work_command_receipts SET request_fingerprint=repeat('e',64),
                 response_snapshot='[{"id":447,"worker":"개인작업자","plannedStartDate":"2026-09-02","details":{"inputQuantity":100}}]'
                 WHERE receipt_key='chain-test';
                INSERT INTO partner_payment_events (id,partner_id,event_type,event_date,status,amount,unapplied_amount,created_at,updated_at)
                VALUES (9501,9001,'PAYMENT_RECEIVED','2026-09-02','FULLY_APPLIED',1200,0,now(),now());
                INSERT INTO payment_allocation_command_receipts (partner_id,request_key,fingerprint,result_json,created_at,updated_at)
                VALUES (9001,'allocation-test',repeat('f',64),'{"id":9501,"amount":1200,"allocatedAmount":1200,"memo":"수납개인메모","paymentDate":"2026-09-02"}',now(),now());
                INSERT INTO partner_settlement_settings (id,partner_id,allow_prepayment,amount_tolerance,auto_match_enabled,auto_settle_enabled,credit_auto_apply_enabled,payment_day_mode,payment_delay_days,settlement_unit,created_at,updated_at,print_template,shipper_code,shipper_name)
                VALUES (9001,9002,false,0,false,false,false,'DAYS_AFTER_AUCTION',0,'DAILY',now(),now(),'STANDARD_A5','private-shipper-9001','출하개인명');
                INSERT INTO auction_shipments (id,created_at,updated_at,shipment_date,status,auction_house_id)
                VALUES (9301,now(),now(),'2026-09-02','SHIPPED',9002);
                INSERT INTO auction_shipment_lots (id,shipment_id,created_at,updated_at,current_status,item_name,variety_name,shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,boxes,follow_up_method)
                VALUES (9301,9301,now(),now(),'PARTIALLY_RETURNED','경매개인속','경매개인품종',10,6,3,1,2,'FARM_RETURN');
                INSERT INTO auction_attempts (id,shipment_lot_id,created_at,updated_at,attempt_no,attempt_status,auction_date)
                VALUES (9301,9301,now(),now(),1,'COMPLETED','2026-09-03');
                INSERT INTO auction_result_lines (id,auction_attempt_id,created_at,updated_at,amount,auction_date,inspection_status,quantity,unit_price)
                VALUES (9301,9301,now(),now(),1199,'2026-09-03','AUTO_MATCHED',6,200);
                INSERT INTO sales_slips (id,created_at,updated_at,sale_date,sales_type,sales_status,payment_status,total_amount,paid_amount,remaining_amount,partner_id,auction_shipment_id)
                VALUES (9301,now(),now(),'2026-09-02','AUCTION','출하 완료','정산 대기',1199,0,0,9002,9301);
                INSERT INTO sales_slip_items (id,sales_slip_id,item_name,genus,quantity,unit_price,amount,auction_shipment_lot_id,boxes)
                VALUES (9301,9301,'경매개인품종','경매개인속',10,0,0,9301,2);
                INSERT INTO auction_lot_status_history (id,changed_at,new_status,previous_status,reason,worker,shipment_lot_id,previous_sold_quantity,new_sold_quantity,previous_waiting_quantity,new_waiting_quantity,previous_returned_quantity,new_returned_quantity)
                VALUES (9301,now(),'PARTIALLY_RETURNED','PARTIALLY_SOLD','경매개인사유','개인작업자',9301,6,6,4,3,0,1);
                INSERT INTO auction_proceeds (id,auction_house_id,source_reference,reported_gross_amount,receivable_amount,matching_confirmed,confirmed_by,confirmed_at,created_at,updated_at)
                VALUES (9301,9002,'private-source-9301',1199,900,true,'개인작업자',now(),now(),now()),
                 (9302,9002,NULL,NULL,NULL,false,NULL,NULL,now(),now());
                INSERT INTO auction_proceeds_results (auction_proceeds_id,auction_result_line_id) VALUES (9301,9301);
                INSERT INTO auction_follow_up_decisions (id,lot_id,method,quantity,decided_at,worker,reason)
                VALUES (9401,9301,'FARM_RETURN',4,now(),'개인작업자','반환개인사유');
                INSERT INTO auction_return_arrivals (id,decision_id,lot_id,quantity,arrival_date,created_at,worker)
                VALUES (9401,9401,9301,1,'2026-09-05',now(),'개인작업자');
                INSERT INTO auction_command_receipts (lot_id,command_type,request_key,request_fingerprint,response_snapshot,created_at)
                VALUES (9301,'ARRIVAL','auction-arrival-test',repeat('b',64),'{"followUp":{"lotId":9301,"quantity":4,"decidedAt":"2026-09-04T09:00:00"},"arrival":{"id":9401,"lotId":9301,"arrivalDate":"2026-09-05","quantity":1,"worker":"개인작업자"}}',now());
            """)
            with connection.cursor() as cursor:
                self.original_counts = sanitize_demo.capture_table_counts(cursor)
        with patch.dict(os.environ, {
            "SANITIZE_DB_URL": self.url, "DEMO_ANONYMIZATION_KEY": "test-key-" * 8,
            "DEMO_DATE_SHIFT_DAYS": "10", "DEMO_QUANTITY_FACTOR": "3", "DEMO_PRICE_FACTOR": "2",
        }):
            with patch("sanitize_demo.assert_original_values_removed", side_effect=sanitize_demo.SanitizationError("injected validation failure")):
                with self.assertRaisesRegex(sanitize_demo.SanitizationError, "injected validation failure"):
                    sanitize_demo.run()
            with self.driver.connect(self.url) as connection:
                self.assertIsNone(connection.execute("SELECT to_regclass('demo_internal.sanitization_marker')").fetchone()[0])
                self.assertEqual(connection.execute("SELECT quantity FROM orchid_groups WHERE id=348").fetchone()[0], 120)
                self.assertEqual(connection.execute("SELECT total_amount FROM direct_sales WHERE sales_slip_id=9101").fetchone()[0], 800)
                self.assertEqual(connection.execute("SELECT tgenabled FROM pg_trigger WHERE tgname='trg_orchid_group_write_fence'").fetchone()[0], 'O')
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
            cursor.execute("SELECT receipt_key, operation_id FROM work_creation_batch_memberships")
            self.assertEqual(cursor.fetchone(), ('chain-test', 447))
            cursor.execute("SELECT state_revision_after, (after_state->>'quantity')::int "
                           "FROM orchid_group_mutation_entries ORDER BY state_revision_after")
            self.assertEqual(cursor.fetchall(), [(0, 300), (1, 360), (2, 360)])
            cursor.execute("SELECT total_amount,paid_amount,remaining_amount FROM sales_slips WHERE id IN (9101,9102,9301) ORDER BY id")
            self.assertEqual(cursor.fetchall(), [(4800,7200,0),(-600,0,0),(7194,0,0)])
            cursor.execute("SELECT quantity,boxes,unit_price,amount FROM sales_slip_items WHERE id=9101")
            self.assertEqual(cursor.fetchone(), (30,6,200,6000))
            cursor.execute("SELECT quantity,previous_unit_price,next_unit_price,amount_delta,allocated_before,canceled_change_id FROM direct_sale_changes ORDER BY id")
            self.assertEqual(cursor.fetchall(), [(6,200,100,-600,7200,None),(3,200,0,-600,7200,None),(3,200,400,600,7200,None),(3,400,200,-600,7200,9203)])
            cursor.execute("SELECT quantity,arrival_date::text,correction_date::text,worker FROM direct_return_arrivals")
            self.assertEqual(cursor.fetchone(), (3,'2026-09-15','2026-09-16','작업자 001'))
            cursor.execute("SELECT arrival_id,request_key,fingerprint FROM direct_return_command_receipts")
            self.assertEqual(cursor.fetchone(), (9205,'direct-arrival-test','b'*64))
            cursor.execute("SELECT reported_gross_amount,receivable_amount,matching_confirmed FROM auction_proceeds ORDER BY id")
            self.assertEqual(cursor.fetchall(), [(7194,5400,True),(None,None,False)])
            cursor.execute("SELECT quantity,unit_price,amount,inspection_status FROM auction_result_lines")
            self.assertEqual(cursor.fetchone(), (18,400,7194,'AUTO_MATCHED'))
            cursor.execute("SELECT new_sold_quantity,new_waiting_quantity,new_returned_quantity FROM auction_lot_status_history")
            self.assertEqual(cursor.fetchone(), (18,9,3))
            cursor.execute("SELECT response_snapshot FROM sales_creation_receipts")
            receipt=cursor.fetchone()[0]
            self.assertEqual(receipt['totalAmount'],6000) # Preserve the creation-time fact, preceding adjustments.
            self.assertEqual(receipt['items'][0]['boxes'],6)
            self.assertEqual(receipt['saleDate'],'2026-09-12')
            cursor.execute("SELECT response_snapshot FROM auction_command_receipts")
            receipt=cursor.fetchone()[0]
            self.assertEqual(receipt['arrival']['quantity'],3)
            self.assertEqual(receipt['arrival']['arrivalDate'],'2026-09-15')
            self.assertEqual(receipt['followUp']['decidedAt'],'2026-09-14T09:00:00')
            cursor.execute("SELECT response_snapshot FROM inbound_creation_receipts")
            self.assertEqual(cursor.fetchone()[0]['inboundDate'],[2026,9,12])
            cursor.execute("SELECT result_json FROM payment_allocation_command_receipts")
            self.assertEqual(cursor.fetchone()[0]['allocatedAmount'],7200)
            directory = Path(__file__).parent
            allowlist = [line.split('\t') for line in (directory / 'schema-allowlist.tsv').read_text().splitlines()]
            values = ','.join("('%s','%s')" % tuple(row) for row in allowlist)
            for script in sorted((directory / 'validate').glob('*.sql')):
                cursor.execute(script.read_text().replace(':allowlist_values', values))
            for statement in (
                "UPDATE direct_sales SET total_amount=total_amount+1 WHERE sales_slip_id=9101",
                "UPDATE sales_slips SET total_amount=total_amount+1 WHERE id=9301",
                "UPDATE sales_slips SET remaining_amount=remaining_amount+1 WHERE id=9101",
            ):
                with self.assertRaises(self.driver.errors.RaiseException):
                    with connection.transaction():
                        cursor.execute(statement)
                        cursor.execute((directory / 'validate/business-invariant-check.sql').read_text())
            for table, column, raw in (
                ('work_operations', 'void_reason', '누락취소사유'),
                ('work_operation_corrections', 'worker', '누락보정작업자'),
                ('orchid_stock_counts', 'memo', '누락실사메모'),
                ('direct_sale_changes', 'reason', '누락가격사유'),
                ('direct_return_arrivals', 'cancellation_reason', '누락반품사유'),
                ('auction_follow_up_decisions', 'worker', '누락경매작업자'),
                ('partner_settlement_settings', 'shipper_name', '누락출하자'),
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
