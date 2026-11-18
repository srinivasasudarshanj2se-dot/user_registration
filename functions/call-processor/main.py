import base64
import json
import os
import re
from datetime import datetime, timezone

import functions_framework
import pymysql
from google.cloud import bigquery
from google.cloud.sql.connector import Connector, IPTypes


_connector = Connector()
_bigquery = bigquery.Client()

_ALLOWED_DIRECTIONS = {"INBOUND", "OUTBOUND"}
_ALLOWED_STATUSES = {"RINGING", "IN_PROGRESS", "COMPLETED", "MISSED"}


def _parse_timestamp(value, field_name, required=True):
    if value is None and not required:
        return None
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field_name} must be an ISO-8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{field_name} must be an ISO-8601 timestamp") from error
    if parsed.tzinfo is None:
        raise ValueError(f"{field_name} must include a timezone")
    return parsed.astimezone(timezone.utc)


def _validated_event(data):
    if not isinstance(data, dict):
        raise ValueError("Call event must be a JSON object")

    call_id = data.get("callId")
    caller_number = data.get("callerNumber")
    direction = data.get("direction")
    status = data.get("status")
    agent_name = data.get("agentName")
    if not isinstance(call_id, str) or not call_id.strip() or len(call_id) > 128:
        raise ValueError("callId is required and must be at most 128 characters")
    if (not isinstance(caller_number, str) or not caller_number.strip()
            or not re.fullmatch(r"[+0-9() .-]{7,32}", caller_number)):
        raise ValueError("callerNumber must be a 7-32 character phone number")
    if not isinstance(direction, str) or direction not in _ALLOWED_DIRECTIONS:
        raise ValueError("direction must be INBOUND or OUTBOUND")
    if not isinstance(status, str) or status not in _ALLOWED_STATUSES:
        raise ValueError("status is not supported")
    if agent_name is not None and (not isinstance(agent_name, str) or len(agent_name) > 100):
        raise ValueError("agentName must be at most 100 characters")

    started_at = _parse_timestamp(data.get("startedAt"), "startedAt")
    ended_at = _parse_timestamp(data.get("endedAt"), "endedAt", required=False)
    if ended_at is not None and ended_at < started_at:
        raise ValueError("endedAt cannot be earlier than startedAt")

    return {
        "call_id": call_id.strip(),
        "caller_number": caller_number.strip(),
        "direction": direction,
        "status": status,
        "agent_name": agent_name,
        "started_at": started_at.replace(tzinfo=None),
        "ended_at": ended_at.replace(tzinfo=None) if ended_at else None,
        "started_at_bq": started_at.isoformat(),
        "ended_at_bq": ended_at.isoformat() if ended_at else None,
    }


def _connection():
    return _connector.connect(
        os.environ["INSTANCE_CONNECTION_NAME"],
        "pymysql",
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        db=os.environ["DB_NAME"],
        ip_type=IPTypes.PUBLIC,
    )


@functions_framework.cloud_event
def process_call(cloud_event):
    message = cloud_event.data.get("message", {})
    encoded = message.get("data")
    if not encoded:
        raise ValueError("Pub/Sub message is missing data")
    try:
        payload = json.loads(base64.b64decode(encoded).decode("utf-8"))
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Pub/Sub message must contain a base64-encoded JSON call event") from error
    event = _validated_event(payload)

    with _connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO call_events
                    (call_id, caller_number, direction, status, agent_name, started_at, ended_at)
                VALUES
                    (%(call_id)s, %(caller_number)s, %(direction)s, %(status)s,
                     %(agent_name)s, %(started_at)s, %(ended_at)s)
                ON DUPLICATE KEY UPDATE
                    caller_number = VALUES(caller_number),
                    direction = VALUES(direction),
                    status = VALUES(status),
                    agent_name = VALUES(agent_name),
                    started_at = VALUES(started_at),
                    ended_at = VALUES(ended_at)
                """,
                event,
            )
        connection.commit()

    row = {
        "call_id": event["call_id"],
        "caller_number": event["caller_number"],
        "direction": event["direction"],
        "status": event["status"],
        "agent_name": event["agent_name"],
        "started_at": event["started_at_bq"],
        "ended_at": event["ended_at_bq"],
    }
    errors = _bigquery.insert_rows_json(
        os.environ["BIGQUERY_TABLE"],
        [row],
        row_ids=[event["call_id"]],
    )
    if errors:
        raise RuntimeError(f"BigQuery rejected the call event: {errors}")
