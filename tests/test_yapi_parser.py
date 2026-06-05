from app.services.yapi_parser import parse_yapi_interfaces


def test_parse_yapi_interfaces_basic() -> None:
    payload = {
        "list": [
            {
                "_id": 1,
                "title": "用户详情",
                "path": "/api/user/{id}",
                "method": "GET",
                "req_query": [{"name": "expand", "required": "1"}],
                "req_params": [{"name": "id", "required": 1, "type": "integer"}],
                "req_headers": [{"name": "Authorization", "required": 1}],
            }
        ]
    }
    interfaces = parse_yapi_interfaces(payload)
    assert len(interfaces) == 1
    item = interfaces[0]
    assert item.path == "/api/user/{id}"
    assert item.method == "GET"
    assert item.path_fields[0].name == "id"
    assert item.path_fields[0].required is True
