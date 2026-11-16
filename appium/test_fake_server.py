import json
import urllib.error
import urllib.request

import pytest

import fake_server


@pytest.fixture(scope="module")
def base():
    server = fake_server.start(port=0, host="127.0.0.1")
    yield f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()


def get(url):
    with urllib.request.urlopen(url) as r:
        return r.status, json.load(r)


def test_status_ok(base):
    status, body = get(base + "/v1/status")
    assert status == 200 and body["configuredFeeds"] == ["fake"]


def test_stop_search_filters_by_name(base):
    status, body = get(base + "/v1/stops?query=connell&limit=20")
    assert status == 200 and [s["id"] for s in body] == ["stop-1"]
    status, body = get(base + "/v1/stops?query=zzz&limit=20")
    assert status == 200 and body == []


def test_arrivals_for_stop(base):
    status, body = get(base + "/v1/stops/stop-1/arrivals?limit=20")
    assert status == 200 and len(body) == 2 and body[0]["tripId"] == "trip-a"


def test_unknown_path_404(base):
    for path in ("/v1/nope", "/v1/stops/missing/arrivals"):
        with pytest.raises(urllib.error.HTTPError) as e:
            get(base + path)
        assert e.value.code == 404
