import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";

import { runApiTest } from "../src/lib/testRunner.js";

test("runApiTest applies path params, query and JSON body", async () => {
  const server = createServer(async (request, response) => {
    assert.equal(request.method, "POST");
    assert.equal(request.url, "/api/users/10001?source=web");
    const chunks = [];
    for await (const chunk of request) {
      chunks.push(chunk);
    }
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    assert.equal(body.email, "tester@example.com");
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ ok: true }));
  });

  await new Promise((resolve) => server.listen(0, resolve));
  const { port } = server.address();
  try {
    const result = await runApiTest({
      baseUrl: `http://127.0.0.1:${port}`,
      request: {
        method: "POST",
        path: "/api/users/{userId}",
        pathParams: { userId: 10001 },
        query: { source: "web" },
        headers: { "Content-Type": "application/json" },
        body: { email: "tester@example.com" }
      }
    });

    assert.equal(result.ok, true);
    assert.equal(result.status, 200);
    assert.deepEqual(result.response.body, { ok: true });
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
