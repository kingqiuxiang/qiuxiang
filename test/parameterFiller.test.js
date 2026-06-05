import assert from "node:assert/strict";
import test from "node:test";

import { fillParameters, guessValue } from "../src/lib/parameterFiller.js";

test("guessValue creates semantic development values", () => {
  assert.equal(guessValue("email"), "tester@example.com");
  assert.equal(guessValue("userId"), 10001);
  assert.equal(guessValue("enabled", "boolean"), true);
  assert.equal(guessValue("pageSize", "number"), 1);
});

test("fillParameters reads YAPI query and JSON schema body", () => {
  const filled = fillParameters({
    title: "创建用户",
    method: "POST",
    path: "/api/users/{userId}",
    req_query: [{ name: "source", type: "string", example: "web" }],
    req_params: [{ name: "userId", type: "integer" }],
    req_headers: [{ name: "X-Trace-Id", example: "trace-1" }],
    req_body_other: JSON.stringify({
      type: "object",
      required: ["email", "password"],
      properties: {
        email: { type: "string" },
        password: { type: "string" },
        enabled: { type: "boolean" }
      }
    })
  });

  assert.equal(filled.request.method, "POST");
  assert.equal(filled.request.path, "/api/users/{userId}");
  assert.equal(filled.request.headers["X-Trace-Id"], "trace-1");
  assert.equal(filled.request.query.source, "web");
  assert.equal(filled.request.pathParams.userId, 10001);
  assert.equal(filled.request.body.email, "tester@example.com");
  assert.equal(filled.request.body.password, "Passw0rd!");
  assert.equal(filled.request.body.enabled, true);
});
