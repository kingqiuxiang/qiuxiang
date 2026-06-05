from app.services.yapi_service import parse_yapi_content


def test_parse_yapi_category_list():
    payload = {
        "list": [
            {
                "name": "user",
                "list": [
                    {
                        "title": "Create User",
                        "method": "post",
                        "path": "/api/user/create",
                        "req_body_other": {
                            "type": "object",
                            "properties": {"name": {"type": "string"}},
                        },
                    }
                ],
            }
        ]
    }
    endpoints = parse_yapi_content(payload)
    assert len(endpoints) == 1
    assert endpoints[0].method == "POST"
    assert endpoints[0].path == "/api/user/create"


def test_parse_single_interface_object():
    payload = {
        "title": "Get User",
        "method": "get",
        "path": "/api/user/{id}",
    }
    endpoints = parse_yapi_content(payload)
    assert len(endpoints) == 1
    assert endpoints[0].method == "GET"
    assert endpoints[0].title == "Get User"
