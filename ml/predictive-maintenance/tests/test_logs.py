import json
import logging
import logging.config
from pathlib import Path

from factorymind_ml.logs import JsonFormatter, QuietProbes

CONFIG = Path(__file__).parent.parent / "factorymind_ml" / "log_config.json"


def record(msg, *args, level=logging.INFO, name="factorymind_ml.api", exc_info=None):
    return logging.LogRecord(name, level, __file__, 1, msg, args, exc_info)


def test_one_json_object_per_line_with_the_spring_boot_keys():
    line = JsonFormatter().format(record("risk of %s is %.2f", "M-102", 0.83))

    parsed = json.loads(line)
    assert parsed["message"] == "risk of M-102 is 0.83"
    assert parsed["level"] == "INFO"
    assert parsed["logger_name"] == "factorymind_ml.api"
    assert parsed["@timestamp"].endswith("+00:00")
    assert "\n" not in line


def test_warning_is_spelled_like_logback():
    assert json.loads(JsonFormatter().format(record("x", level=logging.WARNING)))["level"] == "WARN"


def test_exception_goes_to_stack_trace():
    try:
        raise ValueError("bad window")
    except ValueError:
        import sys
        line = JsonFormatter().format(record("prediction failed", level=logging.ERROR, exc_info=sys.exc_info()))

    parsed = json.loads(line)
    assert parsed["level"] == "ERROR"
    assert "ValueError: bad window" in parsed["stack_trace"]


def access(path):
    # uvicorn.access: client, method, path, http version, status
    return record('%s - "%s %s HTTP/%s" %d', "172.18.0.5:41000", "GET", path, "1.1", 200, name="uvicorn.access")


def test_probes_and_scrapes_are_not_logged_but_predictions_are():
    quiet = QuietProbes()
    assert not quiet.filter(access("/health"))
    assert not quiet.filter(access("/metrics"))
    assert quiet.filter(access("/predict"))
    assert quiet.filter(record("Application startup complete."))


def test_the_uvicorn_config_file_is_valid():
    logging.config.dictConfig(json.loads(CONFIG.read_text()))
    try:
        handler = logging.getLogger("uvicorn.access").handlers[0]
        assert isinstance(handler.formatter, JsonFormatter)
        assert any(isinstance(f, QuietProbes) for f in handler.filters)
    finally:
        logging.config.dictConfig({"version": 1, "disable_existing_loggers": False})
