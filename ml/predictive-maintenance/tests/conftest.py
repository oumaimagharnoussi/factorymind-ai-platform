import pytest

from factorymind_ml import api
from factorymind_ml.train import save, train


@pytest.fixture(scope="session")
def model_dir(tmp_path_factory):
    """A small model trained once for the whole test run (a few seconds instead of the full training)."""
    out = tmp_path_factory.mktemp("model")
    model, card = train(lives=1000, windows_per_life=8, seed=7)
    save(model, card, out)
    return out


@pytest.fixture
def client(model_dir, monkeypatch):
    from fastapi.testclient import TestClient

    monkeypatch.setenv("MODEL_DIR", str(model_dir))
    api.predictor.cache_clear()
    yield TestClient(api.app)
    api.predictor.cache_clear()
