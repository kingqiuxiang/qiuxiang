import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import JSZip from "jszip";
import { createXMindFile, parseOutline } from "../src/xmind.js";

test("parseOutline converts headings and bullets into a tree", () => {
  const nodes = parseOutline(
    [
      "# Product Plan",
      "## Goals",
      "- Improve activation",
      "- Reduce churn",
      "  - Better onboarding",
      "## Risks",
      "- Limited data"
    ].join("\n"),
    "Product Plan"
  );

  assert.deepEqual(nodes, [
    {
      title: "Goals",
      children: [
        { title: "Improve activation" },
        {
          title: "Reduce churn",
          children: [{ title: "Better onboarding" }]
        }
      ]
    },
    {
      title: "Risks",
      children: [{ title: "Limited data" }]
    }
  ]);
});

test("createXMindFile writes modern XMind JSON package", async () => {
  const tempDir = await mkdtemp(path.join(os.tmpdir(), "xmind-mcp-"));
  const outputPath = path.join(tempDir, "map.xmind");

  try {
    const createdPath = await createXMindFile({
      title: "API Test Strategy",
      outputPath,
      nodes: [
        {
          title: "Scope",
          children: [{ title: "Authentication" }, { title: "Regression" }]
        }
      ]
    });

    assert.equal(createdPath, outputPath);

    const zip = await JSZip.loadAsync(await readFile(outputPath));
    const contentFile = zip.file("content.json");
    const metadataFile = zip.file("metadata.json");
    const manifestFile = zip.file("manifest.json");

    assert.ok(contentFile);
    assert.ok(metadataFile);
    assert.ok(manifestFile);

    const content = JSON.parse(await contentFile.async("string"));
    assert.equal(content[0].title, "API Test Strategy");
    assert.equal(content[0].rootTopic.title, "API Test Strategy");
    assert.equal(content[0].rootTopic.children.attached[0].title, "Scope");
    assert.equal(content[0].rootTopic.children.attached[0].children.attached[0].title, "Authentication");
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
});
