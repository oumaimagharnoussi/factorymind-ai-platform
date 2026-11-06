"""The HTTP contract of POST /predict, shared with its caller (ai-insights-service, Java).

The JSON Schemas in ``contracts/http/ml-predict`` are generated from the pydantic models below, so they cannot
drift from the code: ``tests/test_contracts.py`` fails when they differ. After changing a model, regenerate them
with ``python -m factorymind_ml.contracts`` and run the Java side
(``MlPredictContractTest`` in ai-insights-service) before merging.
"""

import json
from pathlib import Path

from .api import PredictionRequest, PredictionResponse

CONTRACT_DIR = Path(__file__).resolve().parents[3] / "contracts" / "http" / "ml-predict"
DIALECT = "https://json-schema.org/draft/2020-12/schema"


def schemas() -> dict[str, dict]:
    request = PredictionRequest.model_json_schema(by_alias=True, mode="validation")
    response = PredictionResponse.model_json_schema(by_alias=True, mode="serialization")
    return {
        "request.schema.json": {
            "$schema": DIALECT,
            "description": "Body of POST /predict (ml-service). Caller: ai-insights-service (ModelClient).",
            **request,
        },
        "response.schema.json": {
            "$schema": DIALECT,
            "description": "200 answer of POST /predict (ml-service). Reader: ai-insights-service (Prediction).",
            **response,
        },
    }


def render(schema: dict) -> str:
    return json.dumps(schema, indent=2, ensure_ascii=False) + "\n"


def main() -> None:
    CONTRACT_DIR.mkdir(parents=True, exist_ok=True)
    for name, schema in schemas().items():
        (CONTRACT_DIR / name).write_text(render(schema), encoding="utf-8")
        print(f"wrote {CONTRACT_DIR / name}")


if __name__ == "__main__":
    main()
