import base64
import json
import unittest
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import main


class CallProcessorTests(unittest.TestCase):
    def setUp(self):
        self.payload = {
            "callId": "call-123",
            "callerNumber": "+1 555 010 0100",
            "direction": "INBOUND",
            "status": "IN_PROGRESS",
            "agentName": "Alex",
            "startedAt": "2026-11-18T10:00:00+05:30",
            "endedAt": None,
        }

    def test_validates_and_normalizes_event(self):
        event = main._validated_event(self.payload)
        self.assertEqual(event["call_id"], "call-123")
        self.assertEqual(event["started_at"], datetime(2026, 11, 18, 4, 30))
        self.assertEqual(event["started_at_bq"], "2026-11-18T04:30:00+00:00")
        self.assertIsNone(event["ended_at"])

    def test_rejects_bad_identity_status_and_time_order(self):
        for field, value in (
            ("callId", ""),
            ("direction", "SIDEWAYS"),
            ("direction", ["INBOUND"]),
            ("status", "UNKNOWN"),
            ("status", {"name": "COMPLETED"}),
            ("callerNumber", "invalid-number"),
            ("startedAt", "not-a-time"),
        ):
            payload = dict(self.payload, **{field: value})
            with self.subTest(field=field), self.assertRaises(ValueError):
                main._validated_event(payload)

        payload = dict(self.payload, endedAt="2026-11-18T03:00:00Z")
        with self.assertRaisesRegex(ValueError, "earlier"):
            main._validated_event(payload)

    @patch.object(main, "_bigquery")
    @patch.object(main, "_connection")
    @patch.dict("os.environ", {"BIGQUERY_TABLE": "test-project.test_dataset.call_events"})
    def test_persists_to_mysql_and_bigquery_idempotently(self, connection_factory, bq):
        cursor = MagicMock()
        connection = MagicMock()
        connection.cursor.return_value.__enter__.return_value = cursor
        connection_factory.return_value.__enter__.return_value = connection
        bq.insert_rows_json.return_value = []
        encoded = base64.b64encode(json.dumps(self.payload).encode()).decode()
        cloud_event = MagicMock()
        cloud_event.data = {"message": {"data": encoded}}

        main.process_call(cloud_event)

        cursor.execute.assert_called_once()
        self.assertEqual(cursor.execute.call_args.args[1]["call_id"], "call-123")
        connection.commit.assert_called_once()
        bq.insert_rows_json.assert_called_once()
        self.assertEqual(bq.insert_rows_json.call_args.kwargs["row_ids"], ["call-123"])

    def test_rejects_missing_pubsub_payload_before_database_access(self):
        cloud_event = MagicMock()
        cloud_event.data = {"message": {}}
        with self.assertRaisesRegex(ValueError, "missing data"):
            main.process_call(cloud_event)


if __name__ == "__main__":
    unittest.main()
