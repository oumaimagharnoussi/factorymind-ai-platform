"""Contract of POST /predict with ai-insights-service (contracts/http/ml-predict, read by MlPredictContractTest)."""

import json

from factorymind_ml.api import PredictionRequest, PredictionResponse
from factorymind_ml.contracts import CONTRACT_DIR, render, schemas


def load(name):
    return json.loads((CONTRACT_DIR / name).read_text(encoding="utf-8"))


def keys(value):
    """The keys of a JSON document, recursively (a list: the keys of its first item)."""
    if isinstance(value, dict):
        return {key: keys(item) for key, item in value.items()}
    if isinstance(value, list):
        return [keys(value[0])] if value else []
    return None


def test_the_shared_schemas_are_generated_from_the_current_models():
    for name, schema in schemas().items():
        committed = (CONTRACT_DIR / name).read_text(encoding="utf-8")
        assert committed == render(schema), (
            f"contracts/http/ml-predict/{name} is out of date: run `python -m factorymind_ml.contracts`, "
            "then the Java contract test of ai-insights-service")


def test_the_request_example_is_what_the_api_accepts():
    PredictionRequest.model_validate(load("request.json"))


def test_the_response_example_is_a_valid_response():
    PredictionResponse.model_validate(load("response.json"))


def test_a_real_answer_has_the_shape_of_the_response_example(client):
    answer = client.post("/predict", json=load("request.json"))

    assert answer.status_code == 200
    assert keys(answer.json()) == keys(load("response.json"))
