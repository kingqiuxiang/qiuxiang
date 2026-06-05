import unittest

from ai_assistant.models import EndpointSpec
from ai_assistant.services.param_filler import autofill_params
from ai_assistant.services.yapi_parser import parse_yapi_payload


class ServiceTests(unittest.TestCase):
    def test_parse_yapi_payload(self) -> None:
        payload = {
            "list": [
                {
                    "path": "/api/users/{id}",
                    "method": "get",
                    "req_query": [{"name": "detail"}],
                    "req_params": [{"name": "id"}],
                    "req_body_form": [{"name": "nickname"}],
                }
            ]
        }
        endpoints = parse_yapi_payload(payload)
        self.assertEqual(len(endpoints), 1)
        self.assertEqual(endpoints[0].method, "GET")
        self.assertEqual(endpoints[0].query_params, ["detail"])
        self.assertEqual(endpoints[0].path_params, ["id"])

    def test_autofill_fallback(self) -> None:
        endpoint = EndpointSpec(
            path="/api/login",
            method="POST",
            body_params=["email", "token"],
            query_params=["page"],
        )
        result = autofill_params(endpoint=endpoint, project_root=".")
        self.assertIn("email", result.body)
        self.assertIn("token", result.body)
        self.assertIn("page", result.query)


if __name__ == "__main__":
    unittest.main()
