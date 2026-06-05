from app.schemas import InterfaceDefinition, InterfaceField
from app.services.parameter_filler import fill_parameters


def test_fill_parameters_heuristic_without_openai_key(monkeypatch) -> None:
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    interface = InterfaceDefinition(
        title="创建订单",
        path="/api/order/create",
        method="POST",
        query_fields=[InterfaceField(name="page", field_type="integer")],
        path_fields=[InterfaceField(name="id", field_type="integer")],
        header_fields=[InterfaceField(name="Authorization", field_type="string")],
        body_schema={
            "type": "object",
            "properties": {
                "userId": {"type": "integer"},
                "items": {"type": "array", "items": {"type": "string"}},
            },
        },
    )
    result = fill_parameters(interface, ".")
    assert result.source == "heuristic"
    assert result.query["page"] == 1
    assert result.path_params["id"] == 1
    assert "userId" in result.body
