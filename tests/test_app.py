import os

os.environ["APP_VERSION"] = "4.2.0"
os.environ["APP_ENV"] = "UAT"
os.environ["APP_HEALTHY"] = "true"

from app.app import app


def test_health():
    client = app.test_client()

    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json()["status"] == "UP"


def test_payment():
    client = app.test_client()

    response = client.get("/payment")

    assert response.status_code == 200
    assert response.get_json()["payment"] == "FIXED"