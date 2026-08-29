import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  assertPaperlessMethod,
  assertPersistentStateSafe,
  buildReceiptQueue,
  cleanupTemporaryFiles,
  fetchAllPages,
  prepareReceiptBlock,
  recordReceiptDecisions,
  writeStateAtomic,
} from "./receipt-audit.mjs";

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json" },
});

test("fetchAllPages follows complete same-origin Paperless pagination", async () => {
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push([url, options]);
    return calls.length === 1
      ? json({ next: "http://paperless.local/api/documents/?page=2", results: [{ id: 1 }] })
      : json({ next: null, results: [{ id: 2 }] });
  };

  const result = await fetchAllPages({
    firstUrl: "http://paperless.local/api/documents/?page=1",
    fetchImpl,
    headers: { Authorization: "Token secret" },
    allowedOrigin: "http://paperless.local",
  });

  assert.deepEqual(result, [{ id: 1 }, { id: 2 }]);
  assert.equal(calls.length, 2);
  assert.ok(calls.every(([, options]) => options.method === "GET"));
});

test("fetchAllPages rejects a cross-origin next link before sending credentials", async () => {
  let calls = 0;
  await assert.rejects(() => fetchAllPages({
    firstUrl: "http://paperless.local/api/documents/",
    fetchImpl: async () => {
      calls += 1;
      return json({ next: "http://attacker.invalid/steal", results: [] });
    },
    headers: { Authorization: "Token secret" },
    allowedOrigin: "http://paperless.local",
  }), /CROSS_ORIGIN_NEXT/);
  assert.equal(calls, 1);
});

test("Paperless client boundary rejects every non-GET method", () => {
  assert.doesNotThrow(() => assertPaperlessMethod("GET"));
  assert.throws(() => assertPaperlessMethod("POST"), /PAPERLESS_GET_ONLY/);
  assert.throws(() => assertPaperlessMethod("DELETE"), /PAPERLESS_GET_ONLY/);
});

test("buildReceiptQueue sorts largest merchants then largest branches and reports unmatched documents", () => {
  const paperless = [1, 2, 3, 4, 5, 6].map(id => ({ id, title: `Bon ${id}` }));
  const receipts = [
    { id: 11, paperlessDocumentId: 1, storeName: "REWE", storeBranch: "Nord" },
    { id: 12, paperlessDocumentId: 2, storeName: "REWE", storeBranch: "Nord" },
    { id: 13, paperlessDocumentId: 3, storeName: "REWE", storeBranch: "Süd" },
    { id: 14, paperlessDocumentId: 4, storeName: "dm", storeBranch: "Mitte" },
    { id: 15, paperlessDocumentId: 5, storeName: "dm", storeBranch: "Mitte" },
  ];

  const queue = buildReceiptQueue(paperless, receipts);

  assert.deepEqual(queue.merchants.map(entry => [entry.merchantKey, entry.receiptCount]), [
    ["rewe", 3],
    ["dm", 2],
    ["unmatched-paperless", 1],
  ]);
  assert.deepEqual(queue.merchants[0].branches.map(entry => [entry.branchKey, entry.receiptCount]), [
    ["nord", 2],
    ["sud", 1],
  ]);
  assert.deepEqual(queue.unmatchedPaperlessDocumentIds, [6]);
});

test("prepareReceiptBlock reparses with exact manual-safe no-AI options", async () => {
  const root = await mkdtemp(join(tmpdir(), "ebon-receipt-test-"));
  const calls = [];
  const state = {
    version: 1,
    runId: "receipt-run",
    merchants: [{
      merchantKey: "rewe",
      receiptCount: 1,
      branches: [{
        branchKey: "nord",
        status: "PENDING",
        documents: [{ paperlessDocumentId: 7, receiptId: 70 }],
      }],
    }],
  };

  const result = await prepareReceiptBlock({
    stateDir: root,
    state,
    clients: {
      paperless: { getDocument: async id => ({ id, content: "MILCH 1,99" }) },
      ebon: {
        reparseReceipt: async (id, options) => {
          calls.push([id, options]);
          return { id, paperlessDocumentId: 7, rawText: "MILCH 1,99", items: [] };
        },
        getParseTrace: async () => [],
      },
    },
  });

  assert.deepEqual(calls, [[70, {
    overwriteManualEdits: false,
    useAiFallback: false,
    rawTextSource: "PAPERLESS",
  }]]);
  assert.match(result.batchPath, /receipt-rewe-nord\.json$/);
  assert.match(await readFile(result.batchPath, "utf8"), /MILCH 1,99/);
});

test("persistent state guard rejects private receipt-shaped fields", () => {
  assert.throws(() => assertPersistentStateSafe({ rawText: "MILCH 1,99" }), /PRIVATE_FIELD_FORBIDDEN/);
  assert.throws(() => assertPersistentStateSafe({ nested: { description: "MILCH" } }), /PRIVATE_FIELD_FORBIDDEN/);
  assert.doesNotThrow(() => assertPersistentStateSafe({ merchantKey: "rewe", receiptId: 7, reasonCodes: [] }));
});

test("recordReceiptDecisions is resumable and deletes its temporary block", async () => {
  const root = await mkdtemp(join(tmpdir(), "ebon-receipt-record-"));
  const temp = join(root, "tmp", "receipt-rewe-nord.json");
  await mkdir(join(root, "tmp"), { recursive: true });
  await writeFile(temp, "private receipt", "utf8");
  await writeStateAtomic(root, {
    version: 1,
    runId: "receipt-run",
    activeBatchPath: temp,
    merchants: [{ merchantKey: "rewe", branches: [{
      branchKey: "nord",
      status: "IN_PROGRESS",
      documents: [{ paperlessDocumentId: 7, receiptId: 70, status: "PENDING" }],
    }] }],
  });
  const decisions = {
    runId: "receipt-run",
    merchantKey: "rewe",
    branchKey: "nord",
    receipts: [{ paperlessDocumentId: 7, receiptId: 70, status: "VERIFIED", reasonCodes: [] }],
  };

  await recordReceiptDecisions({ stateDir: root, decisions });
  await recordReceiptDecisions({ stateDir: root, decisions });

  const persisted = JSON.parse(await readFile(join(root, "progress.json"), "utf8"));
  assert.equal(persisted.merchants[0].branches[0].documents[0].status, "VERIFIED");
  assert.equal(persisted.merchants[0].branches[0].status, "COMPLETED");
  await assert.rejects(() => readFile(temp, "utf8"), error => error.code === "ENOENT");
});

test("recordReceiptDecisions rejects duplicate decisions that omit another receipt", async () => {
  const root = await mkdtemp(join(tmpdir(), "ebon-receipt-duplicate-"));
  await writeStateAtomic(root, {
    version: 1,
    runId: "receipt-run",
    activeBatchPath: null,
    merchants: [{ merchantKey: "rewe", branches: [{
      branchKey: "nord",
      status: "IN_PROGRESS",
      documents: [
        { paperlessDocumentId: 7, receiptId: 70, status: "PENDING" },
        { paperlessDocumentId: 8, receiptId: 80, status: "PENDING" },
      ],
    }] }],
  });
  const duplicate = { paperlessDocumentId: 7, receiptId: 70, status: "VERIFIED", reasonCodes: [] };

  await assert.rejects(() => recordReceiptDecisions({
    stateDir: root,
    decisions: { runId: "receipt-run", merchantKey: "rewe", branchKey: "nord", receipts: [duplicate, duplicate] },
  }), /INCOMPLETE_DECISIONS/);
});

test("recordReceiptDecisions refuses to delete a batch path outside state tmp", async () => {
  const parent = await mkdtemp(join(tmpdir(), "ebon-receipt-unsafe-"));
  const root = join(parent, "state");
  const outside = join(parent, "keep.txt");
  await writeFile(outside, "keep", "utf8");
  await writeStateAtomic(root, {
    version: 1,
    runId: "receipt-run",
    activeBatchPath: outside,
    merchants: [{ merchantKey: "rewe", branches: [{
      branchKey: "nord",
      status: "IN_PROGRESS",
      documents: [{ paperlessDocumentId: 7, receiptId: 70, status: "PENDING" }],
    }] }],
  });

  await assert.rejects(() => recordReceiptDecisions({
    stateDir: root,
    decisions: {
      runId: "receipt-run",
      merchantKey: "rewe",
      branchKey: "nord",
      receipts: [{ paperlessDocumentId: 7, receiptId: 70, status: "VERIFIED", reasonCodes: [] }],
    },
  }), /UNSAFE_BATCH_PATH/);
  assert.equal(await readFile(outside, "utf8"), "keep");
});

test("cleanupTemporaryFiles removes only files below the state tmp directory", async () => {
  const parent = await mkdtemp(join(tmpdir(), "ebon-receipt-clean-"));
  const root = join(parent, "state");
  const sibling = join(parent, "keep.txt");
  await mkdir(join(root, "tmp"), { recursive: true });
  await writeFile(join(root, "tmp", "delete.json"), "private", "utf8");
  await writeFile(sibling, "keep", "utf8");

  assert.equal(await cleanupTemporaryFiles(root), 1);
  assert.equal(await readFile(sibling, "utf8"), "keep");
});
