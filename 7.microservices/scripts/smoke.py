"""End-to-end assertions through the gateway; uses Python's standard library only."""

import json
import os
import secrets
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("LAB_URL", "http://localhost:8080").rstrip("/")


def request(method, path, body=None, token=None, key=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.code == expected, f"{method} {path}: expected {expected}, got {response.code}: {raw!r}"
        return json.loads(raw) if raw else None


def account():
    credentials = {"email": f"learner-{uuid.uuid4()}@example.com", "password": secrets.token_hex(16)}
    created = request("POST", "/auth/register", credentials, expected=201)
    request("POST", "/auth/register", credentials, expected=409)
    bad = {**credentials, "password": secrets.token_hex(16)}
    request("POST", "/auth/login", bad, expected=401)
    token = request("POST", "/auth/login", credentials)
    assert token["tokenType"] == "Bearer" and token["expiresIn"] == 900
    return created["id"], token["accessToken"]


def main():
    request("GET", "/orders", expected=401)
    owner, token = account()
    _, other = account()
    request("GET", "/orders", token=token + "broken", expected=401)
    order_input = {"product": "Spring Interview Book", "quantity": 2, "unitPrice": 19.99}
    key = str(uuid.uuid4())
    order = request("POST", "/orders", order_input, token, key, expected=201)
    assert order["ownerId"] == owner
    duplicate = request("POST", "/orders", order_input, token, key, expected=201)
    assert duplicate["id"] == order["id"]
    request("POST", "/orders", {**order_input, "quantity": 3}, token, key, expected=409)
    request("POST", "/orders", {**order_input, "quantity": 0}, token, str(uuid.uuid4()), expected=400)
    assert request("GET", "/orders/" + order["id"], token=token)["id"] == order["id"]
    request("GET", "/orders/" + order["id"], token=other, expected=404)
    assert request("GET", "/orders", token=other) == []
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        notifications = request("GET", "/notifications", token=token)
        matches = [item for item in notifications if item["orderId"] == order["id"]]
        if matches:
            assert len(matches) == 1, "Duplicate event produced duplicate notifications"
            assert matches[0]["message"] == "Order accepted: 2 x Spring Interview Book"
            break
        time.sleep(2)
    else:
        raise AssertionError("No notification after 60 seconds; inspect outbox and consumer logs")
    assert request("GET", "/notifications", token=other) == []
    print("PASS: registration, login, JWT rejection, validation, ownership, idempotency and Kafka notification")


if __name__ == "__main__":
    main()
