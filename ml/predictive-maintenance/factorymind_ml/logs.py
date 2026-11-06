"""JSON log lines for the container, with the same keys as the Spring Boot services (logstash format),
so Loki queries such as ``{level="ERROR"} | json | message=~"..."`` work for every service.

Used by the Docker image (``uvicorn --log-config factorymind_ml/log_config.json``); run from a terminal,
uvicorn keeps its usual readable format.
"""

import json
import logging
from datetime import datetime, timezone

# Probes and metric scrapes: one every few seconds, no information.
QUIET_PATHS = ("/health", "/metrics")


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        line = {
            "@timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(timespec="milliseconds"),
            "level": "WARN" if record.levelname == "WARNING" else record.levelname,
            "logger_name": record.name,
            "thread_name": record.threadName,
            "message": record.getMessage(),
        }
        if record.exc_info:
            line["stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(line, ensure_ascii=False)


class QuietProbes(logging.Filter):
    """Drops the access-log lines of /health and /metrics."""

    def filter(self, record: logging.LogRecord) -> bool:
        args = record.args if isinstance(record.args, tuple) else ()
        path = str(args[2]) if len(args) > 2 else ""
        return not path.startswith(QUIET_PATHS)
