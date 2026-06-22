import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { writeBrowserCoverage } from "./coverage-output.mjs";

test("writes the Istanbul browser coverage returned by Selenium", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "ebon-e2e-coverage-"));
  const outputFile = join(directory, "nested", "selenium-e2e.json");
  const coverage = {
    "/workspace/frontend/src/App.tsx": { statementMap: {}, fnMap: {}, branchMap: {}, s: {}, f: {}, b: {} }
  };
  const driver = {
    async executeScript(script) {
      assert.match(script, /window\.__coverage__/);
      return coverage;
    }
  };
  t.after(() => rm(directory, { force: true, recursive: true }));

  await writeBrowserCoverage(driver, outputFile);

  assert.deepEqual(JSON.parse(await readFile(outputFile, "utf8")), coverage);
});

test("rejects when Selenium did not execute instrumented application code", async () => {
  const driver = {
    async executeScript() {
      return null;
    }
  };

  await assert.rejects(
    () => writeBrowserCoverage(driver, join(tmpdir(), "ebon-missing-coverage.json")),
    /Istanbul browser coverage was not produced/
  );
});
